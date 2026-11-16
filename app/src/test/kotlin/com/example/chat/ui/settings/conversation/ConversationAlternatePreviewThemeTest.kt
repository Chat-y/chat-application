package com.example.chat.ui.settings.conversation

import com.example.chat.core.theme.ThemePresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Five source-only comparison-theme cases; no current theme is persisted. */
class ConversationAlternatePreviewThemeTest {
    @Test fun round1OffReturnsCurrentThemeInstance() {
        val current = ThemePresets.paper
        assertSame(current, conversationAlternatePreviewTheme(current, false))
    }
    @Test fun round2LightModeComparesWithDark() = assertTrue(
        conversationAlternatePreviewTheme(ThemePresets.monochromeLight, true).isDark)
    @Test fun round3DarkModeComparesWithLight() = assertFalse(
        conversationAlternatePreviewTheme(ThemePresets.monochromeDark, true).isDark)
    @Test fun round4ComparisonKeepsUserFontScale() = assertEquals(2.0,
        conversationAlternatePreviewTheme(ThemePresets.paper.copy(fontScale = 2.0), true).fontScale, 0.0)
    @Test fun round5ComparisonKeepsCurrentBubbleRenderer() = assertEquals(
        ThemePresets.ocean.bubbleStyle, conversationAlternatePreviewTheme(ThemePresets.ocean, true).bubbleStyle)
}
