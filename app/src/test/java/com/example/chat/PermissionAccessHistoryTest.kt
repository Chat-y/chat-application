package com.example.chat
import com.example.chat.ui.settings.permissions.PermissionAccessHistory
import org.junit.Assert.*
import org.junit.Test
class PermissionAccessHistoryTest {
    @Test fun neverGrantedIsNotAnAutoResetClaim() {
        assertTrue(PermissionAccessHistory.offSinceLastCheck(emptySet(), setOf("camera"), emptySet()).isEmpty())
    }
    @Test fun lostAccessIsFlaggedWithoutClaimingItsCause() {
        assertEquals(setOf("camera"), PermissionAccessHistory.offSinceLastCheck(setOf("camera", "photos"), setOf("camera"), emptySet()))
    }
    @Test fun existingOffHintSurvivesNextCheckAndClearsAfterRegrant() {
        assertEquals(setOf("camera"), PermissionAccessHistory.offSinceLastCheck(emptySet(), setOf("camera"), setOf("camera")))
        assertTrue(PermissionAccessHistory.offSinceLastCheck(emptySet(), emptySet(), setOf("camera")).isEmpty())
    }
}
