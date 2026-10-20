package com.harkquant.terminal.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.harkquant.terminal.data.Analysis
import com.harkquant.terminal.ui.theme.Form
import com.harkquant.terminal.ui.theme.Hq
import com.harkquant.terminal.ui.theme.monoStyle
import com.harkquant.terminal.util.Fmt
import kotlin.math.cos
import kotlin.math.sin

private fun frac(score: Double) = ((score.coerceIn(-100.0, 100.0) + 100.0) / 200.0).toFloat()

/** The combined-bias widget, drawn in the style of the active form. */
@Composable
fun BiasWidget(a: Analysis, modifier: Modifier = Modifier, compact: Boolean = false) {
    val s = Hq.s
    when (s.form) {
        Form.TERMINAL, Form.EDITORIAL -> LinearBias(a, modifier, compact)
        Form.HUD -> RingBias(a, modifier, if (compact) 150.dp else 220.dp)
        Form.MOBILE, Form.GLASS -> SemiGauge(a, modifier, if (compact) 190.dp else 260.dp)
    }
}

@Composable
private fun BiasWord(a: Analysis, size: Int) {
    val s = Hq.s
    val c = s.labelColor(a.label)
    val style = when (s.form) {
        Form.HUD -> MaterialTheme.typography.displaySmall.copy(fontSize = size.sp, letterSpacing = 0.08.em)
        Form.EDITORIAL -> MaterialTheme.typography.displaySmall.copy(fontSize = size.sp)
        else -> MaterialTheme.typography.displaySmall.copy(fontSize = size.sp, fontWeight = FontWeight.ExtraBold, fontFamily = if (s.form == Form.MOBILE) s.mono else s.display)
    }
    Text(if (s.form == Form.EDITORIAL) a.label.lowercase().replaceFirstChar { it.uppercase() } else a.label, style = style, color = c)
}

@Composable
fun LinearBias(a: Analysis, modifier: Modifier = Modifier, compact: Boolean = false) {
    val s = Hq.s
    val p = s.p
    val f by animateFloatAsState(frac(a.score), tween(700), label = "needle")
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            BiasWord(a, if (compact) 22 else 34)
            Spacer(Modifier.width(10.dp))
            Text("${Fmt.signed(a.score, 1)} / 100", style = monoStyle(if (compact) 12 else 14, FontWeight.SemiBold), color = s.signColor(Fmt.sign(a.score)), modifier = Modifier.padding(bottom = 4.dp))
        }
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.fillMaxWidth().height(if (compact) 14.dp else 20.dp)) {
            val h = size.height * 0.55f
            val top = (size.height - h) / 2
            val r = if (s.form == Form.EDITORIAL) 0f else 2.dp.toPx()
            drawRoundRect(Brush.horizontalGradient(listOf(p.dn, p.dn.copy(alpha = 0.35f), p.neu.copy(alpha = 0.4f), p.up.copy(alpha = 0.35f), p.up)), Offset(0f, top), Size(size.width, h), CornerRadius(r))
            val bw = (a.neutralBand / 100.0).toFloat() * size.width / 2
            drawRect(p.tx.copy(alpha = 0.10f), Offset(size.width / 2 - bw, top), Size(bw * 2, h))
            if (s.form == Form.EDITORIAL) for (k in 0..10) {
                val x = size.width * k / 10f
                drawLine(p.tx2, Offset(x, top + h), Offset(x, top + h + 4.dp.toPx()), 1f)
            }
            val x = f * size.width
            drawRoundRect(p.tx, Offset(x - 1.5.dp.toPx(), 0f), Size(3.dp.toPx(), size.height), CornerRadius(1.dp.toPx()))
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text("-100 BEARISH", style = monoStyle(9), color = p.tx2)
            Spacer(Modifier.weight(1f))
            Text("neutral ±${a.neutralBand.toInt()}", style = monoStyle(9), color = p.tx2)
            Spacer(Modifier.weight(1f))
            Text("BULLISH +100", style = monoStyle(9), color = p.tx2)
        }
    }
}

@Composable
fun RingBias(a: Analysis, modifier: Modifier = Modifier, size: Dp = 220.dp) {
    val s = Hq.s
    val p = s.p
    val f by animateFloatAsState(frac(a.score), tween(800), label = "ring")
    val c = s.labelColor(a.label)
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = size.toPx() * 0.07f
            val inset = stroke * 1.6f
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val tl = Offset(inset, inset)
            drawArc(p.track, 135f, 270f, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
            // glow pass + main arc from the centre (12 o'clock = 0)
            val mid = 270f
            val sweep = (f - 0.5f) * 270f
            drawArc(c.copy(alpha = 0.25f), mid, sweep, false, tl, arcSize, style = Stroke(stroke * 2.2f, cap = StrokeCap.Round))
            drawArc(c, mid, sweep, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            // ticks
            val r0 = arcSize.width / 2 + stroke
            val cx = this.size.width / 2; val cy = this.size.height / 2
            for (k in 0..20) {
                val ang = Math.toRadians((135 + k * 13.5))
                val r1 = r0 + if (k % 5 == 0) stroke * 0.9f else stroke * 0.45f
                drawLine(p.line, Offset(cx + r0 * cos(ang).toFloat(), cy + r0 * sin(ang).toFloat()), Offset(cx + r1 * cos(ang).toFloat(), cy + r1 * sin(ang).toFloat()), 1.5f)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(a.symbol, style = monoStyle(10), color = p.tx2)
            BiasWord(a, if (size < 180.dp) 18 else 26)
            Text(Fmt.signed(a.score, 1), style = monoStyle(13, FontWeight.Bold), color = c)
            Text("conf ${(a.confidence * 100).toInt()}% · ${a.agreement}", style = monoStyle(9), color = p.tx2)
        }
    }
}

@Composable
fun SemiGauge(a: Analysis, modifier: Modifier = Modifier, width: Dp = 260.dp) {
    val s = Hq.s
    val p = s.p
    val f by animateFloatAsState(frac(a.score), tween(800), label = "gauge")
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.widthIn(max = width).fillMaxWidth().aspectRatio(2f)) {
            val stroke = size.width * 0.075f
            val r = size.width / 2 - stroke
            val tl = Offset(size.width / 2 - r, size.height - r)
            val arc = Size(r * 2, r * 2)
            drawArc(Brush.sweepGradient(listOf(p.up, p.up, p.dn, p.dn, p.warn, p.up), center = Offset(size.width / 2, size.height)), 180f, 180f, false, tl, arc, style = Stroke(stroke, cap = StrokeCap.Round))
            // neutral band shading
            val bandDeg = (a.neutralBand / 100.0 * 90).toFloat()
            drawArc(p.tx.copy(alpha = 0.15f), 270f - bandDeg, bandDeg * 2, false, tl, arc, style = Stroke(stroke * 0.35f))
            val ang = Math.toRadians(180.0 + f * 180.0)
            val cx = size.width / 2; val cy = size.height
            val len = r * 0.82f
            drawLine(p.tx, Offset(cx, cy), Offset(cx + len * cos(ang).toFloat(), cy + len * sin(ang).toFloat()), stroke * 0.32f, StrokeCap.Round)
            drawCircle(p.tx, stroke * 0.42f, Offset(cx, cy))
            drawCircle(s.solidSurface, stroke * 0.18f, Offset(cx, cy))
        }
        Spacer(Modifier.height(6.dp))
        BiasWord(a, if (width < 220.dp) 24 else 32)
        Text("${Fmt.signed(a.score, 1)} / 100", style = monoStyle(13, FontWeight.SemiBold), color = s.signColor(Fmt.sign(a.score)))
        Text("${a.symbol} · ${a.agreement} agree · confidence ${(a.confidence * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = p.tx2, modifier = Modifier.padding(top = 2.dp))
    }
}

/** Compact bias pill for the dashboard (symbol + label + score). */
@Composable
fun BiasMini(a: Analysis?, symbol: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val s = Hq.s
    val p = s.p
    val label = a?.label ?: "N/A"
    val c = if (a == null || a.error != null) p.tx2 else s.labelColor(label)
    val bg = when { c == p.up -> p.upBg; c == p.dn -> p.dnBg; else -> p.track }
    val shape = if (s.form == Form.MOBILE || s.form == Form.GLASS) RoundedCornerShape(14.dp) else s.chipShape
    Column(
        modifier.clip(shape).background(bg).then(if (s.form == Form.HUD || s.form == Form.TERMINAL) Modifier.border(1.dp, c.copy(alpha = 0.4f), shape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Text(symbol, style = monoStyle(10), color = p.tx2)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = monoStyle(14, FontWeight.ExtraBold), color = c, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(6.dp))
            if (a != null && a.error == null) Text(Fmt.signed(a.score, 1), style = monoStyle(13, FontWeight.Bold), color = c)
        }
        if (a != null && a.error == null) Text("${a.agreement} agree", style = monoStyle(9), color = p.tx2)
    }
}

@Composable
fun ComponentRows(a: Analysis, showUnused: Boolean = false) {
    val s = Hq.s
    val p = s.p
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        a.components.filter { it.used || showUnused }.forEach { c ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.id.replace('_', ' '), style = MaterialTheme.typography.bodySmall, color = if (c.used) p.tx2 else p.neu, modifier = Modifier.width(84.dp), maxLines = 1)
                CompBar(c.score, Modifier.weight(1f))
                Text(if (c.score == null) "n/a" else Fmt.signed(c.score), style = monoStyle(11, FontWeight.SemiBold), color = s.signColor(Fmt.sign(c.score)),
                    modifier = Modifier.width(44.dp).padding(start = 8.dp))
            }
        }
    }
}

/** Faint background block used behind a bias word in some forms. */
@Composable
fun Swatch(color: Color, modifier: Modifier = Modifier) { Box(modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color)) }
