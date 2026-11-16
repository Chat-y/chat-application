package com.example.chat

import com.example.chat.ui.calls.CallRedialCooldown
import org.junit.Assert.*
import org.junit.Test

/** NOT RUN. In-memory cooldown only, not signaling/server enforcement. */
class CallRedialCooldownGapTest {
    @Test fun firstAttemptAtZeroIsAllowedAndGlobalBoundaryIsExact() {
        val guard = CallRedialCooldown()
        assertEquals(0L, guard.acquire("A", 0))
        assertEquals(1L, guard.acquire("B", 999))
        assertEquals(0L, guard.acquire("B", 1000))
    }
    @Test fun alternatingRecipientsCannotBypassSameRecipientWait() {
        val guard = CallRedialCooldown()
        assertEquals(0L, guard.acquire("A", 0))
        assertEquals(0L, guard.acquire("B", 1000))
        assertEquals(3000L, guard.acquire("A", 2000))
        assertEquals(1L, guard.acquire("A", 4999))
        assertEquals(0L, guard.acquire("A", 5000))
    }
    @Test fun rejectedTapsDoNotExtendDeadlineAndClockEpochResetDoesNotLockUserOut() {
        val guard = CallRedialCooldown()
        guard.acquire("A", 10_000)
        assertEquals(4900L, guard.acquire("A", 10_100))
        assertEquals(0L, guard.acquire("A", 15_000))
        assertEquals(0L, guard.acquire("A", 0))
    }
}
