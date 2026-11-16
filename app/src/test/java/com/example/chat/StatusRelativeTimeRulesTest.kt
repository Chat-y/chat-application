package com.example.chat

import com.example.chat.ui.updates.StatusRelativeTimeRules
import com.example.chat.ui.updates.StatusRelativeTimeRules.Bucket
import com.example.chat.ui.updates.StatusRelativeTimeRules.Unit
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class StatusRelativeTimeRulesTest {
    private val now = Instant.parse("2026-11-15T12:00:00Z")
    @Test fun futureAndSubminuteDatesAreJustNow() {
        assertEquals(Bucket(Unit.JUST_NOW), StatusRelativeTimeRules.bucket(now.plusSeconds(1), now))
        assertEquals(Bucket(Unit.JUST_NOW), StatusRelativeTimeRules.bucket(now.minusSeconds(59), now))
    }
    @Test fun minuteBoundaryIsExact() {
        assertEquals(Bucket(Unit.MINUTES, 1), StatusRelativeTimeRules.bucket(now.minusSeconds(60), now))
        assertEquals(Bucket(Unit.MINUTES, 59), StatusRelativeTimeRules.bucket(now.minusSeconds(3599), now))
    }
    @Test fun hourBoundaryIsExact() {
        assertEquals(Bucket(Unit.HOURS, 1), StatusRelativeTimeRules.bucket(now.minusSeconds(3600), now))
        assertEquals(Bucket(Unit.HOURS, 23), StatusRelativeTimeRules.bucket(now.minusSeconds(86399), now))
    }
    @Test fun dayBoundaryUsesElapsedDurationRatherThanCalendarDay() {
        assertEquals(Bucket(Unit.DAYS, 1), StatusRelativeTimeRules.bucket(now.minusSeconds(86400), now))
        assertEquals(Bucket(Unit.DAYS, 2), StatusRelativeTimeRules.bucket(now.minusSeconds(172800), now))
    }
    @Test fun expiryWaitHandlesExtremeDatesWithoutMillisecondOverflow() {
        assertEquals(60_000L, StatusRelativeTimeRules.expiryWaitMillis(Instant.MAX, Instant.MIN))
        assertEquals(0L, StatusRelativeTimeRules.expiryWaitMillis(Instant.MIN, Instant.MAX))
    }
    @Test fun expiryWaitNeverRoundsAFutureSubmillisecondInstantToExpired() {
        val now = Instant.parse("2026-11-15T12:00:00Z")
        assertEquals(1L, StatusRelativeTimeRules.expiryWaitMillis(now.plusNanos(1), now))
        assertEquals(0L, StatusRelativeTimeRules.expiryWaitMillis(now, now))
        assertEquals(45_000L, StatusRelativeTimeRules.expiryWaitMillis(now.plusSeconds(45), now))
    }

}
