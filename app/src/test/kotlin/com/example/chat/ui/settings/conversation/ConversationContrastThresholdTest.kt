package com.example.chat.ui.settings.conversation

import com.example.chat.ui.kit.ColorPickerLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Known pairs are synthetic test fixtures, not production UI colours. Source only, unrun. */
class ConversationContrastThresholdTest {
    @Test fun round1BlackWhitePairHasTwentyOneToOneContrast() = assertEquals(21.0,
        ColorPickerLogic.contrastRatio(0xFF000000L, 0xFFFFFFFFL), 0.0001)
    @Test fun round2IdenticalColoursHaveOneToOneContrast() = assertEquals(1.0,
        ColorPickerLogic.contrastRatio(0xFF808080L, 0xFF808080L), 0.0001)
    @Test fun round3NonTextThresholdIncludesEquality() {
        assertTrue(isConversationContrastLow(2.999f, 3f))
        assertFalse(isConversationContrastLow(3f, 3f))
        assertFalse(isConversationContrastLow(3.001f, 3f))
    }
    @Test fun round4TextThresholdIncludesEquality() {
        assertTrue(isConversationContrastLow(4.499f, 4.5f))
        assertFalse(isConversationContrastLow(4.5f, 4.5f))
        assertFalse(isConversationContrastLow(4.501f, 4.5f))
    }
    @Test fun round5KnownPairIsSymmetric() = assertEquals(
        ColorPickerLogic.contrastRatio(0xFF000000L, 0xFF808080L),
        ColorPickerLogic.contrastRatio(0xFF808080L, 0xFF000000L), 0.0001)
}
