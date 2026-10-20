@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.harkquant.terminal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.unit.Density
import com.harkquant.terminal.data.Analysis
import com.harkquant.terminal.data.AppSettings
import com.harkquant.terminal.data.AtrResult
import com.harkquant.terminal.data.BarsResponse
import com.harkquant.terminal.data.Calendar
import com.harkquant.terminal.data.Conn
import com.harkquant.terminal.data.Feed
import com.harkquant.terminal.data.Flow
import com.harkquant.terminal.data.Health
import com.harkquant.terminal.data.HqJson
import com.harkquant.terminal.data.IndicatorCatalog
import com.harkquant.terminal.data.IndicatorResult
import com.harkquant.terminal.data.Load
import com.harkquant.terminal.data.OrderBook
import com.harkquant.terminal.data.Pivots
import com.harkquant.terminal.data.Positioning
import com.harkquant.terminal.data.Repository
import com.harkquant.terminal.data.Sentiment
import com.harkquant.terminal.data.ServerStatus
import com.harkquant.terminal.data.Sessions
import com.harkquant.terminal.data.Summary
import com.harkquant.terminal.data.SymbolsResponse
import com.harkquant.terminal.data.Technical
import com.harkquant.terminal.data.Ticker
import com.harkquant.terminal.ui.ActiveIndicator
import com.harkquant.terminal.ui.HqBottomBar
import com.harkquant.terminal.ui.Tab
import com.harkquant.terminal.ui.components.HqBackground
import com.harkquant.terminal.ui.screens.AnalysisContent
import com.harkquant.terminal.ui.screens.AnalysisState
import com.harkquant.terminal.ui.screens.DashboardContent
import com.harkquant.terminal.ui.screens.DashboardState
import com.harkquant.terminal.ui.screens.IndicatorsContent
import com.harkquant.terminal.ui.screens.NewsContent
import com.harkquant.terminal.ui.screens.SettingsContent
import com.harkquant.terminal.ui.screens.TradingViewContent
import com.harkquant.terminal.ui.theme.HqFonts
import com.harkquant.terminal.ui.theme.HqTheme
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

private lateinit var samplesDir: File
private fun text(n: String) = File(samplesDir, n).readText()
private fun <T> load(n: String, s: KSerializer<T>): T = HqJson.decodeFromString(s, text(n))

private const val W = 400
private const val DENSITY = 2.75f

private fun shot(out: File, name: String, theme: Int, hDp: Int = 870, bar: Tab? = null, content: @Composable () -> Unit) {
    val scene = ImageComposeScene((W * DENSITY).toInt(), (hDp * DENSITY).toInt(), Density(DENSITY)) {
        CompositionLocalProvider(LocalUriHandler provides object : UriHandler { override fun openUri(uri: String) {} }) {
            HqTheme(theme) {
                HqBackground {
                    Column(Modifier.fillMaxSize()) {
                        Column(Modifier.weight(1f)) { content() }
                        if (bar != null) HqBottomBar(bar) {}
                    }
                }
            }
        }
    }
    var t = 0L
    repeat(8) { scene.render(t); t += 250_000_000L }
    val img = scene.render(t)
    val f = File(out, "$name.png")
    f.writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
    scene.close()
    println("wrote ${f.path}")
}

fun main(args: Array<String>) {
    val out = File(args.getOrElse(0) { "../screenshots" }).apply { mkdirs() }
    samplesDir = File(args.getOrElse(1) { "../app/src/test/resources/samples" })
    HqFonts.dir = File(args.getOrElse(2) { "../app/src/main/res/font" })
    val only = args.getOrNull(3)

    val summary = load("summary.json", Summary.serializer())
    val ticker = load("ticker.json", Ticker.serializer())
    val barsXau = load("bars_xau.json", BarsResponse.serializer())
    val barsBtc = load("bars_btc.json", BarsResponse.serializer())
    val feed = load("feed.json", Feed.serializer())
    val now = (maxOf(summary.asOf ?: 0.0, feed.items.maxOfOrNull { it.ts } ?: 0.0) * 1000).toLong() + 240_000
    val prices = mapOf("XAUUSD" to Repository.snapOf("XAUUSD", barsXau), "BTCUSD" to Repository.snapOf("BTCUSD", barsBtc))
    val symbols = load("symbols.json", SymbolsResponse.serializer()).symbols
    val ema = load("ind_ema.json", IndicatorResult.serializer())
    val rsi = load("ind_rsi.json", IndicatorResult.serializer())
    val catalog = load("indicators.json", IndicatorCatalog.serializer())

    fun ok(name: String) = only == null || name.contains(only)

    fun dash(theme: Int, name: String, h: Int = 870) { if (ok(name)) shot(out, name, theme, h, Tab.DASH) {
        DashboardContent(DashboardState(Load.Ok(summary), ticker, prices, Load.Ok(feed.items), Conn.LIVE, "XAUUSD", "15m", Load.Ok(barsXau), false), now, {}, {}, {}, {}, {}, {})
    } }
    fun analysisState(tab: String) = AnalysisState(
        symbols, "XAUUSD", tab, Load.Ok(load("analysis.json", Analysis.serializer())), Load.Ok(load("flow.json", Flow.serializer())),
        Load.Ok(load("orderbook.json", OrderBook.serializer())), Load.Ok(load("ratings.json", Technical.serializer())),
        Load.Ok(load("positioning.json", Positioning.serializer())), Load.Ok(load("sentiment.json", Sentiment.serializer())), false, now,
    )
    fun indicators(name: String, h: Int, theme: Int = 17) { if (ok(name)) shot(out, name, theme, h, Tab.TOOLS) {
        IndicatorsContent(
            symbols.map { it.symbol }, "XAUUSD", "15m", Load.Ok(barsXau), Load.Ok(catalog),
            listOf(ActiveIndicator("talib:EMA", "Exponential Moving Average", ema.params ?: JsonObject(emptyMap())), ActiveIndicator("talib:RSI", "Relative Strength Index", rsi.params ?: JsonObject(emptyMap()))),
            listOf(ema, rsi), Load.Ok(load("atr.json", AtrResult.serializer())), Load.Ok(load("pivots.json", Pivots.serializer())),
            Load.Ok(load("sessions.json", Sessions.serializer())), null, now, {}, {}, {}, {}, {}, { _, _, _ -> }, {},
        )
    } }
    fun analysis(name: String, tab: String, theme: Int, h: Int) { if (ok(name)) shot(out, name, theme, h, Tab.ANALYSIS) { AnalysisContent(analysisState(tab), {}, {}, {}) } }

    dash(17, "01-dashboard-17-onyx-lime")
    dash(17, "02-dashboard-17-full-length", 2350)
    indicators("03-indicators-17", 870)
    indicators("04-indicators-17-full-length", 2500)
    if (ok("05-tradingview")) shot(out, "05-tradingview-native-17", 17, 870, Tab.TV) {
        TradingViewContent(symbols, "XAUUSD", "15m", "native", Load.Ok(barsXau), ema, {}, {}, {}, {})
    }
    analysis("06-analysis-bias-17", "bias", 17, 870)
    analysis("07-analysis-volume-17", "flow", 17, 2000)
    analysis("08-analysis-bulls-bears-17", "bulls", 17, 2000)
    analysis("09-analysis-pressure-17", "pressure", 17, 1650)
    if (ok("10-settings")) shot(out, "10-settings-17", 17, 2400, Tab.SETTINGS) {
        val st = HqJson.parseToJsonElement(text("settings.json")).jsonObject
        SettingsContent(AppSettings(), null, Load.Ok(load("status.json", ServerStatus.serializer())), Load.Ok(Health(true, "harkquant-terminal", "1.0.0") to 312L),
            Load.Ok(st), null, null, {}, {}, {}, {}, {}, {}, {}, {})
    }
    if (ok("11-news")) shot(out, "11-news-feed-17", 17, 870) {
        NewsContent("feed", {}, Load.Ok(feed.items.sortedByDescending { it.ts }), Load.Loading, "ALL", "ALL", "", "week", false, false, now, {}, {}, {}, {}, {}, {}, {})
    }
    if (ok("12-news")) shot(out, "12-news-calendar-17", 17, 870) {
        NewsContent("calendar", {}, Load.Ok(feed.items), Load.Ok(load("calendar.json", Calendar.serializer())), "ALL", "ALL", "", "week", false, false, now, {}, {}, {}, {}, {}, {}, {})
    }
    if (ok("13-dashboard-loading")) shot(out, "13-dashboard-loading-17", 17, 870, Tab.DASH) {
        DashboardContent(DashboardState(Load.Loading, null, emptyMap(), Load.Loading, Conn.CONNECTING, "XAUUSD", "15m", Load.Loading, false), now, {}, {}, {}, {}, {}, {})
    }
    if (ok("14-dashboard-offline")) shot(out, "14-dashboard-offline-17", 17, 870, Tab.DASH) {
        DashboardContent(DashboardState(Load.Err("No connection: can't reach the server"), null, emptyMap(), Load.Err("No connection"), Conn.OFFLINE, "XAUUSD", "15m",
            Load.Err("No connection"), false), now, {}, {}, {}, {}, {}, {})
    }
    listOf(1 to "pro-terminal-navy-gold", 2 to "pro-terminal-amber-black", 3 to "pro-terminal-phosphor", 4 to "pro-terminal-graphite-light",
        5 to "glass-aurora", 6 to "glass-ocean", 7 to "glass-sunset", 8 to "glass-frost",
        9 to "editorial-paper", 10 to "editorial-swiss", 11 to "editorial-sage", 12 to "editorial-ink-night",
        13 to "hud-neon-cyan", 14 to "hud-synthwave", 15 to "hud-toxic", 16 to "hud-crimson-ops",
        18 to "mobile-royal", 19 to "mobile-daylight", 20 to "mobile-rose-gold").forEach { (id, n) -> dash(id, "20-theme-%02d-%s".format(id, n)) }
    analysis("21-analysis-bias-13-hud", "bias", 13, 870)
    analysis("21-analysis-bias-09-editorial", "bias", 9, 870)
    analysis("21-analysis-bias-01-terminal", "bias", 1, 870)
    analysis("21-analysis-bias-05-glass", "bias", 5, 870)
}
