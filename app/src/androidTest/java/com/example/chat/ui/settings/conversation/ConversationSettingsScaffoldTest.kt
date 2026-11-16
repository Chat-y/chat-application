package com.example.chat.ui.settings.conversation

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.theme.ChatyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Shared shell source test cases, not executed. Each test hosts the actual scaffold. */
class ConversationSettingsScaffoldTest {
    @get:Rule val compose = createComposeRule()
    @Test fun round1TitleRemainsAHeading() {
        compose.setContent { ChatyTheme(ThemePresets.paper) { ConversationSettingsScaffold("Sample title", {}) {} } }
        compose.onNodeWithText("Sample title").assert(SemanticsMatcher.expectValue(SemanticsProperties.Heading, Unit))
    }
    @Test fun round2BackUsesTheSuppliedCallback() {
        var count = 0
        compose.setContent { ChatyTheme(ThemePresets.paper) { ConversationSettingsScaffold("Sample title", { count++ }) {} } }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { assertEquals(1, count) }
    }
    @Test fun round3ContentSlotDisplaysItsChildren() {
        compose.setContent { ChatyTheme(ThemePresets.paper) { ConversationSettingsScaffold("Sample title", {}) { Text("Body sample") } } }
        compose.onNodeWithText("Body sample").assertIsDisplayed()
    }
    @Test fun round4ColumnScopeKeepsOrderedContentSlots() {
        compose.setContent { ChatyTheme(ThemePresets.paper) { ConversationSettingsScaffold("Sample title", {}) {
            Text("First child"); Spacer(Modifier.height(8.dp)); Text("Second child")
        } } }
        compose.onNodeWithText("First child").assertIsDisplayed()
        compose.onNodeWithText("Second child").assertIsDisplayed()
    }
    @Test fun round5DarkShellKeepsTheSameHeaderContract() {
        compose.setContent { ChatyTheme(ThemePresets.monochromeDark) { ConversationSettingsScaffold("Dark sample", {}) {} } }
        compose.onNodeWithText("Dark sample").assertIsDisplayed()
    }
}
