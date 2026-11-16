package com.example.chat

import com.example.chat.ui.auth.registrationPasswordError
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class RegistrationPasswordPolicyTest {
    @Test fun namedRulesOnly() {
        assertNull(registrationPasswordError("Abcdefghij12"))
        assertNull(registrationPasswordError("Aa1" + "x".repeat(125)))
        for (bad in listOf("Aa123456789", "Aa1" + "x".repeat(126), "abcdefghijkl1", "ABCDEFGHIJKL1", "Abcdefghijkl")) {
            assertNotNull(registrationPasswordError(bad))
        }
    }
}
