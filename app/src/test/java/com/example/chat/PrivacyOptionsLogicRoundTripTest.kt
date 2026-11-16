package com.example.chat

import com.example.chat.domain.models.PrivacyPreferencesSnapshot
import com.example.chat.domain.models.PrivacyPublication
import com.example.chat.domain.models.PrivacyPublicationPolicy
import com.example.chat.ui.settings.privacy.PrivacyOptionsLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyOptionsLogicRoundTripTest {
    @Test fun `audience labels round-trip and unknown values fail closed`() {
        for (policy in listOf("everyone", "contacts", "contacts_except", "nobody")) {
            val label = PrivacyOptionsLogic.audienceLabel(policy)
            assertEquals(policy, PrivacyOptionsLogic.audiencePolicy(label!!))
        }
        assertNull(PrivacyOptionsLogic.audienceLabel("unknown"))
        assertNull(PrivacyOptionsLogic.audiencePolicy("Strangers"))
    }

    @Test fun `online labels round-trip and unknown values fail closed`() {
        for (policy in listOf("everyone", "same_as_last_seen")) {
            val label = PrivacyOptionsLogic.onlineLabel(policy)
            assertEquals(policy, PrivacyOptionsLogic.onlinePolicy(label!!))
        }
        assertNull(PrivacyOptionsLogic.onlineLabel("unknown"))
    }

    @Test fun `publication policy covers every flag combination`() {
        val policy = PrivacyPublicationPolicy()
        val base = PrivacyPreferencesSnapshot()
        assertTrue(policy.allows(PrivacyPublication.readReceipt, base))
        assertFalse(policy.allows(PrivacyPublication.readReceipt, base.copy(readReceipts = false)))
        assertTrue(policy.allows(PrivacyPublication.typing, base))
        assertFalse(policy.allows(PrivacyPublication.typing, base.copy(typingIndicators = false)))
        assertTrue(policy.allows(PrivacyPublication.recording, base))
        assertFalse(policy.allows(PrivacyPublication.recording, base.copy(recordingIndicators = false)))
        assertTrue(policy.allows(PrivacyPublication.statusView, base))
        assertFalse(policy.allows(PrivacyPublication.statusView, base.copy(hideViewStatus = true)))
        assertTrue(policy.allows(PrivacyPublication.presence, base.copy(hideLastSeenAudience = "Nobody", hideOnlineAudience = "Everyone")))
        assertFalse(policy.allows(PrivacyPublication.presence, base.copy(hideLastSeenAudience = "Nobody", hideOnlineAudience = "Same as Last Seen")))
        assertTrue(policy.allows(PrivacyPublication.lastSeen, base))
        assertFalse(policy.allows(PrivacyPublication.lastSeen, base.copy(freezeLastSeen = true)))
        assertFalse(policy.allows(PrivacyPublication.lastSeen, base.copy(hideLastSeenAudience = "Nobody")))
    }
}
