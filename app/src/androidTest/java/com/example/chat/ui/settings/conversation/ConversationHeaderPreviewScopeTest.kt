package com.example.chat.ui.settings.conversation

// assertDoesNotExist is a SemanticsNodeInteraction member; no top-level import.
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.theme.ChatyTheme
import org.junit.Rule
import org.junit.Test

/** Source-only tests target the extracted preview, not a duplicate test-only renderer. */
class ConversationHeaderPreviewScopeTest {
    @get:Rule val compose = createComposeRule()
    @Test fun round1DefaultPreviewKeepsName() {
        compose.setContent { ChatyTheme(ThemePresets.monochromeLight) { ConversationHeaderPreview() } }
        compose.onNodeWithTag("conversation-header-name", useUnmergedTree = true).assertIsDisplayed()
    }
    @Test fun round2DarkPreviewKeepsAvatar() {
        compose.setContent { ChatyTheme(ThemePresets.monochromeDark) { ConversationHeaderPreview() } }
        compose.onNodeWithTag("conversation-header-avatar", useUnmergedTree = true).assertIsDisplayed()
    }
    @Test fun round3LongNameDoesNotRemoveCalls() {
        compose.setContent { ChatyTheme(ThemePresets.monochromeLight) { ConversationHeaderPreview(longSampleName = true) } }
        compose.onNodeWithTag("conversation-header-audio", useUnmergedTree = true).assertIsDisplayed()
    }
    @Test fun round4HiddenStatusDoesNotLeaveAFocusNode() {
        compose.setContent { ChatyTheme(ThemePresets.monochromeLight) { ConversationHeaderPreview(disableContactStatus = true) } }
        compose.onNodeWithTag("conversation-header-status", useUnmergedTree = true).assertDoesNotExist()
    }
    @Test fun round5HiddenIdentityDoesNotLeaveNameOrAvatarNodes() {
        compose.setContent { ChatyTheme(ThemePresets.monochromeLight) {
            ConversationHeaderPreview(hideProfilePicture = true, hideContactName = true)
        } }
        compose.onNodeWithTag("conversation-header-name", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("conversation-header-avatar", useUnmergedTree = true).assertDoesNotExist()
    }
}
