package com.example.chat.ui.settings.conversation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.theme.ChatyTheme
import org.junit.Rule
import org.junit.Test

/** Layout source cases only. Host device/window rendering remains unverified. */
class ConversationAdaptivePaneTest {
    @get:Rule val compose = createComposeRule()
    private fun host(width: Int, pinned: Boolean = false, dark: Boolean = false) {
        compose.setContent { ChatyTheme(if (dark) ThemePresets.monochromeDark else ThemePresets.paper) {
            Box(Modifier.requiredSize(width.dp, 500.dp)) { ConversationAdaptiveSettingsBody(
                compactPinnedPreview = pinned, preview = { Text("Preview slot") }, controls = { Text("Control slot") }) }
        } }
    }
    @Test fun round1PhoneKeepsOneOrderedBody() { host(360); compose.onNodeWithTag("conversation-settings-one-pane").assertIsDisplayed() }
    @Test fun round2ExactBreakpointUsesTwoPanes() { host(840); compose.onNodeWithTag("conversation-settings-two-pane").assertExists() }
    @Test fun round3WideLayoutHasBothNamedPanes() { host(1000); compose.onNodeWithTag("conversation-settings-preview-pane").assertExists(); compose.onNodeWithTag("conversation-settings-controls-pane").assertExists() }
    @Test fun round4BubbleCompactModeKeepsPinnedPreview() { host(360, pinned = true); compose.onNodeWithTag("conversation-settings-compact-pinned").assertIsDisplayed(); compose.onNodeWithText("Preview slot").assertIsDisplayed() }
    @Test fun round5DarkModeKeepsControlContent() { host(360, dark = true); compose.onNodeWithText("Control slot").assertIsDisplayed() }
}
