package com.example.chat.ui.chats.home

import com.example.chat.domain.models.Conversation

/** R2-17: IDs only, never a retained copy of message text or a durable queue.
 * Read rows stay until End. New unread rows do not join an existing session.
 * The screen owns account/route lifetime and rechecks privacy on each projection. */
data class HomeTriageSnapshot(val conversationIds: List<String>) {
    companion object {
        fun capture(visible: List<Conversation>, mayShow: (String) -> Boolean): HomeTriageSnapshot =
            HomeTriageSnapshot(visible.asSequence()
                .filter { it.unreadCount > 0 && !it.isArchived && !it.isTask && mayShow(it.id) }
                .map { it.id }.distinct().toList())
    }

    /** Resolve fresh account data. Missing/protected IDs are not shown and are not
     * treated as completed. Reading a conversation never removes it from this list. */
    fun resolve(current: List<Conversation>, mayShow: (String) -> Boolean): List<Conversation> {
        val byId = current.distinctBy { it.id }.associateBy { it.id }
        return conversationIds.mapNotNull { id ->
            byId[id]?.takeIf { !it.isTask && mayShow(id) }
        }
    }
}

/** R4-03: an adjacent swap only. Null rejects stale/duplicate IDs and edges.
 * The owner must durably save the full order using the unchanged folders array.
 * This function does not save, rename, remove, or re-create any folder. */
fun homeAdjacentFolderOrder(ids: List<String>, folderId: String, step: Int): List<String>? {
    if (step != -1 && step != 1) return null
    if (ids.any { it.isBlank() } || ids.distinct().size != ids.size) return null
    val index = ids.indexOf(folderId)
    val target = index + step
    if (index < 0 || target !in ids.indices) return null
    return ids.toMutableList().apply {
        this[index] = ids[target]
        this[target] = folderId
    }
}
