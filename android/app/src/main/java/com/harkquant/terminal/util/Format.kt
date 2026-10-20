package com.harkquant.terminal.util

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/** Every time in the app is shown in Pakistan time (Asia/Karachi, UTC+05:00, no DST). */
object Pkt {
    val zone: ZoneId = ZoneId.of("Asia/Karachi")
    const val LABEL = "PKT"

    private val hm = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val hms = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US)
    private val dayHm = DateTimeFormatter.ofPattern("EEE dd · HH:mm", Locale.US)
    private val dayMonth = DateTimeFormatter.ofPattern("dd MMM", Locale.US)
    private val dayHeader = DateTimeFormatter.ofPattern("EEEE, dd MMM", Locale.US)
    private val full = DateTimeFormatter.ofPattern("EEE dd MMM yyyy · HH:mm", Locale.US)
    private val masthead = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.US)

    fun at(epochSec: Double): ZonedDateTime = Instant.ofEpochMilli((epochSec * 1000).roundToLong()).atZone(zone)
    fun now(nowMs: Long = System.currentTimeMillis()): ZonedDateTime = Instant.ofEpochMilli(nowMs).atZone(zone)

    fun hm(epochSec: Double?): String = if (epochSec == null || epochSec.isNaN()) "-" else hm.format(at(epochSec))
    fun hms(epochSec: Double?): String = if (epochSec == null || epochSec.isNaN()) "-" else hms.format(at(epochSec))
    fun dayHm(epochSec: Double?): String = if (epochSec == null || epochSec.isNaN()) "-" else dayHm.format(at(epochSec))
    fun dayMonth(epochSec: Double): String = dayMonth.format(at(epochSec))
    fun dayHeader(epochSec: Double): String = dayHeader.format(at(epochSec))
    fun full(epochSec: Double): String = full.format(at(epochSec)) + " " + LABEL
    fun clock(nowMs: Long = System.currentTimeMillis()): String = hms.format(now(nowMs))
    fun masthead(nowMs: Long = System.currentTimeMillis()): String = masthead.format(now(nowMs))
    fun dayKey(epochSec: Double): Long = at(epochSec).toLocalDate().toEpochDay()

    /** Chart axis label: time of day for intraday frames, date for daily+. */
    fun axis(epochSec: Long, daily: Boolean): String = if (daily) dayMonth(epochSec.toDouble()) else hm(epochSec.toDouble())

    /** UTC hour-of-day converted to a PKT "HH:mm" (sessions are published in UTC hours). */
    fun utcHourToPkt(hourUtc: Int): String {
        val h = ((hourUtc + 5) % 24 + 24) % 24
        return String.format(Locale.US, "%02d:00", h)
    }

    fun greeting(nowMs: Long = System.currentTimeMillis()): String = when (now(nowMs).hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Good night"
    }
}

object Fmt {
    fun isNum(v: Double?): Boolean = v != null && !v.isNaN() && !v.isInfinite()

    /** Same rules as the web app: >=1000 grouped 2dp, >=10 2dp, >=1 4dp, else 4 significant. */
    fun price(v: Double?): String {
        if (!isNum(v)) return "-"
        val a = abs(v!!)
        return when {
            a >= 1000 -> String.format(Locale.US, "%,.2f", v)
            a >= 10 -> String.format(Locale.US, "%.2f", v)
            a >= 1 -> String.format(Locale.US, "%.4f", v)
            a == 0.0 -> "0"
            else -> java.math.BigDecimal(v).round(java.math.MathContext(4)).stripTrailingZeros().toPlainString()
        }
    }

    /** Price with a fixed number of decimals chosen from a reference magnitude (chart axes). */
    fun decimalsFor(ref: Double): Int {
        val a = abs(ref)
        return when { a >= 1000 -> 2; a >= 10 -> 2; a >= 1 -> 4; else -> 5 }
    }

    fun fixed(v: Double?, d: Int): String = if (!isNum(v)) "-" else String.format(Locale.US, "%,.${d}f", v)

    fun big(v: Double?): String {
        if (!isNum(v)) return "-"
        val a = abs(v!!)
        val s = if (v < 0) "-" else ""
        return when {
            a >= 1e9 -> s + String.format(Locale.US, "%.2fB", a / 1e9)
            a >= 1e6 -> s + String.format(Locale.US, "%.2fM", a / 1e6)
            a >= 1e3 -> s + String.format(Locale.US, "%.1fK", a / 1e3)
            a < 10 -> s + String.format(Locale.US, "%.2f", a)
            else -> s + String.format(Locale.US, "%.0f", a)
        }
    }

    fun signed(v: Double?, d: Int = 0): String {
        if (!isNum(v)) return "-"
        val r = String.format(Locale.US, "%.${d}f", v)
        val zero = r.trimStart('-').all { it == '0' || it == '.' }
        return when { zero -> r.trimStart('-'); v!! > 0 -> "+$r"; else -> r }
    }

    fun pct(v: Double?, d: Int = 1): String = if (!isNum(v)) "-" else String.format(Locale.US, "%.${d}f%%", v)

    /** Countdown like the web app: "1d 21:43:05", "11:13:32", "04:09", "NOW". */
    fun countdown(sec: Double?): String {
        if (!isNum(sec)) return "-"
        if (sec!! <= 0) return "NOW"
        val s = floor(sec).toLong()
        val d = s / 86400
        val h = (s % 86400) / 3600
        val m = (s % 3600) / 60
        val r = s % 60
        val sb = StringBuilder()
        if (d > 0) sb.append(d).append("d ")
        if (d > 0 || h > 0) sb.append(String.format(Locale.US, "%02d:", h))
        sb.append(String.format(Locale.US, "%02d:%02d", m, r))
        return sb.toString()
    }

    fun ago(epochSec: Double, nowMs: Long = System.currentTimeMillis()): String {
        val s = maxOf(0L, (nowMs / 1000.0 - epochSec).roundToLong())
        return when {
            s < 60 -> "${s}s"
            s < 3600 -> "${s / 60}m"
            s < 86400 -> "${s / 3600}h"
            else -> "${s / 86400}d"
        }
    }

    /** -1 / 0 / +1 used to colour numbers. */
    fun sign(v: Double?): Int = if (!isNum(v) || v == 0.0) 0 else if (v!! > 0) 1 else -1

    fun recommendation(s: String): String = s.replace('_', ' ')
}

/** Position sizing, mirrored from the web calculator so it works offline; the server endpoint verifies it. */
object RiskMath {
    data class Result(val riskMoney: Double, val lotsExact: Double, val lotsRounded: Double, val long: Boolean, val stopDist: Double, val target: Double, val reward: Double)

    fun size(balance: Double, risk: Double, riskIsPct: Boolean, entry: Double, stop: Double, contract: Double, rr: Double): Result? {
        if (!(entry > 0 && stop > 0) || entry == stop) return null
        val money = if (riskIsPct) balance * risk / 100.0 else risk
        val dist = abs(entry - stop)
        val cs = if (contract > 0) contract else 1.0
        val lots = money / (dist * cs)
        val long = entry > stop
        val target = entry + (if (long) 1 else -1) * dist * rr
        return Result(money, lots, floor(lots * 100) / 100.0, long, dist, target, money * rr)
    }

    val contractDefaults = mapOf(
        "XAUUSD" to 100.0, "XAGUSD" to 5000.0, "EURUSD" to 100000.0, "GBPUSD" to 100000.0, "USDJPY" to 100000.0,
        "AUDUSD" to 100000.0, "USDCAD" to 100000.0, "USDCHF" to 100000.0, "USOIL" to 1000.0,
    )
}
