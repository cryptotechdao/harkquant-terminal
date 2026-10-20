package com.harkquant.terminal.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.harkquant.terminal.data.Conn
import com.harkquant.terminal.ui.theme.Form
import com.harkquant.terminal.ui.theme.Hq
import com.harkquant.terminal.ui.theme.monoStyle
import com.harkquant.terminal.util.Fmt
import kotlin.math.abs

// ================================================================== background
@Composable
fun HqBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val s = Hq.s
    val p = s.p
    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                val grad = p.bgGradient
                if (grad != null) {
                    drawRect(Brush.linearGradient(grad, start = Offset.Zero, end = Offset(size.width, size.height)))
                } else drawRect(p.bg)
                when (s.form) {
                    Form.GLASS -> {
                        val blobs = p.blobs
                        val spots = listOf(Offset(size.width * 0.1f, size.height * 0.12f), Offset(size.width * 0.95f, size.height * 0.45f), Offset(size.width * 0.25f, size.height * 0.9f))
                        blobs.forEachIndexed { i, c ->
                            val r = size.width * (0.75f - i * 0.1f)
                            drawCircle(Brush.radialGradient(listOf(c.copy(alpha = p.blobAlpha * 0.75f), Color.Transparent), center = spots[i % 3], radius = r), r, spots[i % 3])
                        }
                    }
                    Form.HUD -> {
                        p.bg2?.let { drawRect(Brush.radialGradient(listOf(it.copy(alpha = 0.9f), Color.Transparent), center = Offset(size.width / 2, 0f), radius = size.width * 1.1f)) }
                        val step = 28.dp.toPx()
                        var x = 0f
                        while (x < size.width) { drawLine(p.grid, Offset(x, 0f), Offset(x, size.height), 1f); x += step }
                        var y = 0f
                        while (y < size.height) { drawLine(p.grid, Offset(0f, y), Offset(size.width, y), 1f); y += step }
                    }
                    Form.MOBILE -> if (!p.isLight) {
                        drawRect(Brush.radialGradient(listOf(p.acc.copy(alpha = 0.07f), Color.Transparent), center = Offset(size.width, 0f), radius = size.width * 0.9f))
                    }
                    else -> {}
                }
            },
        content = content,
    )
}

// ================================================================== cards
@Composable
fun HqCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    highlighted: Boolean = false,
    contentPadding: Dp? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val s = Hq.s
    val p = s.p
    val pad = contentPadding ?: s.cardPadding
    val shape = s.cardShape
    val borderColor = if (highlighted) p.acc else p.line
    var m = modifier.fillMaxWidth()
    m = when (s.form) {
        Form.TERMINAL -> m.clip(shape).background(p.panel).border(1.dp, borderColor, shape)
        Form.GLASS -> m.clip(shape)
            .background(Brush.verticalGradient(listOf(p.panel.copy(alpha = (p.panel.alpha * 1.6f).coerceAtMost(1f)), p.panel)))
            .border(BorderStroke(1.dp, Brush.verticalGradient(listOf(borderColor, borderColor.copy(alpha = borderColor.alpha * 0.35f)))), shape)
        Form.EDITORIAL -> m.drawBehind {
            drawLine(if (highlighted) p.acc2 else p.tx, Offset(0f, 0f), Offset(size.width, 0f), if (highlighted) 3.dp.toPx() else 1.5.dp.toPx())
        }
        Form.HUD -> m.clip(shape).background(p.panel).border(1.dp, borderColor, shape).drawWithContent {
            drawContent()
            val l = 12.dp.toPx(); val w = 2.dp.toPx(); val c = p.acc
            drawLine(c, Offset(0f, w / 2), Offset(l, w / 2), w); drawLine(c, Offset(w / 2, 0f), Offset(w / 2, l), w)
            drawLine(c, Offset(size.width, size.height - w / 2), Offset(size.width - l, size.height - w / 2), w)
            drawLine(c, Offset(size.width - w / 2, size.height), Offset(size.width - w / 2, size.height - l), w)
        }
        Form.MOBILE -> m.clip(shape).background(p.panel).let { if (highlighted) it.border(1.5.dp, p.acc, shape) else it }
    }
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(m.padding(horizontal = if (s.form == Form.EDITORIAL) 0.dp else pad, vertical = pad)) {
        if (title != null) {
            CardHeader(title, subtitle, action, onAction)
            Spacer(Modifier.height(if (s.form == Form.TERMINAL) 8.dp else 10.dp))
        }
        content()
    }
}

@Composable
fun CardHeader(title: String, subtitle: String? = null, action: String? = null, onAction: (() -> Unit)? = null) {
    val s = Hq.s
    val p = s.p
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        when (s.form) {
            Form.TERMINAL -> {
                Box(Modifier.size(6.dp).background(p.acc))
                Spacer(Modifier.width(7.dp))
                Text(title.uppercase(), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.1.em), color = p.tx)
            }
            Form.HUD -> Text(title.uppercase().replace(" · ", " // ").replace(' ', '.').replace(".//.", " // "), style = MaterialTheme.typography.titleLarge.copy(fontSize = 12.sp, letterSpacing = 0.16.em), color = p.acc)
            Form.EDITORIAL -> Text(title, style = MaterialTheme.typography.headlineSmall, color = p.tx)
            else -> Text(title, style = MaterialTheme.typography.titleMedium, color = p.tx)
        }
        if (subtitle != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                if (s.titleUpper) subtitle.uppercase() else subtitle, style = MaterialTheme.typography.labelSmall, color = p.tx2,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
        } else Spacer(Modifier.weight(1f))
        if (action != null) {
            Text(
                if (s.titleUpper) action.uppercase() else action,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (s.form == Form.EDITORIAL) p.acc2 else p.acc,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(enabled = onAction != null) { onAction?.invoke() }.padding(4.dp),
            )
        }
    }
}

// ================================================================== screen header
@Composable
fun ScreenHeader(kicker: String, title: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    val s = Hq.s
    val p = s.p
    Column(modifier.fillMaxWidth().padding(horizontal = s.screenPadding).padding(top = 8.dp, bottom = 6.dp)) {
        when (s.form) {
            Form.EDITORIAL -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(kicker.uppercase(), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.18.em), color = p.tx2, modifier = Modifier.weight(1f))
                    trailing?.invoke()
                }
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth().height(2.dp).background(p.tx))
                Text(title, style = MaterialTheme.typography.displaySmall, color = p.tx, modifier = Modifier.padding(vertical = 6.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
                Spacer(Modifier.height(2.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
            }
            Form.HUD -> Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("// " + kicker.uppercase(), style = monoStyle(10), color = p.tx2)
                    Text(title.uppercase().replace(' ', '.'), style = MaterialTheme.typography.headlineMedium.copy(letterSpacing = 0.12.em), color = p.acc)
                }
                trailing?.invoke()
            }
            Form.TERMINAL -> Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(kicker.uppercase(), style = monoStyle(10, FontWeight.Bold).copy(letterSpacing = 0.18.em), color = p.acc)
                    Text(title, style = MaterialTheme.typography.headlineSmall, color = p.tx)
                }
                trailing?.invoke()
            }
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(kicker, style = MaterialTheme.typography.bodySmall, color = p.tx2)
                    Text(title, style = MaterialTheme.typography.headlineMedium, color = p.tx)
                }
                trailing?.invoke()
            }
        }
    }
}

// ================================================================== small pieces
@Composable
fun Tag(text: String, color: Color, filled: Boolean = false, modifier: Modifier = Modifier) {
    val s = Hq.s
    val shape = when (s.form) { Form.HUD -> CutCornerShape(3.dp); Form.EDITORIAL -> RoundedCornerShape(0.dp); Form.TERMINAL -> RoundedCornerShape(2.dp); else -> RoundedCornerShape(5.dp) }
    Text(
        text.uppercase(),
        style = monoStyle(9, FontWeight.Bold).copy(letterSpacing = 0.04.em),
        color = if (filled) s.p.onAcc else color,
        maxLines = 1,
        modifier = modifier.clip(shape).then(if (filled) Modifier.background(color) else Modifier.border(1.dp, color.copy(alpha = 0.7f), shape))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

@Composable
fun ImpactTag(impact: String) {
    val s = Hq.s
    val short = when (impact.uppercase()) { "CRITICAL" -> "CRIT"; "MEDIUM" -> "MED"; else -> impact.uppercase() }
    Tag(short, s.impactColor(impact), filled = impact.equals("CRITICAL", true))
}

@Composable
fun StatusDot(status: String?, size: Dp = 7.dp) {
    val p = Hq.c
    val c = when (status) { "ok" -> p.up; "fail", "error" -> p.dn; "needs_key" -> p.info; "off" -> p.neu; null -> p.neu; else -> p.warn }
    Box(Modifier.size(size).clip(CircleShape).background(c))
}

/** Centre-zero bar for a -100..+100 score. */
@Composable
fun CompBar(score: Double?, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    val s = Hq.s
    val p = s.p
    val v = if (Fmt.isNum(score)) score!!.coerceIn(-100.0, 100.0) else 0.0
    val color = s.signColor(Fmt.sign(v))
    Canvas(modifier.fillMaxWidth().height(height)) {
        val r = if (s.form == Form.TERMINAL || s.form == Form.EDITORIAL || s.form == Form.HUD) 0f else size.height / 2
        drawRoundRect(p.track, cornerRadius = CornerRadius(r))
        val mid = size.width / 2
        val w = (abs(v) / 100.0 * mid).toFloat()
        val left = if (v >= 0) mid else mid - w
        drawRoundRect(color, Offset(left, 0f), Size(w, size.height), CornerRadius(r))
        drawLine(p.tx2.copy(alpha = 0.5f), Offset(mid, -2f), Offset(mid, size.height + 2f), 1.dp.toPx())
    }
}

/** Two-colour split bar (buy % vs sell %). */
@Composable
fun SplitBar(leftPct: Double, modifier: Modifier = Modifier, height: Dp = 10.dp) {
    val s = Hq.s
    val p = s.p
    val pct = leftPct.coerceIn(0.0, 100.0).toFloat() / 100f
    Canvas(modifier.fillMaxWidth().height(height)) {
        val gap = 3.dp.toPx()
        val r = if (s.form == Form.MOBILE || s.form == Form.GLASS) size.height / 2 else 0f
        val lw = (size.width - gap) * pct
        drawRoundRect(p.up, Offset.Zero, Size(lw, size.height), CornerRadius(r))
        drawRoundRect(p.dn, Offset(lw + gap, 0f), Size(size.width - lw - gap, size.height), CornerRadius(r))
    }
}

@Composable
fun Tile(label: String, value: String, modifier: Modifier = Modifier, color: Color? = null, sub: String? = null) {
    val s = Hq.s
    val p = s.p
    Column(
        modifier
            .clip(if (s.form == Form.MOBILE || s.form == Form.GLASS) RoundedCornerShape(12.dp) else s.chipShape)
            .background(p.track)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(if (s.titleUpper) label.uppercase() else label, style = MaterialTheme.typography.labelSmall, color = p.tx2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(value, style = monoStyle(15, FontWeight.SemiBold), color = color ?: p.tx, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = p.tx2, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A grid of tiles, 2 or 3 per row. */
@Composable
fun TileGrid(tiles: List<@Composable (Modifier) -> Unit>, perRow: Int = 2) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { it(Modifier.weight(1f)) }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun KeyValue(key: String, value: String, valueColor: Color? = null) {
    val p = Hq.c
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(key, style = MaterialTheme.typography.bodySmall, color = p.tx2, modifier = Modifier.weight(1f))
        Text(value, style = monoStyle(12), color = valueColor ?: p.tx)
    }
}

@Composable
fun Note(text: String, warn: Boolean = false, modifier: Modifier = Modifier) {
    val p = Hq.c
    Text(text, style = MaterialTheme.typography.bodySmall, color = if (warn) p.warn else p.tx2, modifier = modifier.padding(top = 6.dp))
}

@Composable
fun Divider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Hq.c.line))
}

// ================================================================== segmented tabs & chips
@Composable
fun Segmented(options: List<String>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier, labels: List<String>? = null, fill: Boolean = true) {
    val s = Hq.s
    val p = s.p
    val outer = when (s.form) {
        Form.MOBILE, Form.GLASS -> RoundedCornerShape(50)
        Form.HUD -> CutCornerShape(6.dp)
        else -> RoundedCornerShape(3.dp)
    }
    val inner = when (s.form) {
        Form.MOBILE, Form.GLASS -> RoundedCornerShape(50)
        Form.HUD -> CutCornerShape(5.dp)
        else -> RoundedCornerShape(2.dp)
    }
    val rowMod = if (fill) modifier.fillMaxWidth() else modifier.horizontalScroll(rememberScrollState())
    Row(
        rowMod.clip(outer)
            .then(if (s.form == Form.EDITORIAL) Modifier else Modifier.background(p.track))
            .then(if (s.form == Form.TERMINAL || s.form == Form.HUD) Modifier.border(1.dp, p.line, outer) else Modifier)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { i, o ->
            val on = o == selected
            val bg by animateColorAsState(if (on && s.form != Form.EDITORIAL) p.acc else Color.Transparent, label = "seg")
            val label = labels?.getOrNull(i) ?: o
            Box(
                (if (fill) Modifier.weight(1f) else Modifier.widthIn(min = 44.dp))
                    .clip(inner)
                    .background(bg)
                    .clickable { onSelect(o) }
                    .drawBehind {
                        if (on && s.form == Form.EDITORIAL) drawLine(p.acc2, Offset(0f, size.height - 1.dp.toPx()), Offset(size.width, size.height - 1.dp.toPx()), 2.dp.toPx())
                    }
                    .padding(horizontal = 8.dp, vertical = if (s.form == Form.TERMINAL) 5.dp else 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (s.titleUpper) label.uppercase() else label,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                    color = when { on && s.form == Form.EDITORIAL -> p.tx; on -> p.onAcc; else -> p.tx2 },
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun ChipRow(items: List<String>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues(0.dp)) {
    val s = Hq.s
    val p = s.p
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { it ->
            val on = it == selected
            val shape = s.chipShape
            Text(
                it,
                style = monoStyle(11, FontWeight.Bold),
                color = if (on) p.onAcc else p.tx2,
                modifier = Modifier.clip(shape)
                    .background(if (on) p.acc else Color.Transparent)
                    .border(1.dp, if (on) p.acc else p.line, shape)
                    .clickable { onSelect(it) }
                    .padding(horizontal = 11.dp, vertical = 6.dp),
            )
        }
    }
}

// ================================================================== states
@Composable
fun shimmerBrush(): Brush {
    val p = Hq.c
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "x")
    val base = p.track
    val hi = p.tx.copy(alpha = if (p.isLight) 0.06f else 0.08f).compositeOverSafe(base)
    return Brush.linearGradient(listOf(base, hi, base), start = Offset(x * 1400f - 400f, 0f), end = Offset(x * 1400f, 200f))
}

private fun Color.compositeOverSafe(bg: Color): Color = androidx.compose.ui.graphics.Color(
    red = red * alpha + bg.red * (1 - alpha), green = green * alpha + bg.green * (1 - alpha), blue = blue * alpha + bg.blue * (1 - alpha), alpha = maxOf(alpha, bg.alpha),
)

@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, height: Dp = 14.dp) {
    val s = Hq.s
    Box(modifier.height(height).clip(if (s.form == Form.MOBILE || s.form == Form.GLASS) RoundedCornerShape(8.dp) else RoundedCornerShape(2.dp)).background(shimmerBrush()))
}

@Composable
fun SkeletonCard(lines: Int = 3, chart: Boolean = false) {
    HqCard {
        SkeletonBlock(Modifier.width(120.dp), 12.dp)
        Spacer(Modifier.height(12.dp))
        if (chart) { SkeletonBlock(Modifier.fillMaxWidth(), 160.dp); Spacer(Modifier.height(10.dp)) }
        repeat(lines) { i ->
            SkeletonBlock(Modifier.fillMaxWidth(if (i % 2 == 0) 0.92f else 0.7f), 12.dp)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun ErrorState(message: String, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    val s = Hq.s
    val p = s.p
    HqCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(p.dnBg), contentAlignment = Alignment.Center) {
                Text("!", style = MaterialTheme.typography.titleLarge, color = p.dn)
            }
            Spacer(Modifier.height(10.dp))
            Text("Source unavailable", style = MaterialTheme.typography.titleMedium, color = p.tx)
            Text(message, style = MaterialTheme.typography.bodySmall, color = p.tx2, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, start = 8.dp, end = 8.dp))
            if (onRetry != null) {
                Spacer(Modifier.height(12.dp))
                HqButton("Retry", onRetry)
            }
        }
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Hq.c.tx2, textAlign = TextAlign.Center, modifier = modifier.fillMaxWidth().padding(vertical = 18.dp))
}

@Composable
fun StaleBanner(error: String?) {
    if (error == null) return
    val p = Hq.c
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(p.warn.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(p.warn))
        Spacer(Modifier.width(8.dp))
        Text("Showing last data · $error", style = MaterialTheme.typography.bodySmall, color = p.warn, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun HqButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = true, enabled: Boolean = true) {
    val s = Hq.s
    val p = s.p
    val shape = when (s.form) { Form.MOBILE, Form.GLASS -> RoundedCornerShape(50); Form.HUD -> CutCornerShape(8.dp); Form.EDITORIAL -> RoundedCornerShape(0.dp); else -> RoundedCornerShape(4.dp) }
    val label = if (s.titleUpper) text.uppercase() else text
    if (primary) Button(onClick, modifier, enabled = enabled, shape = shape, colors = ButtonDefaults.buttonColors(containerColor = p.acc, contentColor = p.onAcc)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    } else OutlinedButton(onClick, modifier, enabled = enabled, shape = shape, border = BorderStroke(1.dp, p.line)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = p.tx)
    }
}

@Composable
fun ConnBadge(conn: Conn) {
    val s = Hq.s
    val p = s.p
    val (txt, c) = when (conn) {
        Conn.LIVE -> "LIVE" to p.up
        Conn.POLLING -> "POLLING" to p.warn
        Conn.OFFLINE -> "OFFLINE" to p.dn
        Conn.CONNECTING -> "CONNECTING" to p.tx2
    }
    val t = rememberInfiniteTransition(label = "pulse")
    val a by t.animateFloat(1f, 0.3f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Row(
        Modifier.clip(s.chipShape).background(c.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(c.copy(alpha = if (conn == Conn.LIVE) a else 1f)))
        Spacer(Modifier.width(5.dp))
        Text(txt, style = monoStyle(10, FontWeight.Bold), color = c)
    }
}

@Composable
fun SignedText(value: Double?, decimals: Int = 0, suffix: String = "", size: Int = 13, weight: FontWeight = FontWeight.SemiBold) {
    val s = Hq.s
    Text(Fmt.signed(value, decimals) + suffix, style = monoStyle(size, weight), color = s.signColor(Fmt.sign(value)))
}

@Composable
fun LabeledRow(label: String, content: @Composable () -> Unit) {
    val p = Hq.c
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = p.tx2, modifier = Modifier.width(92.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
fun StrokeLine(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.width(14.dp).height(3.dp)) { drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), size.height, StrokeCap.Round) }
}
