package com.example.chat.home

import com.example.chat.ui.navigation.HomeHeaderGeometry
import org.junit.Assert.assertEquals
import org.junit.Test

/** Source fixtures only. Not executed in the source-only review. */
class HomeHeaderGeometryTest {
    @Test fun collapseCannotConsumeAnyActionOrSearchPixel() {
        val hidden = HomeHeaderGeometry.collapsed(0f, -500f, 80f)
        assertEquals(80f, hidden, 0f)
        assertEquals(130, HomeHeaderGeometry.height(80, hidden, 20, 60, 50))
    }
    @Test fun expandingStopsAtExpandedIdentity() {
        assertEquals(0f, HomeHeaderGeometry.collapsed(45f, 90f, 80f), 0f)
    }
    @Test fun configurationShrinkDoesNotKeepHistoricalMaximum() {
        assertEquals(90, HomeHeaderGeometry.actions(60, 90, 56))
        assertEquals(60, HomeHeaderGeometry.actions(60, 56, 56))
        assertEquals(10f, HomeHeaderGeometry.collapsed(80f, 0f, 10f), 0f)
    }
    @Test fun modeChangesKeepSameMeasuredReservation() {
        val action = HomeHeaderGeometry.actions(60, 90, 56)
        assertEquals(220, HomeHeaderGeometry.height(80, 0f, 0, action, 50))
        assertEquals(140, HomeHeaderGeometry.height(80, 80f, 0, action, 50))
    }
    @Test fun floatDpLanePolicyUsedByShellKeepsLargestBudget() {
        assertEquals(60.5f, HomeHeaderGeometry.actions(56f, 60.5f, 56f), 0f)
        assertEquals(56f, HomeHeaderGeometry.actions(56f, 48.25f, 56f), 0f)
        assertEquals(80.25f, HomeHeaderGeometry.actions(80.25f, 60.5f, 56f), 0f)
    }
}
