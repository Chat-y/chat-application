package com.example.chat.ui.settings.conversation

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.theme.ChatyTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Optional title token contract. Source-only, unrun. */
class ConversationTitleTokenTest {
    @get:Rule val compose = createComposeRule()
    private fun layout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("Token title", useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }
    @Test fun defaultKeepsExistingKitSize() {
        compose.setContent { ChatyTheme(ThemePresets.paper) { ConversationSettingsScaffold("Token title", {}) {} } }
        assertEquals(16.5.sp, layout().layoutInput.style.fontSize)
    }
    @Test fun suppliedTokenControlsTitleSize() {
        var expected = TextStyle.Default
        compose.setContent { ChatyTheme(ThemePresets.paper) { expected = MaterialTheme.typography.titleMedium
            ConversationSettingsScaffold("Token title", {}, titleTextStyle = expected) {} } }
        assertEquals(expected.fontSize, layout().layoutInput.style.fontSize)
    }
    @Test fun suppliedTokenControlsTitleWeight() {
        compose.setContent { ChatyTheme(ThemePresets.paper) { ConversationSettingsScaffold("Token title", {}, titleTextStyle = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium)) {} } }
        assertEquals(FontWeight.Medium, layout().layoutInput.style.fontWeight)
    }
    @Test fun darkModeStillUsesTheToken() {
        compose.setContent { ChatyTheme(ThemePresets.monochromeDark) { ConversationSettingsScaffold("Token title", {}, titleTextStyle = MaterialTheme.typography.titleMedium) {} } }
        assertTrue(layout().layoutInput.style.fontSize.value > 0f)
    }
    @Test fun titleStaysOneLine() {
        compose.setContent { ChatyTheme(ThemePresets.paper) { ConversationSettingsScaffold("Token title", {}, titleTextStyle = MaterialTheme.typography.titleMedium) {} } }
        assertEquals(1, layout().lineCount)
    }
}
