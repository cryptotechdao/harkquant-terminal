package com.harkquant.terminal.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** One lenient JSON config for every endpoint: unknown keys ignored, nulls fall back to defaults. */
val HqJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    isLenient = true
    encodeDefaults = true
}

// ------------------------------------------------------------------ market data
@Serializable
data class Bar(
    val time: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double = 0.0,
)

@Serializable
data class BarsResponse(
    val symbol: String = "",
    val tf: String = "",
    val source: String = "",
    val proxy: Boolean = false,
    val note: String = "",
    val bars: List<Bar> = emptyList(),
    val stale: Boolean = false,
)

@Serializable
data class TimeValue(val time: Long = 0, val value: Double = Double.NaN)

@Serializable
data class Component(
    val id: String = "",
    val desc: String = "",
    val score: Double? = null,
    val weight: Double = 0.0,
    val used: Boolean = false,
    val contribution: Double? = null,
    @SerialName("eff_weight") val effWeight: Double = 0.0,
)

@Serializable
data class BiasEvent(
    val country: String = "",
    val title: String = "",
    val minutes: Double = 0.0,
    @SerialName("time_pkt") val timePkt: String = "",
)

@Serializable
data class CompStatus(
    val status: String? = null,
    val proxy: Boolean = false,
    val stale: Boolean = false,
    val error: String? = null,
    val note: String? = null,
    val value: Int? = null,
    val label: String? = null,
    val tag: String? = null,
    val n: Int? = null,
)

@Serializable
data class Analysis(
    val score: Double = 0.0,
    val raw: Double = 0.0,
    val label: String = "NEUTRAL",
    @SerialName("neutral_band") val neutralBand: Double = 15.0,
    val components: List<Component> = emptyList(),
    val used: Int = 0,
    val agreement: String = "",
    val event: BiasEvent? = null,
    val dampened: Boolean = false,
    val confidence: Double = 0.0,
    val symbol: String = "",
    val name: String = "",
    val kind: String = "",
    val notes: String = "",
    val status: Map<String, CompStatus> = emptyMap(),
    @SerialName("as_of") val asOf: Double? = null,
    val details: Details? = null,
    val error: String? = null,
)

@Serializable
data class Details(
    val technical: Technical? = null,
    val flow: Flow? = null,
    val orderbook: OrderBook? = null,
    val positioning: Positioning? = null,
)

@Serializable
data class RatingSummary(
    @SerialName("RECOMMENDATION") val recommendation: String = "",
    @SerialName("BUY") val buy: Int = 0,
    @SerialName("SELL") val sell: Int = 0,
    @SerialName("NEUTRAL") val neutral: Int = 0,
)

@Serializable
data class TfRating(
    val summary: RatingSummary = RatingSummary(),
    val oscillators: String = "",
    val ma: String = "",
    val indicators: Map<String, Double?> = emptyMap(),
    val score: Double? = null,
)

@Serializable
data class Technical(
    val symbol: String = "",
    val available: Boolean = false,
    val source: String = "",
    val tfs: Map<String, TfRating> = emptyMap(),
    val score: Double? = null,
    val note: String? = null,
)

@Serializable
data class FlowWindow(
    val buy: Double = 0.0,
    val sell: Double = 0.0,
    val delta: Double = 0.0,
    @SerialName("buy_pct") val buyPct: Double? = null,
)

@Serializable
data class FlowPoint(
    val time: Long = 0,
    val buy: Double = 0.0,
    val sell: Double = 0.0,
    val delta: Double = 0.0,
    val cvd: Double = 0.0,
)

@Serializable
data class Venue(
    val id: String = "",
    val label: String = "",
    val unit: String = "",
    val series: List<FlowPoint> = emptyList(),
    val w15m: FlowWindow = FlowWindow(),
    val w1h: FlowWindow = FlowWindow(),
    val w4h: FlowWindow = FlowWindow(),
    val exact: Boolean = false,
    val proxy: Boolean = false,
)

@Serializable
data class Tape(
    val trades: Int = 0,
    @SerialName("span_s") val spanS: Double = 0.0,
    @SerialName("buy_usd") val buyUsd: Double = 0.0,
    @SerialName("sell_usd") val sellUsd: Double = 0.0,
    @SerialName("buy_pct") val buyPct: Double? = null,
    @SerialName("last_price") val lastPrice: Double? = null,
    val source: String = "",
)

@Serializable
data class Flow(
    val symbol: String = "",
    val kind: String = "",
    val venues: List<Venue> = emptyList(),
    val proxy: Boolean = false,
    val note: String = "",
    val tape: Tape? = null,
    val available: Boolean = true,
    val score: Double? = null,
)

@Serializable
data class Band(
    @SerialName("bid_usd") val bidUsd: Double = 0.0,
    @SerialName("ask_usd") val askUsd: Double = 0.0,
    val imbalance: Double = 0.0,
)

@Serializable
data class Depth(
    val bids: List<List<Double>> = emptyList(),
    val asks: List<List<Double>> = emptyList(),
)

@Serializable
data class OrderBook(
    val symbol: String = "",
    val available: Boolean = false,
    val source: String = "",
    val mid: Double? = null,
    val spread: Double? = null,
    val bands: Map<String, Band> = emptyMap(),
    val depth: Depth? = null,
    val score: Double? = null,
    val proxy: Boolean = false,
    val note: String = "",
)

@Serializable
data class Positioning(
    val symbol: String = "",
    val available: Boolean = false,
    val source: String = "",
    val proxy: Boolean = false,
    @SerialName("long_short") val longShort: List<TimeValue> = emptyList(),
    @SerialName("ls_now") val lsNow: Double? = null,
    val funding: Double? = null,
    @SerialName("funding_next") val fundingNext: Double? = null,
    @SerialName("oi_usd") val oiUsd: List<TimeValue> = emptyList(),
    @SerialName("oi_change_1h_pct") val oiChange1hPct: Double? = null,
    val score: Double? = null,
    val note: String = "",
)

// ------------------------------------------------------------------ news, calendar, sentiment
@Serializable
data class AssetSentiment(
    val score: Double = 0.0,
    val label: String = "",
    val bull: Int = 0,
    val bear: Int = 0,
    val n: Int = 0,
)

@Serializable
data class FearGreed(
    val value: Int? = null,
    val label: String = "",
    val prev: Int? = null,
    val source: String = "",
)

@Serializable
data class Sentiment(
    @SerialName("XAU") val xau: AssetSentiment? = null,
    @SerialName("BTC") val btc: AssetSentiment? = null,
    @SerialName("USD") val usd: AssetSentiment? = null,
    @SerialName("RISK") val risk: AssetSentiment? = null,
    @SerialName("window_h") val windowH: Double = 6.0,
    @SerialName("crypto_fear_greed") val fearGreed: FearGreed? = null,
    @SerialName("as_of") val asOf: String? = null,
) {
    fun assets(): List<Pair<String, AssetSentiment>> =
        listOfNotNull(xau?.let { "XAU" to it }, btc?.let { "BTC" to it }, usd?.let { "USD" to it }, risk?.let { "RISK" to it })
}

@Serializable
data class CalEvent(
    val id: String = "",
    val ts: Double = 0.0,
    @SerialName("time_pkt") val timePkt: String = "",
    val country: String = "",
    val title: String = "",
    val impact: String = "",
    val forecast: String? = null,
    val previous: String? = null,
    val actual: String? = null,
    val result: String? = null,
    val url: String? = null,
    val past: Boolean = false,
    @SerialName("seconds_to") val secondsTo: Double? = null,
)

@Serializable
data class NewsItem(
    val id: String = "",
    val ts: Double = 0.0,
    @SerialName("time_pkt") val timePkt: String = "",
    val source: String = "",
    @SerialName("source_name") val sourceName: String = "",
    val label: String = "",
    val category: String = "",
    val title: String = "",
    val summary: String = "",
    val url: String = "",
    val impact: String = "LOW",
    val score: Double = 0.0,
    val assets: List<String> = emptyList(),
    val direction: Map<String, String> = emptyMap(),
    val why: String? = null,
)

@Serializable
data class Summary(
    val analysis: Map<String, Analysis> = emptyMap(),
    val sentiment: Sentiment? = null,
    @SerialName("red_folder") val redFolder: List<CalEvent> = emptyList(),
    @SerialName("top_news") val topNews: List<NewsItem> = emptyList(),
    @SerialName("next_high") val nextHigh: CalEvent? = null,
    @SerialName("as_of") val asOf: Double? = null,
    val narrative: List<String> = emptyList(),
)

@Serializable
data class TickerItem(
    val kind: String = "news",
    val id: String = "",
    val text: String = "",
    val source: String? = null,
    val impact: String = "",
    val ts: Double = 0.0,
    @SerialName("time_pkt") val timePkt: String = "",
    val url: String? = null,
    val assets: List<String> = emptyList(),
    val direction: Map<String, String> = emptyMap(),
    val forecast: String? = null,
    val previous: String? = null,
    val red: Boolean = false,
)

@Serializable
data class TickerSettings(
    val speed: Int = 38,
    val paused: Boolean = false,
    val headlines: Int = 3,
    val events: Int = 3,
)

@Serializable
data class Ticker(
    val headlines: List<TickerItem> = emptyList(),
    val events: List<TickerItem> = emptyList(),
    @SerialName("high_remaining") val highRemaining: Int = 0,
    val settings: TickerSettings? = null,
    @SerialName("server_time") val serverTime: Double = 0.0,
    @SerialName("calendar_note") val calendarNote: String? = null,
)

@Serializable
data class Feed(
    val items: List<NewsItem> = emptyList(),
    val count: Int = 0,
    @SerialName("server_time") val serverTime: Double = 0.0,
)

@Serializable
data class Calendar(
    val events: List<CalEvent> = emptyList(),
    val count: Int = 0,
    @SerialName("next_high") val nextHigh: CalEvent? = null,
    val range: String = "week",
    val note: String? = null,
)

@Serializable
data class RedFolder(val events: List<CalEvent> = emptyList(), @SerialName("server_time") val serverTime: Double = 0.0)

@Serializable
data class SymbolInfo(
    val symbol: String = "",
    val kind: String = "",
    val name: String = "",
    val tvw: String = "",
    val rating: Boolean = false,
    val orderbook: Boolean = false,
    val flow: String? = null,
    val notes: String = "",
)

@Serializable
data class SymbolsResponse(val symbols: List<SymbolInfo> = emptyList())

@Serializable
data class SourceStatus(
    @SerialName("latency_ms") val latencyMs: Double? = null,
    val status: String? = null,
    @SerialName("last_ok") val lastOk: Double? = null,
    @SerialName("last_error") val lastError: String? = null,
)

@Serializable
data class NewsHealth(
    val total: Int = 0,
    val ok: Int = 0,
    val fail: Int = 0,
    val stale: Int = 0,
    val pending: Int = 0,
    @SerialName("needs_key") val needsKey: Int = 0,
    val off: Int = 0,
)

@Serializable
data class ServerStatus(
    val market: Map<String, SourceStatus> = emptyMap(),
    val news: NewsHealth? = null,
    @SerialName("news_role") val newsRole: String? = null,
    @SerialName("needs_key") val needsKey: List<String> = emptyList(),
    @SerialName("server_time") val serverTime: Double = 0.0,
)

@Serializable
data class Health(val ok: Boolean = false, val app: String = "", val version: String = "", val time: Double = 0.0)

// ------------------------------------------------------------------ indicators & tools
@Serializable
data class IndicatorMeta(
    val id: String = "",
    val name: String = "",
    val short: String = "",
    val params: Map<String, JsonElement> = emptyMap(),
    val outputs: List<String> = emptyList(),
    val pane: String = "own",
)

@Serializable
data class IndicatorGroup(val group: String = "", val items: List<IndicatorMeta> = emptyList())

@Serializable
data class IndicatorCatalog(
    val groups: List<IndicatorGroup> = emptyList(),
    val total: Int = 0,
    val libraries: List<String> = emptyList(),
    val note: String = "",
)

@Serializable
data class IndicatorLine(val name: String = "", val hist: Boolean = false, val data: List<TimeValue> = emptyList())

@Serializable
data class IndicatorResult(
    val id: String = "",
    val name: String = "",
    val pane: String = "own",
    val params: JsonObject? = null,
    val lines: List<IndicatorLine> = emptyList(),
    val markers: List<TimeValue> = emptyList(),
    val error: String? = null,
)

@Serializable
data class AtrResult(
    val symbol: String = "",
    val tf: String = "",
    val atr: Double = 0.0,
    val close: Double = 0.0,
    val source: String = "",
    val contract: Double = 1.0,
)

@Serializable
data class Hlc(val high: Double = 0.0, val low: Double = 0.0, val close: Double = 0.0)

@Serializable
data class Pivots(
    val symbol: String = "",
    val source: String = "",
    @SerialName("prev_day") val prevDay: Hlc = Hlc(),
    val classic: Map<String, Double> = emptyMap(),
    val fibonacci: Map<String, Double> = emptyMap(),
    val camarilla: Map<String, Double> = emptyMap(),
    val woodie: Map<String, Double> = emptyMap(),
) {
    fun method(name: String): Map<String, Double> = when (name) {
        "fibonacci" -> fibonacci; "camarilla" -> camarilla; "woodie" -> woodie; else -> classic
    }
}

@Serializable
data class TradingSession(
    val name: String = "",
    @SerialName("open_utc") val openUtc: Int = 0,
    @SerialName("close_utc") val closeUtc: Int = 0,
    val active: Boolean = false,
    @SerialName("next_open") val nextOpen: Double? = null,
    val closes: Double? = null,
)

@Serializable
data class Sessions(val sessions: List<TradingSession> = emptyList(), @SerialName("server_time") val serverTime: Double = 0.0)

@Serializable
data class PositionRequest(
    val balance: Double,
    @SerialName("risk_pct") val riskPct: Double? = null,
    @SerialName("risk_money") val riskMoney: Double? = null,
    val entry: Double,
    val stop: Double,
    val contract: Double = 1.0,
    val fx: Double = 1.0,
)

@Serializable
data class PositionResult(
    val risk: Double = 0.0,
    @SerialName("loss_per_lot") val lossPerLot: Double = 0.0,
    val lots: Double = 0.0,
    @SerialName("lots_rounded") val lotsRounded: Double = 0.0,
    val side: String = "",
    @SerialName("stop_distance") val stopDistance: Double = 0.0,
)

/** A parsed Server-Sent Event. */
data class SseEvent(val event: String, val data: String, val id: String? = null)
