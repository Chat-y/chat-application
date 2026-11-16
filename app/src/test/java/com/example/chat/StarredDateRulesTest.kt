package com.example.chat

import com.example.chat.ui.chats.starred.StarredDateRules
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class StarredDateRulesTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 11, 15)
    @Test fun extremeInstantsUseUnavailableInsteadOfThrowing() {
        for (instant in listOf(Instant.MIN, Instant.MAX)) {
            assertEquals(null, StarredDateRules.localDayOrNull(instant, zone))
            assertEquals("unavailable", StarredDateRules.label(instant, zone, today, "unavailable", { error("must not format") }, { error("must not format") }))
        }
    }
    @Test fun sameLocalDayUsesTimeAndOlderDateUsesDate() {
        assertEquals("time", StarredDateRules.label(Instant.parse("2026-11-15T10:00:00Z"), zone, today, "unavailable", { "time" }, { "date" }))
        assertEquals("date", StarredDateRules.label(Instant.parse("2026-11-14T10:00:00Z"), zone, today, "unavailable", { "time" }, { "date" }))
    }
    @Test fun formatterFailureIsOnlyAFailedLabel() {
        assertEquals("unavailable", StarredDateRules.label(Instant.EPOCH, zone, today, "unavailable", { error("bad formatter") }, { error("bad formatter") }))
    }
}
