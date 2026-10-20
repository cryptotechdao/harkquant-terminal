package com.harkquant.terminal

import com.harkquant.terminal.data.ApiException
import com.harkquant.terminal.data.HqApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ApiTest {
    private lateinit var server: MockWebServer
    private var token: String? = null
    private lateinit var api: HqApi

    @Before fun setUp() {
        server = MockWebServer(); server.start()
        api = HqApi({ server.url("/").toString() }, { token })
    }

    @After fun tearDown() { server.shutdown() }

    @Test fun normalizeBase() {
        assertEquals("https://hqt.example.io", HqApi.normalizeBase(" hqt.example.io/ "))
        assertEquals("http://192.168.1.5:8000", HqApi.normalizeBase("http://192.168.1.5:8000/"))
    }

    @Test fun buildsQueryAndParsesBars() = runBlocking {
        server.enqueue(MockResponse().setBody(Samples.text("bars_btc.json")))
        val r = api.bars("BTCUSD", "1h", 9999)
        val req = server.takeRequest()
        assertEquals("/api/bars?symbol=BTCUSD&tf=1h&limit=1500", req.path)
        assertTrue(r.bars.isNotEmpty())
    }

    @Test fun newsRouterPrefix() = runBlocking {
        server.enqueue(MockResponse().setBody(Samples.text("feed.json")))
        api.feed(impact = "HIGH+", asset = null, q = " ", limit = 50)
        assertEquals("/api/news/feed?impact=HIGH%2B&limit=50", server.takeRequest().path)
    }

    @Test fun tokenOnlyOnAuthorisedPost() = runBlocking {
        token = "secret-token"
        server.enqueue(MockResponse().setBody(Samples.text("bars_btc.json")))
        api.bars("BTCUSD", "15m")
        assertEquals(null, server.takeRequest().getHeader("Authorization"))
        server.enqueue(MockResponse().setBody("""{"settings":{}}"""))
        api.saveServerSettings(JsonObject(emptyMap()))
        val post = server.takeRequest()
        assertEquals("POST", post.method)
        assertEquals("Bearer secret-token", post.getHeader("Authorization"))
    }

    @Test fun unauthorizedGivesFriendlyMessage() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"token required (HQT_TOKEN is set on this server)"}"""))
        try { api.saveServerSettings(JsonObject(emptyMap())); fail() } catch (e: ApiException) {
            assertEquals(401, e.code)
            assertTrue(e.message!!.contains("Token required"))
        }
        server.enqueue(MockResponse().setResponseCode(502).setBody("""{"error":"ConnectError: boom","source_failed":true}"""))
        try { api.flow("XAUUSD"); fail() } catch (e: ApiException) { assertEquals("HTTP 502 · ConnectError: boom", e.message) }
    }

    @Test fun sseStreamParsesEvents() = runBlocking {
        val body = "retry: 3000\n\nevent: ticker\ndata: {\"headlines\":[]}\n\n: ping 1\n\nevent: item\nid: abc\ndata: {\"id\":\"abc\",\"title\":\"t\"}\n\n"
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body))
        val evs = api.stream("/api/stream").take(3).toList()
        assertEquals(listOf("open", "ticker", "item"), evs.map { it.event })
        assertEquals("abc", evs[2].id)
    }
}
