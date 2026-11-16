package com.example.chat.data.services

import org.junit.Assert.assertEquals
import org.junit.Test

class CallRingPolicyTest {
    @Test fun ringingBoundMatchesSettingsCopy() {
        assertEquals(30, CallRingPolicy.TIMEOUT_SECONDS)
        assertEquals(30_000L, CallRingPolicy.TIMEOUT_MILLIS)
    }
}
