package com.harkquant.terminal.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import java.io.File

/**
 * Desktop font loader. Compose Desktop has no variation settings for file fonts, so variable TTFs from
 * app/src/main/res/font are pre-instanced per weight (fontTools) into [instDir] as name-<weight>.ttf.
 */
object HqFonts {
    var dir: File = File("../app/src/main/res/font")
    var instDir: File = File("/workspace/.cache/tmp/hqfonts")
    private val weights = listOf(400, 500, 600, 700, 800)
    private fun variable(name: String) = FontFamily(weights.map { w -> Font(File(instDir, "$name-$w.ttf"), FontWeight(w)) })
    private fun static2(regular: String, bold: String) = FontFamily(
        Font(File(dir, "$regular.ttf"), FontWeight.Normal), Font(File(dir, "$regular.ttf"), FontWeight.Medium),
        Font(File(dir, "$bold.ttf"), FontWeight.SemiBold), Font(File(dir, "$bold.ttf"), FontWeight.Bold), Font(File(dir, "$bold.ttf"), FontWeight.ExtraBold),
    )
    private val cache = HashMap<FontKey, FontFamily>()
    fun of(k: FontKey): FontFamily = cache.getOrPut(k) {
        when (k) {
            FontKey.INTER -> variable("inter"); FontKey.JBMONO -> variable("jetbrains_mono"); FontKey.PLEXMONO -> static2("plex_mono_regular", "plex_mono_semibold")
            FontKey.MANROPE -> variable("manrope"); FontKey.SORA -> variable("sora"); FontKey.DMSANS -> variable("dm_sans")
            FontKey.PLAYFAIR -> variable("playfair"); FontKey.FRAUNCES -> variable("fraunces"); FontKey.RAJDHANI -> static2("rajdhani_medium", "rajdhani_semibold")
            FontKey.SHARETECH -> FontFamily(Font(File(dir, "share_tech_mono.ttf"), FontWeight.Normal)); FontKey.ORBITRON -> variable("orbitron")
            FontKey.SPACEGROTESK -> variable("space_grotesk")
        }
    }
}
