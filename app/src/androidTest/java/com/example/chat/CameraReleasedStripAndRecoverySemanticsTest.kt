package com.example.chat

import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.chat.ui.camera.CameraRecentStrip
import com.example.chat.ui.camera.CameraCaptureNoCameraPane
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** CAM-R4-031 isolated UI fixtures NOT RUN. Uri.EMPTY only, never queries gallery/camera/settings. */
class CameraReleasedStripAndRecoverySemanticsTest {
    @get:Rule val compose = createComposeRule()
    private var toggles = 0
    private var picks = 0
    private var primary = 0
    private var secondary = 0
    private fun strip(openInitially: Boolean = false) {
        compose.setContent { MaterialTheme {
            var open by remember { mutableStateOf(openInitially) }
            CameraRecentStrip(open, listOf(Uri.EMPTY), { open = it; toggles++ }, { picks++ })
        } }
    }
    @Test fun collapsedStripNamesShowActionAndHidesPhotos() {
        strip(); compose.onNodeWithContentDescription("Show recent photos").assertHasClickAction()
        compose.onNodeWithContentDescription("Recent photo 1").assertDoesNotExist()
    }
    @Test fun showActionOpensThenNamesHideAction() {
        strip(); compose.onNodeWithContentDescription("Show recent photos").performClick()
        compose.onNodeWithContentDescription("Hide recent photos").assertHasClickAction()
        compose.onNodeWithContentDescription("Recent photo 1").assertHasClickAction()
        compose.runOnIdle { assertEquals(1, toggles); assertEquals(0, picks) }
    }
    @Test fun hideActionRemovesPhotoActions() {
        strip(true); compose.onNodeWithContentDescription("Hide recent photos").performClick()
        compose.onNodeWithContentDescription("Recent photo 1").assertDoesNotExist()
        compose.onNodeWithContentDescription("Show recent photos").assertHasClickAction()
    }
    @Test fun photoActionInvokesOnlyLocalCallback() {
        strip(true); compose.onNodeWithContentDescription("Recent photo 1").performClick()
        compose.runOnIdle { assertEquals(1, picks); assertEquals(0, toggles) }
    }
    @Test fun handleReceivesFocus() {
        strip(); compose.onNodeWithContentDescription("Show recent photos")
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithContentDescription("Show recent photos").assertIsFocused()
    }
    @Test fun photoReceivesFocus() {
        strip(true); compose.onNodeWithContentDescription("Recent photo 1")
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithContentDescription("Recent photo 1").assertIsFocused()
    }
    private fun recovery(withGallery: Boolean = true) {
        compose.setContent { MaterialTheme {
            CameraCaptureNoCameraPane("Camera unavailable", "Test fixture only", "Retry", { primary++ },
                if (withGallery) "Pick from gallery" else null, { secondary++ })
        } }
    }
    @Test fun retryHasActionAndInitialFocus() {
        recovery(); compose.onNodeWithText("Retry").assertHasClickAction().assertIsFocused()
    }
    @Test fun retryInvokesOnlyPrimaryCallback() {
        recovery(); compose.onNodeWithText("Retry").performClick()
        compose.runOnIdle { assertEquals(1, primary); assertEquals(0, secondary) }
    }
    @Test fun galleryFallbackInvokesOnlySecondaryCallback() {
        recovery(); compose.onNodeWithText("Pick from gallery").assertHasClickAction().performClick()
        compose.runOnIdle { assertEquals(0, primary); assertEquals(1, secondary) }
    }
    @Test fun absentSecondaryIsNotAnAction() { recovery(false); compose.onNodeWithText("Pick from gallery").assertDoesNotExist() }
}
