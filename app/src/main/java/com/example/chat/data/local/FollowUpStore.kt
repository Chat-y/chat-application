package com.example.chat.data.local

import com.example.chat.ui.chats.home.HomeFollowUp
import com.example.chat.ui.chats.home.HomeFollowUpStatus
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/** Thrown when a stored follow-up payload cannot be decoded exactly. The raw
 *  payload is always preserved (see FollowUpStore); nothing is rewritten. */
class FollowUpCodecException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Versioned JSON codec for personal follow-ups. Strict by design: any
 * malformed, unsupported-version, blank-identity, unknown-status or
 * duplicate-id payload throws FollowUpCodecException instead of guessing, so
 * the store can preserve the raw bytes rather than silently dropping records.
 */
object FollowUpCodec {
    const val VERSION = 1

    fun encode(followUps: List<HomeFollowUp>): String {
        val items = JSONArray()
        followUps.forEach { followUp ->
            require(
                followUp.id.isNotBlank() &&
                    followUp.conversationId.isNotBlank() &&
                    followUp.title.isNotBlank()
            ) { "Follow-up records require non-blank id, conversationId and title." }
            val entry = JSONObject()
            entry.put("id", followUp.id)
            entry.put("conversation_id", followUp.conversationId)
            followUp.savedItemId?.let { entry.put("saved_item_id", it) }
            entry.put("title", followUp.title)
            entry.put("note", followUp.note)
            followUp.dueAt?.let { entry.put("due_at", it.toString()) }
            followUp.dueZoneId?.let { entry.put("due_zone_id", it.id) }
            entry.put("status", followUp.status.name)
            entry.put("created_at", followUp.createdAt.toString())
            entry.put("updated_at", followUp.updatedAt.toString())
            followUp.completedAt?.let { entry.put("completed_at", it.toString()) }
            items.put(entry)
        }
        return JSONObject().put("version", VERSION).put("items", items).toString()
    }

    fun decode(raw: String?): List<HomeFollowUp> {
        if (raw == null) return emptyList()
        try {
            val root = JSONObject(raw)
            if (root.optInt("version", -1) != VERSION) {
                throw FollowUpCodecException("Unsupported follow-up payload version.")
            }
            val items = root.getJSONArray("items")
            val result = ArrayList<HomeFollowUp>(items.length())
            val seenIds = HashSet<String>(items.length())
            for (index in 0 until items.length()) {
                val entry = items.getJSONObject(index)
                val id = entry.getString("id")
                val conversationId = entry.getString("conversation_id")
                val title = entry.getString("title")
                if (id.isBlank() || conversationId.isBlank() || title.isBlank()) {
                    throw FollowUpCodecException("Follow-up record with blank id, conversation or title.")
                }
                if (!seenIds.add(id)) {
                    throw FollowUpCodecException("Duplicate follow-up id in payload.")
                }
                val status = try {
                    HomeFollowUpStatus.valueOf(entry.getString("status"))
                } catch (error: Exception) {
                    throw FollowUpCodecException("Unknown follow-up status.", error)
                }
                result.add(
                    HomeFollowUp(
                        id = id,
                        conversationId = conversationId,
                        savedItemId = entry.optString("saved_item_id").takeIf { it.isNotBlank() },
                        title = title,
                        note = entry.optString("note", ""),
                        dueAt = entry.optString("due_at").takeIf { it.isNotBlank() }?.let(Instant::parse),
                        dueZoneId = entry.optString("due_zone_id").takeIf { it.isNotBlank() }?.let(ZoneId::of),
                        status = status,
                        createdAt = Instant.parse(entry.getString("created_at")),
                        updatedAt = Instant.parse(entry.getString("updated_at")),
                        completedAt = entry.optString("completed_at").takeIf { it.isNotBlank() }?.let(Instant::parse),
                    )
                )
            }
            return result
        } catch (error: FollowUpCodecException) {
            throw error
        } catch (error: Exception) {
            throw FollowUpCodecException("Malformed follow-up payload.", error)
        }
    }
}

/**
 * Decision 2A (wave 5, source-only; deletion held): local-only, per-account
 * follow-up persistence on top of EncryptedAccountStore. No backend fields,
 * no sync hooks, no assignee field, no alarm scheduling. Snooze and complete
 * are plain upserts of the same id, so no duplicate alarms can be produced
 * here (scheduling itself is out of scope for this file).
 *
 * Corruption posture: an undecodable payload marks
 * the store corrupted, the raw bytes are preserved under a separate backup
 * key (written only when absent, never removed by this class), reads return
 * empty, and every write refuses while corrupted. Recovery and follow-up
 * deletion are pending explicit release; no reset or deletion API is exposed.
 * Corrupt data is never discarded.
 */
class FollowUpStore(private val store: EncryptedAccountStore) {

    private val lock = Mutex()
    private val _revision = MutableStateFlow(0)

    /** Bumped after every durable write so observers re-read. */
    val revision: StateFlow<Int> = _revision

    @Volatile
    private var corrupted = false

    fun isCorrupted(): Boolean = corrupted

    suspend fun list(): List<HomeFollowUp> = lock.withLock {
        // Holding this Mutex across the suspending encrypted-prefs I/O below is intentional and
        // acceptable: it keeps each load/modify/persist sequence atomic for this account.
        // Mutex is not reentrant, so no method calls another locking method while holding it.
        loadLocked()
    }

    /** Inserts or replaces by id. Returns false on durable-write failure,
     *  on invalid input, or while corrupted (unreadable data is preserved). */
    suspend fun upsert(followUp: HomeFollowUp): Boolean = lock.withLock {
        // Holding this Mutex across the suspending encrypted-prefs I/O below is intentional and
        // acceptable: it keeps each load/modify/persist sequence atomic for this account.
        // Mutex is not reentrant, so no method calls another locking method while holding it.
        if (corrupted) return@withLock false
        if (followUp.id.isBlank() || followUp.conversationId.isBlank() || followUp.title.isBlank()) {
            return@withLock false
        }
        val current = loadLocked()
        if (corrupted) return@withLock false
        val index = current.indexOfFirst { it.id == followUp.id }
        val next = if (index >= 0) {
            current.toMutableList().apply { this[index] = followUp }
        } else {
            current + followUp
        }
        persistLocked(next)
    }

    private suspend fun loadLocked(): List<HomeFollowUp> {
        val raw = store.read(KEY_STATE) ?: return emptyList()
        return try {
            FollowUpCodec.decode(raw)
        } catch (error: FollowUpCodecException) {
            corrupted = true
            preserveRawLocked(raw)
            emptyList()
        }
    }

    /** Best-effort preservation: the payload key itself is never touched on
     *  the failure path, so a failed backup write loses nothing. */
    private suspend fun preserveRawLocked(raw: String?) {
        if (raw == null) return
        try {
            if (store.read(KEY_CORRUPT_BACKUP) == null) store.write(KEY_CORRUPT_BACKUP, raw)
        } catch (ignored: Exception) {
            // Raw bytes remain under KEY_STATE; preservation is retried on the next access.
        }
    }

    private suspend fun persistLocked(followUps: List<HomeFollowUp>): Boolean =
        try {
            store.write(KEY_STATE, FollowUpCodec.encode(followUps))
            _revision.value += 1
            true
        } catch (error: Exception) {
            false
        }

    private companion object {
        const val KEY_STATE = "follow_ups_v1"
        const val KEY_CORRUPT_BACKUP = "follow_ups_v1_corrupt_backup"
    }
}
