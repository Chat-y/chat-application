package com.example.chat.ui.settings.conversation

import com.example.chat.core.theme.ThemePresets
import org.junit.Assert.*
import org.junit.Test

/** Pure resolver contract for the Material-role values supplied by the UI. Source-only, unrun. */
class ConversationMaterialFallbackTest {
    private val t = ThemePresets.paper
    @Test fun absentHeaderUsesSuppliedSurface() {
        assertEquals(t.surfaceColor, resolveConversationHeaderColors(null, null, null, t.surfaceColor, t.cardColor, t.secondaryTextColor).bar)
    }
    @Test fun absentStatusUsesSuppliedContainerRole() {
        assertEquals(t.cardColor, resolveConversationHeaderColors(null, null, null, t.surfaceColor, t.cardColor, t.secondaryTextColor).statusBackground)
    }
    @Test fun absentStatusTextUsesSuppliedOnSurfaceVariant() {
        assertEquals(t.secondaryTextColor, resolveConversationHeaderColors(null, null, null, t.surfaceColor, t.cardColor, t.secondaryTextColor).statusText)
    }
    @Test fun customHeaderDoesNotFollowMaterialDefault() {
        assertEquals(t.accentColor, resolveConversationHeaderColors(t.accentColor, null, null, t.surfaceColor, t.cardColor, t.secondaryTextColor).bar)
    }
    @Test fun customStatusValuesWinOverRoleChanges() {
        val dark = ThemePresets.monochromeDark
        val result = resolveConversationHeaderColors(null, t.accentColor, t.primaryTextColor, dark.surfaceColor, dark.cardColor, dark.secondaryTextColor)
        assertEquals(t.accentColor, result.statusBackground); assertEquals(t.primaryTextColor, result.statusText)
    }
}
