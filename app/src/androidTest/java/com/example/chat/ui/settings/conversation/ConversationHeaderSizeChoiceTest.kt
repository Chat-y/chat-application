package com.example.chat.ui.settings.conversation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.unit.dp
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.theme.ChatyTheme
import org.junit.Rule
import org.junit.Test

/** Chosen existing production minimum, not a fixed-height clamp. Source-only, unrun. */
class ConversationHeaderSizeChoiceTest {
    @get:Rule val compose = createComposeRule()
    @Test fun defaultHeaderHas64dpMinimum() {
        compose.setContent { ChatyTheme(ThemePresets.paper) { ConversationHeaderPreview() } }
        compose.onNodeWithTag("conversation-header", useUnmergedTree = true).assertHeightIsAtLeast(64.dp)
    }
    @Test fun darkHeaderKeepsTheMinimum() {
        compose.setContent { ChatyTheme(ThemePresets.monochromeDark) { ConversationHeaderPreview() } }
        compose.onNodeWithTag("conversation-header", useUnmergedTree = true).assertHeightIsAtLeast(64.dp)
    }
    @Test fun longNameIsNotAReasonToShrinkTargets() {
        compose.setContent { ChatyTheme(ThemePresets.paper) { ConversationHeaderPreview(longSampleName = true) } }
        compose.onNodeWithTag("conversation-header", useUnmergedTree = true).assertHeightIsAtLeast(64.dp)
    }
    @Test fun hiddenIdentityKeepsInteractiveMinimum() {
        compose.setContent { ChatyTheme(ThemePresets.paper) { ConversationHeaderPreview(hideProfilePicture = true, hideContactName = true) } }
        compose.onNodeWithTag("conversation-header", useUnmergedTree = true).assertHeightIsAtLeast(64.dp)
    }
    @Test fun hiddenStatusKeepsInteractiveMinimum() {
        compose.setContent { ChatyTheme(ThemePresets.paper) { ConversationHeaderPreview(disableContactStatus = true) } }
        compose.onNodeWithTag("conversation-header", useUnmergedTree = true).assertHeightIsAtLeast(64.dp)
    }
}
