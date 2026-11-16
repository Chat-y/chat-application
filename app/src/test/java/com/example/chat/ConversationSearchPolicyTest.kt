package com.example.chat

import com.example.chat.ui.chats.detail.ConversationSearchPolicy
import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.MessageType
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class ConversationSearchPolicyTest {
    private fun message(id: String = "m") = ChatMessage(id, "room", "sender", text = "Hello WORLD", createdAt = Instant.EPOCH)
    @Test fun matchingIsCaseInsensitiveAndBlankHasNoHits() {
        assertEquals(listOf("m"), ConversationSearchPolicy.matchingIds(listOf(message()), " world "))
        assertTrue(ConversationSearchPolicy.matchingIds(listOf(message()), " ").isEmpty())
    }
    @Test fun allDeletedVariantsAndSystemRowsAreExcluded() {
        for (m in listOf(message().copy(isDeletedForMe = true), message().copy(isDeletedForEveryone = true),
            message().copy(isRevokedBySender = true), message().copy(type = MessageType.system))) {
            assertTrue(ConversationSearchPolicy.matchingIds(listOf(m), "hello").isEmpty())
        }
    }
    @Test fun protectedAndMalformedFlagsFailClosed() {
        for (flag in listOf(true, "true", "TRUE", 1, "unexpected", emptyMap<String, String>())) {
            assertTrue(ConversationSearchPolicy.matchingIds(listOf(message().copy(metadata = mapOf("view_once" to flag))), "hello").isEmpty())
        }
    }
    @Test fun readableFalseValuesAndDuplicateIdentityAreHandled() {
        for (flag in listOf(false, "false", 0)) {
            val m = message().copy(metadata = mapOf("view_once" to flag))
            assertEquals(listOf("m"), ConversationSearchPolicy.matchingIds(listOf(m, m), "hello"))
        }
    }
}
