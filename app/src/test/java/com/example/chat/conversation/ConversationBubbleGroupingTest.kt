package com.example.chat.ui.settings.conversation

import org.junit.Assert.*
import org.junit.Test

/** Preview-only logical-corner contract, not rendered acceptance. Unrun. */
class ConversationBubbleGroupingTest {
    @Test fun singleIncomingKeepsFourOuterCorners() {
        assertEquals(ConversationGroupedCorners(16,16,16,16), conversationGroupedCorners(false,true,true))
    }
    @Test fun firstIncomingJoinsBottomStartOnly() {
        assertEquals(ConversationGroupedCorners(16,16,4,16), conversationGroupedCorners(false,true,false))
    }
    @Test fun lastIncomingJoinsTopStartOnly() {
        assertEquals(ConversationGroupedCorners(4,16,16,16), conversationGroupedCorners(false,false,true))
    }
    @Test fun middleIncomingJoinsOnlySenderSide() {
        assertEquals(ConversationGroupedCorners(4,16,4,16), conversationGroupedCorners(false,false,false))
    }
    @Test fun outgoingMirrorsLogicalSenderSide() {
        assertEquals(ConversationGroupedCorners(16,16,16,4), conversationGroupedCorners(true,true,false))
        assertEquals(ConversationGroupedCorners(16,4,16,16), conversationGroupedCorners(true,false,true))
    }
}
