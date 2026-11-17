package com.example.chat

import com.example.chat.notifications.UnknownBindingRecoveryPolicy
import org.junit.Assert.*
import org.junit.Test

class UnknownBindingRecoveryPolicyTest {
    @Test fun missingMessageMappingRequestsOneRepairAcrossRetrySequence() {
        val requests = (0..4).count { attempt ->
            UnknownBindingRecoveryPolicy.shouldRequestRegistration(false, attempt, false)
        }
        assertEquals(1, requests)
    }
    @Test fun knownMappingNeverTriggersUnrelatedRegistration() {
        (0..4).forEach { assertFalse(UnknownBindingRecoveryPolicy.shouldRequestRegistration(false, it, true)) }
    }
    @Test fun callsAndInvalidAttemptNeverEnterMessageRegistrationRepair() {
        (0..4).forEach { assertFalse(UnknownBindingRecoveryPolicy.shouldRequestRegistration(true, it, false)) }
        assertFalse(UnknownBindingRecoveryPolicy.shouldRequestRegistration(false, -1, false))
    }
}
