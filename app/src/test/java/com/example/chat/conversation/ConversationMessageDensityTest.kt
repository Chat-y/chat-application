package com.example.chat.ui.chats.detail

import org.junit.Assert.*
import org.junit.Test

/** Exact opted-in gaps, original defaults retained. Source-only, unrun. */
class ConversationMessageDensityTest {
    @Test fun absentGapPreservesOriginalClusterPolicy() {
        assertNull(conversationMessageGap(null))
        assertEquals(2,conversationMessageBottomGap(null,SenderClusterPosition.FIRST))
        assertEquals(12,conversationMessageBottomGap(null,SenderClusterPosition.SINGLE))
    }
    @Test fun compactGapIsFourAcrossAllClusterPositions() {
        SenderClusterPosition.entries.forEach { assertEquals(4,conversationMessageBottomGap(4,it)) }
    }
    @Test fun roomyGapIsEightAcrossAllClusterPositions() {
        SenderClusterPosition.entries.forEach { assertEquals(8,conversationMessageBottomGap(8,it)) }
    }
    @Test fun malformedGapDoesNotChangeDefaults() {
        listOf(0,3,4.1,Long.MAX_VALUE,Double.NaN,"4").forEach { assertNull(conversationMessageGap(it)) }
    }
    @Test fun invalidDirectOverrideAlsoFallsBack() {
        assertEquals(2,conversationMessageBottomGap(9,SenderClusterPosition.MIDDLE))
        assertEquals(12,conversationMessageBottomGap(9,SenderClusterPosition.LAST))
    }
}
