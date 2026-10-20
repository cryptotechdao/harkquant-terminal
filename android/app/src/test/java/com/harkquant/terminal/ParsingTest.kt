package com.harkquant.terminal

import com.harkquant.terminal.data.Analysis
import com.harkquant.terminal.data.AtrResult
import com.harkquant.terminal.data.BarsResponse
import com.harkquant.terminal.data.Calendar
import com.harkquant.terminal.data.Feed
import com.harkquant.terminal.data.Flow
import com.harkquant.terminal.data.HqJson
import com.harkquant.terminal.data.IndicatorCatalog
import com.harkquant.terminal.data.IndicatorResult
import com.harkquant.terminal.data.OrderBook
import com.harkquant.terminal.data.Pivots
import com.harkquant.terminal.data.PositionResult
import com.harkquant.terminal.data.Positioning
import com.harkquant.terminal.data.Repository
import com.harkquant.terminal.data.Sentiment
import com.harkquant.terminal.data.ServerStatus
import com.harkquant.terminal.data.Sessions
import com.harkquant.terminal.data.Summary
import com.harkquant.terminal.data.SymbolsResponse
import com.harkquant.terminal.data.Technical
import com.harkquant.terminal.data.Ticker
import com.harkquant.terminal.ui.NewsViewModel
import com.harkquant.terminal.util.Pkt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsingTest {
    /** The server writes "2026-10-20 17:15:05 PKT"; our formatter must agree with it for every timestamp. */
    private fun assertPkt(ts: Double, serverPkt: String) {
        if (serverPkt.isBlank()) return
        val ours = Pkt.at(ts).toLocalDateTime().toString().replace('T', ' ').let { if (it.length == 16) "$it:00" else it }
        assertEquals("ts=$ts", serverPkt.removeSuffix(" PKT"), ours)
    }

    @Test fun summary() {
        val s = Samples.load("summary.json", Summary.serializer())
        assertTrue(s.analysis.containsKey("BTCUSD") && s.analysis.containsKey("XAUUSD"))
        val btc = s.analysis.getValue("BTCUSD")
        assertEquals(6, btc.components.size)
        assertTrue(btc.label in setOf("BULLISH", "BEARISH", "NEUTRAL"))
        assertNotNull(btc.details?.technical)
        assertTrue(btc.details!!.technical!!.tfs.isNotEmpty())
        assertTrue(s.narrative.isNotEmpty())
        assertNotNull(s.sentiment?.xau)
        assertEquals(55, s.sentiment!!.fearGreed!!.value)
        s.redFolder.forEach { assertPkt(it.ts, it.timePkt) }
        s.topNews.forEach { assertPkt(it.ts, it.timePkt) }
        // fear_greed component has null score for metals -> parsed as null, not crash
        val xau = s.analysis.getValue("XAUUSD")
        assertTrue(xau.components.any { it.id == "fear_greed" && it.score == null && !it.used })
    }

    @Test fun ticker() {
        val t = Samples.load("ticker.json", Ticker.serializer())
        assertTrue(t.headlines.isNotEmpty())
        t.headlines.forEach { assertPkt(it.ts, it.timePkt) }
        t.events.forEach { assertPkt(it.ts, it.timePkt) }
        assertEquals(38, t.settings!!.speed)
    }

    @Test fun barsAndPriceSnapshot() {
        val b = Samples.load("bars_xau.json", BarsResponse.serializer())
        assertEquals("XAUUSD", b.symbol)
        assertTrue(b.bars.size >= 100)
        assertTrue(b.bars.zipWithNext().all { (a, c) -> c.time > a.time })
        assertTrue(b.bars.all { it.high >= it.low && it.high >= it.close && it.low <= it.open })
        val snap = Repository.snapOf("XAUUSD", b)
        assertEquals(b.bars.last().close, snap.last, 1e-9)
        assertEquals(96, snap.spark.size)
        val ref = b.bars[b.bars.size - 97].close
        assertEquals((b.bars.last().close - ref) / ref * 100, snap.changePct, 1e-9)
    }

    @Test fun analysisDetails() {
        val a = Samples.load("analysis.json", Analysis.serializer())
        assertEquals("XAUUSD", a.symbol)
        assertTrue(a.notes.isNotBlank())
        assertNotNull(a.details!!.flow)
        assertTrue(a.details!!.flow!!.venues.isNotEmpty())
        assertTrue(a.status.containsKey("technical"))
    }

    @Test fun flowOrderbookPositioningRatings() {
        val f = Samples.load("flow.json", Flow.serializer())
        assertTrue(f.venues.first().series.isNotEmpty())
        assertNotNull(f.venues.first().w1h.buyPct)
        val ob = Samples.load("orderbook.json", OrderBook.serializer())
        assertTrue(ob.available)
        assertTrue(ob.bands.containsKey("0.5%"))
        val p = Samples.load("positioning.json", Positioning.serializer())
        assertTrue(p.longShort.isNotEmpty())
        assertTrue(p.proxy)
        val r = Samples.load("ratings.json", Technical.serializer())
        assertTrue(r.tfs.values.all { it.summary.recommendation.isNotBlank() })
    }

    @Test fun newsCalendarSentiment() {
        val f = Samples.load("feed.json", Feed.serializer())
        assertTrue(f.items.isNotEmpty())
        f.items.forEach { assertPkt(it.ts, it.timePkt) }
        val c = Samples.load("calendar.json", Calendar.serializer())
        assertTrue(c.events.size > 10)
        c.events.forEach { assertPkt(it.ts, it.timePkt) }
        assertEquals("week", c.range)
        val s = Samples.load("sentiment.json", Sentiment.serializer())
        assertEquals(2, s.assets().size)
    }

    @Test fun toolsAndCatalog() {
        val cat = Samples.load("indicators.json", IndicatorCatalog.serializer())
        assertTrue(cat.total > 100)
        assertTrue(cat.groups.flatMap { it.items }.any { it.id == "talib:EMA" })
        val ema = Samples.load("ind_ema.json", IndicatorResult.serializer())
        assertEquals("price", ema.pane)
        assertTrue(ema.lines.first().data.isNotEmpty())
        val rsi = Samples.load("ind_rsi.json", IndicatorResult.serializer())
        assertEquals("own", rsi.pane)
        val piv = Samples.load("pivots.json", Pivots.serializer())
        assertTrue(piv.camarilla.containsKey("R4"))
        assertTrue(piv.method("woodie").containsKey("P"))
        val ses = Samples.load("sessions.json", Sessions.serializer())
        assertEquals(4, ses.sessions.size)
        val atr = Samples.load("atr.json", AtrResult.serializer())
        assertTrue(atr.atr > 0)
        val pos = Samples.load("position.json", PositionResult.serializer())
        assertEquals(0.1, pos.lotsRounded, 1e-9)
        assertEquals("long", pos.side)
    }

    @Test fun symbolsStatus() {
        val s = Samples.load("symbols.json", SymbolsResponse.serializer())
        assertTrue(s.symbols.any { it.symbol == "XAUUSD" && it.tvw == "OANDA:XAUUSD" })
        val st = Samples.load("status.json", ServerStatus.serializer())
        assertTrue(st.market.isNotEmpty())
    }

    @Test fun lenientDecodingOfNullsAndUnknownKeys() {
        val a = HqJson.decodeFromString(Analysis.serializer(), """{"score":null,"label":"BULLISH","new_field":{"x":1},"components":[{"id":"x","score":null,"weight":0.1}]}""")
        assertEquals(0.0, a.score, 0.0)
        assertEquals("BULLISH", a.label)
        val b = HqJson.decodeFromString(BarsResponse.serializer(), """{"bars":[{"time":1,"open":1,"high":2,"low":0.5,"close":1.5}]}""")
        assertEquals(0.0, b.bars.first().volume, 0.0)
    }

    @Test fun newsFilter() {
        val f = Samples.load("feed.json", Feed.serializer()).items
        val high = NewsViewModel.filterNews(f, "HIGH+", "ALL", "")
        assertTrue(high.all { it.impact == "HIGH" || it.impact == "CRITICAL" })
        val btc = NewsViewModel.filterNews(f, "ALL", "BTC", "")
        assertTrue(btc.all { "BTC" in it.assets })
        val sorted = NewsViewModel.filterNews(f, "ALL", "ALL", "")
        assertEquals(f.size, sorted.size)
        assertTrue(sorted.zipWithNext().all { (a, b) -> a.ts >= b.ts })
        assertFalse(NewsViewModel.filterNews(f, "ALL", "ALL", "zzzz-no-such-word").isNotEmpty())
    }
}
