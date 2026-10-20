package com.harkquant.terminal

import com.harkquant.terminal.util.Fmt
import com.harkquant.terminal.util.Pkt
import com.harkquant.terminal.util.RiskMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatTest {
    // 2026-10-20 12:15:05 UTC == 17:15:05 PKT (UTC+05:00)
    private val ts = 1792498505.0

    @Test fun pktClockIsUtcPlusFive() {
        assertEquals("17:15", Pkt.hm(ts))
        assertEquals("17:15:05", Pkt.hms(ts))
        assertEquals("Tue 20 · 17:15", Pkt.dayHm(ts))
        assertEquals("Tue 20 Oct 2026 · 17:15 PKT", Pkt.full(ts))
        assertEquals("20 Oct", Pkt.dayMonth(ts))
    }

    @Test fun pktIsIndependentOfDeviceZone() {
        val old = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/New_York"))
            assertEquals("17:15", Pkt.hm(ts))
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Tokyo"))
            assertEquals("17:15", Pkt.hm(ts))
        } finally { java.util.TimeZone.setDefault(old) }
    }

    @Test fun midnightRollsDateInPkt() {
        // 2026-10-20 19:30 UTC -> 2026-10-21 00:30 PKT
        val t = 1792524600.0
        assertEquals("Wed 21 · 00:30", Pkt.dayHm(t))
    }

    @Test fun utcSessionHoursToPkt() {
        assertEquals("05:00", Pkt.utcHourToPkt(0))
        assertEquals("12:00", Pkt.utcHourToPkt(7))
        assertEquals("02:00", Pkt.utcHourToPkt(21))
        assertEquals("04:00", Pkt.utcHourToPkt(23))
    }

    @Test fun greetingByPktHour() {
        assertEquals("Good evening", Pkt.greeting((ts * 1000).toLong()))
        assertEquals("Good morning", Pkt.greeting(((ts - 8 * 3600) * 1000).toLong()))
    }

    @Test fun axisLabels() {
        assertEquals("17:15", Pkt.axis(1792498505, false))
        assertEquals("20 Oct", Pkt.axis(1792498505, true))
    }

    @Test fun countdownLikeWeb() {
        assertEquals("NOW", Fmt.countdown(0.0))
        assertEquals("00:59", Fmt.countdown(59.4))
        assertEquals("04:09", Fmt.countdown(249.0))
        assertEquals("11:13:32", Fmt.countdown(40412.0))
        assertEquals("1d 21:43:05", Fmt.countdown(86400.0 + 21 * 3600 + 43 * 60 + 5))
        assertEquals("-", Fmt.countdown(null))
    }

    @Test fun priceFormatting() {
        assertEquals("82,128.00", Fmt.price(82128.0))
        assertEquals("4,175.18", Fmt.price(4175.18))
        assertEquals("51.37", Fmt.price(51.37))
        assertEquals("1.1641", Fmt.price(1.16412))
        assertEquals("0.1234", Fmt.price(0.12341))
        assertEquals("-", Fmt.price(Double.NaN))
        assertEquals("-", Fmt.price(null))
    }

    @Test fun signedBigPct() {
        assertEquals("+23.1", Fmt.signed(23.1, 1))
        assertEquals("-21", Fmt.signed(-21.0))
        assertEquals("0", Fmt.signed(-0.2))
        assertEquals("0.0", Fmt.signed(0.0, 1))
        assertEquals("2.98M", Fmt.big(2980830.0))
        assertEquals("-1.2K", Fmt.big(-1180.0))
        assertEquals("1.79B", Fmt.big(1.79e9))
        assertEquals("8.78", Fmt.big(8.78))
        assertEquals("57.8%", Fmt.pct(57.8))
    }

    @Test fun agoUnits() {
        val now = ((ts + 125) * 1000).toLong()
        assertEquals("2m", Fmt.ago(ts, now))
        assertEquals("45s", Fmt.ago(ts, ((ts + 45) * 1000).toLong()))
        assertEquals("3h", Fmt.ago(ts, ((ts + 3 * 3600 + 5) * 1000).toLong()))
    }

    @Test fun positionSizingMatchesServerExample() {
        // server: balance 10000, risk 1%, entry 4000, stop 3990, contract 100 -> 0.1 lots, long, risk 100
        val r = RiskMath.size(10000.0, 1.0, true, 4000.0, 3990.0, 100.0, 2.0)!!
        assertEquals(100.0, r.riskMoney, 1e-9)
        assertEquals(0.1, r.lotsExact, 1e-9)
        assertEquals(0.1, r.lotsRounded, 1e-9)
        assertTrue(r.long)
        assertEquals(4020.0, r.target, 1e-9)
        val short = RiskMath.size(1000.0, 25.0, false, 82000.0, 82500.0, 1.0, 3.0)!!
        assertEquals(0.05, short.lotsExact, 1e-9)
        assertEquals(80500.0, short.target, 1e-9)
        assertNull(RiskMath.size(1000.0, 1.0, true, 10.0, 10.0, 1.0, 2.0))
    }
}
