@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.harkquant.terminal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.harkquant.terminal.data.Conn
import com.harkquant.terminal.data.PriceSnap
import com.harkquant.terminal.data.Ticker
import com.harkquant.terminal.ui.theme.Form
import com.harkquant.terminal.ui.theme.Hq
import com.harkquant.terminal.ui.theme.monoStyle
import com.harkquant.terminal.util.Fmt
import com.harkquant.terminal.util.Pkt
import kotlinx.coroutines.delay

/** Live 1-second clock, shared by countdowns. */
@Composable
fun rememberNow(periodMs: Long = 1000): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) { while (true) { delay(periodMs); now = System.currentTimeMillis() } }
    return now
}

@Composable
fun TickerStrip(ticker: Ticker?, prices: Map<String, PriceSnap>, conn: Conn, modifier: Modifier = Modifier, nowMs: Long = System.currentTimeMillis()) {
    val s = Hq.s
    val p = s.p
    val speed = (ticker?.settings?.speed ?: 38).coerceIn(10, 200)
    val bg = when (s.form) {
        Form.EDITORIAL -> p.bg
        Form.GLASS -> p.panel
        else -> p.track
    }
    Row(
        modifier.fillMaxWidth().background(bg).padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val liveColor = when (conn) { Conn.LIVE -> p.dn; Conn.POLLING -> p.warn; else -> p.tx2 }
        Box(Modifier.padding(start = s.screenPadding, end = 8.dp)) {
            Tag(if (conn == Conn.LIVE) "LIVE" else if (conn == Conn.POLLING) "POLL" else "OFF", liveColor, filled = conn == Conn.LIVE)
        }
        Row(
            Modifier.weight(1f).basicMarquee(iterations = Int.MAX_VALUE, velocity = speed.dp, initialDelayMillis = 800, repeatDelayMillis = 0),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            prices.values.sortedBy { it.symbol }.forEach { ps ->
                Text(ps.symbol, style = monoStyle(11, FontWeight.Bold), color = p.tx2)
                Spacer(Modifier.width(5.dp))
                Text(Fmt.price(ps.last), style = monoStyle(11, FontWeight.SemiBold), color = p.tx)
                Spacer(Modifier.width(4.dp))
                Text(Fmt.signed(ps.changePct, 2) + "%", style = monoStyle(11), color = s.signColor(Fmt.sign(ps.changePct)))
                Sep()
            }
            ticker?.headlines?.forEach { h ->
                ImpactTag(h.impact)
                Spacer(Modifier.width(6.dp))
                Text(Pkt.hm(h.ts), style = monoStyle(11), color = p.tx2)
                Spacer(Modifier.width(6.dp))
                Text(h.text, style = monoStyle(11), color = p.tx, maxLines = 1)
                if (!h.source.isNullOrBlank()) { Spacer(Modifier.width(6.dp)); Text(h.source, style = monoStyle(10), color = p.tx2) }
                Sep()
            }
            ticker?.events?.forEach { e ->
                ImpactTag(e.impact)
                Spacer(Modifier.width(6.dp))
                Text(Pkt.dayHm(e.ts), style = monoStyle(11), color = p.tx2)
                Spacer(Modifier.width(6.dp))
                Text(e.text, style = monoStyle(11, FontWeight.SemiBold), color = p.tx, maxLines = 1)
                Spacer(Modifier.width(6.dp))
                Text("in " + Fmt.countdown(e.ts - nowMs / 1000.0), style = monoStyle(11), color = p.warn)
                Sep()
            }
            if (ticker == null && prices.isEmpty()) Text("Connecting to HarkQuant feed…", style = monoStyle(11), color = p.tx2)
        }
    }
}

@Composable
private fun Sep() {
    val p = Hq.c
    Spacer(Modifier.width(14.dp))
    Box(Modifier.width(4.dp).padding(vertical = 0.dp).clip(androidx.compose.foundation.shape.CircleShape).background(p.line).padding(2.dp))
    Spacer(Modifier.width(14.dp))
}
