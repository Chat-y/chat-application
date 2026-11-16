package com.example.chat

import com.example.chat.data.api.EmailCodeSendLimiter
import org.junit.Assert.*
import org.junit.Test

class EmailCodeSendLimiterTest {
    @Test fun escalationCapAndCooldownUseOneHourWindow() {
        val base = "https://limiter-contract.test"
        fun reserve(time: Long) = EmailCodeSendLimiter.reserve(base, "tenant", "maya@example.com", time)
        assertEquals(1, reserve(1000))
        try { reserve(1059); fail("Cooldown must hold") } catch (_: IllegalStateException) { }
        assertEquals(2, reserve(1060))
        assertEquals(3, reserve(1180))
        assertEquals(4, reserve(1480))
        assertEquals(5, reserve(1780))
        try { reserve(2080); fail("Five sends per hour") } catch (_: IllegalStateException) { }
        assertEquals(1, reserve(5400))
        assertEquals(60, EmailCodeSendLimiter.delayAfter(1))
        assertEquals(120, EmailCodeSendLimiter.delayAfter(2))
        assertEquals(300, EmailCodeSendLimiter.delayAfter(3))
    }
}
