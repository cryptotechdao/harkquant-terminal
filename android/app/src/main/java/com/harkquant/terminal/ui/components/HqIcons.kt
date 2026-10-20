package com.harkquant.terminal.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Small stroke icon set (24dp grid) so we don't need the 10 MB extended icon library. */
object HqIcons {
    private fun icon(name: String, vararg paths: String, fill: String? = null): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        paths.forEach { d ->
            b.addPath(
                pathData = addPathNodes(d),
                stroke = SolidColor(Color.Black), strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            )
        }
        if (fill != null) b.addPath(pathData = addPathNodes(fill), fill = SolidColor(Color.Black))
        return b.build()
    }

    val Dashboard = icon("dash", "M4 4h7v7H4z", "M13 4h7v4h-7z", "M13 10h7v10h-7z", "M4 13h7v7H4z")
    val Tools = icon("tools", "M4 7h9", "M17 7h3", "M15 5v4", "M4 17h3", "M11 17h9", "M9 15v4", "M4 12h12", "M18 10v4")
    val Candles = icon("tv", "M7 3v18", "M5 7h4v8H5z", "M17 4v14", "M15 8h4v6h-4z")
    val Gauge = icon("gauge", "M3.5 17a8.5 8.5 0 1 1 17 0", "M12 17l4.2-5.2", "M6 17h.01", "M18 17h.01")
    val Settings = icon("settings", "M12 15.2a3.2 3.2 0 1 0 0-6.4 3.2 3.2 0 0 0 0 6.4z",
        "M19.4 13.5a7.6 7.6 0 0 0 0-3l2-1.6-2-3.4-2.4 1a7.6 7.6 0 0 0-2.6-1.5L14 2.5h-4l-.4 2.5A7.6 7.6 0 0 0 7 6.5l-2.4-1-2 3.4 2 1.6a7.6 7.6 0 0 0 0 3l-2 1.6 2 3.4 2.4-1a7.6 7.6 0 0 0 2.6 1.5l.4 2.5h4l.4-2.5a7.6 7.6 0 0 0 2.6-1.5l2.4 1 2-3.4z")
    val News = icon("news", "M5 5h11v14H6a1 1 0 0 1-1-1z", "M16 9h3v9a1 1 0 0 1-1 1h-2", "M8 9h5", "M8 12h5", "M8 15h3")
    val Refresh = icon("refresh", "M20 11a8 8 0 1 0-2.3 5.7", "M20 5v6h-6")
    val Back = icon("back", "M15 5l-7 7 7 7")
    val Close = icon("close", "M6 6l12 12", "M18 6L6 18")
    val Search = icon("search", "M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14z", "M20 20l-4-4")
    val Plus = icon("plus", "M12 5v14", "M5 12h14")
    val External = icon("ext", "M14 4h6v6", "M20 4l-9 9", "M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5")
    val Bell = icon("bell", "M6 16V11a6 6 0 1 1 12 0v5l1.5 2h-15z", "M10 20a2 2 0 0 0 4 0")
    val Check = icon("check", "M5 12.5l4.5 4.5L19 7.5")
    val Calendar = icon("cal", "M4 6h16v14H4z", "M4 10h16", "M8 3v5", "M16 3v5")
    val Eye = icon("eye", "M2.5 12s3.5-6.5 9.5-6.5S21.5 12 21.5 12s-3.5 6.5-9.5 6.5S2.5 12 2.5 12z", "M12 14.6a2.6 2.6 0 1 0 0-5.2 2.6 2.6 0 0 0 0 5.2z")
    val Expand = icon("expand", "M4 9V4h5", "M20 9V4h-5", "M4 15v5h5", "M20 15v5h-5")
}
