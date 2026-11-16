package com.example.chat

import com.example.chat.ui.chats.newchat.NewChatRules
import org.junit.Assert.*
import org.junit.Test

class NewChatRequestReservationTest {
    @Test fun sameImmutableRecipientCannotBeReservedUnderAnotherUsername() {
        val sending = mutableSetOf<String>()
        assertTrue(NewChatRules.canReserveRequest("peer", "self", emptySet(), sending, emptySet()))
        sending += "peer"
        assertFalse(NewChatRules.canReserveRequest("peer", "self", emptySet(), sending, emptySet()))
        assertTrue(NewChatRules.canReserveRequest("other", "self", emptySet(), sending, emptySet()))
    }
    @Test fun pendingAndUncertainRecipientsCannotBeRetriedBlind() {
        assertFalse(NewChatRules.canReserveRequest("peer", "self", setOf("peer"), emptySet(), emptySet()))
        assertFalse(NewChatRules.canReserveRequest("peer", "self", emptySet(), emptySet(), setOf("peer")))
    }
    @Test fun missingIdentityAndSelfAreNeverReservable() {
        assertFalse(NewChatRules.canReserveRequest("peer", null, emptySet(), emptySet(), emptySet()))
        assertFalse(NewChatRules.canReserveRequest("", "self", emptySet(), emptySet(), emptySet()))
        assertFalse(NewChatRules.canReserveRequest("self", "self", emptySet(), emptySet(), emptySet()))
    }
}
