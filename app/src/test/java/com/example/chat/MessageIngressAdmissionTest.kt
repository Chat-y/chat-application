package com.example.chat

import com.example.chat.data.api.ApiPushSyncHelper
import com.example.chat.domain.models.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

/** Pure catch-up policy tests, not a claim about FCM, device delivery or media transport. */
class MessageIngressAdmissionTest {
    private fun conversation(unread: Int = 1) = Conversation("room", ConversationType.direct, "Peer", listOf("alice", "bob"),
        lastMessageText = "Hi", lastMessageTime = Instant.ofEpochSecond(20), lastMessageSenderId = "bob", unreadCount = unread)
    private fun message(id: String, sender: String, at: Long = 20) = ChatMessage(id, "room", sender,
        text = "Hi", createdAt = Instant.ofEpochSecond(at))

    @Test fun incomingIsSymmetricForBothUsersAndNeverIncludesOwnEcho() {
        val rows = listOf(message("a", "alice"), message("b", "bob"))
        val forAlice = ApiPushSyncHelper.findNewIncomingMessagesForPush("alice", conversation(), emptyList(), rows, emptySet())
        val forBob = ApiPushSyncHelper.findNewIncomingMessagesForPush("bob", conversation(), emptyList(), rows, emptySet())
        assertEquals(listOf("b"), forAlice.map { it.id })
        assertEquals(listOf("a"), forBob.map { it.id })
    }

    @Test fun restBeforePushUsesSharedAdmissionWithoutDuplicate() {
        val old = message("old", "bob", 10)
        val new = message("new", "bob")
        val alreadyPosted = mutableSetOf<String>()
        val rest = ApiPushSyncHelper.findNewIncomingMessagesForPush("alice", conversation(), listOf(old), listOf(old, new), alreadyPosted)
        rest.forEach { alreadyPosted.add(it.id) }
        val push = ApiPushSyncHelper.findNewIncomingMessagesForPush("alice", conversation(), listOf(old), listOf(old, new), alreadyPosted)
        assertEquals(listOf("new"), rest.map { it.id })
        assertTrue(push.isEmpty())
    }

    @Test fun deletedSystemAndColdReadHistoryNeverBecomeAlerts() {
        val base = message("new", "bob")
        val rows = listOf(base.copy(id = "deleted", isDeletedForEveryone = true), base.copy(id = "hidden", isDeletedForMe = true),
            base.copy(id = "revoked", isRevokedBySender = true), base.copy(id = "system", type = MessageType.system))
        assertTrue(ApiPushSyncHelper.findNewIncomingMessagesForPush("alice", conversation(), emptyList(), rows, emptySet()).isEmpty())
        assertTrue(ApiPushSyncHelper.findNewIncomingMessagesForPush("alice", conversation(0), emptyList(), listOf(base), emptySet()).isEmpty())
    }
}
