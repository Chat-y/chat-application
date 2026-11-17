package com.example.chat

import com.example.chat.core.audio.*
import com.example.chat.data.services.BannerGifCallGuard
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Authored only; exercises the exact guard used by GIF download/decode/start. */
class BannerGifCallGuardTest {
    @Test fun activeCallBlocksGifAndCallSuspensionRetiresItsMountedSession() = runBlocking {
        val arbiter = DefaultCommunicationMediaArbiter()
        var downloading = false; var animating = false
        val gif = BannerGifCallGuard(arbiter, stop = { downloading = false; animating = false }, allowed = { true })
        assertTrue(gif.start { downloading = true })
        assertTrue(gif.start { animating = true })
        val call = arbiter.acquireCall(CommunicationMediaOwner())
        assertTrue(call.mediaSuspended)
        assertFalse(downloading); assertFalse(animating)
        assertFalse(gif.start { fail("GIF resumed during call") })
        call.release(true)
        assertFalse(gif.start { fail("GIF auto-resumed after call") })
        gif.close(); gif.dispose()
    }
    @Test fun preexistingCallAndPolicyLossRefuseStartAndDisposeStopsWork() = runBlocking {
        val arbiter = DefaultCommunicationMediaArbiter()
        var allowed = true; var stopped = false
        val call = arbiter.acquireCall(CommunicationMediaOwner())
        val gif = BannerGifCallGuard(arbiter, stop = { stopped = true }, allowed = { allowed })
        assertFalse(gif.start { fail("Existing call bypass") })
        call.release(true)
        allowed = false
        assertFalse(gif.start { fail("Policy bypass") })
        gif.close(); gif.dispose(); assertTrue(stopped)
    }
}
