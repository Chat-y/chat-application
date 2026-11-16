package com.example.chat

import com.example.chat.ui.settings.permissions.PermStatus
import com.example.chat.ui.settings.permissions.permissionStatusSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/** PM2-29 summary cases, uncompiled/unrun. */
class PermissionStatusSummaryProposalTest {
    @Test fun checkingDoesNotAnnounceStaleGrantOrBiometricAvailability() {
        assertEquals("Permission summary. Checking permissions.", permissionStatusSummary(true,
            listOf("Camera" to PermStatus.Granted), true))
    }

    @Test fun limitedApproximateAndBiometricsStayDistinctFromGranted() {
        val summary = permissionStatusSummary(false, listOf("Camera" to PermStatus.Granted,
            "Microphone" to PermStatus.Granted, "Location" to PermStatus.Approximate,
            "Photos" to PermStatus.Limited, "Contacts" to PermStatus.Denied), false)
        assertTrue(summary.contains("Granted: Camera, Microphone."))
        assertTrue(summary.contains("Approximate location: Location."))
        assertTrue(summary.contains("Selected media only: Photos."))
        assertTrue(summary.contains("Off: Contacts."))
        assertTrue(summary.endsWith("Biometrics: Unavailable."))
        assertFalse(summary.contains("Granted: Camera, Microphone, Location"))
    }
    @Test fun readySummaryHasStableAccessibleGroupName() {
        assertTrue(permissionStatusSummary(false, emptyList(), true).startsWith("Permission summary."))
    }

    @Test fun emptyCategoriesDoNotCreateBlankSpokenLists() {
        assertEquals("Permission summary. Biometrics: Available.",
            permissionStatusSummary(false, emptyList(), true))
    }
}
