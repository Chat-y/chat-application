package com.example.chat

import com.example.chat.data.services.VoiceNativeCleanup
import org.junit.Assert.*
import org.junit.Test

class VoiceNativeCleanupTest {
    @Test fun attemptsEveryHandleAndPreservesFirstErrorWithSuppressedLaterFailures() {
        val first = IllegalStateException("first")
        val second = IllegalStateException("second")
        val attempted = mutableListOf<Int>()
        val cleanup = VoiceNativeCleanup()
        cleanup.attempt { attempted += 1; throw first }
        cleanup.attempt { attempted += 2; throw second }
        cleanup.attempt { attempted += 3 }
        val result = runCatching { cleanup.throwIfFailed() }.exceptionOrNull()
        assertSame(first, result)
        assertEquals(listOf(1, 2, 3), attempted)
        assertTrue(first.suppressed.contains(second))
    }
    @Test fun startupFailureRemainsPrimaryWhileAllAllocatedHandlesAreAttempted() {
        val startup = IllegalStateException("startup")
        var readerReleased = false
        var encoderReleased = false
        val cleanup = VoiceNativeCleanup(startup)
        cleanup.attempt { readerReleased = true; error("reader cleanup") }
        cleanup.attempt { encoderReleased = true }
        assertSame(startup, runCatching { cleanup.throwIfFailed() }.exceptionOrNull())
        assertTrue(readerReleased); assertTrue(encoderReleased)
        assertEquals(1, startup.suppressed.size)
    }
}
