@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.harkquant.terminal.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.harkquant.terminal.R

// ------------------------------------------------------------------ fonts
private val weights = listOf(400, 500, 600, 700, 800)

private fun variable(res: Int, min: Int = 100, max: Int = 900): FontFamily = FontFamily(
    weights.map { w ->
        val ww = w.coerceIn(min, max)
        Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(ww)))
    },
)

object HqFonts {
    val inter by lazy { variable(R.font.inter) }
    val jbmono by lazy { variable(R.font.jetbrains_mono, max = 800) }
    val plexMono by lazy {
        FontFamily(Font(R.font.plex_mono_regular, FontWeight.Normal), Font(R.font.plex_mono_regular, FontWeight.Medium),
            Font(R.font.plex_mono_semibold, FontWeight.SemiBold), Font(R.font.plex_mono_semibold, FontWeight.Bold), Font(R.font.plex_mono_semibold, FontWeight.ExtraBold))
    }
    val manrope by lazy { variable(R.font.manrope, 200, 800) }
    val sora by lazy { variable(R.font.sora, max = 800) }
    val dmSans by lazy { variable(R.font.dm_sans, 100, 1000) }
    val playfair by lazy { variable(R.font.playfair, 400, 900) }
    val fraunces by lazy { variable(R.font.fraunces) }
    val rajdhani by lazy {
        FontFamily(Font(R.font.rajdhani_medium, FontWeight.Normal), Font(R.font.rajdhani_medium, FontWeight.Medium),
            Font(R.font.rajdhani_semibold, FontWeight.SemiBold), Font(R.font.rajdhani_semibold, FontWeight.Bold), Font(R.font.rajdhani_semibold, FontWeight.ExtraBold))
    }
    val shareTech by lazy { FontFamily(Font(R.font.share_tech_mono, FontWeight.Normal)) }
    val orbitron by lazy { variable(R.font.orbitron, 400, 900) }
    val spaceGrotesk by lazy { variable(R.font.space_grotesk, 300, 700) }

    fun of(k: FontKey): FontFamily = when (k) {
        FontKey.INTER -> inter; FontKey.JBMONO -> jbmono; FontKey.PLEXMONO -> plexMono; FontKey.MANROPE -> manrope
        FontKey.SORA -> sora; FontKey.DMSANS -> dmSans; FontKey.PLAYFAIR -> playfair; FontKey.FRAUNCES -> fraunces
        FontKey.RAJDHANI -> rajdhani; FontKey.SHARETECH -> shareTech; FontKey.ORBITRON -> orbitron; FontKey.SPACEGROTESK -> spaceGrotesk
    }
}

