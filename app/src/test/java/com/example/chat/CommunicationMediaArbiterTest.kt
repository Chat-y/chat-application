package com.example.chat

import com.example.chat.core.audio.CommunicationMediaOwner
import com.example.chat.core.audio.DefaultCommunicationMediaArbiter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class CommunicationMediaArbiterTest {
    @Test fun callDrainsStartThenSuspendsAndRejectsResume() = runBlocking {
        val arbiter = DefaultCommunicationMediaArbiter()
        val entered = CompletableDeferred<Unit>()
        val finishStart = CompletableDeferred<Unit>()
        var capturing = false
        val media = arbiter.acquireVoiceNoteCapture(CommunicationMediaOwner()) { capturing = false }!!
        val starting = async { media.runNative { entered.complete(Unit); finishStart.await(); capturing = true } }
        entered.await()
        val calling = async { arbiter.acquireCall(CommunicationMediaOwner()) }
        yield()
        assertFalse(calling.isCompleted)
        finishStart.complete(Unit)
        assertTrue(starting.await())
        val call = calling.await()
        assertTrue(call.mediaSuspended)
        assertFalse(capturing)
        assertFalse(media.runNative { fail("Native resume must not run") })
        call.release(true)
        assertFalse(capturing) // Releasing a call never starts capture.
        assertTrue(media.runNative { capturing = true })
    }

    @Test fun failedSuspensionRetainsBlockAndStaleReleaseCannotUnlockReplacement() = runBlocking {
        val arbiter = DefaultCommunicationMediaArbiter()
        var failSuspension = true
        val media = arbiter.acquirePlayback(CommunicationMediaOwner()) { check(!failSuspension) }!!
        val first = arbiter.acquireCall(CommunicationMediaOwner())
        assertFalse(first.mediaSuspended)
        first.release(false)
        assertNull(arbiter.acquirePlayback(CommunicationMediaOwner()) {})
        // Previous release(true) abandonment was insufficient proof of native suspension.
        first.release(true)
        assertNull(arbiter.acquirePlayback(CommunicationMediaOwner()) {})
        assertFalse(first.retryMediaSuspension())
        failSuspension = false
        assertTrue(first.retryMediaSuspension())
        first.release(true)
        media.release()
        val next = arbiter.acquireCall(CommunicationMediaOwner())
        first.release(true)
        assertNull(arbiter.acquireVoiceNoteCapture(CommunicationMediaOwner()) {})
        next.release(true)
        assertNotNull(arbiter.acquirePlayback(CommunicationMediaOwner()) {})
    }
}
