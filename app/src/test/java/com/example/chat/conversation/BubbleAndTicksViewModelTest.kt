package com.example.chat.ui.settings.conversation

import androidx.compose.ui.graphics.Color
import com.example.chat.core.theme.ThemePresets
import com.example.chat.domain.models.ConversationPreferences
import org.junit.Assert.*
import org.junit.Test

/** Theme/adapter source cases. Not executed. */
class BubbleAndTicksViewModelTest {
    private val paper = ThemePresets.paper
    private fun theme() = BubblePreviewTheme(paper, Color(paper.surfaceColor), Color(paper.cardColor))
    @Test fun round1DefaultSurfacesFollowThemeInputs() {
        val state = resolveBubblePreview(ConversationPreferences(), theme())
        assertEquals(Color(paper.surfaceColor), state.convBg)
        assertEquals(Color(paper.cardColor), state.leftBubbleBg)
    }
    @Test fun round2ExplicitColorsWinOverFallbacks() {
        val color = ThemePresets.monochromeDark.accentColor
        val state = resolveBubblePreview(ConversationPreferences(rightBubbleColor = color, quotedNameColor = color), theme())
        assertEquals(Color(color), state.rightBubbleBg)
        assertEquals(Color(color), state.quotedName)
    }
    @Test fun round3OutgoingTimeFollowsResolvedTextUnlessOverridden() {
        val state = resolveBubblePreview(ConversationPreferences(rightChatBubbleTextColor = paper.primaryTextColor), theme())
        assertEquals(state.rightTextCol, state.rightTimeCol)
        val custom = resolveBubblePreview(ConversationPreferences(rightBubbleTimeColor = paper.accentColor), theme())
        assertEquals(Color(paper.accentColor), custom.rightTimeCol)
    }
    @Test fun round4ThemeChangesRefreshPreviewWithoutWritingPreferences() {
        var writes = 0
        val model = BubbleAndTicksViewModel({ ConversationPreferences() }, { writes++ }, {}, {}, theme())
        val dark = ThemePresets.monochromeDark
        model.setTheme(BubblePreviewTheme(dark, Color(dark.surfaceColor), Color(dark.cardColor)))
        assertEquals(Color(dark.surfaceColor), model.uiState.value.preview.convBg)
        assertEquals(0, writes)
    }
    @Test fun round5EditsPreserveFreshUnrelatedFields() {
        var latest = ConversationPreferences()
        val model = BubbleAndTicksViewModel({ latest }, { latest = it }, {}, {}, theme())
        latest = latest.copy(hideCallButton = true)
        model.edit { it.copy(removeReadMore = true) }
        assertTrue(latest.hideCallButton)
        assertTrue(model.uiState.value.preview.removeReadMore)
    }
}
