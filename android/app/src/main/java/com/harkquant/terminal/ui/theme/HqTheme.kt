@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.harkquant.terminal.ui.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// ------------------------------------------------------------------ per-form styling tokens
data class HqStyle(
    val spec: ThemeSpec,
    val p: Palette,
    val form: Form,
    val ui: FontFamily,
    val mono: FontFamily,
    val display: FontFamily,
    val cardShape: Shape,
    val chipShape: Shape,
    val cardPadding: Dp,
    val gap: Dp,
    val screenPadding: Dp,
    val border: Dp,
    val titleUpper: Boolean,
    val titleTracking: TextUnit,
    /** Opaque surface colour to use where translucency would be unreadable (sheets, dialogs, menus). */
    val solidSurface: Color,
) {
    val isLight: Boolean get() = p.isLight
    fun signColor(sign: Int): Color = when { sign > 0 -> p.up; sign < 0 -> p.dn; else -> p.tx2 }
    fun impactColor(impact: String): Color = when (impact.uppercase()) {
        "CRITICAL" -> p.dn; "HIGH" -> p.dn; "MEDIUM" -> p.warn; "HOLIDAY" -> p.info; else -> p.tx2
    }
    fun labelColor(label: String): Color = when {
        label.contains("BULL", true) -> p.up; label.contains("BEAR", true) -> p.dn; else -> p.warn
    }
}

val LocalHq = staticCompositionLocalOf<HqStyle> { error("HqTheme missing") }

object Hq {
    val s: HqStyle @Composable @ReadOnlyComposable get() = LocalHq.current
    val c: Palette @Composable @ReadOnlyComposable get() = LocalHq.current.p
}

fun styleFor(spec: ThemeSpec): HqStyle {
    val p = spec.palette
    val solid = if (p.panel.alpha < 0.95f) {
        val base = p.bgGradient?.let { lerpColors(it) } ?: p.bg
        if (p.panel.alpha == 0f) base else p.panel.copy(alpha = (p.panel.alpha * 3f).coerceAtMost(1f)).compositeOver(base)
    } else p.panel
    return when (spec.form) {
        Form.TERMINAL -> HqStyle(spec, p, spec.form, HqFonts.of(spec.fonts.ui), HqFonts.of(spec.fonts.mono), HqFonts.of(spec.fonts.display),
            RoundedCornerShape(4.dp), RoundedCornerShape(3.dp), 12.dp, 8.dp, 10.dp, 1.dp, true, 0.08.em, solid)
        Form.GLASS -> HqStyle(spec, p, spec.form, HqFonts.of(spec.fonts.ui), HqFonts.of(spec.fonts.mono), HqFonts.of(spec.fonts.display),
            RoundedCornerShape(22.dp), RoundedCornerShape(50), 16.dp, 12.dp, 14.dp, 1.dp, false, 0.em, solid)
        Form.EDITORIAL -> HqStyle(spec, p, spec.form, HqFonts.of(spec.fonts.ui), HqFonts.of(spec.fonts.mono), HqFonts.of(spec.fonts.display),
            RectangleShape, RectangleShape, 14.dp, 4.dp, 18.dp, 1.dp, false, 0.em, solid)
        Form.HUD -> HqStyle(spec, p, spec.form, HqFonts.of(spec.fonts.ui), HqFonts.of(spec.fonts.mono), HqFonts.of(spec.fonts.display),
            CutCornerShape(topEnd = 14.dp, bottomStart = 14.dp), CutCornerShape(6.dp), 12.dp, 10.dp, 10.dp, 1.dp, true, 0.14.em, solid)
        Form.MOBILE -> HqStyle(spec, p, spec.form, HqFonts.of(spec.fonts.ui), HqFonts.of(spec.fonts.mono), HqFonts.of(spec.fonts.display),
            RoundedCornerShape(22.dp), RoundedCornerShape(50), 16.dp, 12.dp, 16.dp, 0.dp, false, 0.em, solid)
    }
}

private fun lerpColors(c: List<Color>): Color {
    if (c.isEmpty()) return Color.Black
    val r = c.sumOf { it.red.toDouble() } / c.size
    val g = c.sumOf { it.green.toDouble() } / c.size
    val b = c.sumOf { it.blue.toDouble() } / c.size
    return Color(r.toFloat(), g.toFloat(), b.toFloat())
}

private fun colorScheme(s: HqStyle): ColorScheme {
    val p = s.p
    val surface = s.solidSurface
    val base = p.bgGradient?.first() ?: p.bg
    return if (p.isLight) lightColorScheme(
        primary = p.acc, onPrimary = p.onAcc, primaryContainer = p.acc.copy(alpha = 0.14f).compositeOver(surface), onPrimaryContainer = p.tx,
        secondary = p.acc2, onSecondary = p.onAcc, tertiary = p.info,
        background = base, onBackground = p.tx, surface = surface, onSurface = p.tx,
        surfaceVariant = p.track.compositeOver(surface), onSurfaceVariant = p.tx2, surfaceContainer = surface, surfaceContainerHigh = surface,
        surfaceContainerHighest = p.track.compositeOver(surface), surfaceContainerLow = surface, surfaceContainerLowest = surface,
        outline = p.line, outlineVariant = p.line, error = p.dn, onError = Color.White, inverseSurface = p.tx, inverseOnSurface = base,
        surfaceTint = Color.Transparent,
    ) else darkColorScheme(
        primary = p.acc, onPrimary = p.onAcc, primaryContainer = p.acc.copy(alpha = 0.18f).compositeOver(surface), onPrimaryContainer = p.tx,
        secondary = p.acc2, onSecondary = p.onAcc, tertiary = p.info,
        background = base, onBackground = p.tx, surface = surface, onSurface = p.tx,
        surfaceVariant = p.track.compositeOver(surface), onSurfaceVariant = p.tx2, surfaceContainer = surface, surfaceContainerHigh = surface,
        surfaceContainerHighest = p.track.compositeOver(surface), surfaceContainerLow = surface, surfaceContainerLowest = surface,
        outline = p.line, outlineVariant = p.line, error = p.dn, onError = Color.White, inverseSurface = p.tx, inverseOnSurface = base,
        surfaceTint = Color.Transparent,
    )
}

private fun typography(s: HqStyle): Typography {
    val ui = s.ui
    val disp = s.display
    val hud = s.form == Form.HUD
    val edit = s.form == Form.EDITORIAL
    val dispTrack = if (hud) 0.06.em else if (edit) (-0.01).em else (-0.01).em
    fun t(f: FontFamily, size: Int, w: FontWeight, lh: Int, ls: TextUnit = 0.em) = TextStyle(fontFamily = f, fontSize = size.sp, fontWeight = w, lineHeight = lh.sp, letterSpacing = ls)
    return Typography(
        displayLarge = t(disp, 44, if (hud) FontWeight.Black else FontWeight.Bold, 50, dispTrack),
        displayMedium = t(disp, 36, if (hud) FontWeight.Black else FontWeight.Bold, 42, dispTrack),
        displaySmall = t(disp, 30, FontWeight.Bold, 36, dispTrack),
        headlineLarge = t(disp, 28, FontWeight.Bold, 34, dispTrack),
        headlineMedium = t(disp, if (hud) 20 else 25, if (edit) FontWeight.Bold else FontWeight.SemiBold, 31, dispTrack),
        headlineSmall = t(disp, if (hud) 17 else 21, FontWeight.SemiBold, 27, dispTrack),
        titleLarge = t(if (edit || hud) disp else ui, if (hud) 15 else 18, FontWeight.SemiBold, 24, if (hud) 0.08.em else 0.em),
        titleMedium = t(ui, 15, FontWeight.SemiBold, 21),
        titleSmall = t(ui, 13, FontWeight.SemiBold, 18),
        bodyLarge = t(ui, 15, FontWeight.Normal, 22),
        bodyMedium = t(ui, 13, FontWeight.Normal, 19),
        bodySmall = t(ui, 12, FontWeight.Normal, 16),
        labelLarge = t(ui, 13, FontWeight.SemiBold, 18),
        labelMedium = t(ui, 11, FontWeight.Medium, 15, 0.02.em),
        labelSmall = t(ui, 10, FontWeight.Medium, 13, 0.05.em),
    )
}

@Composable
fun HqTheme(themeId: Int, content: @Composable () -> Unit) {
    val spec = remember(themeId) { Themes.byId(themeId) }
    val style = remember(spec) { styleFor(spec) }
    CompositionLocalProvider(LocalHq provides style) {
        MaterialTheme(colorScheme = colorScheme(style), typography = typography(style), content = content)
    }
}

/** Monospace number style helper. */
@Composable
@ReadOnlyComposable
fun monoStyle(size: Int = 13, weight: FontWeight = FontWeight.Medium): TextStyle =
    TextStyle(fontFamily = LocalHq.current.mono, fontSize = size.sp, fontWeight = weight, lineHeight = (size + 5).sp)
