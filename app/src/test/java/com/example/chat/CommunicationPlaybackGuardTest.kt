package com.example.chat

import com.example.chat.core.audio.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.TimeoutCancellationException
import org.junit.Assert.*
import org.junit.Test

class CommunicationPlaybackGuardTest {
    @Test fun guardedReviewSuspendsBlocksResumeAndDoesNotAutoResume() = runBlocking {
        val arbiter = DefaultCommunicationMediaArbiter()
        var playing = false
        var current = true
        val guard = CommunicationPlaybackGuard(arbiter,
            pauseNative = { playing = false }, disposeNative = { playing = false },
            isOwnerCurrent = { current })
        assertTrue(guard.runNative { playing = true })
        val call = arbiter.acquireCall(CommunicationMediaOwner())
        assertTrue(call.mediaSuspended); assertFalse(playing)
        assertFalse(guard.runNative { fail("Call guard bypass") })
        call.release(true); assertFalse(playing)
        assertTrue(guard.runNative { playing = true })
        guard.pause(); current = false
        assertFalse(guard.runNative { fail("Account guard bypass") })
        guard.dispose()
    }
    @Test fun failedLifecycleDisposeCanRecoverThroughRetainedCallHandle() = runBlocking {
        val arbiter = DefaultCommunicationMediaArbiter()
        var failDispose = true
        var disposed = false
        val guard = CommunicationPlaybackGuard(arbiter, pauseNative = {}, disposeNative = {
            check(!failDispose); disposed = true
        })
        guard.runNative {}
        assertTrue(runCatching { guard.dispose() }.isFailure)
        assertFalse(guard.runNative { fail("Closing player restarted") })
        val call = arbiter.acquireCall(CommunicationMediaOwner())
        assertFalse(call.mediaSuspended); call.release(true)
        assertNull(arbiter.acquirePlayback(CommunicationMediaOwner()) {})
        failDispose = false
        assertTrue(call.retryMediaSuspension()); assertTrue(disposed)
        call.release(true); guard.dispose()
    }
    @Test fun timeoutRetainsUncertainNativeOwnerUntilSerializedCleanupEvenDuringCall() = runBlocking {
        val arbiter = DefaultCommunicationMediaArbiter()
        var reset = false
        val guard = CommunicationPlaybackGuard(arbiter, pauseNative = {}, disposeNative = {},
            nativeOperationTimeoutMillis = 50L)
        val failure = runCatching { guard.runNative { awaitCancellation() } }.exceptionOrNull()
        assertTrue(failure is TimeoutCancellationException)
        assertFalse(guard.runNative { fail("Uncertain native owner restarted") })
        val call = arbiter.acquireCall(CommunicationMediaOwner())
        assertFalse(call.mediaSuspended)
        guard.serializeNativeCleanup { reset = true }
        assertTrue(reset)
        assertTrue(call.retryMediaSuspension())
        call.release(true)
        assertTrue(guard.runNative {})
        guard.dispose()
    }

}
