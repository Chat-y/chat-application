package com.example.chat

import com.example.chat.core.audio.*
import org.junit.Assert.*
import org.junit.Test

/** NOT RUN. Process-local diagnostic policy only; no release APK/device inspection. */
class AudioDiagnosticBufferGapTest {
    private fun event(index: Int, error: String? = null) = AudioPolicyDiagnosticEvent(
        message = "route-$index", useCase = AudioUseCase.communication,
        state = AudioSessionState.receiverCall, route = AudioOutputRoute.receiver,
        error = error, timestampMillis = index.toLong())

    @Test fun ringRetainsNewestTwoHundredAndDoesNotExposeMutableStorage() {
        val buffer = AudioDiagnosticBuffer(true)
        repeat(250) { buffer.record(event(it)) }
        val snapshot = buffer.snapshot()
        assertEquals(200, snapshot.size)
        assertEquals("route-50", snapshot.first().message)
        assertEquals("route-249", snapshot.last().message)
        buffer.record(event(250))
        assertEquals("route-50", snapshot.first().message)
        assertEquals("route-51", buffer.snapshot().first().message)
    }
    @Test fun disabledReleasePolicyKeepsNoEventsOrFailureCounters() {
        val buffer = AudioDiagnosticBuffer(false)
        buffer.record(event(1, "arbitrary native exception details"))
        assertTrue(buffer.snapshot().isEmpty())
        assertEquals(0L, buffer.failureCount())
    }
    @Test fun errorDetailsAreNotRetainedAndCountSurvivesRingEviction() {
        val buffer = AudioDiagnosticBuffer(true, 2)
        buffer.record(event(1, "native failure with private device identifier"), routeFailure = true)
        assertEquals("Audio operation failed", buffer.snapshot().single().error)
        buffer.record(event(2)); buffer.record(event(3))
        assertEquals(2, buffer.snapshot().size)
        assertTrue(buffer.snapshot().none { it.error != null })
        assertEquals(1L, buffer.failureCount())
    }
}
