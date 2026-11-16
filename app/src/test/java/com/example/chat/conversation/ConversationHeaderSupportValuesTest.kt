package com.example.chat.ui.settings.conversation

import com.example.chat.core.theme.ThemePresets
import com.example.chat.domain.models.ConversationPreferences
import org.junit.Assert.*
import org.junit.Test

/** Existing clipboard payload contract source cases. Unrun. */
class ConversationHeaderSupportValuesTest {
    @Test fun round1ContainsExactlySevenAppearanceKeys() {
        assertEquals(setOf("actionBarColor", "hideProfilePicture", "hideContactName", "hideCallButton",
            "disableContactStatus", "contactStatusBgColor", "contactStatusTextColor"),
            actionBarSupportValues(ActionBarUiState.from(ConversationPreferences())).keys)
    }
    @Test fun round2NullColorsStayNullForThemeDefault() {
        val map = actionBarSupportValues(ActionBarUiState.from(ConversationPreferences()))
        assertNull(map["actionBarColor"]); assertNull(map["contactStatusBgColor"]); assertNull(map["contactStatusTextColor"])
    }
    @Test fun round3ToggleValuesAreNotDisplayStrings() {
        val map = actionBarSupportValues(ActionBarUiState.from(ConversationPreferences(hideCallButton = true)))
        assertEquals(true, map["hideCallButton"]); assertEquals(false, map["hideContactName"])
    }
    @Test fun round4CustomColorIsPreserved() {
        val color = ThemePresets.paper.accentColor
        val map = actionBarSupportValues(ActionBarUiState.from(ConversationPreferences(actionBarColor = color)))
        assertEquals(color, map["actionBarColor"])
    }
    @Test fun round5UnrelatedPreferencesNeverEnterPayload() {
        val before = ConversationPreferences()
        assertEquals(actionBarSupportValues(ActionBarUiState.from(before)), actionBarSupportValues(ActionBarUiState.from(
            before.copy(makeTextSelectable = true, emojiHeaderColor = ThemePresets.paper.accentColor))))
    }
}
