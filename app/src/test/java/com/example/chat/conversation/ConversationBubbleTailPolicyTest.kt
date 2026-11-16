package com.example.chat.ui.chats.detail

import org.junit.Assert.*
import org.junit.Test

/** Existing stock painter tail vs joined shape only. Source-only, unrun. */
class ConversationBubbleTailPolicyTest {
    @Test fun singleMessageKeepsTailInBothModes() {
        assertFalse(conversationClassicGroupedShape(SenderClusterPosition.SINGLE,false))
        assertFalse(conversationClassicGroupedShape(SenderClusterPosition.SINGLE,true))
    }
    @Test fun firstMessageKeepsJoinedShapeInBothModes() {
        assertTrue(conversationClassicGroupedShape(SenderClusterPosition.FIRST,false))
        assertTrue(conversationClassicGroupedShape(SenderClusterPosition.FIRST,true))
    }
    @Test fun middleMessageKeepsJoinedShapeInBothModes() {
        assertTrue(conversationClassicGroupedShape(SenderClusterPosition.MIDDLE,false))
        assertTrue(conversationClassicGroupedShape(SenderClusterPosition.MIDDLE,true))
    }
    @Test fun offPreservesPreviousLastJoinedShape() { assertTrue(conversationClassicGroupedShape(SenderClusterPosition.LAST,false)) }
    @Test fun explicitOptInUsesExistingTailPainterOnLast() { assertFalse(conversationClassicGroupedShape(SenderClusterPosition.LAST,true)) }
}
