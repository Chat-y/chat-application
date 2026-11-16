package com.example.chat

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.chat.ui.settings.MediaVisibilitySwitch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** HM4-49 source-only regression cases. Not compiled or executed. */
class HideMediaToggleActionProposalTest {
    @get:Rule val compose = createComposeRule()
    private fun hasActionLabel(label: String) = SemanticsMatcher("toggle action label $label") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == label
    }

    @Test fun offRowLabelsItsExistingActionTurnOnAndTogglesOnce() {
        var count = 0
        compose.setContent { MaterialTheme {
            MediaVisibilitySwitch(Icons.Rounded.PhotoLibrary, "Photos", "Photo marker", false) {
                assertEquals(true, it); count++
            }
        } }
        compose.onNodeWithText("Photos").assert(hasActionLabel("Turn on")).performClick()
        compose.runOnIdle { assertEquals(1, count) }
    }

    @Test fun onRowLabelsItsExistingActionTurnOffAndTogglesOnce() {
        var count = 0
        compose.setContent { MaterialTheme {
            MediaVisibilitySwitch(Icons.Rounded.PhotoLibrary, "Photos", "Photo marker", true) {
                assertEquals(false, it); count++
            }
        } }
        compose.onNodeWithText("Photos").assert(hasActionLabel("Turn off")).performClick()
        compose.runOnIdle { assertEquals(1, count) }
    }
}
