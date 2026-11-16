package com.example.chat.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CS3-16: pins the audience-picker mapping and the exception-selection helper rules.
 * SOURCE-ONLY, NOT RUN by the author; not wired into any CI by this change.
 * Covers pure presentation helpers only; it is not a persisted-preference,
 * server-policy or runtime device proof.
 */
class CallSettingsAudienceMappingTest {

    @Test fun audienceOptionsOffersFourPoliciesInStableOrder() {
        assertEquals(listOf("Everyone", "My Contacts", "My Contacts Except…", "Nobody"), audienceOptions)
    }

    @Test fun exceptAudienceSentinelDistinctFromMyContacts() {
        assertTrue(audienceOptions.contains("My Contacts Except…"))
        assertTrue(audienceOptions.indexOf("My Contacts Except…") != audienceOptions.indexOf("My Contacts"))
    }

    @Test fun legacyAndWireAudienceSpellingsResolveToSameSelection() {
        val kind = com.example.chat.domain.models.PrivacyAudienceValue.CONTACTS_EXCEPT
        assertEquals(kind, com.example.chat.domain.models.PrivacyAudienceValue.parse("My Contacts Except..."))
        assertEquals(kind, com.example.chat.domain.models.PrivacyAudienceValue.parse("My Contacts Except…"))
        assertEquals(kind, com.example.chat.domain.models.PrivacyAudienceValue.parse("contacts_except"))
        assertEquals("My Contacts Except…", callAudienceLabel(kind))
        assertEquals(null, com.example.chat.domain.models.PrivacyAudienceValue.parse("future-policy"))
    }

    @Test fun audioEntrySummaryNamesPreferenceWithoutClaimingActiveRoute() {
        val modes = com.example.chat.core.audio.AudioPreferenceMode.entries
        assertEquals("Automatic", callAudioPreferenceSummary(modes[0], false))
        assertEquals("Standard stereo playback", callAudioPreferenceSummary(modes[1], false))
        assertEquals("Dual-speaker (experimental)", callAudioPreferenceSummary(modes[2], false))
        assertTrue(callAudioPreferenceSummary(modes[0], true).contains("could not be saved"))
    }

    @Test fun excludedContactsLabelIsCountAware() {
        assertEquals("No contacts excluded", excludedContactsLabel(0))
        assertEquals("1 contact excluded", excludedContactsLabel(1))
        assertEquals("7 contacts excluded", excludedContactsLabel(7))
    }

    @Test fun filterCallContactsCaseInsensitiveAndSorted() {
        val contacts = listOf(
            CallContactOption("2", "bob"),
            CallContactOption("1", "Alice"),
            CallContactOption("3", "ALICE2"),
        )
        assertEquals(listOf("1", "3", "2"), filterCallContacts(contacts, "").map { it.id })
        assertEquals(listOf("1", "3"), filterCallContacts(contacts, "alice").map { it.id })
        assertEquals(emptyList<String>(), filterCallContacts(contacts, "zzz").map { it.id })
    }

    @Test fun selectedCallContactsDropsUnknownIdsAndSorts() {
        val contacts = listOf(CallContactOption("1", "b"), CallContactOption("2", "a"))
        assertEquals(listOf("2"), selectedCallContacts(contacts, setOf("2", "unknown")).map { it.id })
        assertEquals(emptyList<String>(), selectedCallContacts(contacts, emptySet()).map { it.id })
    }
}
