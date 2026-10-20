package com.harkquant.terminal.ui.theme

import androidx.compose.ui.graphics.Color

/** The five layout families from the web theme pack. */
enum class Form(val letter: String, val title: String) {
    TERMINAL("A", "Pro Terminal"),
    GLASS("B", "Glassmorphism"),
    EDITORIAL("C", "Minimal Editorial"),
    HUD("D", "Cyber HUD"),
    MOBILE("E", "Mobile App"),
}

enum class FontKey { INTER, JBMONO, PLEXMONO, MANROPE, SORA, DMSANS, PLAYFAIR, FRAUNCES, RAJDHANI, SHARETECH, ORBITRON, SPACEGROTESK }

data class FontSet(val ui: FontKey, val mono: FontKey, val display: FontKey)

/** A palette, straight from themes.py (CSS custom properties) with rgba() parsed to ARGB. */
data class Palette(
    val bg: Color,
    val bgGradient: List<Color>?,      // B forms: linear gradient page
    val bg2: Color?,                   // D forms: radial glow colour
    val panel: Color,
    val line: Color,
    val tx: Color,
    val tx2: Color,
    val acc: Color,
    val acc2: Color,
    val up: Color,
    val dn: Color,
    val warn: Color,
    val onAcc: Color,
    val track: Color,
    val upBg: Color,
    val dnBg: Color,
    val info: Color,
    val neu: Color,
    val glow: Color,
    val grid: Color,
    val blobs: List<Color>,
    val blobAlpha: Float,
    val isLight: Boolean,
)

data class ThemeSpec(
    val id: Int,
    val form: Form,
    val key: String,
    val name: String,
    val description: String,
    val palette: Palette,
    val fonts: FontSet,
) {
    val label: String get() = "%02d · %s".format(id, name)
}

/** Parses "#rgb", "#rrggbb", "rgba(r,g,b,a)" and "transparent". */
fun css(v: String): Color {
    val s = v.trim()
    if (s == "transparent") return Color.Transparent
    if (s.startsWith("#")) {
        val h = s.substring(1)
        val full = if (h.length == 3) h.map { "$it$it" }.joinToString("") else h
        val n = full.toLong(16)
        return if (full.length == 8) Color(n) else Color(0xFF000000 or n)
    }
    if (s.startsWith("rgba(") || s.startsWith("rgb(")) {
        val p = s.substringAfter('(').substringBefore(')').split(',').map { it.trim() }
        val a = if (p.size > 3) p[3].toFloat() else 1f
        return Color(p[0].toInt(), p[1].toInt(), p[2].toInt(), (a * 255).toInt().coerceIn(0, 255))
    }
    error("bad colour $v")
}

private fun gradient(v: String): List<Color> =
    Regex("#[0-9a-fA-F]{3,8}").findAll(v).map { css(it.value) }.toList()

private fun pal(m: Map<String, String>, light: Boolean): Palette {
    fun g(k: String, d: String) = css(m[k] ?: d)
    val bgRaw = m.getValue("bg")
    val grad = if (bgRaw.startsWith("linear-gradient")) gradient(bgRaw) else null
    val bg = grad?.first() ?: css(bgRaw)
    val acc = css(m.getValue("acc"))
    return Palette(
        bg = bg,
        bgGradient = grad,
        bg2 = m["bg2"]?.let(::css),
        panel = css(m.getValue("panel")),
        line = css(m.getValue("line")),
        tx = css(m.getValue("tx")),
        tx2 = css(m.getValue("tx2")),
        acc = acc,
        acc2 = g("acc2", m.getValue("acc")),
        up = css(m.getValue("up")),
        dn = css(m.getValue("dn")),
        warn = g("warn", "#f5b82e"),
        onAcc = g("onacc", if (light) "#fff" else "#000"),
        track = g("track", if (light) "rgba(0,0,0,.06)" else "rgba(255,255,255,.06)"),
        upBg = g("upbg", "rgba(38,198,160,.08)"),
        dnBg = g("dnbg", "rgba(239,83,80,.08)"),
        info = g("info", "#4b8cf7"),
        neu = g("neu", "#6b7280"),
        glow = m["accglow"]?.let(::css) ?: acc.copy(alpha = 0.5f),
        grid = m["gridc"]?.let(::css) ?: m["grid"]?.let(::css) ?: (if (light) Color(0x0F000000) else Color(0x0FFFFFFF)),
        blobs = listOfNotNull(m["b1"], m["b2"], m["b3"]).map(::css),
        blobAlpha = m["blobop"]?.toFloat() ?: 0.55f,
        isLight = light,
    )
}

private val DARK_BASE = mapOf(
    "track" to "rgba(255,255,255,.06)", "upbg" to "rgba(38,198,160,.08)", "dnbg" to "rgba(239,83,80,.08)",
    "neu" to "#6b7280", "onacc" to "#000", "info" to "#4b8cf7",
)

private fun dark(vararg kv: Pair<String, String>) = DARK_BASE + kv.toMap()
private fun light(vararg kv: Pair<String, String>) = kv.toMap()

private val F_TERM = FontSet(FontKey.INTER, FontKey.JBMONO, FontKey.INTER)

private fun spec(id: Int, form: Form, key: String, name: String, desc: String, m: Map<String, String>, isLight: Boolean, fonts: FontSet) =
    ThemeSpec(id, form, key, name, desc, pal(m, isLight), fonts)

object Themes {
    val all: List<ThemeSpec> = listOf(
        // ---------------- A Pro Terminal
        spec(1, Form.TERMINAL, "navy-gold", "Navy Gold", "Deep navy desk with gold accents and teal/red candles — the current app, refined.",
            dark("bg" to "#0a1120", "panel" to "#0e182b", "line" to "#1c2a44", "tx" to "#e6ebf5", "tx2" to "#8392ad", "acc" to "#f5b82e", "acc2" to "#5aa9ff",
                "up" to "#26c6a0", "dn" to "#ef5350", "warn" to "#f5b82e", "track" to "#15223a", "upbg" to "rgba(38,198,160,.09)", "dnbg" to "rgba(239,83,80,.09)"), false, F_TERM),
        spec(2, Form.TERMINAL, "amber-black", "Amber Black", "Bloomberg-style pure black, amber labels, all-monospace type.",
            dark("bg" to "#000000", "panel" to "#070707", "line" to "#2b2418", "tx" to "#f3ead8", "tx2" to "#d08f12", "acc" to "#ff9f00", "acc2" to "#36a3ff",
                "up" to "#00d26a", "dn" to "#ff3b30", "warn" to "#ffb000", "track" to "#141008", "upbg" to "rgba(0,210,106,.07)", "dnbg" to "rgba(255,59,48,.08)", "grid" to "#1a160f"),
            false, FontSet(FontKey.PLEXMONO, FontKey.PLEXMONO, FontKey.PLEXMONO)),
        spec(3, Form.TERMINAL, "phosphor", "Phosphor Green", "Green-screen CRT terminal: black-green panels, phosphor accents, cyan EMA.",
            dark("bg" to "#040c08", "panel" to "#07140d", "line" to "#123422", "tx" to "#cdf7dc", "tx2" to "#5f9c78", "acc" to "#3dff8c", "acc2" to "#00e1ff",
                "up" to "#3dff8c", "dn" to "#ff4f74", "warn" to "#e6ff4d", "track" to "#0c2117", "upbg" to "rgba(61,255,140,.06)", "dnbg" to "rgba(255,79,116,.07)", "grid" to "#0d2418"),
            false, FontSet(FontKey.JBMONO, FontKey.JBMONO, FontKey.JBMONO)),
        spec(4, Form.TERMINAL, "graphite-light", "Graphite Light", "Daylight trading desk: light grey panels, cobalt accent, crisp hairlines.",
            light("bg" to "#e7eaf0", "panel" to "#ffffff", "line" to "#d6dbe4", "tx" to "#141a26", "tx2" to "#66708a", "acc" to "#2156f3", "acc2" to "#9b51e0",
                "up" to "#0a9e6e", "dn" to "#d63c3c", "warn" to "#c47a00", "track" to "#eef1f6", "upbg" to "rgba(10,158,110,.07)", "dnbg" to "rgba(214,60,60,.06)",
                "onacc" to "#fff", "neu" to "#b8bfcc", "info" to "#2156f3", "grid" to "#eceff4"), true, F_TERM),
        // ---------------- B Glassmorphism
        spec(5, Form.GLASS, "aurora", "Aurora", "Violet-to-teal aurora gradient under frosted glass cards.",
            dark("bg" to "linear-gradient(135deg,#1a0f3e,#0b2a3c)", "panel" to "rgba(255,255,255,.07)", "line" to "rgba(255,255,255,.13)",
                "tx" to "#f4f2ff", "tx2" to "rgba(232,226,255,.62)", "acc" to "#a78bfa", "acc2" to "#5eead4", "up" to "#34d3b1", "dn" to "#ff6b8b", "warn" to "#fcd34d", "onacc" to "#160c33",
                "b1" to "#8b5cf6", "b2" to "#14b8a6", "b3" to "#ec4899", "accglow" to "rgba(167,139,250,.5)", "track" to "rgba(255,255,255,.08)",
                "upbg" to "rgba(52,211,177,.12)", "dnbg" to "rgba(255,107,139,.12)"), false, FontSet(FontKey.MANROPE, FontKey.JBMONO, FontKey.SORA)),
        spec(6, Form.GLASS, "ocean", "Ocean Glass", "Deep-sea blues and cyan light, glass panels like looking through water.",
            dark("bg" to "linear-gradient(160deg,#021a33,#043c4f)", "panel" to "rgba(160,220,255,.07)", "line" to "rgba(170,225,255,.16)",
                "tx" to "#eefaff", "tx2" to "rgba(200,235,250,.62)", "acc" to "#38bdf8", "acc2" to "#a5f3fc", "up" to "#2ee6a6", "dn" to "#ff7a7a", "warn" to "#fde047", "onacc" to "#02253d",
                "b1" to "#0ea5e9", "b2" to "#2563eb", "b3" to "#22d3ee", "accglow" to "rgba(56,189,248,.5)", "track" to "rgba(255,255,255,.08)",
                "upbg" to "rgba(46,230,166,.11)", "dnbg" to "rgba(255,122,122,.11)"), false, FontSet(FontKey.MANROPE, FontKey.JBMONO, FontKey.MANROPE)),
        spec(7, Form.GLASS, "sunset", "Sunset Ember", "Warm magenta-orange dusk gradient, amber glass, high-energy.",
            dark("bg" to "linear-gradient(140deg,#2b0c22,#3d1607)", "panel" to "rgba(255,220,200,.08)", "line" to "rgba(255,210,180,.16)",
                "tx" to "#fff3ec", "tx2" to "rgba(255,225,205,.62)", "acc" to "#fb923c", "acc2" to "#fde68a", "up" to "#4ade80", "dn" to "#fb7185", "warn" to "#fde047", "onacc" to "#2b0c06",
                "b1" to "#e11d48", "b2" to "#f97316", "b3" to "#facc15", "accglow" to "rgba(251,146,60,.5)", "track" to "rgba(255,255,255,.08)", "blobop" to ".5",
                "upbg" to "rgba(74,222,128,.11)", "dnbg" to "rgba(251,113,133,.12)"), false, FontSet(FontKey.SORA, FontKey.JBMONO, FontKey.SORA)),
        spec(8, Form.GLASS, "frost", "Frost", "Light pastel glass: icy blue and lilac, dark ink, soft shadows.",
            light("bg" to "linear-gradient(135deg,#dce8f7,#f1e6f7)", "panel" to "rgba(255,255,255,.5)", "line" to "rgba(255,255,255,.85)",
                "tx" to "#1c2240", "tx2" to "#5b6386", "acc" to "#6366f1", "acc2" to "#0ea5e9", "up" to "#059669", "dn" to "#e11d48", "warn" to "#d97706", "onacc" to "#fff", "neu" to "#c7cbe0",
                "b1" to "#93c5fd", "b2" to "#c4b5fd", "b3" to "#a5f3fc", "blobop" to ".85", "accglow" to "rgba(99,102,241,.35)", "track" to "rgba(40,50,110,.07)",
                "upbg" to "rgba(5,150,105,.08)", "dnbg" to "rgba(225,29,72,.07)", "info" to "#6366f1", "grid" to "rgba(40,50,90,.07)"), true,
            FontSet(FontKey.DMSANS, FontKey.JBMONO, FontKey.DMSANS)),
        // ---------------- C Minimal Editorial
        spec(9, Form.EDITORIAL, "paper", "Paper", "Broadsheet newspaper: cream paper, ink-black serif, red editorial accent.",
            light("bg" to "#f5f0e6", "panel" to "transparent", "line" to "#d8cfbe", "tx" to "#1b1714", "tx2" to "#6f665a", "acc" to "#1b1714", "acc2" to "#b3261e",
                "up" to "#1f6f45", "dn" to "#b3261e", "warn" to "#a8620a", "onacc" to "#f5f0e6", "track" to "#e9e2d4", "upbg" to "rgba(31,111,69,.07)", "dnbg" to "rgba(179,38,30,.06)",
                "neu" to "#cfc6b4", "info" to "#2f5c9e", "grid" to "#e6dfd0"), true, FontSet(FontKey.DMSANS, FontKey.PLEXMONO, FontKey.PLAYFAIR)),
        spec(10, Form.EDITORIAL, "swiss", "Swiss", "International-style: pure white, heavy grotesk type, signal-orange accent.",
            light("bg" to "#ffffff", "panel" to "transparent", "line" to "#e2e2e2", "tx" to "#0a0a0a", "tx2" to "#707070", "acc" to "#0a0a0a", "acc2" to "#ff3b00",
                "up" to "#00875a", "dn" to "#e5231b", "warn" to "#e08600", "onacc" to "#fff", "track" to "#f1f1f1", "upbg" to "rgba(0,135,90,.06)", "dnbg" to "rgba(229,35,27,.05)",
                "neu" to "#d4d4d4", "info" to "#1f5eff", "grid" to "#f0f0f0"), true, FontSet(FontKey.INTER, FontKey.JBMONO, FontKey.INTER)),
        spec(11, Form.EDITORIAL, "sage", "Sage", "Calm botanical: sage-grey paper, forest ink, soft serif headlines.",
            light("bg" to "#edf0e8", "panel" to "transparent", "line" to "#cdd5c5", "tx" to "#1e2a21", "tx2" to "#627060", "acc" to "#2f5a40", "acc2" to "#3f7d54",
                "up" to "#3a7a50", "dn" to "#b0543d", "warn" to "#a87a14", "onacc" to "#fff", "track" to "#dfe5d8", "upbg" to "rgba(58,122,80,.08)", "dnbg" to "rgba(176,84,61,.07)",
                "neu" to "#c6cebd", "info" to "#3d6b8f", "grid" to "#dde3d6"), true, FontSet(FontKey.DMSANS, FontKey.PLEXMONO, FontKey.FRAUNCES)),
        spec(12, Form.EDITORIAL, "ink-night", "Ink Night", "Dark editorial: charcoal page, ivory serif type, champagne-gold accent.",
            light("bg" to "#141312", "panel" to "transparent", "line" to "#2f2c28", "tx" to "#efe8dc", "tx2" to "#9a9184", "acc" to "#e8c27a", "acc2" to "#e8c27a",
                "up" to "#7fc495", "dn" to "#e2786a", "warn" to "#e8c27a", "onacc" to "#141312", "track" to "#23211e", "upbg" to "rgba(127,196,149,.08)", "dnbg" to "rgba(226,120,106,.08)",
                "neu" to "#5a554d", "info" to "#8fb3e0", "grid" to "#211f1c"), false, FontSet(FontKey.DMSANS, FontKey.PLEXMONO, FontKey.PLAYFAIR)),
        // ---------------- D Cyber HUD
        spec(13, Form.HUD, "neon-cyan", "Neon Cyan", "Classic sci-fi HUD: black-blue void, cyan glow, angular frames.",
            dark("bg" to "#02060c", "bg2" to "#062033", "panel" to "rgba(0,229,255,.035)", "line" to "rgba(0,229,255,.26)", "tx" to "#d9fbff", "tx2" to "#5fa8b8", "acc" to "#00e5ff", "acc2" to "#ff2bd6",
                "up" to "#00ffa3", "dn" to "#ff2e63", "warn" to "#ffd319", "onacc" to "#001018", "gridc" to "rgba(0,229,255,.06)", "track" to "rgba(0,229,255,.08)",
                "upbg" to "rgba(0,255,163,.07)", "dnbg" to "rgba(255,46,99,.08)"), false, FontSet(FontKey.RAJDHANI, FontKey.SHARETECH, FontKey.ORBITRON)),
        spec(14, Form.HUD, "synthwave", "Synthwave", "Retro-80s grid: deep purple, hot magenta frames, cyan/pink candles.",
            dark("bg" to "#0c0220", "bg2" to "#2a0845", "panel" to "rgba(255,43,214,.04)", "line" to "rgba(255,43,214,.3)", "tx" to "#ffe9fb", "tx2" to "#a87dbf", "acc" to "#ff2bd6", "acc2" to "#2de2e6",
                "up" to "#2de2e6", "dn" to "#ff3864", "warn" to "#ffd319", "onacc" to "#18002a", "gridc" to "rgba(255,43,214,.07)", "track" to "rgba(255,43,214,.09)",
                "upbg" to "rgba(45,226,230,.07)", "dnbg" to "rgba(255,56,100,.08)"), false, FontSet(FontKey.RAJDHANI, FontKey.SHARETECH, FontKey.ORBITRON)),
        spec(15, Form.HUD, "toxic", "Toxic", "Acid-lime on black: radioactive glow, hazard-orange downs.",
            dark("bg" to "#040802", "bg2" to "#13240a", "panel" to "rgba(182,255,0,.03)", "line" to "rgba(182,255,0,.26)", "tx" to "#efffd0", "tx2" to "#88a55c", "acc" to "#b6ff00", "acc2" to "#00e0ff",
                "up" to "#b6ff00", "dn" to "#ff5a1f", "warn" to "#ffd000", "onacc" to "#0b1400", "gridc" to "rgba(182,255,0,.06)", "track" to "rgba(182,255,0,.08)",
                "upbg" to "rgba(182,255,0,.06)", "dnbg" to "rgba(255,90,31,.08)"), false, FontSet(FontKey.RAJDHANI, FontKey.SHARETECH, FontKey.ORBITRON)),
        spec(16, Form.HUD, "crimson-ops", "Crimson Ops", "Tactical red-alert ops room: blood-red frames, white readouts, mint ups.",
            dark("bg" to "#0a0203", "bg2" to "#2a0508", "panel" to "rgba(255,42,61,.035)", "line" to "rgba(255,42,61,.3)", "tx" to "#ffe6e6", "tx2" to "#b37777", "acc" to "#ff2a3d", "acc2" to "#ffffff",
                "up" to "#4dffb2", "dn" to "#ff2a3d", "warn" to "#ffb020", "onacc" to "#fff", "gridc" to "rgba(255,42,61,.06)", "track" to "rgba(255,42,61,.09)",
                "upbg" to "rgba(77,255,178,.06)", "dnbg" to "rgba(255,42,61,.08)"), false, FontSet(FontKey.RAJDHANI, FontKey.SHARETECH, FontKey.ORBITRON)),
        // ---------------- E Mobile App
        spec(17, Form.MOBILE, "onyx-lime", "Onyx Lime", "OLED-black app with electric-lime accent, big rounded cards.",
            dark("bg" to "#000000", "panel" to "#111214", "line" to "#222429", "tx" to "#f5f5f5", "tx2" to "#8b8f99", "acc" to "#c6ff3d", "up" to "#34d27f", "dn" to "#ff4f5e",
                "warn" to "#ffc531", "onacc" to "#000", "track" to "#1c1e22", "upbg" to "rgba(52,210,127,.12)", "dnbg" to "rgba(255,79,94,.12)", "accglow" to "rgba(198,255,61,.6)"),
            false, FontSet(FontKey.INTER, FontKey.JBMONO, FontKey.INTER)),
        spec(18, Form.MOBILE, "royal", "Royal Indigo", "Indigo night with violet accent and soft lavender text.",
            dark("bg" to "#0e0b26", "panel" to "#19163d", "line" to "#2a2560", "tx" to "#f1efff", "tx2" to "#9c97c9", "acc" to "#7c6cff", "up" to "#22d3a6", "dn" to "#ff6680",
                "warn" to "#ffcf5c", "onacc" to "#fff", "track" to "#221e4c", "upbg" to "rgba(34,211,166,.12)", "dnbg" to "rgba(255,102,128,.12)", "accglow" to "rgba(124,108,255,.7)"),
            false, FontSet(FontKey.MANROPE, FontKey.JBMONO, FontKey.MANROPE)),
        spec(19, Form.MOBILE, "daylight", "Daylight", "Clean light fintech app: white cards, cobalt accent, green/red semantics.",
            light("bg" to "#f2f4f8", "panel" to "#ffffff", "line" to "#e3e7ef", "tx" to "#0f172a", "tx2" to "#64748b", "acc" to "#2563eb", "up" to "#16a34a", "dn" to "#dc2626",
                "warn" to "#d97706", "onacc" to "#fff", "track" to "#eef1f6", "upbg" to "rgba(22,163,74,.09)", "dnbg" to "rgba(220,38,38,.08)", "neu" to "#cbd5e1", "info" to "#2563eb",
                "grid" to "#eef1f5"), true, FontSet(FontKey.DMSANS, FontKey.JBMONO, FontKey.DMSANS)),
        spec(20, Form.MOBILE, "rose-gold", "Rose Gold", "Warm espresso-dark app with rose-gold accent and geometric type.",
            dark("bg" to "#181012", "panel" to "#23181b", "line" to "#3a2a2f", "tx" to "#f8ebe6", "tx2" to "#b39a94", "acc" to "#e9a898", "up" to "#7fd1ae", "dn" to "#ff7b7b",
                "warn" to "#f2c27b", "onacc" to "#1a1012", "track" to "#2f2226", "upbg" to "rgba(127,209,174,.11)", "dnbg" to "rgba(255,123,123,.11)", "accglow" to "rgba(233,168,152,.6)"),
            false, FontSet(FontKey.SPACEGROTESK, FontKey.JBMONO, FontKey.SPACEGROTESK)),
    )

    fun byId(id: Int): ThemeSpec = all.firstOrNull { it.id == id } ?: all[16]
}
