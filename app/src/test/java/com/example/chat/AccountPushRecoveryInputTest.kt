package com.example.chat

import com.example.chat.notifications.AccountPushRecoveryWorker
import org.junit.Assert.*
import org.junit.Test

/** Unrun source test. WorkManager/process lifecycle and Doze require Android tests. */
class AccountPushRecoveryInputTest {
    @Test fun onlyCanonicalOpaqueBindingIsAccepted() {
        assertTrue(AccountPushRecoveryWorker.validBinding("123e4567-e89b-12d3-a456-426614174000"))
        listOf("", "../../account", "binding?token=secret", "1-1-1-1-1").forEach {
            assertFalse(AccountPushRecoveryWorker.validBinding(it))
        }
    }
}
