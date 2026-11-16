package com.example.chat

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.chat.ui.media.audioSeekKeyboard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** MV-R3-041 focused-slider fixture, NOT RUN. Local callback, not coordinator/native proof. */
class AudioSeekKeyboardComponentTest {
    @get:Rule val compose = createComposeRule()
    private var target = -1L
    private fun slider(enabled: Boolean = true) {
        compose.setContent { MaterialTheme {
            var position by remember { mutableFloatStateOf(10_000f) }
            Slider(position, { position = it }, valueRange = 0f..60_000f,
                modifier = Modifier.heightIn(min = 48.dp)
                    .audioSeekKeyboard(position.toLong(), 60_000L, enabled) { target = it; position = it.toFloat() }
                    .semantics { contentDescription = "Seek fixture" })
        } }
    }
    private fun focus() { compose.onNodeWithContentDescription("Seek fixture").performSemanticsAction(SemanticsActions.RequestFocus) { it() } }
    @Test fun rightKeyRequestsFiveSecondStep() { slider(); focus(); compose.onNodeWithContentDescription("Seek fixture").performKeyInput { pressKey(Key.DirectionRight) }; compose.runOnIdle { assertEquals(15_000L, target) } }
    @Test fun leftKeyRequestsFiveSecondStep() { slider(); focus(); compose.onNodeWithContentDescription("Seek fixture").performKeyInput { pressKey(Key.DirectionLeft) }; compose.runOnIdle { assertEquals(5_000L, target) } }
    @Test fun nativeSetProgressSemanticsRemainPresent() { slider(); compose.onNodeWithContentDescription("Seek fixture").assert(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)) }
    @Test fun disabledGateDoesNotInvokeKeyboardCallback() { slider(false); focus(); compose.onNodeWithContentDescription("Seek fixture").performKeyInput { pressKey(Key.DirectionRight) }; compose.runOnIdle { assertEquals(-1L, target) } }
}
