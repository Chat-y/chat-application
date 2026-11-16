package com.example.chat

import com.example.chat.domain.models.*
import com.example.chat.ui.settings.privacy.PrivacyOptionsLogic
import org.junit.Assert.*
import org.junit.Test

class PrivacyPolicyAliasTest {
    @Test fun everySameAsLastSeenSpellingSuppressesPresenceForNobody() {
        val policy = PrivacyPublicationPolicy()
        for (online in listOf("Same as Last Seen", "Same as last seen", "same_as_last_seen"))
            for (last in listOf("Nobody", "nobody")) {
                assertFalse(policy.allows(PrivacyPublication.presence, PrivacyPreferencesSnapshot(hideLastSeenAudience = last, hideOnlineAudience = online)))
            }
    }
    @Test fun persistenceLoadAdaptsAliasesToLegacyReaderValues() {
        val p = PrivacyPreferences.fromMap(mapOf("hideLastSeenAudience" to "nobody", "hideOnlineAudience" to "Same as last seen", "whoCanCallMe" to "contacts_except"))
        assertEquals("Nobody", p.hideLastSeenAudience); assertEquals("Same as Last Seen", p.hideOnlineAudience)
        assertEquals("My Contacts Except...", p.whoCanCallMe)
    }
    @Test fun policyAndPreferenceAdaptersRoundtripKnownValues() {
        for (a in PrivacyAudienceValue.entries) {
            assertEquals(a.wire, PrivacyOptionsLogic.audiencePolicy(a.preference))
            assertEquals(a.preference, PrivacyOptionsLogic.audiencePreferenceValue(a.wire))
        }
        for (o in PrivacyOnlineValue.entries) {
            assertEquals(o.wire, PrivacyOptionsLogic.onlinePolicy(o.preference))
            assertEquals(o.preference, PrivacyOptionsLogic.onlinePreferenceValue(o.wire))
        }
        assertEquals("contacts_except", PrivacyOptionsLogic.audiencePolicy("My Contacts Except…"))
    }
    @Test fun unknownValuesAreNotReinterpretedAsKnownPolicies() {
        assertNull(PrivacyAudienceValue.parse("strangers")); assertNull(PrivacyOnlineValue.parse("sometimes"))
        assertEquals("future-value", PrivacyPreferences.fromMap(mapOf("hideOnlineAudience" to "future-value")).hideOnlineAudience)
    }
}
