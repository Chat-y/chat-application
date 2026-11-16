package com.example.chat.ui.settings.conversation

import androidx.compose.ui.graphics.Color
import com.example.chat.core.theme.ThemePresets
import com.example.chat.domain.models.ConversationPreferences
import org.junit.Assert.*
import org.junit.Test

/** Review closeout source cases for the four adapter isolation boundaries. Unrun. */
class ConversationUiStateIsolationTest {
    @Test fun round1HeaderStateIgnoresExtrasChanges() {
        val before = ConversationPreferences()
        assertEquals(ActionBarUiState.from(before), ActionBarUiState.from(before.copy(emojiHeaderColor = ThemePresets.paper.accentColor)))
    }
    @Test fun round2EntryStateIgnoresHeaderChanges() {
        val before = ConversationPreferences()
        assertEquals(EntryStyleUiState.from(before), EntryStyleUiState.from(before.copy(hideContactName = true)))
    }
    @Test fun round3ExtrasStateIgnoresEntryChanges() {
        val before = ConversationPreferences()
        assertEquals(MoreOptionsUiState.from(before), MoreOptionsUiState.from(before.copy(uiButtonsColor = ThemePresets.paper.accentColor)))
    }
    @Test fun round4BubblePreviewIgnoresHeaderChanges() {
        val theme = ThemePresets.paper
        val display = BubblePreviewTheme(theme, Color(theme.surfaceColor), Color(theme.cardColor))
        val before = ConversationPreferences()
        assertEquals(resolveBubblePreview(before, display), resolveBubblePreview(before.copy(hideCallButton = true), display))
    }
    @Test fun round5ExtrasStateIncludesPreviewSpeedDependency() {
        val before = ConversationPreferences()
        assertNotEquals(MoreOptionsUiState.from(before), MoreOptionsUiState.from(before.copy(voicePlaybackSpeed = 1.5)))
    }
}
