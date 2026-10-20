package com.harkquant.terminal.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ApiException(val code: Int, message: String) : IOException(message)

/**
 * Thin HTTP client for the HarkQuant Terminal FastAPI server. Read-only market endpoints plus the
 * position-size tool and the token-protected settings POST. Nothing here can place an order.
 */
class HqApi(
    private val baseUrl: () -> String,
    private val token: () -> String?,
    client: OkHttpClient? = null,
) {
    val http: OkHttpClient = client ?: OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // The server pings every 5-15 s, so 45 s of silence means the stream is dead.
    private val sseHttp: OkHttpClient = http.newBuilder().readTimeout(45, TimeUnit.SECONDS).callTimeout(0, TimeUnit.SECONDS).build()

    fun url(path: String, params: Map<String, Any?> = emptyMap()): HttpUrl {
        val root = normalizeBase(baseUrl()).toHttpUrlOrNull() ?: throw ApiException(0, "Invalid server URL: ${baseUrl()}")
        val b = root.newBuilder()
        path.trim('/').split('/').filter { it.isNotEmpty() }.forEach { b.addPathSegment(it) }
        params.forEach { (k, v) -> if (v != null) b.addQueryParameter(k, v.toString()) }
        return b.build()
    }

    suspend fun raw(path: String, params: Map<String, Any?> = emptyMap(), body: String? = null, auth: Boolean = false): String {
        val rb = Request.Builder().url(url(path, params)).header("Accept", "application/json")
        if (body != null) rb.post(body.toRequestBody(JSON_TYPE))
        if (auth) token()?.takeIf { it.isNotBlank() }?.let { rb.header("Authorization", "Bearer ${it.trim()}") }
        val resp = http.newCall(rb.build()).await()
        return withContext(Dispatchers.IO) {
            resp.use { r ->
                val text = r.body?.string().orEmpty()
                if (!r.isSuccessful) throw ApiException(r.code, errorMessage(r.code, text))
                text
            }
        }
    }

    suspend inline fun <reified T> get(path: String, params: Map<String, Any?> = emptyMap()): T {
        val text = raw(path, params)
        return withContext(Dispatchers.Default) { HqJson.decodeFromString<T>(text) }
    }

    // ---------------------------------------------------------------- endpoints
    suspend fun health(): Health = get("/health")
    suspend fun summary(): Summary = get("/api/summary")
    suspend fun ticker(): Ticker = get("/api/ticker")
    suspend fun bars(symbol: String, tf: String, limit: Int = 300): BarsResponse =
        get("/api/bars", mapOf("symbol" to symbol, "tf" to tf, "limit" to limit.coerceIn(50, 1500)))
    suspend fun analysis(symbol: String): Analysis = get("/api/analysis", mapOf("symbol" to symbol))
    suspend fun ratings(symbol: String): Technical = get("/api/ratings", mapOf("symbol" to symbol))
    suspend fun flow(symbol: String): com.harkquant.terminal.data.Flow = get("/api/flow", mapOf("symbol" to symbol))
    suspend fun orderbook(symbol: String): OrderBook = get("/api/orderbook", mapOf("symbol" to symbol))
    suspend fun positioning(symbol: String): Positioning = get("/api/positioning", mapOf("symbol" to symbol))
    suspend fun redFolder(): RedFolder = get("/api/redfolder")
    suspend fun symbols(): SymbolsResponse = get("/api/symbols")
    suspend fun status(): ServerStatus = get("/api/status")
    suspend fun indicators(): IndicatorCatalog = get("/api/indicators")
    suspend fun atr(symbol: String, tf: String, length: Int = 14): AtrResult =
        get("/api/tools/atr", mapOf("symbol" to symbol, "tf" to tf, "length" to length))
    suspend fun pivots(symbol: String): Pivots = get("/api/tools/pivots", mapOf("symbol" to symbol))
    suspend fun sessions(): Sessions = get("/api/tools/sessions")

    suspend fun position(req: PositionRequest): PositionResult {
        val text = raw("/api/tools/position", body = HqJson.encodeToString(PositionRequest.serializer(), req))
        return HqJson.decodeFromString(PositionResult.serializer(), text)
    }

    suspend fun indicator(id: String, symbol: String, tf: String, params: JsonObject, limit: Int = 600): IndicatorResult {
        val body = buildString {
            append("{\"id\":").append(HqJson.encodeToString(kotlinx.serialization.serializer<String>(), id))
            append(",\"symbol\":\"").append(symbol).append("\",\"tf\":\"").append(tf).append("\"")
            append(",\"limit\":").append(limit).append(",\"params\":").append(params.toString()).append('}')
        }
        return HqJson.decodeFromString(IndicatorResult.serializer(), raw("/api/indicator", body = body))
    }

    suspend fun serverSettings(): JsonObject = HqJson.parseToJsonElement(raw("/api/settings")).jsonObject

    /** POST a partial settings patch; the server merges it. Needs the HQT token when the server has one. */
    suspend fun saveServerSettings(patch: JsonObject): JsonObject =
        HqJson.parseToJsonElement(raw("/api/settings", body = patch.toString(), auth = true)).jsonObject

    suspend fun feed(impact: String? = null, asset: String? = null, q: String? = null, limit: Int = 150): Feed =
        get("/api/news/feed", mapOf("impact" to impact, "asset" to asset, "q" to q?.takeIf { it.isNotBlank() }, "limit" to limit))
    suspend fun calendar(range: String = "week", impact: String? = null): Calendar =
        get("/api/news/calendar", mapOf("range" to range, "impact" to impact))
    suspend fun sentiment(hours: Double = 6.0): Sentiment = get("/api/news/sentiment", mapOf("hours" to hours))
    suspend fun newsItem(id: String): NewsItem = get("/api/news/item/$id")

    /** Server-Sent Events as a cold Flow. Completes (or throws) when the connection drops. */
    fun stream(path: String = "/api/stream"): Flow<SseEvent> = callbackFlow {
        val req = Request.Builder().url(url(path)).header("Accept", "text/event-stream").build()
        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                trySend(SseEvent(type ?: "message", data, id))
            }
            override fun onOpen(eventSource: EventSource, response: Response) {
                trySend(SseEvent("open", ""))
            }
            override fun onClosed(eventSource: EventSource) { close() }
            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                close(t ?: ApiException(response?.code ?: 0, "stream closed (${response?.code ?: "no response"})"))
            }
        }
        val es = EventSources.createFactory(sseHttp).newEventSource(req, listener)
        awaitClose { es.cancel() }
    }

    companion object {
        val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

        fun normalizeBase(raw: String): String {
            var s = raw.trim().trimEnd('/')
            if (s.isEmpty()) return s
            if (!s.startsWith("http://") && !s.startsWith("https://")) s = "https://$s"
            return s
        }

        fun errorMessage(code: Int, body: String): String {
            val detail = runCatching {
                val o = HqJson.parseToJsonElement(body).jsonObject
                (o["error"] ?: o["detail"])?.let { el -> runCatching { el.jsonPrimitive.contentOrNull }.getOrNull() ?: el.toString() }
            }.getOrNull()
            return when {
                code == 401 -> "Token required: enter the server's HQT token in Settings"
                detail != null -> "HTTP $code · $detail"
                else -> "HTTP $code"
            }
        }
    }
}

suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { if (!cont.isCancelled) cont.resumeWithException(e) }
        override fun onResponse(call: Call, response: Response) { cont.resume(response) }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}

/** Human message for any failure, suitable for an error state. */
fun Throwable.friendly(): String = when (this) {
    is ApiException -> message ?: "HTTP $code"
    is java.net.UnknownHostException -> "No connection: can't reach the server"
    is java.net.SocketTimeoutException -> "The server took too long to answer"
    is java.net.ConnectException -> "Can't connect to the server"
    is kotlinx.serialization.SerializationException -> "Unexpected data from the server"
    else -> message ?: this::class.java.simpleName
}
