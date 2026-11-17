package com.example.chat.data.messaging

import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.MessageReaction

/** Versioned MLS application controls. All targets, values and revisions are
 * inside the encrypted payload. Server reaction aggregates are never applied.
 * Set/remove is idempotent; replay cannot toggle a reaction a second time.
 */
object SecureControlEnvelope {
    const val KEY = "secure_control"
    fun isControl(message: ChatMessage) = message.metadata.containsKey(KEY)
    private data class Reaction(val actor: String, val target: String, val emoji: String?, val revision: Long, val event: String)
    private fun parse(message: ChatMessage): Reaction? {
        val authenticated = message.authenticatedMlsSender ?: return null
        if (authenticated.userId != message.senderId || authenticated.deviceId.isBlank() || message.senderId.isBlank()) return null
        val c = message.metadata[KEY] as? Map<*, *> ?: return null
        if ((c["version"] as? Number)?.toInt() != 1 || c["kind"] != "reaction") return null
        val target = c["target_id"] as? String ?: return null
        val event = c["event_id"] as? String ?: return null
        if (target.isBlank() || target.length > 128 || runCatching { java.util.UUID.fromString(event).toString() == event }.getOrDefault(false).not()) return null
        val n = c["revision"] as? Number ?: return null
        val revision = runCatching { java.math.BigDecimal(n.toString()).longValueExact() }.getOrNull() ?: return null
        if (revision <= 0) return null
        val emoji = when (c["operation"]) {
            "remove" -> null
            "set" -> (c["emoji"] as? String)?.takeIf { it.isNotBlank() && it.length <= 64 } ?: return null
            else -> return null
        }
        return Reaction(message.senderId, target, emoji, revision, event)
    }
    fun latestRevision(messages: List<ChatMessage>, actor: String, target: String): Long =
        messages.mapNotNull(::parse).filter { it.actor == actor && it.target == target }.maxOfOrNull { it.revision } ?: 0

    /** Keep original control rows in encrypted snapshots; project only for UI.
     * Each actor can change only their own reaction, never another member's.
     * Concurrent same-user devices use revision + event UUID as a stable tie-break.
     */
    fun project(messages: List<ChatMessage>): List<ChatMessage> {
        val controls = messages.mapNotNull { m -> parse(m)?.let { m.conversationId to it } }
            .groupBy { Triple(it.first, it.second.target, it.second.actor) }
            .values.mapNotNull { rows -> rows.maxWithOrNull(compareBy<Pair<String, Reaction>> { it.second.revision }.thenBy { it.second.event }) }
        return messages.filterNot(::isControl).map { target ->
            if (target.isDeletedForEveryone || target.isDeletedForMe) return@map target.copy(reactions = emptyList())
            val effective = controls.filter { it.first == target.conversationId && it.second.target == target.id }
            // MLS reactions are derived from authenticated controls only, not a server aggregate.
            val reactions = effective.mapNotNull { (_, r) -> r.emoji?.let { it to r.actor } }
                .groupBy({ it.first }, { it.second }).map { (emoji, actors) -> MessageReaction(emoji, actors.distinct()) }
            target.copy(reactions = reactions)
        }
    }
}
