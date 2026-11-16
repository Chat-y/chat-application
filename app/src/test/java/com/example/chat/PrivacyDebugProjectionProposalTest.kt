package com.example.chat

import com.example.chat.domain.models.PrivacyPreferences
import com.example.chat.ui.settings.privacy.privacyDebugRows
import org.junit.Assert.*
import org.junit.Test

/** Actual debug projection; authored-only, uncompiled/unrun. */
class PrivacyDebugProjectionProposalTest {
    @Test fun arbitraryIdentifiersAndTimeStringsAreNotIncluded() {
        val preferences = PrivacyPreferences(whoCanCallMeExceptions = listOf("private-user-id"), frozenLastSeenTime = "private-time")
        val text = privacyDebugRows(preferences, "private-server-user", null, null).toString()
        assertFalse(text.contains("private-user-id"))
        assertFalse(text.contains("private-time"))
        assertFalse(text.contains("private-server-user"))
    }
    @Test fun allowlistedLocalAndServerPoliciesAppearSideBySide() {
        val calls = privacyDebugRows(PrivacyPreferences(whoCanCallMe = "Nobody"), "contacts", null, null).first()
        assertEquals("nobody", calls.local)
        assertEquals("contacts", calls.cachedServer)
    }
    @Test fun flagProjectionReflectsInputWithoutChangingIt() {
        val preferences = PrivacyPreferences(readReceipts = false)
        val row = privacyDebugRows(preferences, null, null, null).first { it.key == "readReceipts" }
        assertEquals("Off", row.local)
        assertEquals("Local flag", row.cachedServer)
        assertFalse(preferences.readReceipts)
    }
}
