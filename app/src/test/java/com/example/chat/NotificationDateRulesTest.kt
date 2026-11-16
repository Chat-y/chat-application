package com.example.chat

import com.example.chat.ui.notifications.NotificationDateRules
import com.example.chat.ui.notifications.NotificationDateRules.RelativeDay
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationDateRulesTest {
    private fun clock(instant: String, zone: String) = Clock.fixed(Instant.parse(instant), ZoneId.of(zone))

    @Test fun todayYesterdayAndOlderUseCalendarDays() {
        val now = clock("2026-11-15T12:00:00Z", "Asia/Kolkata")
        assertEquals(RelativeDay.TODAY, NotificationDateRules.relativeDay(LocalDate.of(2026, 11, 15), now))
        assertEquals(RelativeDay.YESTERDAY, NotificationDateRules.relativeDay(LocalDate.of(2026, 11, 14), now))
        assertEquals(RelativeDay.ABSOLUTE, NotificationDateRules.relativeDay(LocalDate.of(2026, 11, 13), now))
    }
    @Test fun midnightChangesTheRelativeDayWithoutChangingTheEvent() {
        val day = LocalDate.of(2026, 11, 15)
        assertEquals(RelativeDay.TODAY, NotificationDateRules.relativeDay(day, clock("2026-11-15T18:29:59Z", "Asia/Kolkata")))
        assertEquals(RelativeDay.YESTERDAY, NotificationDateRules.relativeDay(day, clock("2026-11-15T18:30:00Z", "Asia/Kolkata")))
    }
    @Test fun sameUtcInstantCanBeDifferentLocalDates() {
        val event = Instant.parse("2026-11-15T01:00:00Z")
        assertEquals(LocalDate.of(2026, 11, 15), NotificationDateRules.localDay(event, ZoneId.of("Asia/Kolkata")))
        assertEquals(LocalDate.of(2026, 11, 14), NotificationDateRules.localDay(event, ZoneId.of("America/Los_Angeles")))
    }
    @Test fun springForwardYesterdayIsNotAnElapsedTwentyFourHourWindow() {
        val now = clock("2026-03-09T04:30:00Z", "America/New_York")
        assertEquals(RelativeDay.YESTERDAY, NotificationDateRules.relativeDay(LocalDate.of(2026, 3, 8), now))
    }
    @Test fun fallBackYesterdayUsesLocalDateDespiteTwentyFiveHourDay() {
        val now = clock("2026-11-02T05:30:00Z", "America/New_York")
        assertEquals(RelativeDay.YESTERDAY, NotificationDateRules.relativeDay(LocalDate.of(2026, 11, 1), now))
    }
    @Test fun corruptExtremeTimestampHasNoLocalDay() {
        assertEquals(null, NotificationDateRules.localDayOrNull(Instant.MAX, ZoneId.of("UTC")))
        assertEquals(null, NotificationDateRules.localDayOrNull(Instant.MIN, ZoneId.of("UTC")))
    }
    @Test fun safeDayRetainsOrdinaryLocalDate() {
        assertEquals(LocalDate.of(2026, 11, 15), NotificationDateRules.localDayOrNull(Instant.parse("2026-11-15T12:00:00Z"), ZoneId.of("UTC")))
    }
    @Test fun futureEventsKeepTheirAbsoluteDate() {
        val now = clock("2026-11-15T12:00:00Z", "Asia/Kolkata")
        assertEquals(RelativeDay.ABSOLUTE, NotificationDateRules.relativeDay(LocalDate.of(2026, 11, 16), now))
    }
}
