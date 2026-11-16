package com.example.chat.ui.settings.conversation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.theme.ChatyTheme
import org.junit.Rule
import org.junit.Test

/** Preview-only slot source cases. No production mute/pin state changes. Unrun. */
class ConversationHeaderIndicatorTest {
    @get:Rule val compose = createComposeRule()
    @Test fun defaultReservesNoIndicatorNodes() {
        compose.setContent { ChatyTheme(ThemePresets.paper) { ConversationHeaderPreview() } }
        compose.onNodeWithTag("conversation-header-sample-muted", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("conversation-header-sample-pinned", useUnmergedTree = true).assertDoesNotExist()
    }
    @Test fun mutedSampleRendersItsSlot() {
        compose.setContent { ChatyTheme(ThemePresets.paper) { ConversationHeaderPreview(sampleMuted = true) } }
        compose.onNodeWithTag("conversation-header-sample-muted", useUnmergedTree = true).assertIsDisplayed()
    }
    @Test fun pinnedSampleRendersItsSlot() {
        compose.setContent { ChatyTheme(ThemePresets.paper) { ConversationHeaderPreview(samplePinned = true) } }
        compose.onNodeWithTag("conversation-header-sample-pinned", useUnmergedTree = true).assertIsDisplayed()
    }
    @Test fun bothSamplesRetainTheName() {
        compose.setContent { ChatyTheme(ThemePresets.paper) { ConversationHeaderPreview(sampleMuted = true, samplePinned = true) } }
        compose.onNodeWithTag("conversation-header-name", useUnmergedTree = true).assertIsDisplayed()
    }
    @Test fun longNameKeepsTheIndicatorNodes() {
        compose.setContent { ChatyTheme(ThemePresets.monochromeDark) { ConversationHeaderPreview(sampleMuted = true, samplePinned = true, longSampleName = true) } }
        compose.onNodeWithTag("conversation-header-sample-muted", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("conversation-header-sample-pinned", useUnmergedTree = true).assertExists()
    }
}
