package com.example.chat

import com.example.chat.ui.settings.permissions.shouldOfferPermissionSettings
import org.junit.Assert.*
import org.junit.Test

class PermissionRequestHistoryTest {
    @Test fun firstRequestIsNeverClassifiedBlocked() {
        for (rationale in listOf(false, true)) for (activity in listOf(false, true))
            assertFalse(shouldOfferPermissionSettings(false, rationale, activity))
    }
    @Test fun completedRequestUsesRationaleOnlyWithActivityEvidence() {
        assertTrue(shouldOfferPermissionSettings(true, false, true))
        assertFalse(shouldOfferPermissionSettings(true, true, true))
        assertFalse(shouldOfferPermissionSettings(true, false, false))
        assertFalse(shouldOfferPermissionSettings(true, true, false))
    }
}
