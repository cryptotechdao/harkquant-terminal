@file:OptIn(ExperimentalMaterial3Api::class)

package com.harkquant.terminal.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harkquant.terminal.data.AtrResult
import com.harkquant.terminal.data.BarsResponse
import com.harkquant.terminal.data.IndicatorCatalog
import com.harkquant.terminal.data.IndicatorMeta
import com.harkquant.terminal.data.IndicatorResult
import com.harkquant.terminal.data.Load
import com.harkquant.terminal.data.Pivots
import com.harkquant.terminal.data.PositionRequest
import com.harkquant.terminal.data.PositionResult
import com.harkquant.terminal.data.Sessions
import com.harkquant.terminal.ui.ActiveIndicator
import com.harkquant.terminal.ui.paramValue
import com.harkquant.terminal.ui.components.CandleChart
import com.harkquant.terminal.ui.components.ChipRow
import com.harkquant.terminal.ui.components.Divider
import com.harkquant.terminal.ui.components.EmptyState
import com.harkquant.terminal.ui.components.HqButton
import com.harkquant.terminal.ui.components.HqCard
import com.harkquant.terminal.ui.components.HqIcons
import com.harkquant.terminal.ui.components.KeyValue
import com.harkquant.terminal.ui.components.LineChart
import com.harkquant.terminal.ui.components.Note
import com.harkquant.terminal.ui.components.Overlay
import com.harkquant.terminal.ui.components.ScreenHeader
import com.harkquant.terminal.ui.components.Segmented
import com.harkquant.terminal.ui.components.SkeletonBlock
import com.harkquant.terminal.ui.components.StaleBanner
import com.harkquant.terminal.ui.components.rememberNow
import com.harkquant.terminal.ui.theme.Hq
import com.harkquant.terminal.ui.theme.css
import com.harkquant.terminal.ui.theme.monoStyle
import com.harkquant.terminal.util.Fmt
import com.harkquant.terminal.util.Pkt
import com.harkquant.terminal.util.RiskMath

val IND_COLORS = listOf("#f0b90b", "#4c8dff", "#e040fb", "#26c6da", "#ff7043", "#9ccc65", "#ab47bc", "#ffd54f", "#8d6e63", "#78909c").map { css(it) }
val IND_TFS = listOf("1m", "5m", "15m", "1h", "4h", "1d")

@Composable
fun IndicatorsContent(
    symbols: List<String>, symbol: String, tf: String, bars: Load<BarsResponse>, catalog: Load<IndicatorCatalog>,
    active: List<ActiveIndicator>, results: List<IndicatorResult?>, atr: Load<AtrResult>, pivots: Load<Pivots>, sessions: Load<Sessions>,
    serverPos: Load<PositionResult>?, nowMs: Long,
    onSymbol: (String) -> Unit, onTf: (String) -> Unit, onRefresh: () -> Unit, onAdd: (IndicatorMeta) -> Unit, onRemove: (Int) -> Unit,
    onParam: (Int, String, Double) -> Unit, onVerify: (PositionRequest) -> Unit,
) {
    val s = Hq.s
    val p = s.p
    var showCatalog by remember { mutableStateOf(false) }
    var editIndex by remember { mutableStateOf<Int?>(null) }
    val barsData = bars.value
    HqList(false, onRefresh) {
        item("h") { ScreenHeader("Own-data chart · TA-Lib", "Indicators & Tools") }
        item("sym") { ChipRow(symbols, symbol, onSymbol, Modifier.screenPad()) }
        item("tf") { Segmented(IND_TFS, tf, onTf, Modifier.screenPad()) }
        item("chart") {
            val overlays = results.mapIndexedNotNull { i, r ->
                if (r == null || r.error != null || r.pane != "price") null
                else r.lines.mapIndexed { j, ln -> Overlay(ln.name, if (j == 0) IND_COLORS[i % IND_COLORS.size] else IND_COLORS[(i + j + 3) % IND_COLORS.size], ln.data) }
            }.flatten()
            HqCard(Modifier.screenPad(), title = symbol, subtitle = barsData?.let { it.source + if (it.proxy) " (proxy)" else "" }) {
                when (bars) {
                    is Load.Loading -> SkeletonBlock(Modifier.fillMaxWidth(), 300.dp)
                    is Load.Err -> EmptyState(bars.message)
                    is Load.Ok -> {
                        if (bars.stale) StaleBanner(bars.error)
                        CandleChart(bars.data.bars, height = 310.dp, overlays = overlays, daily = tf == "1d", initialVisible = 120)
                    }
                }
                Spacer(Modifier.height(10.dp))
                // active indicator legend
                active.forEachIndexed { i, a ->
                    val r = results.getOrNull(i)
                    Row(Modifier.fillMaxWidth().clickable { editIndex = i }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(IND_COLORS[i % IND_COLORS.size]))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.name, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = p.tx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val params = a.params.entries.take(3).joinToString(" · ") { (k, v) -> "$k ${v.toString().trim('"')}" }
                            Text(
                                when { r == null -> "computing…"; r.error != null -> r.error; else -> "${r.pane} · $params" },
                                style = monoStyle(10), color = if (r?.error != null) p.dn else p.tx2, maxLines = 1,
                            )
                        }
                        r?.lines?.firstOrNull()?.data?.lastOrNull()?.let { lv -> Text(Fmt.price(lv.value), style = monoStyle(11, FontWeight.SemiBold), color = p.tx) }
                        androidx.compose.material3.IconButton(onClick = { onRemove(i) }) { Icon(HqIcons.Close, "Remove", tint = p.tx2, modifier = Modifier.size(16.dp)) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                HqButton("Add indicator", { showCatalog = true }, Modifier.fillMaxWidth(), primary = false)
            }
        }
        results.forEachIndexed { i, r ->
            if (r != null && r.error == null && r.pane == "own" && r.lines.isNotEmpty()) {
                item("pane$i${active.getOrNull(i)?.id}") {
                    HqCard(Modifier.screenPad(), title = r.name.take(32), subtitle = r.lines.joinToString(" · ") { it.name }) {
                        val series = r.lines.mapIndexed { j, ln -> Overlay(ln.name, if (j == 0) IND_COLORS[i % IND_COLORS.size] else IND_COLORS[(i + j + 3) % IND_COLORS.size], ln.data.takeLast(220), hist = ln.hist) }
                        LineChart(series, height = 130.dp, daily = tf == "1d", valueFormat = { Fmt.fixed(it, if (kotlin.math.abs(it) >= 100) 0 else 2) })
                    }
                }
            }
            if (r != null && r.error == null && r.pane == "marker") {
                item("mk$i") { HqCard(Modifier.screenPad(), title = r.name.take(32), subtitle = "pattern markers") { Text("${r.markers.size} signals in range · last ${r.markers.lastOrNull()?.let { Pkt.dayHm(it.time.toDouble()) } ?: "-"} PKT", style = monoStyle(11), color = p.tx2) } }
            }
        }
        item("pos") { PositionCalculator(symbol, barsData?.bars?.lastOrNull()?.close, serverPos, onVerify) }
        item("atr") {
            HqCard(Modifier.screenPad(), title = "ATR stop / target", subtitle = "1.5×ATR(14) · $tf") {
                when (atr) {
                    is Load.Loading -> SkeletonBlock(Modifier.fillMaxWidth(), 80.dp)
                    is Load.Err -> EmptyState(atr.message)
                    is Load.Ok -> {
                        val a = atr.data
                        KeyValue("ATR(14)", Fmt.price(a.atr))
                        KeyValue("Close", Fmt.price(a.close))
                        KeyValue("Long SL / TP", "${Fmt.price(a.close - 1.5 * a.atr)} / ${Fmt.price(a.close + 3 * a.atr)}", p.up)
                        KeyValue("Short SL / TP", "${Fmt.price(a.close + 1.5 * a.atr)} / ${Fmt.price(a.close - 3 * a.atr)}", p.dn)
                        KeyValue("Source", a.source)
                    }
                }
            }
        }
        item("piv") { PivotsCard(pivots) }
        item("sess") { SessionsCard(sessions, nowMs) }
        item("note") {
            val c = catalog.value
            Note(c?.let { "${it.total} indicators · ${it.libraries.joinToString(" · ")}. ${it.note}" } ?: "Indicators are computed on the server from the chart's own bars.", modifier = Modifier.screenPad())
        }
    }
    if (showCatalog) CatalogSheet(catalog, onDismiss = { showCatalog = false }) { onAdd(it); showCatalog = false }
    editIndex?.let { idx ->
        val a = active.getOrNull(idx)
        if (a == null) editIndex = null else ParamDialog(a, onDismiss = { editIndex = null }) { k, v -> onParam(idx, k, v) }
    }
}

@Composable
private fun CatalogSheet(catalog: Load<IndicatorCatalog>, onDismiss: () -> Unit, onPick: (IndicatorMeta) -> Unit) {
    val s = Hq.s
    val p = s.p
    var q by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = s.solidSurface) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(horizontal = 16.dp)) {
            Text("Add indicator", style = MaterialTheme.typography.titleLarge, color = p.tx)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Search 180+ indicators") },
                leadingIcon = { Icon(HqIcons.Search, null, tint = p.tx2) },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = p.acc, unfocusedBorderColor = p.line))
            Spacer(Modifier.height(8.dp))
            when (catalog) {
                is Load.Loading -> repeat(6) { SkeletonBlock(Modifier.fillMaxWidth().padding(vertical = 5.dp), 28.dp) }
                is Load.Err -> EmptyState(catalog.message)
                is Load.Ok -> {
                    val ql = q.trim().lowercase()
                    val rows = catalog.data.groups.flatMap { g ->
                        val its = g.items.filter { ql.isEmpty() || it.name.lowercase().contains(ql) || it.short.lowercase().contains(ql) }
                        if (its.isEmpty()) emptyList() else listOf<Any>(g.group) + its
                    }
                    LazyColumn(Modifier.weight(1f)) {
                        items(rows) { r ->
                            if (r is String) Text(r, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = p.acc, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
                            else if (r is IndicatorMeta) Row(Modifier.fillMaxWidth().clickable { onPick(r) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(r.name, style = MaterialTheme.typography.bodyMedium, color = p.tx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(r.short + when (r.pane) { "price" -> " · overlay"; "marker" -> " · markers"; else -> " · pane" }, style = monoStyle(10), color = p.tx2)
                                }
                                Icon(HqIcons.Plus, "Add", tint = p.acc)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ParamDialog(a: ActiveIndicator, onDismiss: () -> Unit, onSet: (String, Double) -> Unit) {
    val s = Hq.s
    val p = s.p
    val keys = a.params.keys.take(5)
    val values = remember(a) { keys.associateWith { mutableStateOf(paramValue(a.params, it)?.let { v -> if (v % 1.0 == 0.0) v.toLong().toString() else v.toString() } ?: "") } }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = s.solidSurface,
        title = { Text(a.name, style = MaterialTheme.typography.titleMedium, color = p.tx) },
        text = {
            Column {
                if (keys.isEmpty()) Text("This indicator has no parameters.", color = p.tx2)
                keys.forEach { k ->
                    OutlinedTextField(values.getValue(k).value, { values.getValue(k).value = it }, Modifier.fillMaxWidth().padding(vertical = 3.dp), label = { Text(k) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
            }
        },
        confirmButton = { TextButton({ keys.forEach { k -> values.getValue(k).value.toDoubleOrNull()?.let { onSet(k, it) } }; onDismiss() }) { Text("Apply", color = p.acc) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = p.tx2) } },
    )
}

@Composable
fun PositionCalculator(symbol: String, lastClose: Double?, serverPos: Load<PositionResult>?, onVerify: (PositionRequest) -> Unit) {
    val s = Hq.s
    val p = s.p
    var balance by rememberSaveable { mutableStateOf("1000") }
    var mode by rememberSaveable { mutableStateOf("pct") }
    var risk by rememberSaveable { mutableStateOf("1") }
    var entry by rememberSaveable(symbol) { mutableStateOf("") }
    var stop by rememberSaveable(symbol) { mutableStateOf("") }
    var contract by rememberSaveable(symbol) { mutableStateOf((RiskMath.contractDefaults[symbol] ?: 1.0).let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }) }
    var rr by rememberSaveable { mutableStateOf("2") }
    LaunchedEffect(symbol, lastClose) { if (entry.isBlank() && lastClose != null) entry = Fmt.fixed(lastClose, Fmt.decimalsFor(lastClose)).replace(",", "") }
    val res = RiskMath.size(balance.toDoubleOrNull() ?: 0.0, risk.toDoubleOrNull() ?: 0.0, mode == "pct", entry.toDoubleOrNull() ?: 0.0, stop.toDoubleOrNull() ?: 0.0, contract.toDoubleOrNull() ?: 1.0, rr.toDoubleOrNull() ?: 2.0)
    HqCard(Modifier.screenPad(), title = "Position size / risk", subtitle = "no orders are sent") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("Balance", balance, { balance = it }, Modifier.weight(1f))
            NumField(if (mode == "pct") "Risk %" else "Risk $", risk, { risk = it }, Modifier.weight(1f))
        }
        Segmented(listOf("pct", "money"), mode, { mode = it }, Modifier.padding(vertical = 6.dp), labels = listOf("% of balance", "Fixed money"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("Entry", entry, { entry = it }, Modifier.weight(1f))
            NumField("Stop loss", stop, { stop = it }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("Contract size", contract, { contract = it }, Modifier.weight(1f))
            NumField("Target R:R", rr, { rr = it }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().clip(s.chipShape).background(p.track).padding(12.dp)) {
            if (res == null) Text("Enter an entry and a stop loss", style = MaterialTheme.typography.bodySmall, color = p.tx2)
            else Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(Fmt.fixed(res.lotsRounded, 2) + " lots", style = monoStyle(22, FontWeight.Bold), color = p.acc)
                    Spacer(Modifier.width(8.dp))
                    Text(if (res.long) "LONG" else "SHORT", style = monoStyle(12, FontWeight.Bold), color = if (res.long) p.up else p.dn, modifier = Modifier.padding(bottom = 4.dp))
                }
                Text("exact ${"%.4f".format(res.lotsExact)} · risk ${Fmt.fixed(res.riskMoney, 2)} · stop ${"%.6g".format(res.stopDist)}", style = monoStyle(10), color = p.tx2)
                Text("TP @ ${Fmt.price(res.target)}  (${rr}R = ${Fmt.fixed(res.reward, 2)})", style = monoStyle(11), color = p.tx)
            }
        }
        Spacer(Modifier.height(8.dp))
        HqButton("Verify on server", {
            val e = entry.toDoubleOrNull(); val st = stop.toDoubleOrNull()
            if (e != null && st != null) onVerify(PositionRequest(balance.toDoubleOrNull() ?: 0.0,
                riskPct = if (mode == "pct") risk.toDoubleOrNull() else null, riskMoney = if (mode == "money") risk.toDoubleOrNull() else null,
                entry = e, stop = st, contract = contract.toDoubleOrNull() ?: 1.0))
        }, Modifier.fillMaxWidth(), primary = false, enabled = res != null)
        when (serverPos) {
            null -> {}
            is Load.Loading -> Note("Asking the server…")
            is Load.Err -> Note(serverPos.message, warn = true)
            is Load.Ok -> Note("Server: ${Fmt.fixed(serverPos.data.lotsRounded, 2)} lots ${serverPos.data.side} · risk ${Fmt.fixed(serverPos.data.risk, 2)} · loss/lot ${Fmt.fixed(serverPos.data.lossPerLot, 2)}")
        }
    }
}

@Composable
private fun NumField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val p = Hq.c
    OutlinedTextField(
        value, { v -> onChange(v.filter { it.isDigit() || it == '.' || it == '-' }) }, modifier.padding(vertical = 2.dp),
        label = { Text(label) }, singleLine = true, textStyle = monoStyle(14),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = p.acc, unfocusedBorderColor = p.line, focusedLabelColor = p.acc),
    )
}

@Composable
fun PivotsCard(pivots: Load<Pivots>) {
    val s = Hq.s
    val p = s.p
    var method by rememberSaveable { mutableStateOf("classic") }
    HqCard(Modifier.screenPad(), title = "Pivot points", subtitle = "previous UTC day") {
        Segmented(listOf("classic", "fibonacci", "camarilla", "woodie"), method, { method = it }, labels = listOf("Classic", "Fib", "Camarilla", "Woodie"))
        Spacer(Modifier.height(8.dp))
        when (pivots) {
            is Load.Loading -> SkeletonBlock(Modifier.fillMaxWidth(), 120.dp)
            is Load.Err -> EmptyState(pivots.message)
            is Load.Ok -> {
                val m = pivots.data.method(method)
                listOf("R4", "R3", "R2", "R1", "P", "S1", "S2", "S3", "S4").filter { m.containsKey(it) }.forEach { k ->
                    val c: Color = when { k.startsWith("R") -> p.dn; k.startsWith("S") -> p.up; else -> p.acc }
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(k, style = monoStyle(12, FontWeight.Bold), color = c, modifier = Modifier.width(36.dp))
                        Box(Modifier.weight(1f).height(1.dp).background(p.line))
                        Spacer(Modifier.width(8.dp))
                        Text(Fmt.price(m[k]), style = monoStyle(12), color = p.tx)
                    }
                }
                val d = pivots.data.prevDay
                Note("Prev day H ${Fmt.price(d.high)} · L ${Fmt.price(d.low)} · C ${Fmt.price(d.close)} · ${pivots.data.source}")
            }
        }
    }
}

@Composable
fun SessionsCard(sessions: Load<Sessions>, nowMs: Long) {
    val s = Hq.s
    val p = s.p
    HqCard(Modifier.screenPad(), title = "Trading sessions", subtitle = "PKT") {
        when (sessions) {
            is Load.Loading -> SkeletonBlock(Modifier.fillMaxWidth(), 100.dp)
            is Load.Err -> EmptyState(sessions.message)
            is Load.Ok -> {
                val utcH = ((nowMs / 1000) % 86400) / 3600.0
                sessions.data.sessions.forEach { se ->
                    Column(Modifier.padding(vertical = 5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(se.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = if (se.active) p.tx else p.tx2, modifier = Modifier.weight(1f))
                            Text(
                                if (se.active && se.closes != null) "open · closes in ${Fmt.countdown(se.closes - nowMs / 1000.0)}"
                                else "${Pkt.utcHourToPkt(se.openUtc)}–${Pkt.utcHourToPkt(se.closeUtc)}" + (se.nextOpen?.let { " · opens in ${Fmt.countdown(it - nowMs / 1000.0)}" } ?: ""),
                                style = monoStyle(10.5f.toInt()), color = if (se.active) p.up else p.tx2,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(8.dp)) {
                            drawRect(p.track)
                            val a = se.openUtc / 24f
                            val w = (((se.closeUtc - se.openUtc) + 24) % 24) / 24f
                            val c = if (se.active) p.up else p.acc.copy(alpha = 0.5f)
                            val first = minOf(w, 1f - a)
                            drawRect(c, androidx.compose.ui.geometry.Offset(a * size.width, 0f), androidx.compose.ui.geometry.Size(first * size.width, size.height))
                            if (a + w > 1f) drawRect(c, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Size((a + w - 1f) * size.width, size.height))
                            val x = (utcH / 24.0).toFloat() * size.width
                            drawRect(p.warn, androidx.compose.ui.geometry.Offset(x - 1.5f, -2f), androidx.compose.ui.geometry.Size(3f, size.height + 4f))
                        }
                    }
                }
                Divider(Modifier.padding(top = 6.dp))
                Note("Hours in PKT (standard-time convention; DST shifts London/NY by 1h). Bar = 24h UTC, marker = now.")
            }
        }
    }
}
