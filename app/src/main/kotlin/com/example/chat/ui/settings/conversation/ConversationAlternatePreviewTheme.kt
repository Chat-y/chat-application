package com.example.chat.ui.settings.conversation

import com.example.chat.core.theme.ThemeConfig
import com.example.chat.core.theme.ThemePresets

/** Preview-only comparison; never applies a theme or writes preferences. */
internal fun conversationAlternatePreviewTheme(current: ThemeConfig, otherMode: Boolean): ThemeConfig {
    if (!otherMode) return current
    val alternate = if (current.isDark) ThemePresets.monochromeLight else ThemePresets.monochromeDark
    return alternate.copy(fontScale = current.fontScale, layoutMode = current.layoutMode,
        bubbleStyle = current.bubbleStyle, deliveryTickStyle = current.deliveryTickStyle)
}
