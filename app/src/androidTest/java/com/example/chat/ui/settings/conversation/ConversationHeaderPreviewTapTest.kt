package com.example.chat.ui.settings.conversation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.theme.ChatyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Preview callbacks only. No call placement permitted by these sample controls. Source-only, unrun. */
class ConversationHeaderPreviewTapTest {
    @get:Rule val compose = createComposeRule()
    @Test fun audioPreviewRoutesToSettingCallback() {
        var taps = 0
        compose.setContent { ChatyTheme(ThemePresets.paper) { ConversationHeaderPreview(onCallPreviewTap = { taps++ }) } }
        compose.onNodeWithTag("conversation-header-audio", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(1, taps) }
    }
    @Test fun videoPreviewRoutesToSameSettingCallback() {
        var taps = 0
        compose.setContent { ChatyTheme(ThemePresets.paper) { ConversationHeaderPreview(onCallPreviewTap = { taps++ }) } }
        compose.onNodeWithTag("conversation-header-video", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(1, taps) }
    }
    @Test fun defaultCallbackRemainsSafe() {
        compose.setContent { ChatyTheme(ThemePresets.paper) { ConversationHeaderPreview() } }
        compose.onNodeWithTag("conversation-header-audio", useUnmergedTree = true).performClick()
    }
    @Test fun hiddenCallControlsHaveNoTapNode() {
        compose.setContent { ChatyTheme(ThemePresets.paper) { ConversationHeaderPreview(hideCallButton = true) } }
        compose.onNodeWithTag("conversation-header-audio", useUnmergedTree = true).assertDoesNotExist()
    }
    @Test fun darkPreviewStillRoutesWithoutCalling() {
        var taps = 0
        compose.setContent { ChatyTheme(ThemePresets.monochromeDark) { ConversationHeaderPreview(onCallPreviewTap = { taps++ }) } }
        compose.onNodeWithTag("conversation-header-video", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(1, taps) }
    }
}
