package com.example.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.chat.ui.camera.CameraCaptureOptions
import com.example.chat.ui.camera.CameraOptionsPanel
import com.example.chat.ui.camera.CameraZoomControls
import org.junit.Rule
import org.junit.Test

/** CAM-R4-031 partial source suite, NOT RUN. Isolated UI callbacks, no real camera/hardware/account. */
class CameraReleasedOptionsSemanticsTest {
    @get:Rule val compose = createComposeRule()

    private fun options(stabilization: Boolean = true, night: Boolean = true, front: Boolean = true) {
        compose.setContent { MaterialTheme {
            var settings by remember { mutableStateOf(CameraCaptureOptions()) }
            CameraOptionsPanel(settings, stabilization, night, { settings = it }, {}, frontCamera = front)
        } }
    }
    private fun choice(label: String) = compose.onNodeWithText(label).performScrollTo()

    @Test fun aspectChoiceReportsSelection() { options(); choice("1:1").assertIsNotSelected().performClick().assertIsSelected() }
    @Test fun timerChoiceReportsSelection() { options(); choice("3s").assertIsNotSelected().performClick().assertIsSelected() }
    @Test fun gridChoiceReportsSelection() { options(); choice("Grid lines").assertIsNotSelected().performClick().assertIsSelected() }
    @Test fun levelChoiceReportsSelection() { options(); choice("Level").assertIsNotSelected().performClick().assertIsSelected() }
    @Test fun mirrorChoiceIsExplicitlySelectableOnFront() { options(); choice("Mirror saved selfies").assertIsNotSelected().performClick().assertIsSelected() }
    @Test fun rearMirrorChoiceIsDisabled() { options(front = false); choice("Mirror selfies · front camera only").assertIsNotEnabled() }
    @Test fun unsupportedStabilizationIsDisabled() { options(stabilization = false); choice("Stabilization unavailable on this camera").assertIsNotEnabled() }
    @Test fun unsupportedNightIsDisabled() { options(night = false); choice("Night mode unavailable on this camera").assertIsNotEnabled() }
    @Test fun watermarkChoiceReportsSelection() { options(); choice("Watermark").assertIsNotSelected().performClick().assertIsSelected() }
    @Test fun clarityChoiceReportsSelection() { options(); choice("Clarity").assertIsNotSelected().performClick().assertIsSelected() }
    @Test fun optionsCloseKeepsLabelAndAction() { options(); choice("Close").assertHasClickAction() }
    @Test fun customZoomTargetIsFocusableAndAdjustable() {
        compose.setContent { MaterialTheme {
            var zoom by remember { mutableStateOf(1f) }
            CameraZoomControls(zoom, 1f, 6f, true, forceSlider = true, onZoom = { zoom = it })
        } }
        compose.onNodeWithContentDescription("Camera zoom").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithContentDescription("Camera zoom").assertIsFocused()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }
        compose.onNodeWithContentDescription("Camera zoom").assertRangeInfoEquals(androidx.compose.ui.semantics.ProgressBarRangeInfo(2f, 1f..6f))
    }
}
