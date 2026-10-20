package com.harkquant.terminal.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min

/** Loading state shared by every screen. `stale` keeps the last good data visible while a refresh fails. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ok<T>(val data: T, val stale: Boolean = false, val error: String? = null) : Load<T>
    data class Err(val message: String) : Load<Nothing>

    val value: T? get() = (this as? Ok<T>)?.data
}

fun <T> Load<T>.failedWith(msg: String): Load<T> = when (this) {
    is Load.Ok -> copy(stale = true, error = msg)
    else -> Load.Err(msg)
}

enum class Conn { CONNECTING, LIVE, POLLING, OFFLINE }

data class PriceSnap(
    val symbol: String,
    val last: Double,
    val change: Double,
    val changePct: Double,
    val spark: List<Bar>,
    val source: String,
)

/**
 * Live market state for the whole app. Prefers the SSE stream (/api/stream: ticker, summary, item);
 * when it drops, it falls back to polling at the user's refresh interval and keeps retrying the stream.
 */
class Repository(val api: HqApi, private val scope: CoroutineScope) {
    private val _summary = MutableStateFlow<Load<Summary>>(Load.Loading)
    val summary: StateFlow<Load<Summary>> = _summary.asStateFlow()

    private val _ticker = MutableStateFlow<Ticker?>(null)
    val ticker: StateFlow<Ticker?> = _ticker.asStateFlow()

    private val _prices = MutableStateFlow<Map<String, PriceSnap>>(emptyMap())
    val prices: StateFlow<Map<String, PriceSnap>> = _prices.asStateFlow()

    private val _news = MutableStateFlow<Load<List<NewsItem>>>(Load.Loading)
    val news: StateFlow<Load<List<NewsItem>>> = _news.asStateFlow()

    private val _symbols = MutableStateFlow<List<SymbolInfo>>(DEFAULT_SYMBOLS)
    val symbols: StateFlow<List<SymbolInfo>> = _symbols.asStateFlow()

    private val _conn = MutableStateFlow(Conn.CONNECTING)
    val conn: StateFlow<Conn> = _conn.asStateFlow()

    private val _lastUpdate = MutableStateFlow(0L)
    val lastUpdate: StateFlow<Long> = _lastUpdate.asStateFlow()

    /** New headlines as they arrive on the stream (for notifications). */
    private val _incoming = MutableSharedFlow<NewsItem>(extraBufferCapacity = 32)
    val incoming: SharedFlow<NewsItem> = _incoming.asSharedFlow()

    @Volatile var refreshSec: Int = 20
    private var liveJob: Job? = null
    private var pollJob: Job? = null

    val isRunning: Boolean get() = liveJob?.isActive == true

    fun start() {
        if (liveJob?.isActive == true) return
        liveJob = scope.launch {
            launch { refreshAll() }
            launch { streamLoop() }
            launch { priceLoop() }
        }
    }

    fun stop() {
        liveJob?.cancel(); liveJob = null
        pollJob?.cancel(); pollJob = null
    }

    /** Restart everything, e.g. after the server URL changed. */
    fun restart() {
        stop()
        _summary.value = Load.Loading; _news.value = Load.Loading; _ticker.value = null; _prices.value = emptyMap()
        _conn.value = Conn.CONNECTING
        start()
    }

    suspend fun refreshAll() = coroutineScope {
        val jobs = listOf(
            async { loadSummary() }, async { loadTicker() }, async { loadNews() },
            async { loadPrices() }, async { loadSymbols() },
        )
        jobs.forEach { it.await() }
    }

    private suspend fun loadSummary() {
        runCatching { api.summary() }
            .onSuccess { _summary.value = Load.Ok(it); touch() }
            .onFailure { e -> _summary.update { it.failedWith(e.friendly()) } }
    }

    private suspend fun loadTicker() { runCatching { api.ticker() }.onSuccess { _ticker.value = it } }

    private suspend fun loadNews() {
        runCatching { api.feed(limit = 120) }
            .onSuccess { _news.value = Load.Ok(it.items) }
            .onFailure { e -> _news.update { it.failedWith(e.friendly()) } }
    }

    private suspend fun loadSymbols() {
        if (_symbols.value !== DEFAULT_SYMBOLS) return
        runCatching { api.symbols() }.onSuccess { if (it.symbols.isNotEmpty()) _symbols.value = it.symbols }
    }

    suspend fun loadPrices() = coroutineScope {
        PRICE_SYMBOLS.map { s -> async { s to runCatching { api.bars(s, "15m", 120) }.getOrNull() } }.forEach { d ->
            val (sym, r) = d.await()
            if (r != null && r.bars.isNotEmpty()) _prices.update { it + (sym to snapOf(sym, r)) }
        }
    }

    private suspend fun priceLoop() {
        while (scope.isActive) {
            delay(refreshSec.coerceAtLeast(10) * 1000L)
            loadPrices()
        }
    }

    private suspend fun streamLoop() {
        var backoff = 2_000L
        while (scope.isActive) {
            try {
                api.stream("/api/stream").collect { ev ->
                    when (ev.event) {
                        "open" -> { _conn.value = Conn.LIVE; backoff = 2_000L; pollJob?.cancel() }
                        "ticker" -> runCatching { HqJson.decodeFromString(Ticker.serializer(), ev.data) }.onSuccess { _ticker.value = it; touch() }
                        "summary" -> runCatching { HqJson.decodeFromString(Summary.serializer(), ev.data) }.onSuccess { _summary.value = Load.Ok(it); touch() }
                        "item" -> runCatching { HqJson.decodeFromString(NewsItem.serializer(), ev.data) }.onSuccess { addNews(it) }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Throwable) {
                // fall through to polling
            }
            ensurePolling()
            delay(backoff)
            backoff = min(backoff * 2, 60_000L)
        }
    }

    private fun ensurePolling() {
        if (pollJob?.isActive == true) return
        _conn.value = if (_summary.value is Load.Ok && !(_summary.value as Load.Ok).stale) Conn.POLLING else Conn.OFFLINE
        pollJob = scope.launch {
            while (isActive) {
                loadSummary(); loadTicker()
                _conn.value = if ((_summary.value as? Load.Ok)?.stale == false) Conn.POLLING else Conn.OFFLINE
                delay(refreshSec.coerceAtLeast(5) * 1000L)
            }
        }
    }

    private fun addNews(item: NewsItem) {
        _news.update { cur ->
            val list = cur.value.orEmpty()
            if (list.any { it.id == item.id }) cur else Load.Ok((listOf(item) + list).take(300))
        }
        _incoming.tryEmit(item)
    }

    private fun touch() { _lastUpdate.value = System.currentTimeMillis() }

    companion object {
        val PRICE_SYMBOLS = listOf("BTCUSD", "XAUUSD")

        fun snapOf(sym: String, r: BarsResponse): PriceSnap {
            val bars = r.bars
            val last = bars.last().close
            // change vs ~24h ago on 15m bars (96 bars), or the first bar we have
            val ref = bars[maxOf(0, bars.size - 97)].close
            val ch = last - ref
            return PriceSnap(sym, last, ch, if (ref != 0.0) ch / ref * 100 else 0.0, bars.takeLast(96), r.source)
        }

        val DEFAULT_SYMBOLS = listOf(
            SymbolInfo("BTCUSD", "crypto", "Bitcoin", "BITSTAMP:BTCUSD"),
            SymbolInfo("ETHUSD", "crypto", "Ethereum", "BITSTAMP:ETHUSD"),
            SymbolInfo("XAUUSD", "metal", "Gold (spot)", "OANDA:XAUUSD"),
            SymbolInfo("XAGUSD", "metal", "Silver (spot)", "OANDA:XAGUSD"),
            SymbolInfo("EURUSD", "fx", "Euro / US Dollar", "FX:EURUSD"),
        )
    }
}
