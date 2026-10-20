package com.harkquant.terminal.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.harkquant.terminal.data.BarsResponse
import com.harkquant.terminal.data.IndicatorResult
import com.harkquant.terminal.data.Load
import com.harkquant.terminal.data.SymbolInfo
import com.harkquant.terminal.ui.components.CandleChart
import com.harkquant.terminal.ui.components.ChipRow
import com.harkquant.terminal.ui.components.EmptyState
import com.harkquant.terminal.ui.components.HqCard
import com.harkquant.terminal.ui.components.KeyValue
import com.harkquant.terminal.ui.components.Note
import com.harkquant.terminal.ui.components.Overlay
import com.harkquant.terminal.ui.components.ScreenHeader
import com.harkquant.terminal.ui.components.Segmented
import com.harkquant.terminal.ui.components.SkeletonBlock
import com.harkquant.terminal.ui.components.StaleBanner
import com.harkquant.terminal.ui.components.StrokeLine
import com.harkquant.terminal.ui.theme.Hq
import com.harkquant.terminal.ui.theme.monoStyle
import com.harkquant.terminal.util.Fmt
import com.harkquant.terminal.util.Pkt

private val TV_INTERVAL = mapOf("1m" to "1", "5m" to "5", "15m" to "15", "1h" to "60", "4h" to "240", "1d" to "D")

@Composable
fun TradingViewContent(
    symbols: List<SymbolInfo>, symbol: String, tf: String, mode: String, bars: Load<BarsResponse>, ema: IndicatorResult?,
    onSymbol: (String) -> Unit, onTf: (String) -> Unit, onMode: (String) -> Unit, onRefresh: () -> Unit,
) {
    val s = Hq.s
    val p = s.p
    val info = symbols.firstOrNull { it.symbol == symbol }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(info?.name ?: "Chart", "TradingView")
        Column(Modifier.screenPad(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChipRow(symbols.map { it.symbol }, symbol, onSymbol)
            Segmented(listOf("native", "tv"), mode, onMode, labels = listOf("Our data", "TradingView chart"))
            Segmented(CHART_TFS_TV, tf, onTf)
        }
        Spacer(Modifier.height(s.gap))
        if (mode == "tv") {
            val tvw = info?.tvw?.ifBlank { null } ?: "OANDA:XAUUSD"
            Box(Modifier.weight(1f).screenPad().padding(bottom = 8.dp)) {
                TradingViewWidget(tvw, TV_INTERVAL[tf] ?: "15", s.isLight, s.solidSurface.toArgb(), Modifier.fillMaxSize())
            }
            Text("Chart by TradingView · official Advanced Chart widget", style = MaterialTheme.typography.labelSmall, color = p.tx2, modifier = Modifier.screenPad().padding(bottom = 6.dp))
        } else {
            HqList(false, onRefresh, Modifier.weight(1f)) {
                item("chart") {
                    val b = bars.value
                    val last = b?.bars?.lastOrNull()
                    val prev = b?.bars?.getOrNull((b.bars.size - 2).coerceAtLeast(0))
                    HqCard(Modifier.screenPad()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(symbol, style = monoStyle(14, FontWeight.Bold), color = p.tx)
                            Spacer(Modifier.width(6.dp))
                            Text(b?.source ?: "", style = MaterialTheme.typography.labelSmall, color = p.tx2, modifier = Modifier.weight(1f), maxLines = 1)
                            if (last != null) {
                                Text(Fmt.price(last.close), style = monoStyle(15, FontWeight.Bold), color = p.tx)
                                Spacer(Modifier.width(6.dp))
                                val ch = if (prev != null && prev.close != 0.0) (last.close - prev.close) / prev.close * 100 else 0.0
                                Text(Fmt.signed(ch, 2) + "%", style = monoStyle(12, FontWeight.SemiBold), color = s.signColor(Fmt.sign(ch)))
                            }
                        }
                        if (last != null) Text("O ${Fmt.price(last.open)}  H ${Fmt.price(last.high)}  L ${Fmt.price(last.low)}  C ${Fmt.price(last.close)}", style = monoStyle(10), color = p.tx2, modifier = Modifier.padding(top = 2.dp))
                        Spacer(Modifier.height(8.dp))
                        when (bars) {
                            is Load.Loading -> SkeletonBlock(Modifier.fillMaxWidth(), 380.dp)
                            is Load.Err -> EmptyState(bars.message)
                            is Load.Ok -> {
                                if (bars.stale) StaleBanner(bars.error)
                                val ov = ema?.lines?.firstOrNull()?.let { listOf(Overlay("EMA 21", p.acc2, it.data)) } ?: emptyList()
                                CandleChart(bars.data.bars, height = 400.dp, overlays = ov, daily = tf == "1d", initialVisible = 110)
                                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (ov.isNotEmpty()) { StrokeLine(p.acc2); Spacer(Modifier.width(6.dp)); Text("EMA 21", style = monoStyle(10), color = p.tx2); Spacer(Modifier.width(12.dp)) }
                                    Text("Pinch to zoom · drag to scroll · long-press for OHLC · double-tap to reset", style = MaterialTheme.typography.labelSmall, color = p.tx2)
                                }
                            }
                        }
                    }
                }
                item("stats") {
                    val list = bars.value?.bars.orEmpty()
                    if (list.isNotEmpty()) HqCard(Modifier.screenPad(), title = "Range", subtitle = "${list.size} bars · $tf") {
                        val hi = list.maxOf { it.high }; val lo = list.minOf { it.low }
                        KeyValue("High", Fmt.price(hi), p.up)
                        KeyValue("Low", Fmt.price(lo), p.dn)
                        KeyValue("From", Pkt.full(list.first().time.toDouble()))
                        KeyValue("To", Pkt.full(list.last().time.toDouble()))
                        KeyValue("TradingView symbol", info?.tvw ?: "-")
                        if (!info?.notes.isNullOrBlank()) Note(info!!.notes, warn = true)
                    }
                }
            }
        }
    }
}

private val CHART_TFS_TV = listOf("1m", "5m", "15m", "1h", "4h", "1d")

fun tvHtml(symbol: String, interval: String, light: Boolean, bgArgb: Int): String {
    val bg = String.format("#%06X", 0xFFFFFF and bgArgb)
    val theme = if (light) "light" else "dark"
    return """
<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
<style>html,body{margin:0;padding:0;height:100%;background:$bg;overflow:hidden}.tradingview-widget-container{height:100%;width:100%}</style></head>
<body><div class="tradingview-widget-container"><div class="tradingview-widget-container__widget" style="height:100%;width:100%"></div>
<script type="text/javascript" src="https://s3.tradingview.com/external-embedding/embed-widget-advanced-chart.js" async>
{"autosize":true,"symbol":"$symbol","interval":"$interval","timezone":"Asia/Karachi","theme":"$theme","style":"1","locale":"en",
"backgroundColor":"$bg","allow_symbol_change":true,"hide_side_toolbar":false,"withdateranges":true,"details":false,"calendar":false,
"support_host":"https://www.tradingview.com"}
</script></div></body></html>
""".trimIndent()
}
