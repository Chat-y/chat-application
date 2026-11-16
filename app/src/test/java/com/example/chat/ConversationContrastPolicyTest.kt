package com.example.chat

import com.example.chat.ui.kit.ColorPickerLogic
import org.junit.Assert.*
import org.junit.Test

class ConversationContrastPolicyTest {
    @Test fun blackWhiteAndEqualPairsHaveKnownRatios() {
        assertEquals(21.0, ColorPickerLogic.contrastRatio(0xFF000000L, 0xFFFFFFFFL), 0.0001)
        assertEquals(1.0, ColorPickerLogic.contrastRatio(0xFF123456L, 0xFF123456L), 0.0001)
    }
    @Test fun thresholdsAreNotRoundedBeforeWarnings() {
        assertTrue(ColorPickerLogic.hasContrastWarning(4.4999))
        assertFalse(ColorPickerLogic.hasContrastWarning(4.5))
        assertFalse(ColorPickerLogic.hasContrastWarning(null))
        assertTrue(ColorPickerLogic.contrastRatio(0xFFFFFF00L, 0xFFFFFFFFL) < 3.0)
    }
    @Test fun swapOrderPreservesRatio() {
        assertEquals(ColorPickerLogic.contrastRatio(0xFF13579BL, 0xFFABCDEF), ColorPickerLogic.contrastRatio(0xFFABCDEF, 0xFF13579BL), 0.00001)
    }
}
