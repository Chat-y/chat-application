package com.example.chat.ui.settings.conversation

import com.example.chat.core.theme.ThemePresets
import org.junit.Assert.*
import org.junit.Test

/** UI-only ARGB read policy. Source cases, unrun. */
class ConversationStoredColorTest {
    @Test fun nullStillMeansThemeDefault() { assertNull(opaqueConversationStoredColor(null)) }
    @Test fun opaqueThemeColorRemainsUnchanged() {
        val color = ThemePresets.paper.accentColor
        assertEquals(color, opaqueConversationStoredColor(color))
    }
    @Test fun transparentRgbIsMadeOpaqueWithoutChangingRgb() {
        val opaque = ThemePresets.paper.accentColor
        val transparent = opaque and ((1L shl 24) - 1)
        assertEquals(opaque, opaqueConversationStoredColor(transparent))
    }
    @Test fun signedAndroidIntAndUnsignedArgbReadTheSame() {
        val color = ThemePresets.paper.accentColor
        assertEquals(opaqueConversationStoredColor(color), opaqueConversationStoredColor(color.toInt().toLong()))
    }
    @Test fun outOfRangeValuesFallBackWithoutWrapping() {
        assertNull(opaqueConversationStoredColor(UInt.MAX_VALUE.toLong() + 1))
        assertNull(opaqueConversationStoredColor(Int.MIN_VALUE.toLong() - 1))
        assertNull(opaqueConversationStoredColor(Long.MAX_VALUE))
    }
}
