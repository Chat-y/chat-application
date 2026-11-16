package com.example.chat.ui.settings.conversation

import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.kit.ColorPickerLogic
import org.junit.Assert.*
import org.junit.Test

/** Source palette contracts. Shipped source values also inspected numerically; tests unrun. */
class ConversationBubblePaletteTest {
    @Test fun paletteContainsEightChoices() { assertEquals(8, conversationBubbleColorPairs().size) }
    @Test fun everyPairMeetsTextContrastMinimum() {
        conversationBubbleColorPairs().forEach { assertTrue(it.id, ColorPickerLogic.contrastRatio(it.foreground, it.background) >= 4.5) }
    }
    @Test fun backgroundChoicesAreDistinct() { assertEquals(8, conversationBubbleColorPairs().map { it.background }.distinct().size) }
    @Test fun pairsComeFromExistingThemes() {
        val themes = ThemePresets.all.associateBy { it.id }
        conversationBubbleColorPairs().forEach { pair -> assertEquals(themes.getValue(pair.id).outgoingBubbleColor, pair.background) }
    }
    @Test fun idsAreStableAndLabelsAreNotPopularityClaims() {
        val pairs = conversationBubbleColorPairs()
        assertEquals(pairs.size, pairs.map { it.id }.distinct().size)
        assertTrue(pairs.all { it.name.isNotBlank() && !it.name.contains("popular", ignoreCase = true) })
    }
}
