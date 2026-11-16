package com.example.chat

import com.example.chat.ui.settings.privacy.OptionalCrashConsent
import com.example.chat.ui.settings.privacy.OPTIONAL_CRASH_CONSENT_VERSION
import org.junit.Assert.*
import org.junit.Test

/** Actual local policy, uncompiled/unrun. No uploader/network/real consent writes. */
class OptionalCrashConsentPolicyProposalTest {
    @Test fun oldOptInIsNotCurrentConsentAndRequiresReview() {
        val old = OptionalCrashConsent(true, OPTIONAL_CRASH_CONSENT_VERSION - 1)
        assertTrue(old.needsReview)
        assertFalse(old.allowedForCurrentVersion)
    }
    @Test fun currentExplicitOptInIsRecognized() {
        val current = OptionalCrashConsent(true, OPTIONAL_CRASH_CONSENT_VERSION)
        assertFalse(current.needsReview)
        assertTrue(current.allowedForCurrentVersion)
    }
    @Test fun offChoiceIsNeverTurnedOnOrPromptedByVersionMismatch() {
        for (version in listOf(0, OPTIONAL_CRASH_CONSENT_VERSION, 999)) {
            val off = OptionalCrashConsent(false, version)
            assertFalse(off.needsReview)
            assertFalse(off.allowedForCurrentVersion)
        }
    }
}
