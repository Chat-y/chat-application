package com.example.chat.ui.settings.conversation

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
// assertDoesNotExist is a SemanticsNodeInteraction member; no top-level import.
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.theme.ChatyTheme
import org.junit.Rule
import org.junit.Test

/** Actual screen warning component. Source-only, unrun. */
class ConversationHeaderIdentityWarningTest {
    @get:Rule val compose = createComposeRule()
    @Test fun round1VisibleIdentityHasNoWarning() {
        compose.setContent { ChatyTheme(ThemePresets.paper) { ConversationHeaderIdentityWarning(false, false) } }
        compose.onNodeWithTag("conversation-header-identity-warning").assertDoesNotExist()
    }
    @Test fun round2HiddenAvatarAloneHasNoWarning() {
        compose.setContent { ChatyTheme(ThemePresets.paper) { ConversationHeaderIdentityWarning(true, false) } }
        compose.onNodeWithTag("conversation-header-identity-warning").assertDoesNotExist()
    }
    @Test fun round3HiddenNameAloneHasNoWarning() {
        compose.setContent { ChatyTheme(ThemePresets.paper) { ConversationHeaderIdentityWarning(false, true) } }
        compose.onNodeWithTag("conversation-header-identity-warning").assertDoesNotExist()
    }
    @Test fun round4ToggleBothShowsTheWarningAndRestoreHidesIt() {
        val avatar = mutableStateOf(false); val name = mutableStateOf(false)
        compose.setContent { ChatyTheme(ThemePresets.paper) { Column {
            Button(onClick = { avatar.value = !avatar.value }) { Text("Toggle avatar") }
            Button(onClick = { name.value = !name.value }) { Text("Toggle name") }
            ConversationHeaderIdentityWarning(avatar.value, name.value)
        } } }
        compose.onNodeWithText("Toggle avatar").performClick()
        compose.onNodeWithText("Toggle name").performClick()
        compose.onNodeWithTag("conversation-header-identity-warning").assertIsDisplayed()
        compose.onNodeWithText("Toggle name").performClick()
        compose.onNodeWithTag("conversation-header-identity-warning").assertDoesNotExist()
    }
    @Test fun round5DarkModeKeepsTheWarning() {
        compose.setContent { ChatyTheme(ThemePresets.monochromeDark) { ConversationHeaderIdentityWarning(true, true) } }
        compose.onNodeWithTag("conversation-header-identity-warning").assertIsDisplayed()
    }
}
