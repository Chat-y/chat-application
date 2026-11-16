package com.example.chat

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
// Invalid package-level import retained: import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.hasToggleAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.performClick
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.settings.CallContactOption
import com.example.chat.ui.settings.CallExceptionContactRow
import com.example.chat.ui.settings.CallExceptionDialog
import com.example.chat.ui.theme.ChatyTheme
import com.example.chat.ui.theme.LocalAppColors
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * CS4-40: TalkBack-proxy semantics of the exception dialog and its contact rows
 * (pane title, actions, toggle roles). Semantics-tree checks only; NOT a TalkBack
 * device run. NOT RUN by the author.
 */
class CallExceptionDialogSemanticsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun dialogExposesPaneTitleAndActions() {
        var dismissed = 0
        rule.setContent {
            ChatyTheme(ThemePresets.getSystemDefaultTheme(true)) {
                CallExceptionDialog(
                    fullScreen = false,
                    onDismissRequest = { dismissed++ },
                    containerColor = LocalAppColors.current.surfaceElevated,
                    title = { Text("People who can't call you") },
                    text = { Text("fixture body") },
                    dismissButton = { TextButton(onClick = { dismissed++ }) { Text("Cancel") } },
                    confirmButton = { TextButton(onClick = {}) { Text("Save") } },
                )
            }
        }
        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "People who can't call you")).assertExists()
        rule.onNodeWithText("Cancel").assertHasClickAction().performClick()
        rule.runOnIdle { assertEquals(1, dismissed) }
        rule.onNodeWithText("Save").assertHasClickAction()
    }

    @Test fun contactRowsExposeToggleState() {
        rule.setContent {
            ChatyTheme(ThemePresets.getSystemDefaultTheme(true)) {
                CallExceptionContactRow(CallContactOption("c1", "Ada Lovelace"), checked = false, onToggle = {})
            }
        }
        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)).assertExists()
        rule.onNode(hasToggleAction()).assertExists()
        rule.onNodeWithText("Ada Lovelace").assertExists()
    }
}
