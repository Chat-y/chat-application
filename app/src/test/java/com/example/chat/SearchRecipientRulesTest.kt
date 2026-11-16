package com.example.chat

import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.ConversationType
import com.example.chat.ui.search.SearchRecipientRules
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class SearchRecipientRulesTest {
    private fun room(id: String = "room", group: Boolean = false, members: List<String> = listOf("owner", "peer")) =
        Conversation(id, if (group) ConversationType.group else ConversationType.direct, "Fixture", members,
            lastMessageText = "", lastMessageTime = Instant.EPOCH, lastMessageSenderId = "")
    @Test fun knownProtectedDirectBlocksPrecreation() {
        assertTrue(SearchRecipientRules.hasProtectedDirect("peer", listOf(room())) { true })
        assertFalse(SearchRecipientRules.hasProtectedDirect("other", listOf(room())) { true })
        assertFalse(SearchRecipientRules.hasProtectedDirect("peer", listOf(room())) { false })
    }
    @Test fun groupMembershipIsNotAProtectedDirectMatch() {
        assertFalse(SearchRecipientRules.hasProtectedDirect("peer", listOf(room(group = true))) { true })
        assertTrue(SearchRecipientRules.hasProtectedDirect("", emptyList()) { false })
    }
    @Test fun expectedDirectRoomRequiresExactOwnerRecipientPair() {
        assertTrue(SearchRecipientRules.validDirectRoom(room(), "owner", "peer"))
        assertFalse(SearchRecipientRules.validDirectRoom(room(members = listOf("owner", "stranger")), "owner", "peer"))
        assertFalse(SearchRecipientRules.validDirectRoom(room(members = listOf("owner", "peer", "extra")), "owner", "peer"))
    }
    @Test fun invalidRoomTypeAndIdentityFailClosed() {
        assertFalse(SearchRecipientRules.validDirectRoom(room(group = true), "owner", "peer"))
        assertFalse(SearchRecipientRules.validDirectRoom(room(id = ""), "owner", "peer"))
        assertFalse(SearchRecipientRules.validDirectRoom(room(), null, "peer"))
        assertFalse(SearchRecipientRules.validDirectRoom(room(), "owner", "owner"))
    }
}
