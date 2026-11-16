package com.example.chat

import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.ConversationType
import com.example.chat.ui.chats.newchat.NewChatRules
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

/** Pure callback-boundary fixtures. Not transport, Compose, or server authorization coverage. */
class NewChatCompletionBoundaryTest {
    private fun room(group: Boolean = false, id: String = "room", members: List<String> = listOf("self", "peer")) =
        Conversation(id = id, type = if (group) ConversationType.group else ConversationType.direct,
            title = "Fixture", participantIds = members, lastMessageText = "",
            lastMessageTime = Instant.EPOCH, lastMessageSenderId = "")

    @Test fun directRoomMustBelongToExpectedPair() {
        assertTrue(NewChatRules.validConversation(room(), "self", setOf("peer"), false))
        assertFalse(NewChatRules.validConversation(room(members = listOf("self", "stranger")), "self", setOf("peer"), false))
        assertFalse(NewChatRules.validConversation(room(members = listOf("self", "peer", "third")), "self", setOf("peer"), false))
    }
    @Test fun blankRoomAndMissingOwnerFailClosed() {
        assertFalse(NewChatRules.validConversation(room(id = " "), "self", setOf("peer"), false))
        assertFalse(NewChatRules.validConversation(room(), null, setOf("peer"), false))
        assertFalse(NewChatRules.validConversation(room(members = listOf("peer")), "self", setOf("peer"), false))
    }
    @Test fun groupNeedsExpectedTypeAndEveryRequestedMember() {
        val group = room(group = true, members = listOf("self", "peer", "second"))
        assertTrue(NewChatRules.validConversation(group, "self", setOf("peer", "second"), true))
        assertFalse(NewChatRules.validConversation(group, "self", setOf("peer", "missing"), true))
        assertFalse(NewChatRules.validConversation(room(), "self", setOf("peer"), true))
    }
    @Test fun emptyOrSelfSelectionIsInvalid() {
        assertFalse(NewChatRules.validConversation(room(), "self", emptySet(), false))
        assertFalse(NewChatRules.validConversation(room(), "self", setOf("self"), false))
        assertFalse(NewChatRules.validConversation(room(), "self", setOf(""), false))
    }
    @Test fun limitAppliesToFinalPrefixedTitle() {
        assertTrue(NewChatRules.validFinalTitle("Team", true, 1, 15))
        assertFalse(NewChatRules.validFinalTitle("Team", true, 1, 14))
        assertFalse(NewChatRules.validFinalTitle("", false, 1, 15))
        assertFalse(NewChatRules.validFinalTitle("", true, 123, 10))
    }
    @Test fun broadcastPrefixRequiresAWordBoundary() {
        assertEquals("Broadcast: Broadcasting", NewChatRules.effectiveGroupTitle("Broadcasting", true, 1))
        assertEquals("Broadcast: Team", NewChatRules.effectiveGroupTitle("Broadcast: Team", true, 1))
        assertEquals("broadcast news", NewChatRules.effectiveGroupTitle("broadcast news", true, 1))
    }
}
