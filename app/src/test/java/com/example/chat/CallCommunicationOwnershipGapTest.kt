package com.example.chat.core.audio

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** NOT RUN. Call-side contracts only; not proof that every media producer uses the arbiter. */
class CallCommunicationOwnershipGapTest {
    private class Lease(var suspended: Boolean) : CommunicationCallReservation {
        override val mediaSuspended get() = suspended
        val releases = mutableListOf<Boolean>()
        override suspend fun retryMediaSuspension(): Boolean { suspended = true; return true }
        override suspend fun release(cleanupSucceeded: Boolean) { releases += cleanupSucceeded }
    }
    private class Arbiter(val lease: Lease) : CommunicationMediaArbiter {
        override suspend fun acquireCall(owner: CommunicationMediaOwner) = lease
        override suspend fun acquireVoiceNoteCapture(owner: CommunicationMediaOwner, suspendForCall: suspend () -> Unit): CommunicationMediaReservation? = null
        override suspend fun acquirePlayback(owner: CommunicationMediaOwner, suspendForCall: suspend () -> Unit): CommunicationMediaReservation? = null
    }
    @Test fun failedSuspensionRetainsHandleUntilExplicitRetry() = runBlocking {
        val lease = Lease(false)
        val recovery = CallAudioRecoveryStore()
        val ownership = CallCommunicationOwnership(Arbiter(lease), recovery)
        try { ownership.acquire(); fail() } catch (_: IllegalStateException) { }
        ownership.finishCleanup(true)
        assertEquals(listOf(false), lease.releases)
        assertEquals(1, recovery.status.value.pending)
        assertTrue(recovery.retry())
        assertEquals(listOf(false, true), lease.releases)
    }
    @Test fun emptySecondDisposeNeverClearsNativeUncertainty() = runBlocking {
        val lease = Lease(true)
        val recovery = CallAudioRecoveryStore()
        val ownership = CallCommunicationOwnership(Arbiter(lease), recovery)
        ownership.acquire()
        ownership.markCleanupUncertain()
        ownership.finishCleanup(true)
        ownership.finishCleanup(true)
        assertEquals(listOf(false, false), lease.releases)
        assertFalse(recovery.retry())
        assertTrue(recovery.status.value.nativeCleanupUncertain)
    }
    @Test fun cleanupAttemptsLaterStagesAndPreservesFirstFailure() = runBlocking {
        val first = IllegalStateException("fixture")
        val later = IllegalArgumentException("fixture")
        var reached = false
        var confirmed = true
        try {
            com.example.chat.data.services.finishCallAudioCleanup(listOf(
                { throw first }, { reached = true; throw later },
            )) { confirmed = it }
            fail()
        } catch (caught: IllegalStateException) {
            assertSame(first, caught)
            assertTrue(caught.suppressed.contains(later))
        }
        assertTrue(reached)
        assertFalse(confirmed)
    }
}
