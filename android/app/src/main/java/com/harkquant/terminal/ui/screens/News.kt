package com.harkquant.terminal.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.harkquant.terminal.data.Calendar
import com.harkquant.terminal.data.Load
import com.harkquant.terminal.data.NewsItem
import com.harkquant.terminal.ui.components.ChipRow
import com.harkquant.terminal.ui.components.Divider
import com.harkquant.terminal.ui.components.EmptyState
import com.harkquant.terminal.ui.components.ErrorState
import com.harkquant.terminal.ui.components.HqCard
import com.harkquant.terminal.ui.components.HqIcons
import com.harkquant.terminal.ui.components.Note
import com.harkquant.terminal.ui.components.ScreenHeader
import com.harkquant.terminal.ui.components.Segmented
import com.harkquant.terminal.ui.components.SkeletonCard
import com.harkquant.terminal.ui.components.StaleBanner
import com.harkquant.terminal.ui.components.rememberNow
import com.harkquant.terminal.ui.theme.Hq
import com.harkquant.terminal.util.Pkt

@Composable
fun NewsContent(
    tab: String, onTab: (String) -> Unit,
    items: Load<List<NewsItem>>, cal: Load<Calendar>,
    impact: String, asset: String, q: String, range: String, highOnly: Boolean, refreshing: Boolean, nowMs: Long,
    onBack: () -> Unit, onRefresh: () -> Unit, onImpact: (String) -> Unit, onAsset: (String) -> Unit, onQuery: (String) -> Unit,
    onRange: (String) -> Unit, onHighOnly: (Boolean) -> Unit,
) {
    val s = Hq.s
    val p = s.p
    var open by remember { mutableStateOf<NewsItem?>(null) }
    Column(Modifier.fillMaxSize()) {
        BackRow("Back", onBack)
        HqList(refreshing, onRefresh) {
            item("h") { ScreenHeader("News Desk · ${Pkt.hm(nowMs / 1000.0)} PKT", if (tab == "feed") "News feed" else "Economic calendar") }
            item("tabs") { Segmented(listOf("feed", "calendar"), tab, onTab, Modifier.screenPad(), labels = listOf("Headlines", "Calendar")) }
            if (tab == "feed") {
                item("filters") {
                    Column(Modifier.screenPad()) {
                        OutlinedTextField(
                            q, onQuery, Modifier.fillMaxWidth(), singleLine = true,
                            placeholder = { Text("Search headlines") },
                            leadingIcon = { Icon(HqIcons.Search, null, tint = p.tx2) },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            shape = s.chipShape.takeIf { s.form != com.harkquant.terminal.ui.theme.Form.EDITORIAL } ?: androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = p.acc, unfocusedBorderColor = p.line, focusedContainerColor = p.track, unfocusedContainerColor = p.track),
                        )
                        Spacer(Modifier.height(8.dp))
                        ChipRow(listOf("ALL", "MEDIUM+", "HIGH+", "CRITICAL"), impact, onImpact)
                        Spacer(Modifier.height(6.dp))
                        ChipRow(listOf("ALL", "XAU", "BTC", "USD", "RISK"), asset, onAsset)
                    }
                }
                when (items) {
                    is Load.Loading -> item("sk") { Column(Modifier.screenPad()) { SkeletonCard(6) } }
                    is Load.Err -> item("err") { ErrorState(items.message, onRefresh, Modifier.screenPad()) }
                    is Load.Ok -> {
                        if (items.stale) item("stale") { Column(Modifier.screenPad()) { StaleBanner(items.error) } }
                        item("list") {
                            HqCard(Modifier.screenPad(), title = "${items.data.size} headlines", subtitle = "FF · FinancialJuice · Reddit · X · wires · CB") {
                                if (items.data.isEmpty()) EmptyState("Nothing matches these filters")
                                items.data.take(150).forEachIndexed { i, n ->
                                    if (i > 0) Divider()
                                    NewsRow(n, nowMs) { open = n }
                                }
                            }
                        }
                    }
                }
            } else {
                item("calf") {
                    Column(Modifier.screenPad()) {
                        Segmented(listOf("today", "week", "upcoming"), range, onRange, labels = listOf("Today", "This week", "Upcoming"))
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("High impact only", style = MaterialTheme.typography.bodyMedium, color = p.tx, modifier = Modifier.weight(1f))
                            Switch(highOnly, onHighOnly, colors = SwitchDefaults.colors(checkedTrackColor = p.acc, checkedThumbColor = p.onAcc))
                        }
                    }
                }
                when (cal) {
                    is Load.Loading -> item("csk") { Column(Modifier.screenPad()) { SkeletonCard(6) } }
                    is Load.Err -> item("cerr") { ErrorState(cal.message, onRefresh, Modifier.screenPad()) }
                    is Load.Ok -> {
                        val groups = cal.data.events.sortedBy { it.ts }.groupBy { Pkt.dayKey(it.ts) }
                        if (groups.isEmpty()) item("cempty") { HqCard(Modifier.screenPad()) { EmptyState("No events in this range") } }
                        groups.forEach { (k, evs) ->
                            item("d$k") {
                                HqCard(Modifier.screenPad(), title = Pkt.dayHeader(evs.first().ts), subtitle = "${evs.size} events") {
                                    evs.forEachIndexed { i, e -> if (i > 0) Divider(); EventRow(e, nowMs) }
                                }
                            }
                        }
                        cal.data.note?.let { n -> item("cnote") { Note("Times in PKT. $n", modifier = Modifier.screenPad()) } }
                    }
                }
            }
        }
    }
    open?.let { NewsDetailSheet(it) { open = null } }
}
