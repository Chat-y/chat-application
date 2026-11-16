package com.example.chat.ui.settings.conversation

import com.example.chat.core.theme.ThemePresets
import com.example.chat.domain.models.ConversationPreferences
import org.junit.Assert.*
import org.junit.Test

/** Existing local support-copy payload. Source-only, unrun. */
class ConversationExtrasSupportValuesTest {
    @Test fun payloadHasExactlyNineColorKeys() {
        assertEquals(ConversationExtraColor.entries.map { it.key }.toSet(), moreOptionsSupportValues(MoreOptionsUiState.from(ConversationPreferences())).keys)
    }
    @Test fun defaultsKeepNullValues() { assertTrue(moreOptionsSupportValues(MoreOptionsUiState.from(ConversationPreferences())).values.all { it == null }) }
    @Test fun customLinkValueIsPreserved() {
        val color = ThemePresets.paper.linkColor
        assertEquals(color, moreOptionsSupportValues(MoreOptionsUiState.from(ConversationPreferences(hyperlinksColor = color)))["hyperlinksColor"])
    }
    @Test fun previewSpeedNeverEntersSupportPayload() {
        val before = ConversationPreferences()
        assertEquals(moreOptionsSupportValues(MoreOptionsUiState.from(before)), moreOptionsSupportValues(MoreOptionsUiState.from(before.copy(voicePlaybackSpeed = 1.5))))
    }
    @Test fun bubbleStyleAndHeaderSettingsNeverEnterPayload() {
        val before = ConversationPreferences()
        assertEquals(moreOptionsSupportValues(MoreOptionsUiState.from(before)), moreOptionsSupportValues(MoreOptionsUiState.from(before.copy(hideContactName = true, bubbleStyle = "unknown"))))
    }
}
