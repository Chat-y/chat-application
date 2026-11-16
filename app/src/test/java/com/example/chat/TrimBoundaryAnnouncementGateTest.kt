package com.example.chat

import com.example.chat.ui.chats.detail.TrimBoundaryAnnouncementGate
import org.junit.Assert.*
import org.junit.Test

/** Source-only cases, NOT RUN. */
class TrimBoundaryAnnouncementGateTest {
    @Test fun subsecondMovementDoesNotSpeakEvenAfterBudget() {
        val gate = TrimBoundaryAnnouncementGate(0, 5000)
        assertFalse(gate.shouldAnnounce(999, 5999, 10000))
    }
    @Test fun firstCrossingSpeaksImmediately() { assertTrue(TrimBoundaryAnnouncementGate(0, 5000).shouldAnnounce(1000, 5000, 0)) }
    @Test fun rapidCrossingsAreThrottled() {
        val gate = TrimBoundaryAnnouncementGate(0, 5000)
        assertTrue(gate.shouldAnnounce(1000, 5000, 10000))
        assertFalse(gate.shouldAnnounce(2000, 5000, 10999))
        assertTrue(gate.shouldAnnounce(3000, 5000, 11000))
    }
    @Test fun noDeferredFractionalSpeechForSuppressedCrossing() {
        val gate = TrimBoundaryAnnouncementGate(0, 5000)
        assertTrue(gate.shouldAnnounce(1000, 5000, 0))
        assertFalse(gate.shouldAnnounce(2000, 5000, 500))
        assertFalse(gate.shouldAnnounce(2500, 5000, 1500))
    }
    @Test fun endCrossingAndBackwardCrossingCount() {
        val gate = TrimBoundaryAnnouncementGate(1000, 5000)
        assertTrue(gate.shouldAnnounce(1000, 6000, 0))
        assertTrue(gate.shouldAnnounce(999, 6000, 1000))
    }
    @Test fun bothThumbsProduceOneDecision() { assertTrue(TrimBoundaryAnnouncementGate(0, 5000).shouldAnnounce(1000, 6000, 0)) }
    @Test fun clockResetDoesNotStallSpeech() {
        val gate = TrimBoundaryAnnouncementGate(0, 5000)
        assertTrue(gate.shouldAnnounce(1000, 5000, 10000))
        assertTrue(gate.shouldAnnounce(2000, 5000, 0))
    }
    @Test fun newSourceGateHasIndependentBudget() {
        assertTrue(TrimBoundaryAnnouncementGate(0, 5000).shouldAnnounce(1000, 5000, 0))
        assertTrue(TrimBoundaryAnnouncementGate(0, 5000).shouldAnnounce(1000, 5000, 0))
    }
}
