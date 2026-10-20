package com.harkquant.terminal.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.harkquant.terminal.BuildConfig
import com.harkquant.terminal.data.AppSettings
import com.harkquant.terminal.data.Health
import com.harkquant.terminal.data.Load
import com.harkquant.terminal.data.ServerStatus
import com.harkquant.terminal.ui.components.Divider
import com.harkquant.terminal.ui.components.EmptyState
import com.harkquant.terminal.ui.components.HqButton
import com.harkquant.terminal.ui.components.HqCard
import com.harkquant.terminal.ui.components.HqIcons
import com.harkquant.terminal.ui.components.KeyValue
import com.harkquant.terminal.ui.components.Note
import com.harkquant.terminal.ui.components.ScreenHeader
import com.harkquant.terminal.ui.components.SkeletonBlock
import com.harkquant.terminal.ui.components.StatusDot
import com.harkquant.terminal.ui.theme.Form
import com.harkquant.terminal.ui.theme.Hq
import com.harkquant.terminal.ui.theme.ThemeSpec
import com.harkquant.terminal.ui.theme.Themes
import com.harkquant.terminal.ui.theme.monoStyle
import com.harkquant.terminal.util.Pkt
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun SettingsContent(
    settings: AppSettings, token: String?, status: Load<ServerStatus>, health: Load<Pair<Health, Long>>?, server: Load<JsonObject>,
    saveResult: String?, tokenSaved: Boolean?,
    onTheme: (Int) -> Unit, onRefresh: (Int) -> Unit, onAlerts: (Boolean) -> Unit, onBaseUrl: (String) -> Unit, onTest: () -> Unit,
    onToken: (String?) -> Unit, onSaveServer: (JsonObject) -> Unit, onReload: () -> Unit,
) {
    val s = Hq.s
    val p = s.p
    val uri = LocalUriHandler.current
    HqList(false, onReload) {
        item("h") { ScreenHeader("Saved on this device", "Settings") }
        item("theme") {
            HqCard(Modifier.screenPad(), title = "Theme", subtitle = "20 themes · 5 layouts × 4 palettes") {
                Form.entries.forEach { f ->
                    Text("${f.letter} · ${f.title}", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = p.tx2, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Themes.all.filter { it.form == f }.forEach { t -> ThemeSwatch(t, t.id == settings.themeId, Modifier.weight(1f)) { onTheme(t.id) } }
                    }
                }
                Spacer(Modifier.height(8.dp))
                val cur = Themes.byId(settings.themeId)
                Text("${cur.label} — ${cur.description}", style = MaterialTheme.typography.bodySmall, color = p.tx2)
            }
        }
        item("conn") {
            var url by rememberSaveable(settings.baseUrl) { mutableStateOf(settings.baseUrl) }
            HqCard(Modifier.screenPad(), title = "Server", subtitle = "HarkQuant Terminal API") {
                Field("Server URL", url, { url = it.trim() }, keyboard = KeyboardType.Uri)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    HqButton("Save", { onBaseUrl(url) }, Modifier.weight(1f), enabled = url.isNotBlank() && url != settings.baseUrl)
                    HqButton("Test", onTest, Modifier.weight(1f), primary = false)
                }
                if (url != BuildConfig.DEFAULT_BASE_URL) Text("Reset to default", style = MaterialTheme.typography.labelMedium, color = p.acc,
                    modifier = Modifier.padding(top = 8.dp).clickable { url = BuildConfig.DEFAULT_BASE_URL; onBaseUrl(BuildConfig.DEFAULT_BASE_URL) })
                when (health) {
                    null -> {}
                    is Load.Loading -> Note("Testing…")
                    is Load.Err -> Note("Failed: ${health.message}", warn = true)
                    is Load.Ok -> Note("Connected · ${health.data.first.app} v${health.data.first.version} · ${health.data.second} ms")
                }
                Spacer(Modifier.height(10.dp)); Divider(); Spacer(Modifier.height(6.dp))
                Text("Source status", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = p.tx2)
                when (status) {
                    is Load.Loading -> SkeletonBlock(Modifier.fillMaxWidth().padding(top = 6.dp), 60.dp)
                    is Load.Err -> Note(status.message, warn = true)
                    is Load.Ok -> {
                        status.value!!.market.forEach { (k, v) ->
                            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                StatusDot(v.status); Spacer(Modifier.width(8.dp))
                                Text(k, style = MaterialTheme.typography.bodySmall, color = p.tx, modifier = Modifier.weight(1f))
                                Text(v.latencyMs?.let { "${it.toInt()} ms" } ?: (v.status ?: "-"), style = monoStyle(11), color = p.tx2)
                            }
                        }
                        status.value!!.news?.let { n ->
                            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                StatusDot(if (n.fail > n.ok) "fail" else "ok"); Spacer(Modifier.width(8.dp))
                                Text("news sources", style = MaterialTheme.typography.bodySmall, color = p.tx, modifier = Modifier.weight(1f))
                                Text("${n.ok} ok · ${n.fail} fail · ${n.needsKey} need key", style = monoStyle(11), color = p.tx2)
                            }
                        }
                        Note("Server clock ${Pkt.clock((status.value!!.serverTime * 1000).toLong())} PKT")
                    }
                }
            }
        }
        item("refresh") {
            var v by remember(settings.refreshSec) { mutableFloatStateOf(settings.refreshSec.toFloat()) }
            HqCard(Modifier.screenPad(), title = "Refresh", subtitle = "polling fallback & charts") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Refresh interval", style = MaterialTheme.typography.bodyMedium, color = p.tx, modifier = Modifier.weight(1f))
                    Text("${v.toInt()} s", style = monoStyle(13, FontWeight.Bold), color = p.acc)
                }
                Slider(v, { v = (it / 5f).roundToInt() * 5f }, valueRange = 5f..120f, onValueChangeFinished = { onRefresh(v.toInt()) },
                    colors = SliderDefaults.colors(thumbColor = p.acc, activeTrackColor = p.acc, inactiveTrackColor = p.track))
                Note("Live data streams over SSE; this interval drives price cards, charts and the polling fallback when the stream drops.")
            }
        }
        item("alerts") {
            HqCard(Modifier.screenPad(), title = "Notifications") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("High-impact headline alerts", style = MaterialTheme.typography.bodyMedium, color = p.tx)
                        Text("HIGH and CRITICAL headlines while the app is running in the background", style = MaterialTheme.typography.bodySmall, color = p.tx2)
                    }
                    Switch(settings.headlineAlerts, onAlerts, colors = SwitchDefaults.colors(checkedTrackColor = p.acc, checkedThumbColor = p.onAcc))
                }
            }
        }
        item("token") {
            var t by rememberSaveable { mutableStateOf("") }
            var show by remember { mutableStateOf(false) }
            HqCard(Modifier.screenPad(), title = "Settings token", subtitle = "for saving server settings") {
                OutlinedTextField(
                    t, { t = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(if (token != null) "Token saved — type to replace" else "HQT token") },
                    visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { IconButton({ show = !show }) { Icon(HqIcons.Eye, "Show", tint = p.tx2) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = p.acc, unfocusedBorderColor = p.line, focusedLabelColor = p.acc),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    HqButton("Save token", { onToken(t); t = "" }, Modifier.weight(1f), enabled = t.isNotBlank())
                    HqButton("Clear", { onToken(null); t = "" }, Modifier.weight(1f), primary = false, enabled = token != null)
                }
                Note(when {
                    tokenSaved == false -> "Encrypted storage unavailable on this device: the token is kept for this session only."
                    token != null -> "A token is stored, encrypted with the Android Keystore. It is only sent when you save server settings."
                    else -> "Only needed to change settings on the server. Read-only data works without it."
                })
            }
        }
        item("server") { ServerSettingsCard(server, saveResult, token != null, onSaveServer) }
        item("about") {
            HqCard(Modifier.screenPad(), title = "About") {
                KeyValue("App", "HarkQuant Terminal")
                KeyValue("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                KeyValue("Time zone", "Asia/Karachi (PKT, UTC+05:00)")
                KeyValue("Theme", Themes.byId(settings.themeId).label)
                Note("Read-only market analysis. This app never places, modifies or cancels orders.")
                Note("Charts in the TradingView tab are provided by TradingView's official widget.")
                Text("Open web terminal", style = MaterialTheme.typography.labelLarge, color = p.acc, modifier = Modifier.padding(top = 10.dp).clickable { runCatching { uri.openUri(settings.baseUrl) } })
            }
        }
    }
}

@Composable
private fun ThemeSwatch(t: ThemeSpec, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val s = Hq.s
    val p = s.p
    val tp = t.palette
    Column(modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        val shape = RoundedCornerShape(10.dp)
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.78f).clip(shape)
                .background(tp.bgGradient?.let { Brush.linearGradient(it) } ?: Brush.linearGradient(listOf(tp.bg, tp.bg)))
                .border(if (selected) 2.5.dp else 1.dp, if (selected) p.acc else p.line, shape)
                .padding(6.dp),
        ) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.fillMaxWidth(0.6f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(tp.acc))
                Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(if (t.form == Form.MOBILE || t.form == Form.GLASS) 6.dp else 1.dp))
                    .background(if (tp.panel.alpha < 0.05f) tp.track else tp.panel).border(1.dp, tp.line, RoundedCornerShape(if (t.form == Form.MOBILE || t.form == Form.GLASS) 6.dp else 1.dp)).padding(4.dp)) {
                    Row(Modifier.align(Alignment.BottomStart), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
                        listOf(10, 16, 12, 20, 14, 22).forEachIndexed { i, h -> Box(Modifier.width(3.dp).height(h.dp).background(if (i % 3 == 2) tp.dn else tp.up)) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(tp.up)); Box(Modifier.size(6.dp).clip(CircleShape).background(tp.dn)); Box(Modifier.size(6.dp).clip(CircleShape).background(tp.tx))
                }
            }
            if (selected) Box(Modifier.align(Alignment.TopEnd).size(16.dp).clip(CircleShape).background(p.acc), contentAlignment = Alignment.Center) {
                Icon(HqIcons.Check, null, tint = p.onAcc, modifier = Modifier.size(11.dp))
            }
        }
        Text(t.name, style = MaterialTheme.typography.labelSmall, color = if (selected) p.acc else p.tx2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        Text("%02d".format(t.id), style = monoStyle(9), color = p.tx2)
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, keyboard: KeyboardType = KeyboardType.Text) {
    val p = Hq.c
    OutlinedTextField(value, onChange, modifier.fillMaxWidth(), singleLine = true, label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = p.acc, unfocusedBorderColor = p.line, focusedLabelColor = p.acc))
}

private fun JsonObject.path(a: String, b: String): String? = runCatching { this[a]?.jsonObject?.get(b)?.jsonPrimitive?.contentOrNull }.getOrNull()

@Composable
private fun ServerSettingsCard(server: Load<JsonObject>, saveResult: String?, hasToken: Boolean, onSave: (JsonObject) -> Unit) {
    val s = Hq.s
    val p = s.p
    HqCard(Modifier.screenPad(), title = "Server settings", subtitle = "stored on the server") {
        when (server) {
            is Load.Loading -> SkeletonBlock(Modifier.fillMaxWidth(), 120.dp)
            is Load.Err -> EmptyState(server.message)
            is Load.Ok -> {
                val root = server.data
                val st = runCatching { root["settings"]!!.jsonObject }.getOrNull()
                val tokenRequired = runCatching { root["token_required"]?.jsonPrimitive?.contentOrNull == "true" }.getOrDefault(false)
                if (st == null) { EmptyState("No settings returned"); return@HqCard }
                var analysisS by remember(st) { mutableStateOf(st.path("refresh", "analysis_s") ?: "") }
                var tickerS by remember(st) { mutableStateOf(st.path("refresh", "ticker_s") ?: "") }
                var band by remember(st) { mutableStateOf(st.path("bias", "neutral_band") ?: "") }
                var minImpact by remember(st) { mutableStateOf(st.path("ticker", "headline_min_impact") ?: "MEDIUM") }
                Field("Analysis / summary refresh (s)", analysisS, { analysisS = it }, keyboard = KeyboardType.Number)
                Field("Ticker refresh (s)", tickerS, { tickerS = it }, Modifier.padding(top = 6.dp), keyboard = KeyboardType.Number)
                Field("Bias neutral band", band, { band = it }, Modifier.padding(top = 6.dp), keyboard = KeyboardType.Decimal)
                Text("Ticker min headline impact", style = MaterialTheme.typography.bodySmall, color = p.tx2, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                com.harkquant.terminal.ui.components.Segmented(listOf("LOW", "MEDIUM", "HIGH", "CRITICAL"), minImpact, { minImpact = it })
                Spacer(Modifier.height(10.dp))
                HqButton("Save to server", {
                    onSave(buildJsonObject {
                        put("refresh", buildJsonObject {
                            analysisS.toIntOrNull()?.let { put("analysis_s", JsonPrimitive(it)) }
                            tickerS.toIntOrNull()?.let { put("ticker_s", JsonPrimitive(it)) }
                        })
                        band.toDoubleOrNull()?.let { put("bias", buildJsonObject { put("neutral_band", JsonPrimitive(it)) }) }
                        put("ticker", buildJsonObject { put("headline_min_impact", JsonPrimitive(minImpact)) })
                    })
                }, Modifier.fillMaxWidth(), enabled = !tokenRequired || hasToken)
                Note(when {
                    tokenRequired && !hasToken -> "This server requires its HQT token. Add it above to save."
                    tokenRequired -> "Changes apply to every client of this server."
                    else -> "This server has no token set; changes apply to every client."
                })
                saveResult?.let { Note(it, warn = it.startsWith("Save failed")) }
            }
        }
    }
}
