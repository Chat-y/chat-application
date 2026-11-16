package com.example.chat.ui.chats.detail

import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.kit.ColorPickerLogic
import org.junit.Assert.*
import org.junit.Test

/** Live-renderer link tint contract, no URL behavior. Source-only, unrun. */
class ConversationLinkColorTest {
    @Test fun explicitColorWins() {
        val t = ThemePresets.paper
        assertEquals(t.accentColor, resolveConversationLinkColor(t, t.incomingBubbleColor, t.accentColor))
    }
    @Test fun legibleThemeLinkRoleIsKept() {
        ThemePresets.all.forEach { t ->
            if (ColorPickerLogic.contrastRatio(t.linkColor, t.incomingBubbleColor) >= 4.5)
                assertEquals(t.linkColor, resolveConversationLinkColor(t, t.incomingBubbleColor, null))
        }
    }
    @Test fun defaultIncomingLinksMeetContrastInShippedThemes() {
        ThemePresets.all.forEach { t -> assertTrue(t.id,
            ColorPickerLogic.contrastRatio(resolveConversationLinkColor(t, t.incomingBubbleColor, null), t.incomingBubbleColor) >= 4.5) }
    }
    @Test fun defaultOutgoingLinksMeetContrastInShippedThemes() {
        ThemePresets.all.forEach { t -> assertTrue(t.id,
            ColorPickerLogic.contrastRatio(resolveConversationLinkColor(t, t.outgoingBubbleColor, null), t.outgoingBubbleColor) >= 4.5) }
    }
    @Test fun fallbackAlwaysComesFromExistingThemeRoles() {
        ThemePresets.all.forEach { t -> assertTrue(resolveConversationLinkColor(t, t.outgoingBubbleColor, null) in
            listOf(t.linkColor, t.primaryTextColor, t.secondaryTextColor, t.outgoingTextColor, t.incomingTextColor, t.backgroundColor, t.onAccentColor,
                ThemePresets.monochromeLight.accentColor, ThemePresets.monochromeDark.accentColor)) }
    }
}
