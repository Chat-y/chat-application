package com.example.chat.ui.chats.detail

import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.kit.ColorPickerLogic
import org.junit.Assert.*
import org.junit.Test

/** Five source policy cases each for auto contrast, icon tint, divider. Unrun. */
class ConversationHeaderColorOptionsTest {
    private val light = ThemePresets.monochromeLight.accentColor
    private val dark = ThemePresets.monochromeDark.accentColor
    @Test fun autoContrastDefaultsOff() { assertFalse(ConversationHeaderAppearance.fromMap(emptyMap()).autoContrast) }
    @Test fun autoContrastRequiresExplicitBoolean() {
        assertTrue(ConversationHeaderAppearance.fromMap(mapOf(ConversationHeaderAppearance.AUTO_CONTRAST to true)).autoContrast)
        assertFalse(ConversationHeaderAppearance.fromMap(mapOf(ConversationHeaderAppearance.AUTO_CONTRAST to "true")).autoContrast)
    }
    @Test fun autoContrastCandidatesAreExistingMonochromeTokens() {
        ThemePresets.all.forEach { assertTrue(conversationHeaderContrastForeground(it.surfaceColor) in listOf(light,dark)) }
    }
    @Test fun autoContrastSelectsBestCandidate() {
        ThemePresets.all.forEach { theme ->
            val bg = theme.surfaceColor
            assertEquals(maxOf(ColorPickerLogic.contrastRatio(light,bg),ColorPickerLogic.contrastRatio(dark,bg)),
                ColorPickerLogic.contrastRatio(conversationHeaderContrastForeground(bg),bg),0.000001)
        }
    }
    @Test fun autoContrastClearsNormalTextForShippedSurfaces() {
        ThemePresets.all.forEach { assertTrue(it.id,ColorPickerLogic.contrastRatio(conversationHeaderContrastForeground(it.surfaceColor),it.surfaceColor) >= 4.5) }
    }
    @Test fun nullIconTintStillMeansDefault() { assertNull(ConversationHeaderAppearance.fromMap(emptyMap()).iconsColor) }
    @Test fun explicitOpaqueIconTintIsKept() {
        val color = ThemePresets.paper.accentColor
        assertEquals(color,ConversationHeaderAppearance.fromMap(mapOf(ConversationHeaderAppearance.ICONS_COLOR to color)).iconsColor)
    }
    @Test fun signedAndroidIconTintMatchesUnsignedArgb() {
        val color = ThemePresets.paper.accentColor
        assertEquals(color,ConversationHeaderAppearance.fromMap(mapOf(ConversationHeaderAppearance.ICONS_COLOR to color.toInt().toLong())).iconsColor)
    }
    @Test fun malformedIconTintsFallBackRatherThanWrap() {
        listOf(Long.MAX_VALUE,Double.NaN,Double.POSITIVE_INFINITY,12.5,"blue").forEach {
            assertNull(ConversationHeaderAppearance.fromMap(mapOf(ConversationHeaderAppearance.ICONS_COLOR to it)).iconsColor)
        }
    }
    @Test fun iconTintResetDoesNotDisableOtherOptIns() {
        val current = mapOf(ConversationHeaderAppearance.AUTO_CONTRAST to true, ConversationHeaderAppearance.ICONS_COLOR to ThemePresets.paper.accentColor)
        val reset = ConversationHeaderAppearance.fromMap(current + (ConversationHeaderAppearance.ICONS_COLOR to null))
        assertNull(reset.iconsColor);assertTrue(reset.autoContrast)
    }
    @Test fun dividerDefaultsOff() { assertFalse(ConversationHeaderAppearance.fromMap(emptyMap()).divider) }
    @Test fun dividerRequiresExplicitBoolean() {
        assertTrue(ConversationHeaderAppearance.fromMap(mapOf(ConversationHeaderAppearance.DIVIDER to true)).divider)
        assertFalse(ConversationHeaderAppearance.fromMap(mapOf(ConversationHeaderAppearance.DIVIDER to 1)).divider)
    }
    @Test fun dividerColorClearsThreeOnShippedHeaderSurfaces() {
        ThemePresets.all.forEach { assertTrue(it.id,ColorPickerLogic.contrastRatio(conversationHeaderContrastForeground(it.surfaceColor),it.surfaceColor) >= 3.0) }
    }
    @Test fun dividerColorClearsThreeOnExistingAccentColors() {
        ThemePresets.all.forEach { assertTrue(it.id,ColorPickerLogic.contrastRatio(conversationHeaderContrastForeground(it.accentColor),it.accentColor) >= 3.0) }
    }
    @Test fun dividerResetIsOffAndPreservesDefaultNameWeight() {
        val reset = ConversationHeaderAppearance.fromMap(ConversationHeaderAppearance.defaultValues())
        assertFalse(reset.divider);assertEquals(800,reset.nameWeightValue())
    }
}
