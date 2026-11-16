package com.example.chat

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performClick
import com.example.chat.ui.settings.MediaVisibilitySwitch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** HM4-23 focus interaction checks. Border pixels/contrast still require actual visual review. */
class HideMediaFocusRingProposalTest {
    @get:Rule val compose = createComposeRule()

    @Test fun requestingKeyboardFocusDoesNotTogglePreference() {
        var changes = 0
        compose.setContent { MaterialTheme {
            MediaVisibilitySwitch(Icons.Rounded.PhotoLibrary, "Photos", "Photo markers", false, { changes++ })
        } }
        compose.onNodeWithText("Photos").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            .assertIsFocused()
        compose.runOnIdle { assertEquals(0, changes) }
    }

    @Test fun focusedRowRetainsExactlyOneToggleAction() {
        var changes = 0
        compose.setContent { MaterialTheme {
            MediaVisibilitySwitch(Icons.Rounded.PhotoLibrary, "Photos", "Photo markers", false, { changes++ })
        } }
        compose.onNodeWithText("Photos").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            .assertIsFocused().performClick()
        compose.runOnIdle { assertEquals(1, changes) }
    }
}
