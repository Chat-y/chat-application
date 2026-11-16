package com.example.chat.ui.chats.detail

import org.junit.Assert.*
import org.junit.Test

/** UI-only metrics and opt-in contracts, not device/rendered evidence. Unrun. */
class ConversationComposerAppearanceTest {
    @Test fun compactDefaultsOffAndKeepsEight() {
        val value = ConversationComposerAppearance.fromMap(emptyMap());assertFalse(value.compact);assertEquals(8,value.verticalPaddingDp)
    }
    @Test fun explicitCompactUsesFour() { assertEquals(4,ConversationComposerAppearance.fromMap(mapOf(ConversationComposerAppearance.COMPACT to true)).verticalPaddingDp) }
    @Test fun malformedCompactFlagDoesNotOptIn() { assertEquals(8,ConversationComposerAppearance.fromMap(mapOf(ConversationComposerAppearance.COMPACT to "true")).verticalPaddingDp) }
    @Test fun compactDoesNotEnableFloating() { assertFalse(ConversationComposerAppearance(compact = true).floating) }
    @Test fun disablingCompactPreservesFloatingChoice() {
        val before = mapOf<String,Any?>(ConversationComposerAppearance.COMPACT to true,ConversationComposerAppearance.FLOATING to true)
        val after = ConversationComposerAppearance.fromMap(before + (ConversationComposerAppearance.COMPACT to false))
        assertEquals(8,after.verticalPaddingDp);assertTrue(after.floating)
    }
    @Test fun floatingDefaultsOffAndAddsNoMarginOrShadow() {
        val value = ConversationComposerAppearance.fromMap(emptyMap());assertFalse(value.floating);assertEquals(0,value.floatingMarginDp);assertEquals(0,value.floatingElevationDp)
    }
    @Test fun explicitFloatingHasExactMetrics() {
        val value = ConversationComposerAppearance.fromMap(mapOf(ConversationComposerAppearance.FLOATING to true))
        assertEquals(8,value.floatingMarginDp);assertEquals(6,value.floatingElevationDp)
    }
    @Test fun malformedFloatingFlagDoesNotOptIn() { assertFalse(ConversationComposerAppearance.fromMap(mapOf(ConversationComposerAppearance.FLOATING to 1)).floating) }
    @Test fun floatingDoesNotEnableCompact() { assertEquals(8,ConversationComposerAppearance(floating = true).verticalPaddingDp) }
    @Test fun unknownMapKeysHaveNoEffect() { assertEquals(ConversationComposerAppearance(),ConversationComposerAppearance.fromMap(mapOf("entryStyle" to "RC-Flat Floating"))) }
}
