package com.example.chat

import com.example.chat.ui.kit.ToastContrastPolicy as Policy
import com.example.chat.ui.kit.ColorPickerLogic
import org.junit.Assert.*
import org.junit.Test

/** Unrun pure-policy tests; color literals are fixtures, not UI defaults. */
class ToastContrastPolicyTest {
    private val dark = 0xFF111111L
    private val light = 0xFFF5F5F5L
    @Test fun preservesAnAlreadyReadableCustomColor() {
        assertEquals(light, Policy.readable(dark, light, listOf(dark, light)))
        assertFalse(Policy.needsFallback(dark, light))
    }
    @Test fun unreadableCustomColorFallsBackWithoutMutatingStorage() {
        assertTrue(Policy.needsFallback(dark, dark))
        val result = Policy.readable(dark, dark, listOf(dark, light))
        assertEquals(light, result)
        assertTrue(ColorPickerLogic.contrastRatio(dark, result) >= 4.5)
    }
    @Test fun missingCustomTextAndReversedThemeCandidatesAreHandled() {
        assertFalse(Policy.needsFallback(light, null))
        assertEquals(dark, Policy.readable(light, null, listOf(light, dark)))
    }
    @Test fun hostileCustomThemeStillSelectsBestAvailableWithoutInventingColors() {
        val gray = 0xFF888888L
        assertEquals(gray, Policy.readable(dark, dark, listOf(dark, gray)))
    }
}
