package com.example.chat

import android.content.res.Configuration
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/** Resource-only b85 declarations; uncompiled/unrun, not rendered badge/AT evidence. */
class PrivacyAffectedPeopleProposalTest {
    private val resources get() = InstrumentationRegistry.getInstrumentation().targetContext.let {
        it.createConfigurationContext(Configuration(it.resources.configuration).apply { setLocale(Locale.ENGLISH) }).resources
    }
    @Test fun retentionBadgeNamesSenderWithoutClaimingConsent() {
        val text = resources.getString(R.string.privacy_affects_you_sender)
        assertTrue(text.contains("Affects others"))
        assertTrue(text.contains("sender"))
        assertFalse(text.contains("consented", true))
    }
    @Test fun forwardedBadgeNamesRecipientWithoutClaimingServerProtection() {
        val text = resources.getString(R.string.privacy_affects_you_recipient)
        assertTrue(text.contains("recipient"))
        assertFalse(text.contains("server", true))
    }
    @Test fun optionalCrashCopyRetainsOffDefaultAndNoUploadClaim() {
        val text = resources.getString(R.string.privacy_crash_choice_explanation)
        assertTrue(text.startsWith("Off by default."))
        assertTrue(text.contains("this choice alone sends nothing"))
    }
    @Test fun androidHistoryLinkDoesNotClaimEveryPermissionLastUseIsAvailable() {
        val text = resources.getString(R.string.privacy_android_access_history_help)
        assertTrue(text.contains("Android privacy settings"))
        assertTrue(text.contains("where supported"))
        assertFalse(text.contains("every permission", true))
    }
    @Test fun forwardedActionIsNotMisnamedAsRetention() {
        val text = resources.getString(R.string.privacy_hide_forwarded_tag_action)
        assertTrue(text.contains("forwarded tag"))
        assertFalse(text.contains("retention", true))
    }
    @Test fun consentStorageFailureDoesNotClaimThatTheNewChoiceWasSaved() {
        val text = resources.getString(R.string.privacy_crash_choice_unavailable)
        assertTrue(text.contains("could not be read or saved"))
        assertFalse(text.contains("successfully", true))
    }
    @Test fun decimalToggleDoesNotClaimThatCoordinatesAreExact() {
        val label = resources.getString(R.string.privacy_location_more_digits)
        assertTrue(label.contains("decimal places"))
        assertFalse(label.contains("exact", true))
        assertTrue(resources.getString(R.string.privacy_location_digits_accuracy).contains("do not improve location accuracy"))
    }
}
