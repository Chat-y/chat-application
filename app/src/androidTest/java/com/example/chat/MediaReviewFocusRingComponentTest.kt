package com.example.chat

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.chat.ui.chats.detail.reviewHeaderFocusRing
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** MR-R4-038 isolated semantics fixture, NOT RUN. Focus-ring pixels/contrast are NOT tested. */
class MediaReviewFocusRingComponentTest {
    @get:Rule val compose = createComposeRule()
    private var clicks = 0
    private fun button(enabled: Boolean = true) {
        compose.setContent { MaterialTheme {
            TextButton(onClick = { clicks++ }, enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp).reviewHeaderFocusRing()) { Text("Edit a copy") }
        } }
    }
    @Test fun ringDoesNotAddAnotherFocusableTarget() {
        button(); compose.onAllNodes(isFocusable(), useUnmergedTree = true).assertCountEquals(1)
    }
    @Test fun ringDoesNotAddAnotherClickAction() {
        button(); compose.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(1)
    }
    @Test fun originalButtonStillReceivesFocus() {
        button(); compose.onNodeWithText("Edit a copy").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithText("Edit a copy").assertIsFocused()
    }
    @Test fun originalButtonStillInvokesOneCallback() {
        button(); compose.onNodeWithText("Edit a copy").performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }
    @Test fun disabledStateIsRetained() { button(false); compose.onNodeWithText("Edit a copy").assertIsNotEnabled(); compose.runOnIdle { assertEquals(0, clicks) } }
    @Test fun focusKeepsTheExistingMinimumLayoutHeight() {
        button(); compose.onNodeWithText("Edit a copy").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithText("Edit a copy").assertHeightIsAtLeast(48.dp)
    }
}
