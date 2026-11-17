package com.example.chat.data.messaging

import com.example.chat.core.crypto.MlsEncryptedPayload
import com.example.chat.core.crypto.MlsEncryptor
import com.example.chat.domain.models.AuthenticatedMlsRevision
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** No raw decrypted projection cache. Only explicitly authored non-expiring text
 * edits enter the encrypted owner journal. Server hashes are consistency checks,
 * never authentication; the MLS application context binds message/revision. */
internal class ImmutableMlsRevisions(
    private val rpc: SupabaseRpc,
    private val cache: SnapshotCacheStore,
    private val namespace: String,
    private val userId: String,
    private val mls: MlsEncryptor,
    private val current: () -> Unit,
) {
    data class Revision(val seq: Int, val hash: String, val previous: String?, val editor: String,
        val device: String, val group: String, val epoch: Int, val ciphertext: String, val client: String?)
    data class Chain(val messageId: String, val revisions: List<Revision>, val timerOff: Boolean) {
        val seq get() = revisions.lastOrNull()?.seq ?: 0
        val hash get() = revisions.lastOrNull()?.hash
    }
    suspend fun chain(messageId: String): Chain = revisionLock(namespace, messageId) { chainLocked(messageId) }

    private suspend fun chainLocked(messageId: String): Chain {
        current()
        require(messageId.matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))) { "Invalid revision message ID." }
        check(cache.readJsonStrict(namespace, "revision_retired_$messageId") == null) { "Message revisions were retired locally." }
        val raw = try {
            rpc.rpc("get_mls_message_revisions", mapOf("p_message_id" to messageId)) as? Map<*, *>
                ?: error("Immutable revision chain is unavailable.")
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            current()
            eraseAuthoredPayloads(cache, namespace, messageId)
            throw error
        }
        current()
        val timerOff = timerOff(raw)
        if (timerOff != true) {
            eraseAuthoredPayloads(cache, namespace, messageId)
            error("Room timer is on or unverified. Authored revision records were erased; edits are blocked.")
        }
        check(raw["message_id"] == messageId && int(raw["base_revision_seq"]) == 0) { "Revision chain message mismatch." }
        val rows = raw["revisions"] as? List<*> ?: error("Invalid revision list.")
        check(rows.size <= 4096) { "Revision chain exceeds the safe client limit." }
        val revisions = rows.mapIndexed { index, item ->
            val row = item as? Map<*, *> ?: error("Invalid revision.")
            Revision(int(row["revision_seq"]), row["revision_hash"] as? String ?: error("Missing revision hash."),
                row["prev_revision_hash"] as? String, row["editor_user_id"] as? String ?: error("Missing editor."),
                row["editor_device_id"] as? String ?: error("Missing device."), row["mls_group_id"] as? String ?: error("Missing group."),
                int(row["mls_epoch"]), row["ciphertext"] as? String ?: error("Missing ciphertext."), row["client_revision_id"] as? String).also {
                check(it.seq == index + 1 && row["hash_version"] == "chaty-edit-v1") { "Revision ordering/version mismatch." }
            }
        }
        var previous: String? = null
        for (revision in revisions) {
            check(revision.previous == previous && revision.hash == contentHash(messageId, revision)) { "Revision chain hash mismatch." }
            previous = revision.hash
        }
        check(int(raw["current_revision_seq"]) == revisions.size) { "Incomplete revision chain." }
        if (revisions.isEmpty() && cache.readJsonStrict(namespace, "revision_keys_$messageId") != null) {
            // A delete racing GET can return an empty chain. Once this client
            // authored a revision, empty is erasure, never a restored original.
            eraseAuthoredPayloads(cache, namespace, messageId)
            error("Previously edited message has no live revisions. Local authored content was erased.")
        }
        val seen = cache.readJsonStrict(namespace, "revision_seen_$messageId") as? Map<*, *>
        if (seen != null) {
            val seq = int(seen["seq"])
            check(seq <= revisions.size && (seq == 0 || revisions[seq - 1].hash == seen["hash"])) { "Revision chain rollback or fork." }
        }
        return Chain(messageId, revisions, true)
    }
    suspend fun project(room: String, messageId: String, sender: String, expectedSeq: Int,
        expectedHash: String?, cipher: String, device: String, group: String?, epoch: Int?): Pair<Map<String, Any?>, AuthenticatedMlsRevision> =
        revisionLock(namespace, messageId) { projectLocked(room, messageId, sender, expectedSeq, expectedHash, cipher, device, group, epoch) }

    private suspend fun projectLocked(room: String, messageId: String, sender: String, expectedSeq: Int,
        expectedHash: String?, cipher: String, device: String, group: String?, epoch: Int?): Pair<Map<String, Any?>, AuthenticatedMlsRevision> {
        val chain = chainLocked(messageId)
        check(chain.seq == expectedSeq && chain.hash == expectedHash && chain.seq > 0) { "Message revision is stale. Refresh the authoritative chain." }
        val revision = chain.revisions.last()
        check(revision.ciphertext == cipher && revision.editor == sender && revision.device == device && revision.group == group && revision.epoch == epoch) { "Revision attribution mismatch." }
        val local = revision.client?.takeIf { sender == userId && it.matches(Regex("[A-Za-z0-9_-]{1,100}")) }
            ?.let { cache.readJsonStrict(namespace, "own_revision_$it") as? Map<*, *> }
        current()
        val payload = if (local != null) {
            check(local["message_id"] == messageId && local["room_id"] == room && local["ciphertext"] == cipher &&
                local["device_id"] == revision.device && local["group_id"] == revision.group && int(local["epoch"]) == revision.epoch &&
                int(local["base_seq"]) == revision.seq - 1 && local["previous_hash"] == revision.previous && local["user_id"] == sender) { "Own revision binding mismatch." }
            @Suppress("UNCHECKED_CAST")
            (local["payload"] as? Map<String, Any?>) ?: error("Own revision payload unavailable.")
        } else {
            val decoded = mls.decryptPayload(room, cipher)
            check(decoded["_authenticated_sender_user_id"] == revision.editor && decoded["_authenticated_sender_device_id"] == revision.device) { "MLS revision editor mismatch." }
            decoded
        }
        current()
        val context = payload["revision_context"] as? Map<*, *> ?: error("Missing authenticated revision context.")
        check(context["message_id"] == messageId && context["conversation_id"] == room && int(context["revision_seq"]) == revision.seq &&
            int(context["base_revision_seq"]) == revision.seq - 1 && context["prev_revision_hash"] == revision.previous &&
            (revision.client == null || context["client_revision_id"] == revision.client)) { "Authenticated revision target mismatch." }
        val metadata = payload["metadata"] as? Map<*, *> ?: emptyMap<Any?, Any?>()
        check(payload["type"] == "text" && !restricted(metadata)) { "Only non-expiring text revisions can be projected." }
        check(cache.readJsonStrict(namespace, "revision_retired_$messageId") == null) { "Message was deleted during revision hydration." }
        cache.writeJsonStrict(namespace, "revision_seen_$messageId", mapOf("seq" to revision.seq, "hash" to revision.hash))
        current()
        return payload to AuthenticatedMlsRevision(messageId, room, revision.seq, revision.hash)
    }
    suspend fun edit(room: String, messageId: String, text: String, metadata: Map<String, Any?>) =
        revisionLock(namespace, messageId) { editLocked(room, messageId, text, metadata) }

    private suspend fun editLocked(room: String, messageId: String, text: String, metadata: Map<String, Any?>) {
        check(!restricted(metadata)) { "Expiring/view-once messages cannot be edited." }
        val currentChain = chainLocked(messageId)
        val pendingKey = "revision_pending_$messageId"
        @Suppress("UNCHECKED_CAST")
        var record = cache.readJsonStrict(namespace, pendingKey) as? Map<String, Any?>
        if (record != null && record["completed"] != true) {
            val client = record["client_id"] as? String ?: error("Invalid pending edit.")
            val accepted = currentChain.revisions.firstOrNull { it.client == client && it.editor == userId }
            if (accepted != null) {
                check(matches(accepted, record)) { "Accepted edit differs from saved operation." }
                remember(messageId, accepted)
                cache.writeJsonStrict(namespace, pendingKey, record + ("completed" to true))
                if ((record["payload"] as? Map<*, *>)?.get("text") == text) return
                record = null
            } else if (currentChain.seq > int(record["base_seq"])) {
                // The authoritative append-only chain passed this operation's CAS
                // base without accepting its client key. It can no longer commit.
                // Rebase this explicit edit request, never claim the old edit won.
                cache.writeJsonStrict(namespace, pendingKey, record + mapOf("completed" to true, "superseded" to true))
                record = null
            }
        } else record = null
        if (record != null) {
            check(record["message_id"] == messageId && record["room_id"] == room && record["user_id"] == userId &&
                (record["payload"] as? Map<*, *>)?.get("text") == text) { "A different edit still needs reconciliation. Retry the same text first." }
            check(int(record["base_seq"]) == currentChain.seq) { "Edit base changed. Refresh and rebase explicitly." }
            mls.assertStoredCipherReady(room, MlsEncryptedPayload(record["group_id"] as String, int(record["epoch"]), record["ciphertext"] as String))
        } else {
            val client = UUID.randomUUID().toString()
            val payload = mapOf("type" to "text", "text" to text, "metadata" to metadata,
                "revision_context" to mapOf("message_id" to messageId, "conversation_id" to room,
                    "base_revision_seq" to currentChain.seq, "revision_seq" to currentChain.seq + 1,
                    "prev_revision_hash" to currentChain.hash, "client_revision_id" to client))
            val encrypted = mls.encryptEditedPayload(room, payload)
            current()
            val device = mls.currentDeviceId ?: error("Editor device unavailable.")
            record = mapOf("message_id" to messageId, "room_id" to room, "user_id" to userId, "client_id" to client,
                "device_id" to device, "base_seq" to currentChain.seq, "previous_hash" to currentChain.hash,
                "group_id" to encrypted.groupId, "epoch" to encrypted.epoch, "ciphertext" to encrypted.ciphertext, "payload" to payload)
            // Recheck under the backend room lock after encryption too. The
            // response is not a lease: acknowledgement policy is checked again.
            val refreshed = chainLocked(messageId)
            check(refreshed.timerOff && refreshed.seq == currentChain.seq && refreshed.hash == currentChain.hash) { "Room policy or edit base changed before persistence." }
            // Persist before HTTP. A lost response retries the same immutable operation.
            val keys = (cache.readJsonStrict(namespace, "revision_keys_$messageId") as? List<*>)?.filterIsInstance<String>().orEmpty()
            cache.writeJsonStrict(namespace, "revision_keys_$messageId", (keys + client).distinct())
            cache.writeJsonStrict(namespace, "own_revision_$client", record)
            cache.writeJsonStrict(namespace, pendingKey, record)
        }
        current()
        val operation = checkNotNull(record)
        check(cache.readJsonStrict(namespace, "revision_retired_$messageId") == null) { "Message was deleted before edit dispatch." }
        val acknowledgement = try {
            rpc.rpc("edit_mls_message_v1", mapOf("p_message_id" to messageId, "p_sender_device_id" to operation["device_id"],
            "p_group_id" to operation["group_id"], "p_epoch" to operation["epoch"], "p_ciphertext" to operation["ciphertext"],
            "p_client_revision_id" to operation["client_id"], "p_base_revision_seq" to operation["base_seq"]))
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            current()
            val policy = try { rpc.rpc("get_mls_message_revisions", mapOf("p_message_id" to messageId)) as? Map<*, *> }
                catch (failure: Throwable) {
                    if (failure is kotlinx.coroutines.CancellationException) throw failure
                    current(); null
                }
            current()
            if (policy?.get("message_id") != messageId || timerOff(policy) != true || (policy?.get("revisions") as? List<*>)?.isNotEmpty() != true)
                eraseAuthoredPayloads(cache, namespace, messageId)
            throw error
        }
        current()
        if (timerOff(acknowledgement as? Map<*, *>) != true) {
            eraseAuthoredPayloads(cache, namespace, messageId)
            error("Edit may have committed, but the room timer changed or was not verified. Local authored content was erased; refresh history, do not resend as new.")
        }
        // Never trust an acknowledgement alone; compare the authoritative immutable chain.
        val accepted = chainLocked(messageId).revisions.firstOrNull { it.client == operation["client_id"] && it.editor == userId }
        check(accepted != null && matches(accepted, operation)) { "Edit acknowledgement needs reconciliation." }
        remember(messageId, checkNotNull(accepted))
        cache.writeJsonStrict(namespace, pendingKey, operation + ("completed" to true))
        current()
    }
    private suspend fun remember(messageId: String, revision: Revision) {
        current()
        check(cache.readJsonStrict(namespace, "revision_retired_$messageId") == null) { "Message was retired." }
        val prior = cache.readJsonStrict(namespace, "revision_seen_$messageId") as? Map<*, *>
        // Do not move a known high-water mark backwards when an older accepted
        // operation is reconciled while another device has already edited again.
        if (prior == null || int(prior["seq"]) <= revision.seq)
            cache.writeJsonStrict(namespace, "revision_seen_$messageId", mapOf("seq" to revision.seq, "hash" to revision.hash))
        current()
    }
    private fun matches(accepted: Revision, operation: Map<String, Any?>): Boolean =
        accepted.ciphertext == operation["ciphertext"] && accepted.device == operation["device_id"] &&
            accepted.group == operation["group_id"] && accepted.epoch == int(operation["epoch"]) &&
            accepted.previous == operation["previous_hash"] && accepted.seq == int(operation["base_seq"]) + 1 &&
            accepted.client == operation["client_id"] && accepted.editor == operation["user_id"]

    companion object {
        /** null is UNKNOWN here; only an explicitly present JSON-null timer is OFF. */
        private fun timerOff(raw: Map<*, *>?): Boolean? {
            if (raw == null || !raw.containsKey("disappear_seconds")) return null
            val timer = raw["disappear_seconds"] ?: return true
            val number = timer as? Number ?: return null
            val seconds = number.toLong()
            return if (seconds > 0 && number.toDouble() == seconds.toDouble()) false else null
        }

        /** Strict inventory also discovers crash orphans, not just indexed keys.
         * A failed/404/empty-policy read authorizes conservative erasure only,
         * never a new key, resend, or a claim of server non-acceptance. */
        suspend fun sweepRetention(cache: SnapshotCacheStore, namespace: String, rpc: SupabaseRpc, current: () -> Unit): Set<String> {
            current()
            val erased = linkedSetOf<String>()
            val scopes = cache.listRevisionPayloadScopes(namespace)
            val byMessage = linkedMapOf<String, MutableList<String>>()
            for (scope in scopes) {
                current()
                val value = cache.readJsonStrict(namespace, scope) as? Map<*, *>
                val id = value?.get("message_id") as? String
                if (id == null || !id.matches(Regex("[0-9a-fA-F-]{36}"))) {
                    cache.writeJsonStrict(namespace, scope, null)
                } else byMessage.getOrPut(id) { mutableListOf() }.add(scope)
            }
            for ((id, _) in byMessage) revisionLock(namespace, id) {
                current()
                val raw = try { rpc.rpc("get_mls_message_revisions", mapOf("p_message_id" to id)) as? Map<*, *> }
                catch (error: Throwable) {
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    current(); null
                }
                current()
                // Empty cannot distinguish an untouched message from concurrent
                // deletion here: a stored authored journal makes it unsafe.
                if (raw?.get("message_id") != id || timerOff(raw) != true || (raw?.get("revisions") as? List<*>)?.isNotEmpty() != true) {
                    eraseAuthoredPayloads(cache, namespace, id)
                    erased.add(id)
                }
            }
            return erased
        }

        /** Caller holds the revision stripe, except chain() which is itself
         * called within that stripe for edit/projection. Erasure is idempotent. */
        private suspend fun eraseAuthoredPayloads(cache: SnapshotCacheStore, namespace: String, messageId: String) {
            for (scope in cache.listRevisionPayloadScopes(namespace)) {
                val value = cache.readJsonStrict(namespace, scope) as? Map<*, *> ?: continue
                if (value["message_id"] == messageId || scope == "revision_pending_$messageId") {
                    val room = value["room_id"] as? String
                    cache.writeJsonStrict(namespace, scope, null)
                    if (room != null && room.matches(Regex("[0-9a-fA-F-]{36}"))) {
                        val snapshot = cache.readJsonStrict(namespace, "messages_$room") as? List<*>
                        if (snapshot != null) cache.writeJsonStrict(namespace, "messages_$room", snapshot.filterNot { (it as? Map<*, *>)?.get("id") == messageId })
                    }
                }
            }
            cache.writeJsonStrict(namespace, "revision_pending_$messageId", null)
            // Keep the content-free index/high-water marker. It prevents a later
            // empty chain from being mistaken for a never-edited original.
        }
        private val revisionLocks = Array(64) { Mutex() }
        private suspend fun <T> revisionLock(namespace: String, id: String, body: suspend () -> T): T =
            revisionLocks[Math.floorMod((namespace + ":" + id).hashCode(), revisionLocks.size)].withLock { body() }
        /** Called for tombstones and successful local deletes. The marker is
         * written first so partial cleanup cannot revive authored edit plaintext. */
        suspend fun forget(cache: SnapshotCacheStore, namespace: String, messageId: String) =
            revisionLock(namespace, messageId) { forgetLocked(cache, namespace, messageId) }

        private suspend fun forgetLocked(cache: SnapshotCacheStore, namespace: String, messageId: String) {
            cache.writeJsonStrict(namespace, "revision_retired_$messageId", mapOf("retired" to true))
            eraseAuthoredPayloads(cache, namespace, messageId)
            val keys = cache.readJsonStrict(namespace, "revision_keys_$messageId") as? List<*> ?: emptyList<Any?>()
            for (client in keys) {
                check(client is String && client.matches(Regex("[A-Za-z0-9_-]{1,100}"))) { "Invalid revision cleanup index." }
                cache.writeJsonStrict(namespace, "own_revision_$client", null)
            }
            cache.writeJsonStrict(namespace, "revision_pending_$messageId", null)
            cache.writeJsonStrict(namespace, "revision_seen_$messageId", null)
            cache.writeJsonStrict(namespace, "revision_keys_$messageId", null)
        }
        private fun int(value: Any?): Int {
            val number = value as? Number ?: error("Invalid revision number.")
            val result = number.toLong()
            check(result in 0..Int.MAX_VALUE.toLong() && number.toDouble() == result.toDouble()) { "Invalid revision number." }
            return result.toInt()
        }
        fun restricted(metadata: Map<*, *>): Boolean = metadata.any { (key, value) ->
            val name = key.toString().lowercase()
            (name.contains("view_once") || name.contains("disappear") || name.contains("expir") || name.contains("ttl")) &&
                value != null && value != false && value != "false" && value != "off" && value != "none" && value != 0 && value != 0L
        }
        private fun quote(value: String): String = buildString {
            append('"')
            for (c in value) when (c) {
                '"' -> append("\\\""); '\\' -> append("\\\\"); '\b' -> append("\\b"); '\u000C' -> append("\\f")
                '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t")
                else -> if (c.code < 32) append("\\u%04x".format(c.code)) else append(c)
            }
            append('"')
        }
        fun contentHash(messageId: String, r: Revision): String {
            val json = "[" + listOf(quote("chaty-edit-v1"), quote(messageId.lowercase()), r.seq.toString(), r.previous?.let(::quote) ?: "null",
                quote(r.editor.lowercase()), quote(r.device), quote(r.group), r.epoch.toString(), quote(r.ciphertext)).joinToString(",") + "]"
            return MessageDigest.getInstance("SHA-256").digest(json.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        }
    }
}

// Row51 first-delta originals retained after P1 retention correction.
//     data class Chain(val messageId: String, val revisions: List<Revision>) {
//     suspend fun chain(messageId: String): Chain {
//         val raw = rpc.rpc("get_mls_message_revisions", mapOf("p_message_id" to messageId)) as? Map<*, *>
//             ?: error("Immutable revision chain is unavailable.")
//         return Chain(messageId, revisions)
//         val chain = chain(messageId)
//         val currentChain = chain(messageId)
//         rpc.rpc("edit_mls_message_v1", mapOf("p_message_id" to messageId, "p_sender_device_id" to operation["device_id"],
//         val accepted = chain(messageId).revisions.firstOrNull { it.client == operation["client_id"] && it.editor == userId }
