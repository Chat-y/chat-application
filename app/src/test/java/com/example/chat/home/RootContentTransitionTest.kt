package com.example.chat.home

import com.example.chat.core.theme.AppNavigationMode
import com.example.chat.ui.navigation.rootContentTransition
import org.junit.Assert.assertEquals
import org.junit.Test

/** Unrun source fixtures. Native pager pixels/motion remain device acceptance. */
class RootContentTransitionTest {
    @Test fun iosUsesNativeSlideWithoutSavedCubeLayer() {
        assertEquals("Default", rootContentTransition(AppNavigationMode.iosStyle, false, "Cube 3D"))
    }
    @Test fun reducedMotionNeverAddsThreeDimensionalLayer() {
        assertEquals("Default", rootContentTransition(AppNavigationMode.topWhatsAppBar, true, "Cube 3D"))
    }
    @Test fun otherModesKeepConfiguredLayer() {
        assertEquals("Cube 3D", rootContentTransition(AppNavigationMode.topWhatsAppBar, false, "Cube 3D"))
    }
}
