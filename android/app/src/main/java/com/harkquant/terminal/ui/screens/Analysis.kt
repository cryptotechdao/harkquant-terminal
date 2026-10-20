package com.harkquant.terminal.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harkquant.terminal.data.Analysis
import com.harkquant.terminal.data.Flow
import com.harkquant.terminal.data.Load
import com.harkquant.terminal.data.OrderBook
import com.harkquant.terminal.data.Positioning
import com.harkquant.terminal.data.Sentiment
import com.harkquant.terminal.data.SymbolInfo
import com.harkquant.terminal.data.Technical
import com.harkquant.terminal.data.TimeValue
import com.harkquant.terminal.ui.components.BiasWidget
import com.harkquant.terminal.ui.components.ChipRow
import com.harkquant.terminal.ui.components.CompBar
import com.harkquant.terminal.ui.components.DepthChart
import com.harkquant.terminal.ui.components.Divider
import com.harkquant.terminal.ui.components.EmptyState
import com.harkquant.terminal.ui.components.ErrorState
import com.harkquant.terminal.ui.components.HqCard
import com.harkquant.terminal.ui.components.KeyValue
import com.harkquant.terminal.ui.components.LineChart
import com.harkquant.terminal.ui.components.Note
import com.harkquant.terminal.ui.components.Overlay
import com.harkquant.terminal.ui.components.ScreenHeader
import com.harkquant.terminal.ui.components.Segmented
import com.harkquant.terminal.ui.components.SkeletonCard
import com.harkquant.terminal.ui.components.SplitBar
import com.harkquant.terminal.ui.components.StaleBanner
import com.harkquant.terminal.ui.components.StatusDot
import com.harkquant.terminal.ui.components.Tag
import com.harkquant.terminal.ui.components.Tile
import com.harkquant.terminal.ui.components.TileGrid
import com.harkquant.terminal.ui.theme.Hq
import com.harkquant.terminal.ui.theme.monoStyle
import com.harkquant.terminal.util.Fmt
import com.harkquant.terminal.util.Pkt

val ANALYSIS_TABS = listOf("bias", "flow", "pressure", "bulls", "sentiment")
val ANALYSIS_TAB_LABELS = listOf("Bias", "Volume", "Pressure", "Bulls/Bears", "Sentiment")

data class AnalysisState(
    val symbols: List<SymbolInfo>, val symbol: String, val tab: String,
    val analysis: Load<Analysis>, val flow: Load<Flow>, val orderbook: Load<OrderBook>, val ratings: Load<Technical>,
    val positioning: Load<Positioning>, val sentiment: Load<Sentiment>, val refreshing: Boolean, val updatedAt: Long,
)

@Composable
fun AnalysisContent(st: AnalysisState, onSymbol: (String) -> Unit, onTab: (String) -> Unit, onRefresh: () -> Unit) {
    HqList(st.refreshing, onRefresh) {
        item("h") { ScreenHeader(st.symbols.firstOrNull { it.symbol == st.symbol }?.name ?: st.symbol, "Market analysis") }
        item("sym") { ChipRow(st.symbols.map { it.symbol }, st.symbol, onSymbol, Modifier.screenPad()) }
        item("tabs") { Segmented(ANALYSIS_TABS, st.tab, onTab, Modifier.screenPad(), labels = ANALYSIS_TAB_LABELS, fill = false) }
        when (st.tab) {
            "bias" -> loadBlock(st.analysis, onRefresh) { a -> item("bias") { BiasTab(a) } }
            "flow" -> loadBlock(st.flow, onRefresh) { f -> item("flow") { FlowTab(f) } }
            "pressure" -> loadBlock(st.orderbook, onRefresh) { ob -> item("ob") { PressureTab(ob, st.flow.value) } }
            "bulls" -> loadBlock(st.ratings, onRefresh) { r -> item("bulls") { BullsTab(r, st.positioning) } }
            "sentiment" -> loadBlock(st.sentiment, onRefresh) { se -> item("sent") { SentimentTab(se, st.analysis.value) } }
        }
        item("upd") {
            Note("Updated ${Pkt.clock(st.updatedAt)} PKT · analysis only, nothing here places orders.", modifier = Modifier.screenPad())
        }
    }
}

private fun <T> androidx.compose.foundation.lazy.LazyListScope.loadBlock(l: Load<T>, retry: () -> Unit, ok: androidx.compose.foundation.lazy.LazyListScope.(T) -> Unit) {
    when (l) {
        is Load.Loading -> { item("lsk1") { Column(Modifier.screenPad()) { SkeletonCard(4, chart = true) } } }
        is Load.Err -> item("lerr") { ErrorState(l.message, retry, Modifier.screenPad()) }
        is Load.Ok -> {
            if (l.stale) item("lst") { Column(Modifier.screenPad()) { StaleBanner(l.error) } }
            ok(l.data)
        }
    }
}

@Composable
private fun BiasTab(a: Analysis) {
    val s = Hq.s
    val p = s.p
    Column(Modifier.screenPad(), verticalArrangement = Arrangement.spacedBy(s.gap)) {
        HqCard(title = "Combined bias", subtitle = a.symbol) {
            BiasWidget(a)
            Spacer(Modifier.height(10.dp))
            if (s.form == com.harkquant.terminal.ui.theme.Form.TERMINAL || s.form == com.harkquant.terminal.ui.theme.Form.EDITORIAL)
                Text("${a.agreement} active components agree · confidence ${(a.confidence * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = p.tx2)
            if (a.dampened) Text("Raw ${Fmt.signed(a.raw, 1)}, damped for event risk", style = MaterialTheme.typography.bodySmall, color = p.warn)
            a.event?.let { e -> Note("Red-folder event in ${e.minutes.toInt()} min: ${e.country} ${e.title} (${e.timePkt}). Score shrunk toward neutral.", warn = true) }
        }
        HqCard(title = "Score breakdown", subtitle = "weights set on the server") {
            a.components.forEachIndexed { i, c ->
                if (i > 0) Divider()
                val st = a.status[c.id]
                Column(Modifier.padding(vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(st?.status)
                        Spacer(Modifier.width(8.dp))
                        Text(c.id.replace('_', ' '), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = if (c.used) p.tx else p.tx2)
                        if (st?.proxy == true) { Spacer(Modifier.width(6.dp)); Tag("proxy", p.warn) }
                        if (st?.stale == true) { Spacer(Modifier.width(6.dp)); Tag("stale", p.tx2) }
                        Spacer(Modifier.weight(1f))
                        Text(if (c.score == null) "n/a" else Fmt.signed(c.score), style = monoStyle(13, FontWeight.Bold), color = s.signColor(Fmt.sign(c.score)))
                    }
                    Text(c.desc, style = MaterialTheme.typography.bodySmall, color = p.tx2, modifier = Modifier.padding(start = 15.dp, top = 2.dp))
                    if (c.score != null) CompBar(c.score, Modifier.padding(start = 15.dp, top = 6.dp))
                    Text("weight ${"%.2f".format(c.weight)} · effective ${if (c.used) "${(c.effWeight * 100).toInt()}%" else "-"} · contribution ${Fmt.signed(c.contribution, 1)}",
                        style = monoStyle(10), color = p.tx2, modifier = Modifier.padding(start = 15.dp, top = 4.dp))
                }
            }
        }
        if (a.notes.isNotBlank()) Note(a.notes, warn = true)
        Note("Score = weighted average of the available components (missing ones skipped, weights renormalised). |score| below ±${a.neutralBand.toInt()} = NEUTRAL.")
    }
}

@Composable
private fun FlowTab(f: Flow) {
    val s = Hq.s
    val p = s.p
    Column(Modifier.screenPad(), verticalArrangement = Arrangement.spacedBy(s.gap)) {
        if (!f.available) { HqCard { EmptyState(f.note.ifBlank { "No volume source for this market" }) }; return }
        if (f.proxy && f.note.isNotBlank()) Note(f.note, warn = true)
        f.venues.forEach { v ->
            HqCard(title = v.label, subtitle = if (v.exact) "taker side" else "estimated") {
                val bp = v.w1h.buyPct ?: 50.0
                Row {
                    Column(Modifier.weight(1f)) {
                        Text(if (v.exact) "BUYING" else "BUY (EST.)", style = MaterialTheme.typography.labelSmall, color = p.tx2)
                        Text(Fmt.pct(bp), style = monoStyle(24, FontWeight.Bold), color = p.up)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("SELLING", style = MaterialTheme.typography.labelSmall, color = p.tx2)
                        Text(Fmt.pct(100 - bp), style = monoStyle(24, FontWeight.Bold), color = p.dn)
                    }
                }
                SplitBar(bp, Modifier.padding(vertical = 8.dp))
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    listOf("window", "buy", "sell", "delta", "buy %").forEachIndexed { i, h ->
                        Text(h, style = MaterialTheme.typography.labelSmall, color = p.tx2, modifier = Modifier.weight(if (i == 0) 0.8f else 1f))
                    }
                }
                listOf("15m" to v.w15m, "1h" to v.w1h, "4h" to v.w4h).forEach { (k, w) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(k, style = monoStyle(11), color = p.tx2, modifier = Modifier.weight(0.8f))
                        Text(Fmt.big(w.buy), style = monoStyle(11), color = p.up, modifier = Modifier.weight(1f))
                        Text(Fmt.big(w.sell), style = monoStyle(11), color = p.dn, modifier = Modifier.weight(1f))
                        Text(Fmt.big(w.delta), style = monoStyle(11, FontWeight.SemiBold), color = s.signColor(Fmt.sign(w.delta)), modifier = Modifier.weight(1f))
                        Text(Fmt.pct(w.buyPct), style = monoStyle(11), color = p.tx, modifier = Modifier.weight(1f))
                    }
                }
                if (v.series.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("Delta per 5m", style = MaterialTheme.typography.labelMedium, color = p.tx2)
                    LineChart(listOf(Overlay("delta", p.up, v.series.map { TimeValue(it.time, it.delta) }, hist = true)), height = 120.dp)
                    Spacer(Modifier.height(6.dp))
                    Text("Cumulative volume delta", style = MaterialTheme.typography.labelMedium, color = p.tx2)
                    LineChart(listOf(Overlay("CVD", p.acc, v.series.map { TimeValue(it.time, it.cvd) })), height = 110.dp, area = true, zeroLine = true)
                }
                Note(if (v.exact) "Exact: the venue reports the aggressor side of every trade (5-min buckets)." else "Estimated: futures bars carry total volume only; the split is inferred from where each bar closed in its range.", warn = !v.exact)
            }
        }
        f.tape?.let { t ->
            HqCard(title = "Tape", subtitle = t.source) {
                TileGrid(listOf(
                    { m -> Tile("Trades", t.trades.toString(), m) },
                    { m -> Tile("Span", Fmt.countdown(t.spanS), m) },
                    { m -> Tile("Buy $", Fmt.big(t.buyUsd), m, p.up) },
                    { m -> Tile("Sell $", Fmt.big(t.sellUsd), m, p.dn) },
                    { m -> Tile("Buy share", Fmt.pct(t.buyPct), m) },
                    { m -> Tile("Last", Fmt.price(t.lastPrice), m) },
                ), perRow = 3)
            }
        }
    }
}

@Composable
private fun PressureTab(ob: OrderBook, flow: Flow?) {
    val s = Hq.s
    val p = s.p
    Column(Modifier.screenPad(), verticalArrangement = Arrangement.spacedBy(s.gap)) {
        if (!ob.available) {
            HqCard(title = "Order book") { EmptyState(ob.note.ifBlank { "No public order book for this market." }) }
        } else {
            HqCard(title = "Order-book imbalance", subtitle = ob.source) {
                val i5 = ob.bands["0.5%"]?.imbalance
                TileGrid(listOf(
                    { m -> Tile("Mid", Fmt.price(ob.mid), m) },
                    { m -> Tile("Spread", Fmt.price(ob.spread), m) },
                    { m -> Tile("Imbalance ±0.5%", Fmt.signed(i5, 1) + "%", m, s.signColor(Fmt.sign(i5))) },
                    { m -> Tile("Book score", Fmt.signed(ob.score), m, s.signColor(Fmt.sign(ob.score))) },
                ))
                Spacer(Modifier.height(10.dp))
                ob.bands.forEach { (k, b) ->
                    Column(Modifier.padding(vertical = 5.dp)) {
                        Row {
                            Text("±$k", style = monoStyle(11, FontWeight.Bold), color = p.tx, modifier = Modifier.width(56.dp))
                            Text("bids ${Fmt.big(b.bidUsd)}", style = monoStyle(10), color = p.up, modifier = Modifier.weight(1f))
                            Text("asks ${Fmt.big(b.askUsd)}", style = monoStyle(10), color = p.dn, modifier = Modifier.weight(1f))
                            Text(Fmt.signed(b.imbalance, 1) + "%", style = monoStyle(11, FontWeight.Bold), color = s.signColor(Fmt.sign(b.imbalance)))
                        }
                        CompBar(b.imbalance, Modifier.padding(top = 4.dp))
                    }
                }
                if (ob.note.isNotBlank()) Note(ob.note, warn = true)
                Note("Positive = more resting bids than asks near price (buyers defending).")
            }
            ob.depth?.let { d -> HqCard(title = "Depth", subtitle = "cumulative, top levels") { DepthChart(d.bids.take(60), d.asks.take(60)) } }
        }
        val v = flow?.venues?.firstOrNull()
        if (flow != null && flow.available && v != null) {
            HqCard(title = "Aggressor delta", subtitle = v.label) {
                TileGrid(listOf(
                    { m -> Tile("Δ 15m", Fmt.big(v.w15m.delta), m, s.signColor(Fmt.sign(v.w15m.delta))) },
                    { m -> Tile("Δ 1h", Fmt.big(v.w1h.delta), m, s.signColor(Fmt.sign(v.w1h.delta))) },
                    { m -> Tile("Δ 4h", Fmt.big(v.w4h.delta), m, s.signColor(Fmt.sign(v.w4h.delta))) },
                    { m -> Tile("Buy share 1h", Fmt.pct(v.w1h.buyPct), m) },
                    { m -> Tile("Flow score", Fmt.signed(flow.score), m, s.signColor(Fmt.sign(flow.score))) },
                ), perRow = 3)
                Note("Delta = taker buys minus taker sells. Sustained positive delta with a flat price often means passive sellers absorbing.")
            }
        }
    }
}

@Composable
private fun BullsTab(r: Technical, pos: Load<Positioning>) {
    val s = Hq.s
    val p = s.p
    Column(Modifier.screenPad(), verticalArrangement = Arrangement.spacedBy(s.gap)) {
        HqCard(title = "Technical ratings", subtitle = r.source) {
            if (!r.available) EmptyState(r.note ?: "Unavailable")
            else {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    r.tfs.forEach { (tf, x) ->
                        val rec = x.summary.recommendation
                        val c = when { rec.contains("BUY") -> p.up; rec.contains("SELL") -> p.dn; else -> p.warn }
                        Column(Modifier.width(118.dp).clip(s.chipShape).background(p.track).padding(10.dp)) {
                            Text(tf, style = monoStyle(11, FontWeight.Bold), color = p.tx2)
                            Text(Fmt.recommendation(rec), style = monoStyle(14, FontWeight.Bold), color = c, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("B ${x.summary.buy} · N ${x.summary.neutral} · S ${x.summary.sell}", style = monoStyle(10), color = p.tx2)
                            Text("osc ${Fmt.recommendation(x.oscillators)}", style = monoStyle(9), color = p.tx2, maxLines = 1)
                            Text("MAs ${Fmt.recommendation(x.ma)}", style = monoStyle(9), color = p.tx2, maxLines = 1)
                            Text(Fmt.signed(x.score), style = monoStyle(12, FontWeight.Bold), color = s.signColor(Fmt.sign(x.score)))
                        }
                    }
                }
                val first = r.tfs.entries.firstOrNull()
                if (first != null) {
                    Spacer(Modifier.height(10.dp))
                    listOf("RSI", "ADX", "Stoch.K", "CCI20", "MACD.macd", "EMA50", "EMA200").forEach { k ->
                        first.value.indicators[k]?.let { v -> KeyValue("$k (${first.key})", if (kotlin.math.abs(v) > 100) Fmt.price(v) else "%.2f".format(v)) }
                    }
                }
            }
        }
        when (pos) {
            is Load.Loading -> SkeletonCard(3, chart = true)
            is Load.Err -> ErrorState(pos.message, null)
            is Load.Ok -> {
                val x = pos.data
                HqCard(title = "Long / short · funding · OI", subtitle = x.source) {
                    if (!x.available) EmptyState(x.note.ifBlank { "Unavailable" })
                    else {
                        val ls = x.lsNow
                        val longPct = if (ls != null) 100 * ls / (1 + ls) else 50.0
                        Row {
                            Column(Modifier.weight(1f)) {
                                Text("BULLS · LONG ACCTS", style = MaterialTheme.typography.labelSmall, color = p.tx2)
                                Text(Fmt.pct(longPct), style = monoStyle(24, FontWeight.Bold), color = p.up)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("BEARS · SHORT ACCTS", style = MaterialTheme.typography.labelSmall, color = p.tx2)
                                Text(Fmt.pct(100 - longPct), style = monoStyle(24, FontWeight.Bold), color = p.dn)
                            }
                        }
                        SplitBar(longPct, Modifier.padding(vertical = 8.dp))
                        TileGrid(listOf(
                            { m -> Tile("L/S ratio", ls?.let { "%.2f".format(it) } ?: "-", m, if ((ls ?: 1.0) > 1) p.up else p.dn) },
                            { m -> Tile("Funding / 8h", x.funding?.let { "%.4f%%".format(it * 100) } ?: "-", m, s.signColor(Fmt.sign(x.funding))) },
                            { m -> Tile("OI change 1h", Fmt.signed(x.oiChange1hPct, 2) + "%", m, s.signColor(Fmt.sign(x.oiChange1hPct))) },
                            { m -> Tile("Crowd score", Fmt.signed(x.score), m, s.signColor(Fmt.sign(x.score))) },
                        ))
                        if (x.longShort.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Text("Long/short account ratio", style = MaterialTheme.typography.labelMedium, color = p.tx2)
                            LineChart(listOf(Overlay("L/S", p.info, x.longShort)), height = 120.dp, valueFormat = { "%.2f".format(it) })
                        }
                        if (x.oiUsd.isNotEmpty()) {
                            Text("Open interest (USD)", style = MaterialTheme.typography.labelMedium, color = p.tx2, modifier = Modifier.padding(top = 6.dp))
                            LineChart(listOf(Overlay("OI", p.warn, x.oiUsd)), height = 110.dp, area = true)
                        }
                        if (x.proxy) Note("Proxy venue for this market.", warn = true)
                        Note("Crowd positioning (trend-following reading). Extremes are often contrarian.")
                    }
                }
            }
        }
    }
}

@Composable
private fun SentimentTab(se: Sentiment, a: Analysis?) {
    val s = Hq.s
    val p = s.p
    Column(Modifier.screenPad(), verticalArrangement = Arrangement.spacedBy(s.gap)) {
        HqCard(title = "News sentiment", subtitle = "${se.windowH.toInt()}h · impact-weighted") {
            se.assets().forEachIndexed { i, (k, v) ->
                if (i > 0) Divider()
                Column(Modifier.padding(vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(k, style = monoStyle(13, FontWeight.Bold), color = p.tx, modifier = Modifier.width(52.dp))
                        Text(v.label.uppercase(), style = monoStyle(11, FontWeight.Bold), color = s.labelColor(v.label), modifier = Modifier.weight(1f))
                        Text(Fmt.signed(v.score), style = monoStyle(15, FontWeight.Bold), color = s.signColor(Fmt.sign(v.score)))
                    }
                    CompBar(v.score, Modifier.padding(vertical = 6.dp), height = 8.dp)
                    Text("${v.bull} bullish · ${v.bear} bearish · ${v.n} items", style = monoStyle(10), color = p.tx2)
                }
            }
            if (se.assets().isEmpty()) EmptyState("No sentiment yet")
        }
        se.fearGreed?.let { fg ->
            HqCard(title = "Crypto Fear & Greed", subtitle = fg.source) {
                val v = (fg.value ?: 50).toDouble()
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(fg.value?.toString() ?: "-", style = monoStyle(34, FontWeight.Bold), color = if (v >= 55) p.up else if (v <= 45) p.dn else p.warn)
                    Spacer(Modifier.width(10.dp))
                    Text(fg.label, style = MaterialTheme.typography.titleMedium, color = p.tx, modifier = Modifier.padding(bottom = 6.dp))
                    Spacer(Modifier.weight(1f))
                    Text("prev ${fg.prev ?: "-"}", style = monoStyle(11), color = p.tx2, modifier = Modifier.padding(bottom = 8.dp))
                }
                CompBar(v * 2 - 100, Modifier.padding(top = 6.dp), height = 8.dp)
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text("Extreme fear", style = monoStyle(9), color = p.tx2); Spacer(Modifier.weight(1f)); Text("Extreme greed", style = monoStyle(9), color = p.tx2)
                }
            }
        }
        a?.let { an ->
            val ns = an.status["news"]
            val comp = an.components.firstOrNull { it.id == "news" }
            HqCard(title = "In ${an.symbol}'s bias", subtitle = "news component") {
                KeyValue("News score", if (ns?.status == "ok") Fmt.signed(comp?.score) else "n/a", s.signColor(Fmt.sign(comp?.score)))
                KeyValue("Tag", ns?.tag ?: "-")
                KeyValue("Items", ns?.n?.toString() ?: "-")
                KeyValue("Weight", comp?.let { "%.2f".format(it.weight) } ?: "-")
            }
        }
        se.asOf?.let { Note("As of $it") }
    }
}
