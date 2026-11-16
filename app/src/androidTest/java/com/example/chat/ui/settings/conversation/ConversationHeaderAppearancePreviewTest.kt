package com.example.chat.ui.settings.conversation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.unit.dp
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.chats.detail.ConversationHeaderAppearance
import com.example.chat.ui.theme.ChatyTheme
import org.junit.Rule
import org.junit.Test

/** Actual shared header source cases. Unrun, not visual acceptance. */
class ConversationHeaderAppearancePreviewTest {
    @get:Rule val compose = createComposeRule()
    @Test fun defaultAvatarIsStill38dp() {
        compose.setContent { ChatyTheme(ThemePresets.paper) { ConversationHeaderPreview() } }
        compose.onNodeWithTag("conversation-header-avatar", useUnmergedTree = true).assertWidthIsEqualTo(38.dp)
    }
    @Test fun explicitSmallAvatarIs32dp() {
        compose.setContent { ChatyTheme(ThemePresets.paper) { ConversationHeaderPreview(appearance = ConversationHeaderAppearance(optionsEnabled = true, avatarSizeDp = 32)) } }
        compose.onNodeWithTag("conversation-header-avatar", useUnmergedTree = true).assertWidthIsEqualTo(32.dp)
    }
    @Test fun explicitLargeAvatarIs44dp() {
        compose.setContent { ChatyTheme(ThemePresets.paper) { ConversationHeaderPreview(appearance = ConversationHeaderAppearance(optionsEnabled = true, avatarSizeDp = 44)) } }
        compose.onNodeWithTag("conversation-header-avatar", useUnmergedTree = true).assertWidthIsEqualTo(44.dp)
    }
    @Test fun centeredTitleKeepsCallActions() {
        compose.setContent { ChatyTheme(ThemePresets.paper) { ConversationHeaderPreview(appearance = ConversationHeaderAppearance(optionsEnabled = true, centeredTitle = true)) } }
        compose.onNodeWithTag("conversation-header-audio", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("conversation-header-video", useUnmergedTree = true).assertIsDisplayed()
    }
    @Test fun regularNameAndSquareAvatarKeepHeaderVisible() {
        compose.setContent { ChatyTheme(ThemePresets.monochromeDark) { ConversationHeaderPreview(appearance = ConversationHeaderAppearance(optionsEnabled = true, nameWeight = "regular", roundedSquareAvatar = true)) } }
        compose.onNodeWithTag("conversation-header-name", useUnmergedTree = true).assertIsDisplayed()
    }
}
