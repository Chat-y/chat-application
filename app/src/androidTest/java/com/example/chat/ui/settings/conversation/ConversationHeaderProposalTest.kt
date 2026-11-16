package com.example.chat.ui.settings.conversation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
// assertDoesNotExist is a SemanticsNodeInteraction member; no top-level import.
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.chat.core.theme.ThemePresets
import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.ConversationType
import com.example.chat.ui.chats.ConversationHeader
import com.example.chat.ui.kit.ChatyAppBar
import com.example.chat.ui.theme.ChatyTheme
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** Released proposal cases, source only. No device run or screenshot claim. */
class ConversationHeaderProposalTest {
    @get:Rule val compose = createComposeRule()

    @Composable private fun Header(avatar: Boolean = true, name: Boolean = true,
        calls: Boolean = true, status: Boolean = true) {
        val theme = ThemePresets.whatsappIosLight
        ChatyTheme(theme) {
            ConversationHeader(
                theme = theme,
                conversation = Conversation("synthetic-preview", ConversationType.direct, "Sample contact",
                    emptyList(), lastMessageText = "", lastMessageTime = Instant.EPOCH, lastMessageSenderId = ""),
                presenceText = "Sample presence", presenceIsOnline = true,
                showHeaderAvatar = avatar, showHeaderName = name, showPresenceLine = status,
                showHeaderCalls = calls, headerBarColor = null, presencePillColor = null,
                presenceTextColor = null, callsAllowed = true, onStartCall = {}, connectionState = null,
                onConnectionDetailsTap = {}, onBack = {}, onOpenInfo = {}, menuOpen = false,
                onMenuOpenChange = {}, onSearch = {}, onOpenWallpaperPicker = {}, onOpenAppearance = null,
                onToggleMute = null, ghostModeEnabled = false, onToggleGhostMode = null,
                secureChannelEnabled = false, onToggleSecureChannel = null, onBlockContact = null,
                onClearConversation = {},
            )
        }
    }
    @Test fun round1ScreenTitleCanBeFoundAsAHeading() {
        compose.setContent {
            ChatyTheme(ThemePresets.whatsappIosLight) {
                ChatyAppBar("Synthetic settings", titleModifier = Modifier.semantics { heading() })
            }
        }
        compose.onNodeWithText("Synthetic settings").assert(SemanticsMatcher.expectValue(SemanticsProperties.Heading, Unit))
    }
    @Test fun round2AvatarTagFollowsVisibility() {
        compose.setContent { Header(avatar = false) }
        compose.onNodeWithTag("conversation-header-avatar", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("conversation-header-name", useUnmergedTree = true).assertIsDisplayed()
    }
    @Test fun round3HiddenNameIsAbsentFromTheTree() {
        compose.setContent { Header(name = false) }
        compose.onNodeWithTag("conversation-header-name", useUnmergedTree = true).assertDoesNotExist()
    }
    @Test fun round4HiddenCallButtonsAreAbsentFromTheTree() {
        compose.setContent { Header(calls = false) }
        compose.onNodeWithTag("conversation-header-audio", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("conversation-header-video", useUnmergedTree = true).assertDoesNotExist()
    }
    @Test fun round5HiddenStatusIsAbsentFromTheTree() {
        compose.setContent { Header(status = false) }
        compose.onNodeWithTag("conversation-header-status", useUnmergedTree = true).assertDoesNotExist()
    }
}
