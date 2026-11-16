package com.example.chat.ui.settings.conversation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.kit.GbSettingRow
import com.example.chat.ui.theme.ChatyTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Shared optional line limit, actual text layout. Unrun. Full text remains in semantics. */
class ConversationSubtitleLineTest {
    @get:Rule val compose = createComposeRule()
    private val longCopy = "Header only. Notifications and the chat list still show the name. ".repeat(4)
    private fun host(lines: Int? = 1, dark: Boolean = false) {
        compose.setContent { ChatyTheme(if (dark) ThemePresets.monochromeDark else ThemePresets.paper) {
            if (lines == null) GbSettingRow(title = "Sample setting", subtitle = longCopy)
            else GbSettingRow(title = "Sample setting", subtitle = longCopy, subtitleMaxLines = lines)
        } }
    }
    private fun result(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(longCopy, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }
    @Test fun oneLineRequestUsesOneLine() { host(); assertEquals(1, result().lineCount) }
    @Test fun fullSubtitleRemainsInSemantics() { host(); compose.onNodeWithText(longCopy, useUnmergedTree = true).assertExists() }
    @Test fun invalidLimitSafelyUsesOneLine() { host(0); assertEquals(1, result().lineCount) }
    @Test fun defaultStillAllowsMultipleLines() { host(null); assertTrue(result().lineCount > 1) }
    @Test fun darkThemeKeepsOneLinePolicy() { host(dark = true); assertEquals(1, result().lineCount) }
}
