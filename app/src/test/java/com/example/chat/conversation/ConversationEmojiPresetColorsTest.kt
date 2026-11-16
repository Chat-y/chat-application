package com.example.chat.ui.settings.conversation

import com.example.chat.core.theme.ThemePresets
import org.junit.Assert.*
import org.junit.Test

/** Five source-only preset projection cases. Unrun. */
class ConversationEmojiPresetColorsTest {
    @Test fun exactlyThreeExplicitChoices() {
        assertEquals(listOf(ConversationEmojiPreset.Light, ConversationEmojiPreset.Dark, ConversationEmojiPreset.Accent), ConversationEmojiPreset.entries.toList())
    }
    @Test fun lightUsesExistingLightRoles() {
        val theme = ThemePresets.monochromeLight
        assertEquals(ConversationEmojiPresetColors(theme.surfaceColor, theme.primaryTextColor, theme.surfaceColor),
            conversationEmojiPresetColors(ConversationEmojiPreset.Light, ThemePresets.paper))
    }
    @Test fun darkUsesExistingDarkRoles() {
        val theme = ThemePresets.monochromeDark
        assertEquals(ConversationEmojiPresetColors(theme.surfaceColor, theme.primaryTextColor, theme.surfaceColor),
            conversationEmojiPresetColors(ConversationEmojiPreset.Dark, ThemePresets.paper))
    }
    @Test fun accentUsesCurrentThemeNotFixedColor() {
        val theme = ThemePresets.paper
        assertEquals(ConversationEmojiPresetColors(theme.accentColor, theme.onAccentColor, theme.surfaceColor),
            conversationEmojiPresetColors(ConversationEmojiPreset.Accent, theme))
    }
    @Test fun explicitLightAndDarkIgnoreCurrentTheme() {
        listOf(ConversationEmojiPreset.Light, ConversationEmojiPreset.Dark).forEach { preset ->
            assertEquals(conversationEmojiPresetColors(preset, ThemePresets.monochromeDark),
                conversationEmojiPresetColors(preset, ThemePresets.paper))
        }
    }
}
