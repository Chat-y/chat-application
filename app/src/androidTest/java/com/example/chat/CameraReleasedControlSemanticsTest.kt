package com.example.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.chat.ui.camera.CameraCaptureModeButton
import com.example.chat.ui.camera.CameraCaptureRoundAction
import com.example.chat.ui.camera.CameraCaptureShutter
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** CAM-R4-031 partial source suite, NOT RUN. Local callbacks only, never opens camera hardware. */
class CameraReleasedControlSemanticsTest {
    @get:Rule val compose = createComposeRule()
    private var clicks = 0
    private fun shutter(enabled: Boolean = true, capturing: Boolean = false, video: Boolean = false,
                        recording: Boolean = false, paused: Boolean = false, seconds: Int = 0) {
        compose.setContent { MaterialTheme {
            CameraCaptureShutter(enabled, capturing, videoActive = video, recording = recording,
                paused = paused, elapsedSeconds = seconds, onClick = { clicks++ })
        } }
    }
    private fun target(label: String) = compose.onNodeWithContentDescription(label)
    private fun state(label: String, value: String) = target(label).assert(
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value))
    @Test fun photoShutterHasActionAndReadyState() { shutter(); target("Take photo").assertHasClickAction(); state("Take photo", "Ready") }
    @Test fun photoShutterInvokesOnlyLocalCallback() { shutter(); target("Take photo").performClick(); compose.runOnIdle { assertEquals(1, clicks) } }
    @Test fun unavailableShutterIsDisabled() { shutter(enabled = false); target("Take photo").assertIsNotEnabled() }
    @Test fun capturingShutterDescribesProgress() { shutter(capturing = true); state("Take photo", "Capturing photo") }
    @Test fun videoIdleShutterHasRecordAction() { shutter(video = true); target("Record video").assertHasClickAction(); state("Record video", "Ready") }
    @Test fun recordingShutterHasStopActionAndElapsedState() { shutter(video = true, recording = true, seconds = 9); target("Stop recording").assertHasClickAction(); state("Stop recording", "Recording, 9 seconds") }
    @Test fun pausedShutterKeepsStopActionAndPausedState() { shutter(video = true, recording = true, paused = true, seconds = 9); target("Stop recording").assertHasClickAction(); state("Stop recording", "Recording paused, 9 seconds") }
    @Test fun shutterCanReceiveKeyboardFocus() {
        shutter(); target("Take photo").performSemanticsAction(SemanticsActions.RequestFocus) { it() }; target("Take photo").assertIsFocused()
    }
    private fun mode(enabled: Boolean = true) {
        compose.setContent { MaterialTheme {
            var selected by remember { mutableStateOf(false) }
            CameraCaptureModeButton("Photo", selected, tight = false, enabled = enabled, onClick = { selected = true; clicks++ })
        } }
    }
    @Test fun modeIsATabWithSelectedState() {
        mode(); target("Photo camera mode").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .assertIsNotSelected().performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(1, clicks) }
    }
    @Test fun modeDisabledStateIsExposed() { mode(enabled = false); target("Photo camera mode").assertIsNotEnabled().assertIsNotSelected() }
    private fun round(label: String, kind: String, description: String? = null) {
        compose.setContent { MaterialTheme { CameraCaptureRoundAction(label, kind, { clicks++ }, actionState = description) } }
    }
    @Test fun closeActionHasLabelAndLocalCallback() { round("Close camera", "close"); target("Close camera").assertHasClickAction().performClick(); compose.runOnIdle { assertEquals(1, clicks) } }
    @Test fun flashActionHasState() { round("Camera flash", "flash", "Auto"); target("Camera flash").assertHasClickAction(); state("Camera flash", "Auto") }
    @Test fun settingsActionHasLabelAndAction() { round("Camera settings", "settings"); target("Camera settings").assertHasClickAction() }
    @Test fun roundActionCanReceiveKeyboardFocus() { round("Close camera", "close"); target("Close camera").performSemanticsAction(SemanticsActions.RequestFocus) { it() }; target("Close camera").assertIsFocused() }
}
