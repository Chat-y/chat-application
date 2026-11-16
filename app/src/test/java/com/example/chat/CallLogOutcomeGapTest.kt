package com.example.chat

import com.example.chat.domain.models.*
import com.example.chat.ui.calls.callLogOutcomeText
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

/** Source fixtures only, NOT RUN. The text seam avoids Android/Compose runtime dependencies. */
class CallLogOutcomeGapTest {
    private val base = CallRecord("id", "me", listOf("me", "peer"), CallType.voice,
        CallDirection.outgoing, Instant.EPOCH, 0)
    private fun text(id: Int): String = when (id) {
        R.string.call_log_short_connected -> "<1s"
        R.string.calls_ui_16 -> "No connection"
        R.string.call_log_connection_unknown -> "Connection not recorded"
        R.string.call_log_declined -> "Declined"
        R.string.call_log_failed -> "Failed"
        R.string.call_log_cancelled -> "Cancelled"
        R.string.call_log_busy -> "Busy"
        R.string.call_log_missed_outcome -> "Missed"
        else -> error("Unexpected resource")
    }
    @Test fun zeroDurationKeepsConnectionEvidence() {
        assertEquals("<1s", callLogOutcomeText(base.copy(wasConnected = true), ::text))
        assertEquals("No connection", callLogOutcomeText(base.copy(wasConnected = false), ::text))
        assertEquals("Connection not recorded", callLogOutcomeText(base, ::text))
    }
    @Test fun terminalReasonsAreNotCollapsedIntoNoConnection() {
        assertEquals("Declined", callLogOutcomeText(base.copy(outcome = CallOutcome.declined), ::text))
        assertEquals("Failed", callLogOutcomeText(base.copy(outcome = CallOutcome.failed), ::text))
        assertEquals("Cancelled", callLogOutcomeText(base.copy(outcome = CallOutcome.cancelled), ::text))
        assertEquals("Busy", callLogOutcomeText(base.copy(outcome = CallOutcome.busy), ::text))
        assertEquals("Failed • <1s", callLogOutcomeText(base.copy(outcome = CallOutcome.failed, wasConnected = true), ::text))
    }
    @Test fun positiveDurationIsKeptAlongsideOutcome() {
        assertTrue(callLogOutcomeText(base.copy(durationSeconds = 65, outcome = CallOutcome.failed), ::text).startsWith("Failed • "))
        assertFalse(callLogOutcomeText(base.copy(durationSeconds = 65), ::text).contains("No connection"))
    }
}
