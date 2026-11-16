package com.example.chat

import com.example.chat.data.api.ApiCallHistoryClient
import com.example.chat.data.services.ApiCallHistoryService
import com.example.chat.domain.models.CallDirection
import com.example.chat.domain.models.CallType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Server call-history entry -> Calls-tab row mapping (research 5.4). */
class ApiCallHistoryServiceTest {

    private fun entry(
        direction: String,
        status: String,
        kind: String = "audio",
        other: String = "user-b",
        startedAt: String? = "2026-11-08T07:30:00Z",
        durationSec: Int = 95,
    ) = ApiCallHistoryClient.CallEntry(
        id = "log-1", callId = "call-1", direction = direction,
        otherUserId = other, otherUsername = "bob", otherDisplayName = "Bob",
        kind = kind, status = status, startedAt = startedAt, durationSec = durationSec,
    )

    @Test
    fun `outgoing answered video call`() {
        val r = ApiCallHistoryService.mapEntry(entry("outgoing", "answered", kind = "video"), "me")!!
        assertEquals(CallDirection.outgoing, r.direction)
        assertEquals(CallType.video, r.type)
        assertEquals("me", r.callerId)
        assertEquals(listOf("me", "user-b"), r.participantIds)
        assertEquals(95L, r.durationSeconds)
        assertEquals("log-1", r.id)
        assertEquals("Bob", r.remoteDisplayName)
        assertEquals("bob", r.remoteUsername)
        assertEquals(1794123000L, r.timestamp.epochSecond)
    }

    @Test
    fun `incoming answered call is incoming`() {
        val r = ApiCallHistoryService.mapEntry(entry("incoming", "answered"), "me")!!
        assertEquals(CallDirection.incoming, r.direction)
        assertEquals("user-b", r.callerId)
    }

    @Test
    fun `incoming missed declined and failed all show as missed`() {
        for (status in listOf("missed", "declined", "failed")) {
            assertEquals(
                status,
                CallDirection.missed,
                ApiCallHistoryService.mapEntry(entry("incoming", status), "me")!!.direction,
            )
        }
    }

    @Test
    fun `blank server names are absent rather than blank call titles`() {
        val r = ApiCallHistoryService.mapEntry(entry("outgoing", "answered").copy(
            otherDisplayName = "  ", otherUsername = "  "), "me")!!
        assertNull(r.remoteDisplayName)
        assertNull(r.remoteUsername)
    }

    @Test
    fun `empty other user drops the row`() {
        assertNull(ApiCallHistoryService.mapEntry(entry("incoming", "answered", other = ""), "me"))
    }

    @Test
    fun `unparseable timestamp keeps the row with a fallback`() {
        val r = ApiCallHistoryService.mapEntry(entry("outgoing", "answered", startedAt = "junk"), "me")!!
        assertTrue(r.timestamp.epochSecond > 0)
    }

    @Test
    fun `negative duration clamps to zero`() {
        assertEquals(0L, ApiCallHistoryService.mapEntry(entry("outgoing", "answered", durationSec = -5), "me")!!.durationSeconds)
    }
    @Test fun `terminal outcome survives direction mapping`() {
        val cases = mapOf("declined" to com.example.chat.domain.models.CallOutcome.declined,
            "failed" to com.example.chat.domain.models.CallOutcome.failed,
            "cancelled" to com.example.chat.domain.models.CallOutcome.cancelled,
            "busy" to com.example.chat.domain.models.CallOutcome.busy)
        cases.forEach { (status, outcome) ->
            val row = ApiCallHistoryService.mapEntry(entry("outgoing", status, durationSec = 0), "me")!!
            assertEquals(CallDirection.outgoing, row.direction)
            assertEquals(outcome, row.outcome)
        }
    }
    @Test fun `answered zero duration is connected but failed zero remains unknown`() {
        assertEquals(true, ApiCallHistoryService.mapEntry(entry("outgoing", "answered", durationSec = 0), "me")!!.wasConnected)
        assertNull(ApiCallHistoryService.mapEntry(entry("outgoing", "failed", durationSec = 0), "me")!!.wasConnected)
        assertNull(ApiCallHistoryService.mapEntry(entry("outgoing", "future_status", durationSec = 0), "me")!!.outcome)
    }
}
