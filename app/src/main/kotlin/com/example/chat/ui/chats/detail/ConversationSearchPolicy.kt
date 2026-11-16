package com.example.chat.ui.chats.detail

import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.MessageType
import java.util.Locale

/** Search only text that the user may currently read. No hits leak deleted or view-once text. */
object ConversationSearchPolicy {
    fun matchingIds(messages: List<ChatMessage>, query: String): List<String> {
        val needle = query.trim().lowercase(Locale.ROOT)
        if (needle.isEmpty()) return emptyList()
        return messages.asSequence()
            .filter { it.type != MessageType.system && !it.isDeletedForMe && !it.isDeletedForEveryone && !it.isRevokedBySender }
            .filter { message ->
                // Unexpected representations fail closed just like a protected message.
                when (val flag = message.metadata["view_once"]) {
                    null, false -> true
                    is String -> flag.equals("false", ignoreCase = true)
                    is Number -> flag.toDouble() == 0.0
                    else -> false
                }
            }
            .filter { it.text.lowercase(Locale.ROOT).contains(needle) }
            .map { it.id }.distinct().toList()
    }
}
