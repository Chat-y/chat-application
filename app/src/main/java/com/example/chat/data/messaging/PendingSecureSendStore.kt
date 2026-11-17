package com.example.chat.data.messaging

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Storage seam for the AES-256-GCM snapshot cache (LocalSnapshotCacheService
 *  port lands with the local-cache slice). */
interface SnapshotCacheStore {
    suspend fun readJson(userId: String, scope: String): Any?
    suspend fun writeJson(userId: String, scope: String, value: Any?)

    /** Strict variants for security-critical scopes (e.g. secure_channels):
     *  storage/decrypt failures THROW instead of degrading to a cache miss or
     *  a silently dropped write, so callers can fail closed. Defaults reject use unless the store explicitly implements the strict contract;
     *  ordinary readJson/writeJson remain lenient for every other scope. */
    suspend fun readJsonStrict(userId: String, scope: String): Any? = throw UnsupportedOperationException("Strict secure-settings reads are not supported by this store.")
    suspend fun writeJsonStrict(userId: String, scope: String, value: Any?): Unit = throw UnsupportedOperationException("Strict secure-settings writes are not supported by this store.")

    /** Read-only listing of this account's stored snapshot scope names, for
     *  backup research. Default: none. Names may be filename-sanitised, so every
     *  returned scope MUST still be verified by readJson (which authenticates the
     *  exact userId:scope pair) before use. */
    suspend fun listScopes(userId: String): List<String> = emptyList()
    /** Internal recovery only. Never used by export/backup listing. */
    suspend fun listOwnOperationScopes(userId: String): List<String> = emptyList()
    /** Strict internal retention sweep. A missing implementation is not an empty inventory. */
    suspend fun listRevisionPayloadScopes(userId: String): List<String> =
        throw UnsupportedOperationException("Strict revision inventory unavailable.")
}

/**
 * Exact port of PendingSecureSendStore semantics: dedupe by client_message_id
 * (idempotency key), sorted by created_at, max 128 pending, serialized
 * mutations.
 */
class DiscardedSecureSendException : IllegalStateException("This queued operation was discarded.")

// Previous constructor retained inactive: class PendingSecureSendStore(private val cache: SnapshotCacheStore) {
class PendingSecureSendStore(
    private val cache: SnapshotCacheStore,
    /** Opt-in E2EE durability policy. The existing cache still encrypts the same
     * format with the same key; strict calls only stop swallowing I/O/decrypt errors. */
    private val strictStorage: Boolean = false,
    private val assertOwnerActive: suspend (String) -> Unit = {},
    private val deletionPolicy: Boolean = strictStorage,
) {
    companion object {
        const val SCOPE = "pending_secure_sends"
        const val MAX_PENDING = 128
    }

    private val mutationBarrier = Mutex()

    @Suppress("UNCHECKED_CAST")
// Previous namespace behavior retained inactive:     suspend fun read(userId: String): List<PendingSecureSend> {
    suspend fun read(userId: String, storageNamespace: String = userId): List<PendingSecureSend> = mutationBarrier.withLock {
        readLocked(userId, storageNamespace)
    }

    private suspend fun readLocked(userId: String, storageNamespace: String): List<PendingSecureSend> {
        val storageId = if (strictStorage) storageNamespace else userId
        require(storageId.isNotBlank())
        if (deletionPolicy) {
            assertOwnerActive(storageNamespace)
            val retired = cache.readJsonStrict("deleted-owner-$storageNamespace", "queue_tombstone")
            check(retired == null) { "This account queue was retired after deletion. Nothing was read or queued." }
        }
        if (strictStorage && storageId != userId) {
            val legacy = cache.readJsonStrict(userId, SCOPE)
            check(legacy == null || legacy == emptyList<Any?>()) { "Legacy queue owner is unverified. Nothing was imported or sent." }
        }
        // Previous lenient read retained inactive:
        // val value = cache.readJson(userId = userId, scope = SCOPE)
// Previous namespace behavior retained inactive:         val value = if (strictStorage) cache.readJsonStrict(userId = userId, scope = SCOPE)
        val value = if (strictStorage) cache.readJsonStrict(userId = storageId, scope = SCOPE)
            else cache.readJson(userId = userId, scope = SCOPE)
        // Missing storage is a new empty queue, not a corruption signal.
        if (value == null) return emptyList()
        // Previous lenient shape handling retained inactive: if (value !is List<*>) return emptyList()
        if (value !is List<*>) {
            check(!strictStorage) { "Encrypted pending-send queue is malformed. Nothing was changed." }
            return emptyList()
        }
        val byClientMessageId = LinkedHashMap<String, PendingSecureSend>()
        for (item in value) {
            // Previous skip retained inactive: if (item !is Map<*, *>) continue
            if (item !is Map<*, *>) {
                check(!strictStorage) { "Encrypted pending-send row is malformed. Nothing was changed." }
                continue
            }
            val pending = PendingSecureSend.fromJson(
                item.entries.associate { (k, v) -> k.toString() to v }
            )
            // Previous skip retained inactive:
            // if (pending.clientMessageId.isEmpty() || pending.conversationId.isEmpty()) continue
            if (pending.clientMessageId.isEmpty() || pending.conversationId.isEmpty()) {
                check(!strictStorage) { "Encrypted pending-send row has no identity. Nothing was changed." }
                continue
            }
            // A client message ID is the idempotency key. Keep only one entry
            // even if an older/corrupt snapshot contains duplicates.
            byClientMessageId[pending.clientMessageId] = pending
        }
        return byClientMessageId.values.sortedBy { it.createdAt }
    }

// Previous namespace behavior retained inactive:     suspend fun put(userId: String, item: PendingSecureSend) = mutationBarrier.withLock {
// Previous namespace behavior retained inactive:         val items = read(userId).toMutableList()
    suspend fun put(userId: String, item: PendingSecureSend, storageNamespace: String = userId) = mutationBarrier.withLock {
        val items = readLocked(userId, storageNamespace).toMutableList()
        val index = items.indexOfFirst { it.clientMessageId == item.clientMessageId }
        if (index >= 0) {
            items[index] = item
        } else {
            check(items.size < MAX_PENDING) { "Secure pending-send queue is full." }
            items.add(item)
        }
        // Previous lenient write retained inactive:
        // cache.writeJson(userId = userId, scope = SCOPE, value = items.map { it.toJson() })
// Previous namespace behavior retained inactive:         writeQueue(userId, items.map { it.toJson() })
        writeQueue(if (strictStorage) storageNamespace else userId, items.map { it.toJson() })
    }

// Previous namespace behavior retained inactive:     suspend fun remove(userId: String, clientMessageId: String) = mutationBarrier.withLock {
// Previous namespace behavior retained inactive:         val items = read(userId).filterNot { it.clientMessageId == clientMessageId }
    suspend fun remove(userId: String, clientMessageId: String, storageNamespace: String = userId) = mutationBarrier.withLock {
        val items = readLocked(userId, storageNamespace).filterNot { it.clientMessageId == clientMessageId }
        // Previous lenient write retained inactive:
        // cache.writeJson(userId = userId, scope = SCOPE, value = items.map { it.toJson() })
// Previous namespace behavior retained inactive:         writeQueue(userId, items.map { it.toJson() })
        writeQueue(if (strictStorage) storageNamespace else userId, items.map { it.toJson() })
    }

    /** Dart PendingSecureSendStore.clear: serialized write of an empty queue (EncryptedMessageOutbox.clear). */
    suspend fun clear(userId: String) = mutationBarrier.withLock {
        // Previous lenient clear retained inactive:
        // cache.writeJson(userId = userId, scope = SCOPE, value = emptyList<Any?>())
        writeQueue(userId, emptyList<Any?>())
    }

    /** Durable retirement under the same barrier as put/remove. A late put
     * must re-read this tombstone after obtaining the barrier and fail closed.
     * Separate storage identity survives deletion of the target's snapshots. */
    suspend fun quarantineNamespace(userId: String, namespace: String) = mutationBarrier.withLock {
        require(userId.isNotBlank() && namespace.isNotBlank())
        cache.writeJsonStrict("deleted-owner-$namespace", "queue_tombstone", mapOf("retired" to true))
        // Pending server outcome: retain queued payload, blocked from all admission.
    }

    suspend fun releasePendingNamespace(namespace: String) = mutationBarrier.withLock {
        cache.writeJsonStrict("deleted-owner-$namespace", "queue_tombstone", null)
    }

    suspend fun retireNamespace(userId: String, namespace: String) = mutationBarrier.withLock {
        require(userId.isNotBlank() && namespace.isNotBlank())
        cache.writeJsonStrict("deleted-owner-$namespace", "queue_tombstone", mapOf("retired" to true))
        // Raw UID legacy storage has ambiguous backend ownership. Never erase it.
        if (strictStorage) writeQueue(namespace, emptyList<Any?>())
    }

    suspend fun assertNamespaceActive(namespace: String) {
        if (deletionPolicy) assertOwnerActive(namespace)
        if (deletionPolicy) check(cache.readJsonStrict("deleted-owner-$namespace", "queue_tombstone") == null) {
            "Account deletion is pending or complete. Verify its server outcome before using this account."
        }
    }

    /** Deletion waits for an already-started delivery, then retires the namespace
     * before requesting server deletion. No later direct send/retry can start. */
    suspend fun <T> withActiveNamespace(userId: String, namespace: String, admission: PendingSecureSend? = null, block: suspend () -> T): T = mutationBarrier.withLock {
        if (deletionPolicy) assertOwnerActive(namespace)
        if (deletionPolicy) check(cache.readJsonStrict("deleted-owner-$namespace", "queue_tombstone") == null) {
            "Account deletion is pending or complete. Sending is blocked."
        }
        if (admission != null) {
            val binding = cache.readJsonStrict(namespace, "own_binding_${admission.clientMessageId}") as? Map<*, *>
            val items = readLocked(userId, namespace).toMutableList()
            if (binding?.get("discarded") == true) {
                writeQueue(if (strictStorage) namespace else userId, items.filterNot { it.clientMessageId == admission.clientMessageId }.map { it.toJson() })
                throw DiscardedSecureSendException()
            }
            val index = items.indexOfFirst { it.clientMessageId == admission.clientMessageId }
            if (index >= 0) items[index] = admission else { check(items.size < MAX_PENDING); items.add(admission) }
            writeQueue(if (strictStorage) namespace else userId, items.map { it.toJson() })
        }
        block()
    }

    /** Caller holds conversation dispatch lock. This barrier orders marker/write
     * and queue removal against deletion retirement and every active delivery. */
    suspend fun discardHeld(userId: String, namespace: String, clientId: String) = withActiveNamespace(userId, namespace) {
        val key = "own_binding_$clientId"
        val binding = cache.readJsonStrict(namespace, key) as? Map<String, Any?> ?: error("Held send unavailable.")
        check(binding["user_id"] == userId && binding["held"] == true)
        cache.writeJsonStrict(namespace, key, binding + ("discarded" to true))
        val remaining = readLocked(userId, namespace).filterNot { it.clientMessageId == clientId }
        writeQueue(if (strictStorage) namespace else userId, remaining.map { it.toJson() })
    }

    private suspend fun writeQueue(userId: String, value: Any?) {
        if (strictStorage) cache.writeJsonStrict(userId = userId, scope = SCOPE, value = value)
        else cache.writeJson(userId = userId, scope = SCOPE, value = value)
    }

}

// Previous implementation retained inactive (unified v4):
//     suspend fun read(userId: String, storageNamespace: String = userId): List<PendingSecureSend> {
//         val items = read(userId, storageNamespace).toMutableList()
//         val items = read(userId, storageNamespace).filterNot { it.clientMessageId == clientMessageId }
