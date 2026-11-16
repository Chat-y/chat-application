package com.example.chat

import com.example.chat.domain.models.*
import com.example.chat.ui.calls.callLogDayGroups
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/** NOT RUN. Grouping only, no sticky-header pixels/accessibility proof. */
class CallLogDayGroupsGapTest {
    private fun record(id: String, time: String) = CallRecord(id, "self", listOf("self", "other"),
        CallType.voice, CallDirection.outgoing, Instant.parse(time), 0)
    @Test fun unsortedRecordsProduceUniqueDayHeadersAndNewestFirstRows() {
        val old = record("old", "2026-11-14T01:00:00Z")
        val early = record("early", "2026-11-16T01:00:00Z")
        val late = record("late", "2026-11-16T03:00:00Z")
        val groups = callLogDayGroups(listOf(early, old, late), ZoneId.of("UTC"))
        assertEquals(listOf("2026-11-16", "2026-11-14"), groups.map { it.first.toString() })
        assertEquals(listOf("late", "early"), groups.first().second.map { it.id })
    }
    @Test fun sameInstantFollowsRenderZoneAndEmptyInputHasNoHeader() {
        val call = record("one", "2026-11-16T01:00:00Z")
        assertEquals("2026-11-15", callLogDayGroups(listOf(call), ZoneId.of("America/Los_Angeles")).single().first.toString())
        assertEquals(emptyList<Pair<java.time.LocalDate, List<CallRecord>>>(), callLogDayGroups(emptyList(), ZoneId.of("UTC")))
    }
}
