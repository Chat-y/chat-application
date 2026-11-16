package com.example.chat

import com.example.chat.ui.calls.callLogLocalDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/** NOT RUN. Pure date conversion only, not broadcast/lifecycle/device coverage. */
class CallLogTimezoneGapTest {
    @Test fun sameInstantUsesTheChosenZoneForTheDayBucket() {
        val instant = Instant.parse("2026-11-16T01:00:00Z")
        assertEquals(LocalDate.of(2026, 11, 15), callLogLocalDate(instant, ZoneId.of("America/Los_Angeles")))
        assertEquals(LocalDate.of(2026, 11, 16), callLogLocalDate(instant, ZoneId.of("Asia/Kolkata")))
    }
    @Test fun repeatedDaylightSavingHourStaysInOneDay() {
        val zone = ZoneId.of("America/New_York")
        val first = Instant.parse("2026-11-01T05:30:00Z")
        val second = Instant.parse("2026-11-01T06:30:00Z")
        assertEquals(LocalDate.of(2026, 11, 1), callLogLocalDate(first, zone))
        assertEquals(callLogLocalDate(first, zone), callLogLocalDate(second, zone))
    }
}
