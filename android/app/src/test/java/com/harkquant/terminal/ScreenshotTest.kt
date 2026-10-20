package com.harkquant.terminal

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
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
import com.harkquant.terminal.ui.Tab
import com.harkquant.terminal.ui.HqBottomBar
import com.harkquant.terminal.ui.components.HqBackground
import com.harkquant.terminal.ui.screens.AnalysisContent
import com.harkquant.terminal.ui.screens.AnalysisState
import com.harkquant.terminal.ui.screens.DashboardContent
import com.harkquant.terminal.ui.screens.DashboardState
import com.harkquant.terminal.ui.screens.IndicatorsContent
import com.harkquant.terminal.ui.screens.NewsContent
import com.harkquant.terminal.ui.screens.SettingsContent
import com.harkquant.terminal.ui.screens.TradingViewContent
import com.harkquant.terminal.ui.theme.HqTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the real screen composables with live-captured server JSON (no network) and writes PNGs to
 * screenshots/. Run: ./gradlew :app:testDebugUnitTest --tests '*ScreenshotTest*'
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@org.robolectric.annotation.ConscryptMode(org.robolectric.annotation.ConscryptMode.Mode.OFF)
@Config(sdk = [34], qualifiers = "w400dp-h880dp-xhdpi", application = android.app.Application::class)
class ScreenshotTest {
    @get:Rule val rule = createComposeRule()

    // Robolectric native graphics ships no linux-aarch64 runtime; there the desktop-shots harness renders instead.
    @org.junit.Before fun requireNativeGraphicsHost() {
        val os = System.getProperty("os.name").lowercase(); val arch = System.getProperty("os.arch").lowercase()
        org.junit.Assume.assumeFalse("no Robolectric native graphics on linux-aarch64", os.contains("linux") && (arch == "aarch64" || arch == "arm64"))
    }

    private val out = System.getProperty("user.dir")!!.let { java.io.File(it).let { d -> if (d.name == "app") d.parentFile else d } }.resolve("screenshots")

    private val summary = Samples.load("summary.json", Summary.serializer())
    private val ticker = Samples.load("ticker.json", Ticker.serializer())
    private val barsXau = Samples.load("bars_xau.json", BarsResponse.serializer())
    private val barsBtc = Samples.load("bars_btc.json", BarsResponse.serializer())
    private val feed = Samples.load("feed.json", Feed.serializer())
    private val now = ((summary.asOf ?: 1792499077.0) * 1000).toLong()
    private val prices = mapOf("XAUUSD" to Repository.snapOf("XAUUSD", barsXau), "BTCUSD" to Repository.snapOf("BTCUSD", barsBtc))
    private val symbols = Samples.load("symbols.json", SymbolsResponse.serializer()).symbols

    private fun shot(name: String, theme: Int, withBar: Tab? = null, content: @Composable () -> Unit) {
        rule.setContent {
            HqTheme(theme) {
                HqBackground {
                    Column(Modifier.fillMaxSize()) {
                        Column(Modifier.weight(1f)) { content() }
                        if (withBar != null) HqBottomBar(withBar) {}
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(1500)
        out.mkdirs()
        rule.onRoot().captureRoboImage(out.resolve("$name.png").path)
    }

    private fun dash(theme: Int, name: String) = shot(name, theme, Tab.DASH) {
        DashboardContent(
            DashboardState(Load.Ok(summary), ticker, prices, Load.Ok(feed.items), Conn.LIVE, "XAUUSD", "15m", Load.Ok(barsXau), false),
            now, {}, {}, {}, {}, {}, {},
        )
    }

    @Test fun dashboardDefaultTheme() = dash(17, "01-dashboard-17-onyx-lime")

    @Test @Config(qualifiers = "w400dp-h2300dp-xhdpi")
    fun dashboardFullLength() = dash(17, "02-dashboard-17-full-length")

    @Test fun indicators() = shot("03-indicators-17", 17, Tab.TOOLS) {
        val ema = Samples.load("ind_ema.json", IndicatorResult.serializer())
        val rsi = Samples.load("ind_rsi.json", IndicatorResult.serializer())
        IndicatorsContent(
            symbols.map { it.symbol }, "XAUUSD", "15m", Load.Ok(barsXau), Load.Ok(Samples.load("indicators.json", IndicatorCatalog.serializer())),
            listOf(ActiveIndicator("talib:EMA", "Exponential Moving Average", ema.params ?: JsonObject(emptyMap())), ActiveIndicator("talib:RSI", "Relative Strength Index", rsi.params ?: JsonObject(emptyMap()))),
            listOf(ema, rsi), Load.Ok(Samples.load("atr.json", AtrResult.serializer())), Load.Ok(Samples.load("pivots.json", Pivots.serializer())),
            Load.Ok(Samples.load("sessions.json", Sessions.serializer())), null, now, {}, {}, {}, {}, {}, { _, _, _ -> }, {},
        )
    }

    @Test @Config(qualifiers = "w400dp-h2400dp-xhdpi")
    fun indicatorsFullLength() = shot("04-indicators-17-full-length", 17, Tab.TOOLS) {
        val ema = Samples.load("ind_ema.json", IndicatorResult.serializer())
        val rsi = Samples.load("ind_rsi.json", IndicatorResult.serializer())
        IndicatorsContent(
            symbols.map { it.symbol }, "XAUUSD", "15m", Load.Ok(barsXau), Load.Ok(Samples.load("indicators.json", IndicatorCatalog.serializer())),
            listOf(ActiveIndicator("talib:EMA", "Exponential Moving Average", ema.params ?: JsonObject(emptyMap())), ActiveIndicator("talib:RSI", "Relative Strength Index", rsi.params ?: JsonObject(emptyMap()))),
            listOf(ema, rsi), Load.Ok(Samples.load("atr.json", AtrResult.serializer())), Load.Ok(Samples.load("pivots.json", Pivots.serializer())),
            Load.Ok(Samples.load("sessions.json", Sessions.serializer())), null, now, {}, {}, {}, {}, {}, { _, _, _ -> }, {},
        )
    }

    @Test fun tradingViewNative() = shot("05-tradingview-native-17", 17, Tab.TV) {
        TradingViewContent(symbols, "XAUUSD", "15m", "native", Load.Ok(barsXau), Samples.load("ind_ema.json", IndicatorResult.serializer()), {}, {}, {}, {})
    }

    private fun analysisState(tab: String) = AnalysisState(
        symbols, "XAUUSD", tab, Load.Ok(Samples.load("analysis.json", Analysis.serializer())), Load.Ok(Samples.load("flow.json", Flow.serializer())),
        Load.Ok(Samples.load("orderbook.json", OrderBook.serializer())), Load.Ok(Samples.load("ratings.json", Technical.serializer())),
        Load.Ok(Samples.load("positioning.json", Positioning.serializer())), Load.Ok(Samples.load("sentiment.json", Sentiment.serializer())), false, now,
    )

    @Test fun analysisBias() = shot("06-analysis-bias-17", 17, Tab.ANALYSIS) { AnalysisContent(analysisState("bias"), {}, {}, {}) }
    @Test @Config(qualifiers = "w400dp-h2000dp-xhdpi")
    fun analysisFlow() = shot("07-analysis-volume-17", 17, Tab.ANALYSIS) { AnalysisContent(analysisState("flow"), {}, {}, {}) }
    @Test @Config(qualifiers = "w400dp-h2000dp-xhdpi")
    fun analysisBulls() = shot("08-analysis-bulls-bears-17", 17, Tab.ANALYSIS) { AnalysisContent(analysisState("bulls"), {}, {}, {}) }
    @Test @Config(qualifiers = "w400dp-h1600dp-xhdpi")
    fun analysisPressure() = shot("09-analysis-pressure-17", 17, Tab.ANALYSIS) { AnalysisContent(analysisState("pressure"), {}, {}, {}) }

    @Test @Config(qualifiers = "w400dp-h1500dp-xhdpi")
    fun settings() = shot("10-settings-17", 17, Tab.SETTINGS) {
        val st = HqJson.parseToJsonElement(Samples.text("settings.json")).jsonObject
        SettingsContent(AppSettings(), null, Load.Ok(Samples.load("status.json", ServerStatus.serializer())), Load.Ok(Health(true, "harkquant-terminal", "1.0.0") to 312L),
            Load.Ok(st), null, null, {}, {}, {}, {}, {}, {}, {}, {})
    }

    @Test fun news() = shot("11-news-feed-17", 17) {
        NewsContent("feed", {}, Load.Ok(feed.items.sortedByDescending { it.ts }), Load.Loading, "ALL", "ALL", "", "week", false, false, now, {}, {}, {}, {}, {}, {}, {})
    }

    @Test fun calendar() = shot("12-news-calendar-17", 17) {
        NewsContent("calendar", {}, Load.Ok(feed.items), Load.Ok(Samples.load("calendar.json", Calendar.serializer())), "ALL", "ALL", "", "week", false, false, now, {}, {}, {}, {}, {}, {}, {})
    }

    @Test fun loadingState() = shot("13-dashboard-loading-17", 17, Tab.DASH) {
        DashboardContent(DashboardState(Load.Loading, null, emptyMap(), Load.Loading, Conn.CONNECTING, "XAUUSD", "15m", Load.Loading, false), now, {}, {}, {}, {}, {}, {})
    }

    @Test fun errorState() = shot("14-dashboard-offline-17", 17, Tab.DASH) {
        DashboardContent(DashboardState(Load.Err("No connection: can't reach the server"), null, emptyMap(), Load.Err("No connection"), Conn.OFFLINE, "XAUUSD", "15m",
            Load.Err("No connection"), false), now, {}, {}, {}, {}, {}, {})
    }

    // one dashboard per form/palette family, so every layout style is visible
    @Test fun themeNavyGold() = dash(1, "20-theme-01-pro-terminal-navy-gold")
    @Test fun themeAmberBlack() = dash(2, "20-theme-02-pro-terminal-amber-black")
    @Test fun themeGraphite() = dash(4, "20-theme-04-pro-terminal-graphite-light")
    @Test fun themeAurora() = dash(5, "20-theme-05-glass-aurora")
    @Test fun themeFrost() = dash(8, "20-theme-08-glass-frost")
    @Test fun themePaper() = dash(9, "20-theme-09-editorial-paper")
    @Test fun themeInkNight() = dash(12, "20-theme-12-editorial-ink-night")
    @Test fun themeNeonCyan() = dash(13, "20-theme-13-hud-neon-cyan")
    @Test fun themeSynthwave() = dash(14, "20-theme-14-hud-synthwave")
    @Test fun themeRoyal() = dash(18, "20-theme-18-mobile-royal")
    @Test fun themeDaylight() = dash(19, "20-theme-19-mobile-daylight")
    @Test fun themeRoseGold() = dash(20, "20-theme-20-mobile-rose-gold")
    @Test fun analysisHud() = shot("21-analysis-bias-13-hud", 13, Tab.ANALYSIS) { AnalysisContent(analysisState("bias"), {}, {}, {}) }
    @Test fun analysisEditorial() = shot("21-analysis-bias-09-editorial", 9, Tab.ANALYSIS) { AnalysisContent(analysisState("bias"), {}, {}, {}) }
    @Test fun analysisTerminal() = shot("21-analysis-bias-01-terminal", 1, Tab.ANALYSIS) { AnalysisContent(analysisState("bias"), {}, {}, {}) }
}
