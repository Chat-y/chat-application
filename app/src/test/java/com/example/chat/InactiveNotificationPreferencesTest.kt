package com.example.chat

import com.example.chat.data.api.InactiveNotificationPreferences as Policy
import org.junit.Assert.*
import org.junit.Test

class InactiveNotificationPreferencesTest {
    private fun root(enabled: Boolean = true) = mapOf(
        "notification" to mapOf("enableGlobalNotifications" to enabled),
        "security" to mapOf("lockedConversationIds" to listOf("protected"), "hiddenConversationIds" to emptyList<String>()),
    )
    @Test fun missingBlobUsesFreshDefaultsButMalformedExistingBlobDoesNot() {
        assertEquals(Policy.Snapshot(true, emptySet()), Policy.decode(null))
        assertNull(Policy.decode(emptyMap()))
        assertNull(Policy.decode(mapOf("notification" to false)))
    }
    @Test fun explicitOptOutAndProtectionSurvive() {
        assertEquals(Policy.Snapshot(false, setOf("protected")), Policy.decode(root(false)))
    }
    @Test fun malformedProtectionNeverBecomesEmptyAllowedSet() {
        assertNull(Policy.decode(root() + ("security" to mapOf("lockedConversationIds" to listOf(7), "hiddenConversationIds" to emptyList<String>()))))
    }
}
