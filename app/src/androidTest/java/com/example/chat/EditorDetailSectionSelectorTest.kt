package com.example.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.chat.ui.chats.detail.EditorDetailSectionSelector
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** MR-R3-005 isolated source cases, NOT RUN. Not full editor/visible-control/pixels proof. */
class EditorDetailSectionSelectorTest {
    @get:Rule val compose = createComposeRule()
    private var calls = 0
    private fun content(enabled: Boolean = true, labels: Map<String, String> = emptyMap()) {
        compose.setContent { MaterialTheme {
            var section by remember { mutableStateOf("Content") }
            EditorDetailSectionSelector("Text controls", section, listOf("Content", "Alignment", "Placement", "Ink"), enabled, keyLabels = labels) { section = it; calls++ }
        } }
    }
    @Test fun chosenPanelIsNamed() { content(); compose.onNodeWithContentDescription("Text controls: Content").assertHasClickAction() }
    @Test fun choosingPanelUpdatesLabelAndClosesMenu() {
        content(); compose.onNodeWithContentDescription("Text controls: Content").performClick()
        compose.onNodeWithText("Alignment").performClick()
        compose.onNodeWithContentDescription("Text controls: Alignment").assertExists()
        compose.onNodeWithText("Ink").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, calls) }
    }
    @Test fun selectedPanelIsExposedInMenu() {
        content(); compose.onNodeWithContentDescription("Text controls: Content").performClick()
        compose.onNodeWithText("Content").assertIsSelected()
        compose.onNodeWithText("Placement").assertIsNotSelected()
    }
    @Test fun disabledPanelSelectorCannotOpen() { content(false); compose.onNodeWithContentDescription("Text controls: Content").assertIsNotEnabled(); compose.runOnIdle { assertEquals(0, calls) } }
    //159 nonidentity display fixtures. NOT RUN; not translation/locale or full editor proof.
    @Test fun displayLabelDoesNotReplaceRawSelectionKey() {
        content(labels = mapOf("Content" to "Entry label", "Alignment" to "Arrange label"))
        compose.onNodeWithContentDescription("Text controls: Entry label").performClick()
        compose.onNodeWithText("Arrange label").performClick()
        compose.onNodeWithContentDescription("Text controls: Arrange label").performClick()
        compose.onNodeWithText("Arrange label").assertIsSelected()
        compose.runOnIdle { assertEquals(1, calls) }
    }
    @Test fun missingDisplayMappingFallsBackToRawKey() {
        content(labels = mapOf("Alignment" to "Arrange label"))
        compose.onNodeWithContentDescription("Text controls: Content").performClick()
        compose.onNodeWithText("Placement").assertExists()
    }
}
