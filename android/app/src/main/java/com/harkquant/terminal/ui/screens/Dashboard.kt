package com.harkquant.terminal.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.harkquant.terminal.data.BarsResponse
import com.harkquant.terminal.data.Conn
import com.harkquant.terminal.data.Load
import com.harkquant.terminal.data.NewsItem
import com.harkquant.terminal.data.PriceSnap
import com.harkquant.terminal.data.Summary
import com.harkquant.terminal.data.Ticker
import com.harkquant.terminal.ui.components.BiasMini
import com.harkquant.terminal.ui.components.BiasWidget
import com.harkquant.terminal.ui.components.CandleChart
import com.harkquant.terminal.ui.components.ComponentRows
import com.harkquant.terminal.ui.components.CompBar
import com.harkquant.terminal.ui.components.ConnBadge
import com.harkquant.terminal.ui.components.Divider
import com.harkquant.terminal.ui.components.EmptyState
import com.harkquant.terminal.ui.components.ErrorState
import com.harkquant.terminal.ui.components.HqCard
import com.harkquant.terminal.ui.components.Note
import com.harkquant.terminal.ui.components.ScreenHeader
import com.harkquant.terminal.ui.components.Segmented
import com.harkquant.terminal.ui.components.SkeletonBlock
import com.harkquant.terminal.ui.components.SkeletonCard
import com.harkquant.terminal.ui.components.StaleBanner
import com.harkquant.terminal.ui.components.TickerStrip
import com.harkquant.terminal.ui.components.Tile
import com.harkquant.terminal.ui.components.rememberNow
import com.harkquant.terminal.ui.theme.Form
import com.harkquant.terminal.ui.theme.Hq
import com.harkquant.terminal.ui.theme.monoStyle
import com.harkquant.terminal.util.Fmt
import com.harkquant.terminal.util.Pkt

data class DashboardState(
    val summary: Load<Summary>,
    val ticker: Ticker?,
    val prices: Map<String, PriceSnap>,
    val news: Load<List<NewsItem>>,
    val conn: Conn,
    val symbol: String,
    val tf: String,
    val bars: Load<BarsResponse>,
    val refreshing: Boolean,
)

val CHART_TFS = listOf("5m", "15m", "1h", "4h", "1d")
private val NAMES = mapOf("BTCUSD" to "Bitcoin", "XAUUSD" to "Gold spot")

@Composable
fun DashboardContent(
    st: DashboardState,
    nowMs: Long,
    onRefresh: () -> Unit,
    onSymbol: (String) -> Unit,
    onTf: (String) -> Unit,
    onOpenNews: () -> Unit,
    onOpenAnalysis: () -> Unit,
    onOpenChart: () -> Unit,
) {
    val s = Hq.s
    val p = s.p
    var openItem by remember { mutableStateOf<NewsItem?>(null) }
    HqList(st.refreshing, onRefresh) {
        item("hdr") {
            val (kicker, title) = when (s.form) {
                Form.MOBILE -> "${Pkt.dayHm(nowMs / 1000.0)} PKT" to Pkt.greeting(nowMs)
                Form.EDITORIAL -> Pkt.masthead(nowMs) to "HarkQuant Terminal"
                Form.HUD -> "terminal v1 · ${Pkt.clock(nowMs)} pkt" to "Harkquant"
                Form.TERMINAL -> "HarkQuant · ${Pkt.clock(nowMs)} PKT" to "Dashboard"
                Form.GLASS -> "${Pkt.dayHm(nowMs / 1000.0)} PKT" to "Markets today"
            }
            ScreenHeader(kicker, title) { ConnBadge(st.conn) }
        }
        item("ticker") { TickerStrip(st.ticker, st.prices, st.conn, nowMs = nowMs) }
        item("prices") {
            Row(Modifier.screenPad(), horizontalArrangement = Arrangement.spacedBy(s.gap)) {
                listOf("BTCUSD", "XAUUSD").forEach { sym ->
                    PriceCard(sym, NAMES[sym] ?: sym, st.prices[sym], st.symbol == sym, Modifier.weight(1f)) { onSymbol(sym) }
                }
            }
        }
        item("chart") {
            HqCard(Modifier.screenPad(), title = "${NAMES[st.symbol] ?: st.symbol} · ${st.tf}", subtitle = st.bars.value?.source, action = "Full chart", onAction = onOpenChart) {
                Segmented(CHART_TFS, st.tf, onTf)
                Spacer(Modifier.height(8.dp))
                when (val b = st.bars) {
                    is Load.Loading -> SkeletonBlock(Modifier.fillMaxWidth(), 240.dp)
                    is Load.Err -> EmptyState("Chart unavailable · ${b.message}")
                    is Load.Ok -> {
                        if (b.data.bars.isEmpty()) EmptyState("No bars for ${st.symbol}")
                        else CandleChart(b.data.bars, height = 250.dp, daily = st.tf == "1d", initialVisible = 70)
                        if (b.data.proxy || b.data.note.isNotBlank()) Note(b.data.note.ifBlank { "Proxy data source" }, warn = true)
                    }
                }
            }
        }
        when (val sm = st.summary) {
            is Load.Loading -> {
                item("sk1") { Column(Modifier.screenPad()) { SkeletonCard(4) } }
                item("sk2") { Column(Modifier.screenPad()) { SkeletonCard(3) } }
            }
            is Load.Err -> item("err") { ErrorState(sm.message, onRefresh, Modifier.screenPad()) }
            is Load.Ok -> {
                val sum = sm.data
                if (sm.stale) item("stale") { Column(Modifier.screenPad()) { StaleBanner(sm.error) } }
                item("bias") {
                    HqCard(Modifier.screenPad(), title = "Market analysis", subtitle = "bias", action = "Details", onAction = onOpenAnalysis) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BiasMini(sum.analysis["XAUUSD"], "XAUUSD", Modifier.weight(1f)) { onSymbol("XAUUSD") }
                            BiasMini(sum.analysis["BTCUSD"], "BTCUSD", Modifier.weight(1f)) { onSymbol("BTCUSD") }
                        }
                        val a = sum.analysis[st.symbol] ?: sum.analysis.values.firstOrNull()
                        if (a != null && a.error == null) {
                            Spacer(Modifier.height(14.dp))
                            BiasWidget(a, compact = true)
                            Spacer(Modifier.height(12.dp))
                            ComponentRows(a)
                            a.event?.let { e -> Note("Event risk: ${e.country} ${e.title} in ${e.minutes.toInt()} min — score damped", warn = true) }
                        }
                    }
                }
                item("summary") {
                    HqCard(Modifier.screenPad(), title = "Summary", subtitle = "as of ${Pkt.hm(sum.asOf)} PKT") {
                        if (sum.narrative.isEmpty()) EmptyState("Building market summary…") else BulletList(sum.narrative)
                    }
                }
                item("redfolder") {
                    HqCard(Modifier.screenPad(), title = "Red folder", subtitle = "high impact · ForexFactory", action = "Calendar", onAction = onOpenNews) {
                        sum.nextHigh?.let { nh ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("NEXT HIGH", style = monoStyle(10, FontWeight.Bold), color = p.dn)
                                Spacer(Modifier.width(8.dp))
                                Text("${nh.country} ${nh.title}", style = monoStyle(11, FontWeight.SemiBold), color = p.tx, modifier = Modifier.weight(1f), maxLines = 1)
                                Text(Fmt.countdown(nh.ts - nowMs / 1000.0), style = monoStyle(12, FontWeight.Bold), color = p.warn)
                            }
                            Spacer(Modifier.height(4.dp)); Divider()
                        }
                        if (sum.redFolder.isEmpty()) EmptyState("No high-impact events left this ForexFactory week. Next week's calendar is published on Sunday.")
                        sum.redFolder.take(6).forEach { EventRow(it, nowMs) }
                    }
                }
                sum.sentiment?.let { se ->
                    item("sent") {
                        HqCard(Modifier.screenPad(), title = "News sentiment", subtitle = "${se.windowH.toInt()}h impact-weighted") {
                            se.assets().forEach { (k, v) ->
                                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(k, style = monoStyle(11, FontWeight.Bold), color = p.tx2, modifier = Modifier.width(40.dp))
                                    CompBar(v.score, Modifier.weight(1f))
                                    Text(Fmt.signed(v.score), style = monoStyle(11, FontWeight.Bold), color = s.signColor(Fmt.sign(v.score)), modifier = Modifier.width(44.dp).padding(start = 8.dp))
                                }
                            }
                            se.fearGreed?.let { fg ->
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Tile("Fear & Greed", fg.value?.toString() ?: "-", Modifier.weight(1f), sub = fg.label)
                                    Tile("Previous", fg.prev?.toString() ?: "-", Modifier.weight(1f), sub = "crypto only")
                                }
                            }
                        }
                    }
                }
            }
        }
        item("news") {
            HqCard(Modifier.screenPad(), title = "News feed", subtitle = "live", action = "All news", onAction = onOpenNews) {
                when (val n = st.news) {
                    is Load.Loading -> repeat(4) { SkeletonBlock(Modifier.fillMaxWidth().padding(vertical = 6.dp), 30.dp) }
                    is Load.Err -> EmptyState(n.message)
                    is Load.Ok -> {
                        val items = n.data.sortedByDescending { it.ts }.take(6)
                        if (items.isEmpty()) EmptyState("No headlines yet")
                        items.forEachIndexed { i, it ->
                            if (i > 0) Divider()
                            NewsRow(it, nowMs) { openItem = it }
                        }
                    }
                }
            }
        }
        item("foot") {
            Text(
                "Analysis only, not a trade signal. This app never places orders. All times PKT (Asia/Karachi).",
                style = MaterialTheme.typography.labelSmall, color = p.tx2, modifier = Modifier.screenPad().padding(top = 4.dp),
            )
        }
    }
    openItem?.let { NewsDetailSheet(it) { openItem = null } }
}
