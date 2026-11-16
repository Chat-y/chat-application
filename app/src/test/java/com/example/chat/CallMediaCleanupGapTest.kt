package com.example.chat

import com.example.chat.data.services.CallMediaCleanup
import com.example.chat.data.services.StreamTrackCleanup
import org.junit.Assert.*
import org.junit.Test

/** NOT RUN. Independent action ordering only; no actual camera/microphone/peer resource proof. */
class CallMediaCleanupGapTest {
    @Test fun everyRemainingReleaseRunsAfterAnySingleOrdinaryFailure() {
        repeat(18) { failing ->
            val cleanup = CallMediaCleanup()
            val visited = mutableListOf<Int>()
            val original = IllegalStateException("synthetic teardown failure")
            repeat(18) { index -> cleanup.attempt { visited += index; if (index == failing) throw original } }
            assertEquals((0 until 18).toList(), visited)
            assertSame(original, runCatching { cleanup.finish() }.exceptionOrNull())
        }
    }
    @Test fun firstFailureIsRetainedAndLaterFailuresAreSuppressed() {
        val cleanup = CallMediaCleanup()
        val first = SecurityException("first")
        val later = IllegalStateException("later")
        cleanup.attempt { throw first }
        cleanup.attempt { throw later }
        val error = runCatching { cleanup.finish() }.exceptionOrNull()
        assertSame(first, error)
        assertEquals(listOf(later), error!!.suppressed.toList())
    }
    @Test fun streamFailureDoesNotSkipDetachedTracksButNeverDoubleDisposesOwnedTrack() {
        var ownedCalls = 0
        var detachedCalls = 0
        val first = IllegalStateException("stream failure")
        val error = runCatching {
            StreamTrackCleanup.dispose({ throw first }, listOf(
                true to { ownedCalls++; Unit }, false to { detachedCalls++; Unit },
            ))
        }.exceptionOrNull()
        assertSame(first, error)
        assertEquals(0, ownedCalls)
        assertEquals(1, detachedCalls)
    }
    @Test fun interruptionStillAttemptsRestorationAndRestoresInterruptFlag() {
        Thread.interrupted()
        try {
            val cleanup = CallMediaCleanup()
            var lastRan = false
            cleanup.attempt { throw InterruptedException("synthetic") }
            cleanup.attempt { lastRan = true }
            assertTrue(lastRan)
            assertTrue(runCatching { cleanup.finish() }.exceptionOrNull() is InterruptedException)
            assertTrue(Thread.currentThread().isInterrupted)
        } finally { Thread.interrupted() }
    }
}
