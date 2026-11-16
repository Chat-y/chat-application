package com.example.chat.ui.settings.conversation

import org.junit.Assert.assertEquals
import org.junit.Test

/** Token regression cases, not screenshot/layout acceptance. Source only, unrun. */
class ConversationGridSpacingTest {
    @Test fun round1CardsUseSixteenDpPadding() = assertEquals(16, ConversationPreviewLayout.cardPaddingDp)
    @Test fun round2OuterSettingsMarginsUseSixteenDp() = assertEquals(16, ConversationPreviewLayout.outerPaddingDp)
    @Test fun round3QuoteInsetsUseEightDp() = assertEquals(8, ConversationPreviewLayout.quotePaddingDp)
    @Test fun round4PreviewDetailsHaveEightDpGaps() = assertEquals(8, ConversationPreviewLayout.detailGapDp)
    @Test fun round5NewTokensStayOnEightDpGrid() {
        listOf(ConversationPreviewLayout.cardPaddingDp, ConversationPreviewLayout.quotePaddingDp,
            ConversationPreviewLayout.detailGapDp).forEach { assertEquals(0, it % 8) }
    }
}
