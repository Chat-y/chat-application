package com.example.chat.data.messaging

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Encrypted, backend-namespace-bound recovery state. No auth token is persisted.
 * Pending means server outcome UNKNOWN, never permission to erase private data.
 * Confirmed receipts replay cleanup after process death; successful records remain
 * as durable quarantine so relogin cannot silently recreate a deleted identity. */
class AccountDeletionJournal(private val cache: SnapshotCacheStore) {
    private val lock = Mutex()
    data class Entry(val namespace: String, val userId: String, val mediaSession: String?, val confirmed: Boolean,
        val operationId: String? = null, internal val receiptToken: String? = null,
        val expiresAt: String? = null, val baseUrl: String? = null, val releasedPending: Boolean = false, val dispatched: Boolean = false, val revision: Long = 0, val notDeletedStatus: String? = null) {
        override fun toString() = "AccountDeletionEntry(redacted)"
    }
    private suspend fun readLocked(): List<Entry> {
        val raw = cache.readJsonStrict("account-deletion-journal-v1", "records") ?: return emptyList()
        check(raw is List<*>) { "Account deletion recovery record is corrupt." }
        return raw.map {
            check(it is Map<*, *>) { "Account deletion recovery record is corrupt." }
            val ns = it["namespace"] as? String; val uid = it["user_id"] as? String
            check(!ns.isNullOrBlank() && !uid.isNullOrBlank() && it["confirmed"] is Boolean) { "Account deletion recovery owner is corrupt." }
            Entry(ns, uid, it["media_session"] as? String, it["confirmed"] as Boolean,
                it["operation_id"] as? String, it["receipt_token"] as? String, it["expires_at"] as? String, it["base_url"] as? String, it["released_pending"] == true, it["dispatched"] == true, (it["revision"] as? Number)?.toLong() ?: 0, it["not_deleted_status"] as? String)
        }
    }
    suspend fun entries(): List<Entry> = lock.withLock { readLocked() }
    suspend fun record(entry: Entry) = mutate(entry, false)
    suspend fun beginNextOperation(entry: Entry) = mutate(entry, true)
    private suspend fun mutate(entry: Entry, beginNext: Boolean) = lock.withLock {
        require(entry.namespace.isNotBlank() && entry.userId.isNotBlank())
        val old = readLocked(); val previous = old.firstOrNull { it.namespace == entry.namespace }
        check(previous == null || previous.userId == entry.userId) { "Deletion recovery owner mismatch." }
        // A terminal confirmation can never be reopened by a stale writer.
        if (previous?.confirmed == true && !entry.confirmed) {
            check(!beginNext) { "This account deletion is already confirmed." }
            return@withLock
        }
        check(!entry.releasedPending || entry.notDeletedStatus in setOf("rejected", "expired")) { "Only a verified not-deleted outcome can release quarantine." }
        if (beginNext) {
            check(!entry.confirmed && !entry.dispatched && !entry.releasedPending && entry.operationId != null) { "Invalid new deletion operation." }
            check(previous == null || (!previous.confirmed && previous.releasedPending && previous.notDeletedStatus in setOf("rejected", "expired") && previous.operationId != entry.operationId)) {
                "Previous deletion outcome is unresolved. Recover it before starting a new operation."
            }
        } else check(previous == null || previous.revision == entry.revision) { "Deletion journal revision changed. Re-read the operation." }
        val absorbingConfirmed = entry.confirmed || previous?.confirmed == true
        check(previous == null || previous.operationId == entry.operationId || (!previous.confirmed && previous.releasedPending)) { "Deletion operation changed." }
        val updated = entry.copy(confirmed = absorbingConfirmed,
            releasedPending = !absorbingConfirmed && entry.releasedPending,
            revision = Math.addExact(previous?.revision ?: 0L, 1L))
        cache.writeJsonStrict("account-deletion-journal-v1", "records", (old.filterNot { it.namespace == entry.namespace } + updated).map {
            mapOf("namespace" to it.namespace, "user_id" to it.userId, "media_session" to it.mediaSession, "confirmed" to it.confirmed,
                "operation_id" to it.operationId, "receipt_token" to it.receiptToken,
                "expires_at" to it.expiresAt, "base_url" to it.baseUrl, "released_pending" to it.releasedPending, "dispatched" to it.dispatched, "revision" to it.revision, "not_deleted_status" to it.notDeletedStatus)
        })
    }
}

// Row51 v10 originals retained byte-for-byte after the comment prefix.
// Original segment 1:
//         val expiresAt: String? = null, val baseUrl: String? = null, val releasedPending: Boolean = false, val dispatched: Boolean = false, val revision: Long = 0) {
// Original segment 2:
//                 it["operation_id"] as? String, it["receipt_token"] as? String, it["expires_at"] as? String, it["base_url"] as? String, it["released_pending"] == true, it["dispatched"] == true, (it["revision"] as? Number)?.toLong() ?: 0)
// Original segment 3:
//             check(previous == null || (!previous.confirmed && previous.releasedPending && !previous.dispatched && previous.operationId != entry.operationId)) {
// Original segment 4:
//                 "expires_at" to it.expiresAt, "base_url" to it.baseUrl, "released_pending" to it.releasedPending, "dispatched" to it.dispatched, "revision" to it.revision)
