package com.example.chat.ui.settings.conversation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** New cases for the released preview-spacing proposals. Source only, not executed. */
class ConversationPreviewLayoutTest {
    @Test fun round1AllPreviewSpacingUsesEightDpGrid() {
        listOf(ConversationPreviewLayout.innerPaddingDp, ConversationPreviewLayout.outerPaddingDp,
            ConversationPreviewLayout.inlineGapDp, ConversationPreviewLayout.sectionGapDp).forEach {
            assertTrue(it > 0)
            assertEquals(0, it % 8)
        }
    }
    @Test fun round2OuterMarginsAreSixteenDp() = assertEquals(16, ConversationPreviewLayout.outerPaddingDp)
    @Test fun round3InlineActionsHaveAnEightDpGap() = assertEquals(8, ConversationPreviewLayout.inlineGapDp)
    @Test fun round4CompactPreviewKeepsEightDpInnerPadding() = assertEquals(8, ConversationPreviewLayout.innerPaddingDp)
    @Test fun round5SectionGapMatchesOuterMargin() =
        assertEquals(ConversationPreviewLayout.outerPaddingDp, ConversationPreviewLayout.sectionGapDp)
}
