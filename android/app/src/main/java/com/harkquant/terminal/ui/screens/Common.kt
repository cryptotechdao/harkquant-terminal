@file:OptIn(ExperimentalMaterial3Api::class)

package com.harkquant.terminal.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harkquant.terminal.data.CalEvent
import com.harkquant.terminal.data.NewsItem
import com.harkquant.terminal.data.PriceSnap
import com.harkquant.terminal.ui.components.Divider
import com.harkquant.terminal.ui.components.HqButton
import com.harkquant.terminal.ui.components.HqCard
import com.harkquant.terminal.ui.components.hqNavBars
import com.harkquant.terminal.ui.components.ImpactTag
import com.harkquant.terminal.ui.components.SkeletonBlock
import com.harkquant.terminal.ui.components.Sparkline
import com.harkquant.terminal.ui.components.Tag
import com.harkquant.terminal.ui.theme.Form
import com.harkquant.terminal.ui.theme.Hq
import com.harkquant.terminal.ui.theme.monoStyle
import com.harkquant.terminal.util.Fmt
import com.harkquant.terminal.util.Pkt

/** Pull-to-refresh list with the form's spacing. */
@Composable
fun HqList(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    val s = Hq.s
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(s.gap),
            content = content,
        )
    }
}

/** Horizontal padding for list items. */
@Composable
fun Modifier.screenPad(): Modifier = this.padding(horizontal = Hq.s.screenPadding)

@Composable
fun PriceCard(symbol: String, name: String, snap: PriceSnap?, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val s = Hq.s
    val p = s.p
    HqCard(modifier, highlighted = selected, onClick = onClick, contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(symbol, style = monoStyle(11, FontWeight.Bold), color = if (selected) p.acc else p.tx2)
            Spacer(Modifier.weight(1f))
            if (snap != null) Tag(if (snap.change >= 0) "▲" else "▼", s.signColor(Fmt.sign(snap.change)))
        }
        Text(name, style = MaterialTheme.typography.labelSmall, color = p.tx2, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        if (snap == null) {
            SkeletonBlock(Modifier.fillMaxWidth(0.8f), 22.dp)
            Spacer(Modifier.height(6.dp))
            SkeletonBlock(Modifier.fillMaxWidth(0.5f), 12.dp)
            Spacer(Modifier.height(6.dp))
            SkeletonBlock(Modifier.fillMaxWidth(), 30.dp)
        } else {
            Text(Fmt.price(snap.last), style = monoStyle(if (s.form == Form.MOBILE) 21 else 19, FontWeight.Bold), color = p.tx, maxLines = 1)
            Text("${Fmt.signed(snap.change, if (snap.last > 1000) 0 else 2)}  (${Fmt.signed(snap.changePct, 2)}%) 24h", style = monoStyle(10.5f.toInt()), color = s.signColor(Fmt.sign(snap.change)), maxLines = 1)
            Spacer(Modifier.height(6.dp))
            Sparkline(snap.spark.map { it.close }, s.signColor(Fmt.sign(snap.change)).let { if (it == p.tx2) p.acc else it }, height = 30.dp)
        }
    }
}

@Composable
fun NewsRow(n: NewsItem, nowMs: Long, onClick: () -> Unit) {
    val s = Hq.s
    val p = s.p
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Pkt.hm(n.ts), style = monoStyle(11, FontWeight.SemiBold), color = p.tx2)
            Spacer(Modifier.width(6.dp))
            ImpactTag(n.impact)
            Spacer(Modifier.width(6.dp))
            Text(n.sourceName.ifBlank { n.label }, style = MaterialTheme.typography.labelSmall, color = p.tx2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            n.assets.take(3).forEach { a ->
                val dir = n.direction[a]
                val c = when (dir) { "bullish" -> p.up; "bearish" -> p.dn; else -> p.tx2 }
                Spacer(Modifier.width(4.dp))
                Text(a + when (dir) { "bullish" -> "↑"; "bearish" -> "↓"; else -> "" }, style = monoStyle(10, FontWeight.Bold), color = c)
            }
            Spacer(Modifier.width(6.dp))
            Text(Fmt.ago(n.ts, nowMs), style = monoStyle(10), color = p.tx2)
        }
        Spacer(Modifier.height(3.dp))
        Text(n.title, style = if (s.form == Form.EDITORIAL) MaterialTheme.typography.titleMedium.copy(fontFamily = s.display) else MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = p.tx, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun NewsDetailSheet(n: NewsItem, onDismiss: () -> Unit) {
    val s = Hq.s
    val p = s.p
    val uri = LocalUriHandler.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = s.solidSurface, contentColor = p.tx) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).hqNavBars()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ImpactTag(n.impact)
                Spacer(Modifier.width(8.dp))
                Text(n.sourceName.ifBlank { n.label }, style = MaterialTheme.typography.labelMedium, color = p.tx2)
            }
            Spacer(Modifier.height(10.dp))
            Text(n.title, style = MaterialTheme.typography.headlineSmall, color = p.tx)
            Spacer(Modifier.height(8.dp))
            Text(Pkt.full(n.ts), style = monoStyle(11), color = p.tx2)
            if (n.summary.isNotBlank()) { Spacer(Modifier.height(10.dp)); Text(n.summary, style = MaterialTheme.typography.bodyMedium, color = p.tx) }
            if (!n.why.isNullOrBlank()) { Spacer(Modifier.height(10.dp)); Text("Why it matters: ${n.why}", style = MaterialTheme.typography.bodySmall, color = p.tx2) }
            if (n.assets.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    n.assets.forEach { a ->
                        val dir = n.direction[a]
                        Tag(a + (dir?.let { " · $it" } ?: ""), when (dir) { "bullish" -> p.up; "bearish" -> p.dn; else -> p.tx2 })
                    }
                }
            }
            Text("Impact score ${"%.1f".format(n.score)} · ${n.category}", style = MaterialTheme.typography.labelSmall, color = p.tx2, modifier = Modifier.padding(top = 10.dp))
            if (n.url.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                HqButton("Open source", { runCatching { uri.openUri(n.url) } }, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun EventRow(e: CalEvent, nowMs: Long) {
    val s = Hq.s
    val p = s.p
    val left = e.ts - nowMs / 1000.0
    val past = left < 0
    val soon = !past && left < 3600
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 12.dp, height = 9.dp).clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)).background(s.impactColor(e.impact).copy(alpha = if (past) 0.4f else 1f)))
        Spacer(Modifier.width(8.dp))
        Text(Pkt.dayHm(e.ts), style = monoStyle(11), color = p.tx2, modifier = Modifier.width(98.dp))
        Text(e.country, style = monoStyle(11, FontWeight.Bold), color = if (past) p.tx2 else p.tx, modifier = Modifier.width(38.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), color = if (past) p.tx2 else p.tx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val afp = listOfNotNull(e.actual?.let { "A $it" }, e.forecast?.let { "F $it" }, e.previous?.let { "P $it" }).joinToString("  ")
            if (afp.isNotEmpty()) Text(afp, style = monoStyle(10), color = p.tx2, maxLines = 1)
        }
        Text(
            if (past) (if (e.actual != null) "done" else "passed") else Fmt.countdown(left),
            style = monoStyle(11, FontWeight.SemiBold),
            color = when { past -> p.tx2; soon -> p.dn; else -> p.warn },
        )
    }
}

@Composable
fun BulletList(lines: List<String>) {
    val p = Hq.c
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        lines.forEach { l ->
            Row {
                Text("•", style = MaterialTheme.typography.bodySmall, color = p.acc, modifier = Modifier.width(14.dp))
                Text(l, style = MaterialTheme.typography.bodySmall, color = p.tx)
            }
        }
    }
}

@Composable
fun RowDivider() { Divider(Modifier.padding(vertical = 0.dp)) }

@Composable
fun BackRow(title: String, onBack: () -> Unit) {
    val p = Hq.c
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.IconButton(onClick = onBack) {
            androidx.compose.material3.Icon(com.harkquant.terminal.ui.components.HqIcons.Back, "Back", tint = p.tx)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = p.tx)
    }
}
