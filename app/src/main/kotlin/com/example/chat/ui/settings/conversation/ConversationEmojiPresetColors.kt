package com.example.chat.ui.settings.conversation

import com.example.chat.core.theme.ThemeConfig
import com.example.chat.core.theme.ThemePresets

internal enum class ConversationEmojiPreset { Light, Dark, Accent }
internal data class ConversationEmojiPresetColors(val header: Long, val icons: Long, val background: Long)

/** Exact three-field projection; no new setting, automatic default, or persistence format. */
internal fun conversationEmojiPresetColors(preset: ConversationEmojiPreset, current: ThemeConfig): ConversationEmojiPresetColors {
    val theme = when (preset) {
        ConversationEmojiPreset.Light -> ThemePresets.monochromeLight
        ConversationEmojiPreset.Dark -> ThemePresets.monochromeDark
        ConversationEmojiPreset.Accent -> current
    }
    return ConversationEmojiPresetColors(
        if (preset == ConversationEmojiPreset.Accent) theme.accentColor else theme.surfaceColor,
        if (preset == ConversationEmojiPreset.Accent) theme.onAccentColor else theme.primaryTextColor,
        theme.surfaceColor,
    )
}
