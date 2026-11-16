package com.example.chat

import android.Manifest
import com.example.chat.ui.settings.permissions.overviewLocationPermissions
import org.junit.Assert.*
import org.junit.Test

/** Actual overview request list, uncompiled/unrun; not an Android prompt test. */
class PermissionApproximateRequestProposalTest {
    @Test fun overviewLocationRequestContainsOnlyCoarse() {
        assertEquals(listOf(Manifest.permission.ACCESS_COARSE_LOCATION), overviewLocationPermissions)
        assertFalse(overviewLocationPermissions.contains(Manifest.permission.ACCESS_FINE_LOCATION))
    }
}
