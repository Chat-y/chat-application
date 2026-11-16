package com.example.chat.ui.search

import com.example.chat.domain.models.Conversation

/** Local preflight is not server authorization; keep the post-response privacy check too. */
internal object SearchRecipientRules {
    fun hasProtectedDirect(targetId: String, conversations: List<Conversation>, isProtected: (String) -> Boolean): Boolean =
        targetId.isBlank() || conversations.any { it.isDirect && targetId in it.participantIds && isProtected(it.id) }

    fun validDirectRoom(room: Conversation, ownerId: String?, targetId: String): Boolean =
        !ownerId.isNullOrBlank() && targetId.isNotBlank() && targetId != ownerId && room.id.isNotBlank() &&
            room.isDirect && room.participantIds.toSet() == setOf(ownerId, targetId)
}
