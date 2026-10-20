package com.harkquant.terminal.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harkquant.terminal.data.Bar
import com.harkquant.terminal.data.TimeValue
import com.harkquant.terminal.ui.theme.Form
import com.harkquant.terminal.ui.theme.Hq
import com.harkquant.terminal.util.Fmt
import com.harkquant.terminal.util.Pkt
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

data class Overlay(val name: String, val color: Color, val points: List<TimeValue>, val hist: Boolean = false)

/** "Nice" tick step for an axis span. */
fun niceStep(span: Double, ticks: Int): Double {
    if (span <= 0 || span.isNaN()) return 1.0
    val raw = span / ticks
    val mag = 10.0.pow(floor(log10(raw)))
    val n = raw / mag
    val m = when { n < 1.5 -> 1.0; n < 3 -> 2.0; n < 7 -> 5.0; else -> 10.0 }
    return m * mag
}

private fun DrawScope.label(tm: TextMeasurer, text: String, style: TextStyle, x: Float, y: Float) {
    drawText(tm, text, Offset(x, y), style)
}

/**
 * Native candlestick chart drawn on a Compose Canvas: pinch to zoom, horizontal drag to scroll,
 * long-press to inspect a bar (crosshair + OHLC in PKT), double-tap to reset. Vertical drags pass
 * through to the parent list so the page still scrolls.
 */
@Composable
fun CandleChart(
    bars: List<Bar>,
    modifier: Modifier = Modifier,
    height: Dp = 300.dp,
    overlays: List<Overlay> = emptyList(),
    daily: Boolean = false,
    showVolume: Boolean = true,
    initialVisible: Int = 90,
    interactive: Boolean = true,
) {
    val s = Hq.s
    val p = s.p
    val n = bars.size
    val tm = rememberTextMeasurer()
    var visible by remember(n) { mutableFloatStateOf(min(initialVisible, max(n, 1)).toFloat().coerceAtLeast(min(10, n).toFloat())) }
    var offset by remember(n) { mutableFloatStateOf(0f) }
    var crossX by remember { mutableStateOf<Float?>(null) }
    var plotWidthPx by remember { mutableFloatStateOf(1f) }

    val aligned = remember(bars, overlays) {
        val idx = HashMap<Long, Int>(n * 2)
        bars.forEachIndexed { i, b -> idx[b.time] = i }
        overlays.map { o ->
            val arr = FloatArray(n) { Float.NaN }
            o.points.forEach { pt -> idx[pt.time]?.let { i -> if (!pt.value.isNaN()) arr[i] = pt.value.toFloat() } }
            arr
        }
    }
    val dec = remember(bars) { if (n == 0) 2 else Fmt.decimalsFor(bars.last().close) }
    val axisStyle = TextStyle(fontFamily = s.mono, fontSize = 9.5.sp, color = p.tx2)
    val tagStyle = TextStyle(fontFamily = s.mono, fontSize = 9.5.sp, color = Color.White, fontWeight = FontWeight.Bold)
    val infoStyle = TextStyle(fontFamily = s.mono, fontSize = 10.sp, color = p.tx)

    var mod = modifier.fillMaxWidth().height(height)
    if (interactive && n > 5) {
        mod = mod
            .pointerInput(n) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var mode = 0
                    var acc = Offset.Zero
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        val maxVis = min(n, 500).toFloat()
                        if (pressed >= 2) {
                            mode = 2
                            val z = event.calculateZoom()
                            if (z != 1f) visible = (visible / z).coerceIn(min(12f, maxVis), maxVis)
                            val pan = event.calculatePan()
                            val bw = plotWidthPx / visible
                            offset = (offset + pan.x / bw).coerceIn(0f, max(0f, n - visible))
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } else if (mode == 0 || mode == 1) {
                            val ch = event.changes.firstOrNull() ?: break
                            val d = ch.positionChange()
                            if (mode == 0) {
                                acc += d
                                val slop = viewConfiguration.touchSlop
                                if (abs(acc.x) > slop && abs(acc.x) > abs(acc.y) * 1.2f) mode = 1
                                else if (abs(acc.y) > slop) mode = -1
                            }
                            if (mode == 1 && crossX == null) {
                                val bw = plotWidthPx / visible
                                offset = (offset + d.x / bw).coerceIn(0f, max(0f, n - visible))
                                ch.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(n) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { crossX = it.x },
                    onDrag = { c, _ -> crossX = c.position.x; c.consume() },
                    onDragEnd = { crossX = null },
                    onDragCancel = { crossX = null },
                )
            }
            .pointerInput(n) {
                detectTapGestures(onDoubleTap = { visible = min(initialVisible, n).toFloat(); offset = 0f })
            }
    }

    Canvas(mod) {
        if (n == 0) return@Canvas
        val axisW = 58.dp.toPx()
        val timeH = 16.dp.toPx()
        val plotW = size.width - axisW
        plotWidthPx = plotW
        val plotH = size.height - timeH
        val volH = if (showVolume) plotH * 0.17f else 0f
        val priceH = plotH - volH - 4.dp.toPx()
        val vis = visible.coerceAtMost(n.toFloat())
        val end = n - offset
        val start = end - vis
        val bw = plotW / vis
        val i0 = max(0, floor(start).toInt())
        val i1 = min(n - 1, ceil(end).toInt())
        if (i1 < i0) return@Canvas

        var lo = Double.MAX_VALUE
        var hi = -Double.MAX_VALUE
        var vmax = 0.0
        for (i in i0..i1) {
            val b = bars[i]
            lo = min(lo, b.low); hi = max(hi, b.high); vmax = max(vmax, b.volume)
            aligned.forEachIndexed { k, arr -> if (!overlays[k].hist) { val v = arr[i]; if (!v.isNaN()) { lo = min(lo, v.toDouble()); hi = max(hi, v.toDouble()) } } }
        }
        if (hi <= lo) { hi = lo + 1; lo -= 1 }
        val pad = (hi - lo) * 0.07
        lo -= pad; hi += pad
        fun y(v: Double) = (priceH * (1 - (v - lo) / (hi - lo))).toFloat()
        fun x(i: Int) = plotW - (end - i - 0.5f) * bw

        // grid + price axis
        val step = niceStep(hi - lo, 5)
        var t = ceil(lo / step) * step
        while (t <= hi) {
            val yy = y(t)
            drawLine(p.grid.takeIf { it.alpha > 0.02f } ?: p.line.copy(alpha = 0.4f), Offset(0f, yy), Offset(plotW, yy), 1f)
            label(tm, Fmt.fixed(t, min(dec, max(0, -floor(log10(step)).toInt()))), axisStyle, plotW + 6.dp.toPx(), yy - 6.dp.toPx())
            t += step
        }
        // time axis labels every ~70dp
        val every = max(1, (70.dp.toPx() / bw).toInt())
        var lastLabelX = -1000f
        for (i in i0..i1) {
            if (i % every != 0) continue
            val xx = x(i)
            if (xx < 0 || xx > plotW - 20.dp.toPx() || xx - lastLabelX < 56.dp.toPx()) continue
            drawLine(p.grid.takeIf { it.alpha > 0.02f } ?: p.line.copy(alpha = 0.3f), Offset(xx, 0f), Offset(xx, plotH), 1f)
            label(tm, Pkt.axis(bars[i].time, daily), axisStyle, xx - 14.dp.toPx(), plotH + 3.dp.toPx())
            lastLabelX = xx
        }

        // volume
        if (showVolume && vmax > 0) {
            for (i in i0..i1) {
                val b = bars[i]
                val h = (b.volume / vmax * volH).toFloat()
                val c = (if (b.close >= b.open) p.up else p.dn).copy(alpha = 0.35f)
                drawRect(c, Offset(x(i) - bw * 0.36f, plotH - h), Size(bw * 0.72f, h))
            }
        }
        // candles
        val bodyW = max(1f, bw * 0.68f)
        for (i in i0..i1) {
            val b = bars[i]
            val xx = x(i)
            if (xx < -bw || xx > plotW + bw) continue
            val up = b.close >= b.open
            val c = if (up) p.up else p.dn
            drawLine(c, Offset(xx, y(b.high)), Offset(xx, y(b.low)), max(1f, bw * 0.1f).coerceAtMost(2.dp.toPx()))
            val top = y(max(b.open, b.close)); val bot = y(min(b.open, b.close))
            drawRect(c, Offset(xx - bodyW / 2, top), Size(bodyW, max(1f, bot - top)))
        }
        // overlays (price-pane lines)
        aligned.forEachIndexed { k, arr ->
            if (overlays[k].hist) return@forEachIndexed
            val path = Path()
            var started = false
            for (i in i0..i1) {
                val v = arr[i]
                if (v.isNaN()) { started = false; continue }
                val pt = Offset(x(i), y(v.toDouble()))
                if (!started) { path.moveTo(pt.x, pt.y); started = true } else path.lineTo(pt.x, pt.y)
            }
            drawPath(path, overlays[k].color, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
        }
        // last price
        val last = bars.last()
        val ly = y(last.close).coerceIn(0f, priceH)
        val lc = if (last.close >= last.open) p.up else p.dn
        drawLine(lc.copy(alpha = 0.8f), Offset(0f, ly), Offset(plotW, ly), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        val txt = Fmt.fixed(last.close, dec)
        val lay = tm.measure(txt, tagStyle)
        val rr = if (s.form == Form.MOBILE || s.form == Form.GLASS) 4.dp.toPx() else 1f
        drawRoundRect(lc, Offset(plotW + 2.dp.toPx(), ly - lay.size.height / 2f - 2.dp.toPx()), Size(axisW - 2.dp.toPx(), lay.size.height + 4.dp.toPx()), CornerRadius(rr))
        drawText(lay, topLeft = Offset(plotW + 5.dp.toPx(), ly - lay.size.height / 2f))

        // crosshair
        crossX?.let { cx ->
            val i = (end - (plotW - cx) / bw - 0.5f).let { Math.round(it) }.coerceIn(i0, i1)
            val b = bars[i]
            val xx = x(i)
            drawLine(p.tx2, Offset(xx, 0f), Offset(xx, plotH), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))
            val cy = y(b.close)
            drawLine(p.tx2, Offset(0f, cy), Offset(plotW, cy), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))
            val info = "${Pkt.dayHm(b.time.toDouble())} PKT\nO ${Fmt.fixed(b.open, dec)}  H ${Fmt.fixed(b.high, dec)}\nL ${Fmt.fixed(b.low, dec)}  C ${Fmt.fixed(b.close, dec)}"
            val il = tm.measure(info, infoStyle)
            val bx = if (xx > plotW / 2) 6.dp.toPx() else plotW - il.size.width - 14.dp.toPx()
            drawRoundRect(s.solidSurface.copy(alpha = 0.94f), Offset(bx, 6.dp.toPx()), Size(il.size.width + 10.dp.toPx(), il.size.height + 8.dp.toPx()), CornerRadius(6.dp.toPx()))
            drawRoundRect(p.line, Offset(bx, 6.dp.toPx()), Size(il.size.width + 10.dp.toPx(), il.size.height + 8.dp.toPx()), CornerRadius(6.dp.toPx()), style = Stroke(1f))
            drawText(il, topLeft = Offset(bx + 5.dp.toPx(), 10.dp.toPx()))
        }
    }
}

/** Generic multi-series line chart (positioning, OI, CVD, indicator panes). Histogram series draw as bars. */
@Composable
fun LineChart(
    series: List<Overlay>,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
    area: Boolean = false,
    zeroLine: Boolean = false,
    daily: Boolean = false,
    valueFormat: (Double) -> String = { Fmt.big(it) },
) {
    val s = Hq.s
    val p = s.p
    val tm = rememberTextMeasurer()
    val axisStyle = TextStyle(fontFamily = s.mono, fontSize = 9.sp, color = p.tx2)
    Canvas(modifier.fillMaxWidth().height(height)) {
        val all = series.flatMap { it.points }.filter { !it.value.isNaN() }
        if (all.isEmpty()) return@Canvas
        val axisW = 50.dp.toPx(); val timeH = 14.dp.toPx()
        val w = size.width - axisW; val h = size.height - timeH
        val t0 = all.minOf { it.time }; val t1 = all.maxOf { it.time }
        var lo = all.minOf { it.value }; var hi = all.maxOf { it.value }
        if (series.any { it.hist } || zeroLine) { lo = min(lo, 0.0); hi = max(hi, 0.0) }
        if (hi <= lo) { hi = lo + 1; lo -= 1 }
        val pad = (hi - lo) * 0.08; lo -= pad; hi += pad
        fun x(t: Long) = if (t1 == t0) w / 2 else ((t - t0).toFloat() / (t1 - t0) * w)
        fun y(v: Double) = (h * (1 - (v - lo) / (hi - lo))).toFloat()
        val step = niceStep(hi - lo, 4)
        var t = ceil(lo / step) * step
        while (t <= hi) {
            drawLine(p.line.copy(alpha = 0.45f), Offset(0f, y(t)), Offset(w, y(t)), 1f)
            drawText(tm, valueFormat(t), Offset(w + 5.dp.toPx(), y(t) - 6.dp.toPx()), axisStyle)
            t += step
        }
        if (zeroLine || series.any { it.hist }) drawLine(p.tx2.copy(alpha = 0.6f), Offset(0f, y(0.0)), Offset(w, y(0.0)), 1f)
        // time labels: start, middle, end
        listOf(t0, (t0 + t1) / 2, t1).forEachIndexed { k, tt ->
            val lbl = Pkt.axis(tt, daily || (t1 - t0) > 3 * 86400)
            val lw = tm.measure(lbl, axisStyle).size.width
            val xx = when (k) { 0 -> 0f; 1 -> x(tt) - lw / 2; else -> w - lw }
            drawText(tm, lbl, Offset(xx, h + 2.dp.toPx()), axisStyle)
        }
        series.forEach { sr ->
            val pts = sr.points.filter { !it.value.isNaN() }
            if (pts.isEmpty()) return@forEach
            if (sr.hist) {
                val bw = max(1f, w / pts.size * 0.7f)
                pts.forEach { pt ->
                    val c = if (pt.value >= 0) p.up else p.dn
                    val y0 = y(0.0); val y1 = y(pt.value)
                    drawRect(c.copy(alpha = 0.8f), Offset(x(pt.time) - bw / 2, min(y0, y1)), Size(bw, abs(y1 - y0).coerceAtLeast(1f)))
                }
            } else {
                val path = Path()
                pts.forEachIndexed { i, pt -> if (i == 0) path.moveTo(x(pt.time), y(pt.value)) else path.lineTo(x(pt.time), y(pt.value)) }
                if (area) {
                    val fill = Path().apply { addPath(path); lineTo(x(pts.last().time), h); lineTo(x(pts.first().time), h); close() }
                    drawPath(fill, Brush.verticalGradient(listOf(sr.color.copy(alpha = 0.28f), Color.Transparent), 0f, h))
                }
                drawPath(path, sr.color, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
            }
        }
    }
}

@Composable
fun Sparkline(values: List<Double>, color: Color, modifier: Modifier = Modifier, height: Dp = 36.dp, fill: Boolean = true) {
    Canvas(modifier.fillMaxWidth().height(height)) {
        if (values.size < 2) return@Canvas
        val lo = values.min(); val hi = values.max()
        val span = if (hi - lo == 0.0) 1.0 else hi - lo
        fun x(i: Int) = i.toFloat() / (values.size - 1) * size.width
        fun y(v: Double) = (size.height * (1 - (v - lo) / span) * 0.9f + size.height * 0.05f).toFloat()
        val path = Path()
        values.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
        if (fill) {
            val f = Path().apply { addPath(path); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
            drawPath(f, Brush.verticalGradient(listOf(color.copy(alpha = 0.3f), Color.Transparent)))
        }
        drawPath(path, color, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(color, 2.6.dp.toPx(), Offset(x(values.size - 1), y(values.last())))
    }
}

/** Cumulative order-book depth (bids left, asks right). */
@Composable
fun DepthChart(bids: List<List<Double>>, asks: List<List<Double>>, modifier: Modifier = Modifier, height: Dp = 180.dp) {
    val s = Hq.s
    val p = s.p
    val tm = rememberTextMeasurer()
    val axisStyle = TextStyle(fontFamily = s.mono, fontSize = 9.sp, color = p.tx2)
    Canvas(modifier.fillMaxWidth().height(height)) {
        val b = mutableListOf<Pair<Double, Double>>(); var c = 0.0
        bids.forEach { if (it.size >= 2) { c += it[1]; b.add(it[0] to c) } }
        val a = mutableListOf<Pair<Double, Double>>(); c = 0.0
        asks.forEach { if (it.size >= 2) { c += it[1]; a.add(it[0] to c) } }
        if (b.isEmpty() || a.isEmpty()) return@Canvas
        val minP = b.last().first; val maxP = a.last().first
        val maxQ = max(b.last().second, a.last().second)
        val h = size.height - 16.dp.toPx()
        fun x(pr: Double) = ((pr - minP) / (maxP - minP).coerceAtLeast(1e-9) * size.width).toFloat()
        fun y(q: Double) = (h - q / maxQ * (h - 6.dp.toPx())).toFloat()
        fun fill(pts: List<Pair<Double, Double>>, col: Color) {
            val path = Path().apply {
                moveTo(x(pts[0].first), h)
                pts.forEach { lineTo(x(it.first), y(it.second)) }
                lineTo(x(pts.last().first), h); close()
            }
            drawPath(path, col.copy(alpha = 0.22f))
            val line = Path().apply { pts.forEachIndexed { i, it -> if (i == 0) moveTo(x(it.first), y(it.second)) else lineTo(x(it.first), y(it.second)) } }
            drawPath(line, col, style = Stroke(1.6.dp.toPx()))
        }
        fill(b, p.up); fill(a, p.dn)
        drawText(tm, Fmt.price(minP), Offset(0f, h + 2.dp.toPx()), axisStyle)
        val r = Fmt.price(maxP); val rw = tm.measure(r, axisStyle).size.width
        drawText(tm, r, Offset(size.width - rw, h + 2.dp.toPx()), axisStyle)
    }
}
