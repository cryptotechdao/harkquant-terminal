package com.harkquant.terminal.ui

import com.harkquant.terminal.ui.components.hqNavBars
import com.harkquant.terminal.ui.components.hqStatusBars

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.harkquant.terminal.ui.components.HqIcons
import com.harkquant.terminal.ui.theme.Form
import com.harkquant.terminal.ui.theme.Hq


enum class Tab(val route: String, val label: String, val short: String, val icon: ImageVector) {
    DASH("dashboard", "Dashboard", "Home", HqIcons.Dashboard),
    TOOLS("tools", "Indicators & Tools", "Tools", HqIcons.Tools),
    TV("tv", "TradingView", "Chart", HqIcons.Candles),
    ANALYSIS("analysis", "Market Analysis", "Analysis", HqIcons.Gauge),
    SETTINGS("settings", "Settings", "Settings", HqIcons.Settings),
}

/** Bottom navigation, styled per form (flat terminal bar, floating glass pill, editorial text tabs, HUD bar, app tab bar). */
@Composable
fun HqBottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    val s = Hq.s
    val p = s.p
    val inner: @Composable (Modifier) -> Unit = { m ->
        Row(m.fillMaxWidth().height(if (s.form == Form.TERMINAL || s.form == Form.EDITORIAL) 56.dp else 62.dp), verticalAlignment = Alignment.CenterVertically) {
            Tab.entries.forEach { t -> NavItem(t, t == current, Modifier.weight(1f).fillMaxHeight()) { onSelect(t) } }
        }
    }
    when (s.form) {
        Form.GLASS -> Box(Modifier.fillMaxWidth().hqNavBars().padding(horizontal = 14.dp, vertical = 8.dp)) {
            val shape = RoundedCornerShape(50)
            inner(Modifier.clip(shape).background(s.solidSurface.copy(alpha = 0.82f)).border(1.dp, p.line, shape).padding(horizontal = 6.dp))
        }
        Form.HUD -> Box(Modifier.fillMaxWidth().background(p.bg.copy(alpha = 0.94f)).drawBehind {
            drawLine(p.line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
        }.hqNavBars()) { inner(Modifier.padding(horizontal = 6.dp)) }
        Form.EDITORIAL -> Box(Modifier.fillMaxWidth().background(p.bg).drawBehind {
            drawLine(p.tx, Offset(0f, 0f), Offset(size.width, 0f), 1.5.dp.toPx())
        }.hqNavBars()) { inner(Modifier) }
        Form.TERMINAL -> Box(Modifier.fillMaxWidth().background(p.panel).drawBehind {
            drawLine(p.line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
        }.hqNavBars()) { inner(Modifier) }
        Form.MOBILE -> Box(Modifier.fillMaxWidth().background(s.solidSurface.copy(alpha = 0.97f)).drawBehind {
            drawLine(p.line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
        }.hqNavBars()) { inner(Modifier.padding(horizontal = 4.dp)) }
    }
}

@Composable
private fun NavItem(t: Tab, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val s = Hq.s
    val p = s.p
    val src = remember { MutableInteractionSource() }
    val label = when (s.form) { Form.TERMINAL, Form.HUD -> t.short.uppercase(); else -> t.short }
    Column(
        modifier.clickable(interactionSource = src, indication = null, onClick = onClick).drawBehind {
            when (s.form) {
                Form.TERMINAL -> if (on) drawLine(p.acc, Offset(size.width * 0.2f, 1.dp.toPx()), Offset(size.width * 0.8f, 1.dp.toPx()), 2.dp.toPx())
                Form.EDITORIAL -> if (on) drawLine(p.acc2, Offset(size.width * 0.25f, size.height - 10.dp.toPx()), Offset(size.width * 0.75f, size.height - 10.dp.toPx()), 2.dp.toPx())
                else -> {}
            }
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val c = when {
            s.form == Form.EDITORIAL -> if (on) p.tx else p.tx2
            on -> p.acc
            else -> p.tx2
        }
        when (s.form) {
            Form.HUD -> Box(
                Modifier.clip(CutCornerShape(5.dp)).background(if (on) p.acc.copy(alpha = 0.16f) else Color.Transparent)
                    .border(1.dp, if (on) p.acc else Color.Transparent, CutCornerShape(5.dp)).padding(horizontal = 10.dp, vertical = 3.dp),
            ) { Icon(t.icon, t.label, tint = c, modifier = Modifier.size(20.dp)) }
            Form.MOBILE, Form.GLASS -> Box(
                Modifier.clip(RoundedCornerShape(50)).background(if (on) p.acc.copy(alpha = 0.16f) else Color.Transparent).padding(horizontal = 14.dp, vertical = 3.dp),
            ) { Icon(t.icon, t.label, tint = c, modifier = Modifier.size(21.dp)) }
            Form.EDITORIAL -> {}
            Form.TERMINAL -> Icon(t.icon, t.label, tint = c, modifier = Modifier.size(19.dp))
        }
        if (s.form != Form.EDITORIAL) Spacer(Modifier.height(3.dp))
        Text(
            label,
            style = when (s.form) {
                Form.EDITORIAL -> MaterialTheme.typography.titleSmall.copy(fontFamily = s.display, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp)
                Form.HUD -> MaterialTheme.typography.labelSmall.copy(fontFamily = s.display, letterSpacing = 0.1.em, fontSize = 8.5.sp)
                Form.TERMINAL -> MaterialTheme.typography.labelSmall.copy(fontFamily = s.mono, letterSpacing = 0.06.em, fontSize = 9.sp)
                else -> MaterialTheme.typography.labelSmall.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, fontSize = 10.5.sp)
            },
            color = c,
            maxLines = 1,
        )
    }
}

@Composable
fun HqNavRail(current: Tab, onSelect: (Tab) -> Unit) {
    val s = Hq.s
    val p = s.p
    Column(
        Modifier.fillMaxHeight().width(88.dp).background(if (s.form == Form.GLASS) s.solidSurface.copy(alpha = 0.6f) else p.panel.takeIf { it.alpha > 0.05f } ?: p.bg)
            .drawBehind { drawLine(p.line, Offset(size.width, 0f), Offset(size.width, size.height), 1.dp.toPx()) }
            .hqStatusBars().hqNavBars().padding(top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("HQ", style = MaterialTheme.typography.titleLarge.copy(fontFamily = s.display, fontWeight = FontWeight.Black), color = p.acc)
        Spacer(Modifier.height(12.dp))
        Tab.entries.forEach { t -> NavItem(t, t == current, Modifier.fillMaxWidth().height(64.dp)) { onSelect(t) } }
    }
}
