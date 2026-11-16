package com.example.chat.ui.chats.detail

import com.example.chat.core.theme.ThemeConfig
import com.example.chat.ui.kit.ColorPickerLogic

/** Color only. Does not detect links, choose URLs or change navigation.
 * Explicit user color wins. Default keeps the theme link role when legible on this bubble;
 * otherwise selects the highest-contrast existing theme foreground, never a fixed color. */
internal fun resolveConversationLinkColor(theme: ThemeConfig, bubbleBackground: Long, custom: Long?): Long {
    com.example.chat.ui.kit.opaqueConversationArgb(custom)?.let { return it }
    if (ColorPickerLogic.contrastRatio(theme.linkColor, bubbleBackground) >= 4.5) return theme.linkColor
    return listOf(theme.primaryTextColor, theme.secondaryTextColor, theme.outgoingTextColor,
        theme.incomingTextColor, theme.backgroundColor, theme.onAccentColor,
        com.example.chat.core.theme.ThemePresets.monochromeLight.accentColor,
        com.example.chat.core.theme.ThemePresets.monochromeDark.accentColor).maxBy {
        ColorPickerLogic.contrastRatio(it, bubbleBackground)
    }
}
