package com.example.chat.data.api

import com.example.chat.data.local.SnapshotCodec
import com.example.chat.data.messaging.SnapshotCacheStore
import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.UserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Encrypted, account-scoped local snapshot coordinator for [ApiBackendService].
 *
 * Stores conversations, users, and per-room message lists under distinct scope
 * keys in [SnapshotCacheStore] so data persists continuously across restarts and
 * re-logins without interfering with [com.example.chat.data.messaging.ConversationsRepository].
 */
internal class ApiSnapshotCacheCoordinator(
    private val store: SnapshotCacheStore,
) {
    suspend fun persistConversations(namespace: String, conversations: List<Conversation>) {
        if (namespace.isBlank()) return
        val payload = conversations.map { SnapshotCodec.conversationToJson(it) }
        // Previous cache fallback swallowed cancellation: runCatching { store.writeJson(namespace, CONVERSATIONS_SCOPE, payload) }
        cacheAttempt { store.writeJson(namespace, CONVERSATIONS_SCOPE, payload) }
    }

    suspend fun readConversations(namespace: String): List<Conversation>? {
        if (namespace.isBlank()) return null
        // Previous cache fallback swallowed cancellation: val raw = runCatching { store.readJson(namespace, CONVERSATIONS_SCOPE) }.getOrNull() ?: return null
        val raw = cacheAttempt { store.readJson(namespace, CONVERSATIONS_SCOPE) } ?: return null
        val list = raw as? List<*> ?: return null
        return list.mapNotNull { item ->
            // Previous cache fallback swallowed cancellation: asStringMap(item)?.let { runCatching { SnapshotCodec.conversationFromJson(it) }.getOrNull() }
            asStringMap(item)?.let { cacheAttempt { SnapshotCodec.conversationFromJson(it) } }
        }.filter { it.id.isNotBlank() && !it.isTask }
    }

    suspend fun persistUsers(namespace: String, users: List<UserProfile>) {
        if (namespace.isBlank()) return
        val payload = users.map { it.toJson() }
        // Previous cache fallback swallowed cancellation: runCatching { store.writeJson(namespace, USERS_SCOPE, payload) }
        cacheAttempt { store.writeJson(namespace, USERS_SCOPE, payload) }
    }

    suspend fun readUsers(namespace: String): List<UserProfile>? {
        if (namespace.isBlank()) return null
        // Previous cache fallback swallowed cancellation: val raw = runCatching { store.readJson(namespace, USERS_SCOPE) }.getOrNull() ?: return null
        val raw = cacheAttempt { store.readJson(namespace, USERS_SCOPE) } ?: return null
        val list = raw as? List<*> ?: return null
        return list.mapNotNull { item ->
            // Previous cache fallback swallowed cancellation: asStringMap(item)?.let { runCatching { UserProfile.fromJson(it) }.getOrNull() }
            asStringMap(item)?.let { cacheAttempt { UserProfile.fromJson(it) } }
        }.filter { it.id.isNotBlank() }
    }

    suspend fun persistMessages(namespace: String, roomId: String, messages: List<ChatMessage>) {
        if (namespace.isBlank() || roomId.isBlank()) return
        // Previous input-tail cap could discard newer messages from descending input:
        // val bounded = if (messages.size > MAX_CACHED_MESSAGES_PER_ROOM) {
        //     messages.takeLast(MAX_CACHED_MESSAGES_PER_ROOM)
        // } else {
        //     messages
        // }
        val bounded = messages.sortedBy { it.createdAt }.takeLast(MAX_CACHED_MESSAGES_PER_ROOM)
        val payload = bounded.map { SnapshotCodec.chatMessageToJson(it) }
        // Previous cache fallback swallowed cancellation: runCatching { store.writeJson(namespace, messagesScope(roomId), payload) }
        cacheAttempt { store.writeJson(namespace, messagesScope(roomId), payload) }
    }

    suspend fun readMessages(namespace: String, roomId: String): List<ChatMessage>? {
        if (namespace.isBlank() || roomId.isBlank()) return null
        // Previous cache fallback swallowed cancellation: val raw = runCatching { store.readJson(namespace, messagesScope(roomId)) }.getOrNull() ?: return null
        val raw = cacheAttempt { store.readJson(namespace, messagesScope(roomId)) } ?: return null
        val list = raw as? List<*> ?: return null
        return list.mapNotNull { item ->
            // Previous cache fallback swallowed cancellation: asStringMap(item)?.let { runCatching { SnapshotCodec.chatMessageFromJson(it) }.getOrNull() }
            asStringMap(item)?.let { cacheAttempt { SnapshotCodec.chatMessageFromJson(it) } }
        // Previous read sorted but accepted unbounded legacy lists:
        // }.filter { it.id.isNotBlank() }.sortedBy { it.createdAt }
        }.filter { it.id.isNotBlank() }.sortedBy { it.createdAt }.takeLast(MAX_CACHED_MESSAGES_PER_ROOM)
    }

    /** Cache corruption and I/O failures remain misses, but cancellation is control flow.
     * The post-check also catches lower cache implementations that swallow cancellation. */
    private suspend fun <T> cacheAttempt(action: suspend () -> T): T? {
        currentCoroutineContext().ensureActive()
        return try {
            val result = action()
            currentCoroutineContext().ensureActive()
            result
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            null
        }
    }

    private fun asStringMap(value: Any?): Map<String, Any?>? {
        if (value !is Map<*, *>) return null
        return value.entries.associate { (k, v) -> k.toString() to v }
    }

    companion object {
        private const val CONVERSATIONS_SCOPE = "api_conversations_v1"
        private const val USERS_SCOPE = "api_users_v1"
        private const val MAX_CACHED_MESSAGES_PER_ROOM = 300

        private fun messagesScope(roomId: String): String = "api_messages_v1_$roomId"
    }
}
