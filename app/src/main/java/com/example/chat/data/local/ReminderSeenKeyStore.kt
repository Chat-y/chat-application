package com.example.chat.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/** Thrown when a stored seen-key payload cannot be decoded exactly. The raw
 *  payload is always preserved (see ReminderSeenKeyStore). */
class ReminderSeenKeyCodecException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Versioned JSON codec for reminder seen-keys. Strict by design: any
 * malformed, unsupported-version, blank or duplicate key payload throws
 * ReminderSeenKeyCodecException so the store preserves the raw bytes.
 */
object ReminderSeenKeyCodec {
    const val VERSION = 1

    fun encode(eventIds: Set<String>): String {
        val array = JSONArray()
        eventIds.forEach { eventId ->
            require(eventId.isNotBlank()) { "Seen-keys require a non-blank event id." }
            array.put(eventId)
        }
        return JSONObject().put("version", VERSION).put("event_ids", array).toString()
    }

    fun decode(raw: String?): Set<String> {
        if (raw == null) return emptySet()
        try {
            val root = JSONObject(raw)
            if (root.optInt("version", -1) != VERSION) {
                throw ReminderSeenKeyCodecException("Unsupported seen-key payload version.")
            }
            val array = root.getJSONArray("event_ids")
            val result = LinkedHashSet<String>(array.length())
            for (index in 0 until array.length()) {
                val eventId = array.getString(index)
                if (eventId.isBlank()) {
                    throw ReminderSeenKeyCodecException("Blank event id in seen-key payload.")
                }
                if (!result.add(eventId)) {
                    throw ReminderSeenKeyCodecException("Duplicate event id in seen-key payload.")
                }
            }
            return result
        } catch (error: ReminderSeenKeyCodecException) {
            throw error
        } catch (error: Exception) {
            throw ReminderSeenKeyCodecException("Malformed seen-key payload.", error)
        }
    }
}

/**
 * Decision 3A, R3-47 (wave 5, source-only; deletion held): local-only,
 * per-account record of reminder tap event ids that have already acted, on
 * top of EncryptedAccountStore. One reminder tap = one action; the
 * notification dispatcher checks hasSeen + account binding via
 * HomeReminderTapPolicy.mayAct before any navigation and records the
 * seen-key only after acting.
 *
 * R3-47 edge cases:
 * - A blank event id can never act, so it is never recorded: hasSeen("")
 *  is false and markSeen("") refuses to write.
 * - Fail closed on corruption: when the payload is undecodable, hasSeen
 *  returns true for every queried id (a tap whose seen state is unknown
 *  must not repeat a read action), markSeen refuses to write, and the raw
 *  bytes are preserved under a backup key. Recovery is pending a later
 *  explicit release; no reset or deletion API is exposed.
 */
class ReminderSeenKeyStore(private val store: EncryptedAccountStore) {

    private val lock = Mutex()
    private val _revision = MutableStateFlow(0)

    /** Bumped after every durable write so observers re-read. */
    val revision: StateFlow<Int> = _revision

    @Volatile
    private var corrupted = false

    fun isCorrupted(): Boolean = corrupted

    /** True when this event id was already recorded as acted. Blank ids
     *  report false (they can never act). While corrupted, reports true for
     *  every id: fail closed against duplicate actions. */
    suspend fun hasSeen(eventId: String): Boolean = lock.withLock {
        // Holding this Mutex across the suspending encrypted-prefs I/O below is intentional and
        // acceptable: it keeps each load/modify/persist sequence atomic for this account.
        // Mutex is not reentrant, so no method calls another locking method while holding it.
        if (eventId.isBlank()) return@withLock false
        if (corrupted) return@withLock true
        val seen = loadLocked()
        if (corrupted) return@withLock true
        eventId in seen
    }

    /** Records an event id after its tap acted. Returns false - writing
     *  nothing - for a blank id, on durable-write failure, or while
     *  corrupted. Recording an already-seen id is a no-op success. */
    suspend fun markSeen(eventId: String): Boolean = lock.withLock {
        // Holding this Mutex across the suspending encrypted-prefs I/O below is intentional and
        // acceptable: it keeps each load/modify/persist sequence atomic for this account.
        // Mutex is not reentrant, so no method calls another locking method while holding it.
        if (corrupted || eventId.isBlank()) return@withLock false
        val seen = loadLocked()
        if (corrupted) return@withLock false
        if (eventId in seen) return@withLock true
        persistLocked(seen + eventId)
    }

    private suspend fun loadLocked(): Set<String> {
        val raw = store.read(KEY_STATE) ?: return emptySet()
        return try {
            ReminderSeenKeyCodec.decode(raw)
        } catch (error: ReminderSeenKeyCodecException) {
            corrupted = true
            preserveRawLocked(raw)
            emptySet()
        }
    }

    private suspend fun preserveRawLocked(raw: String?) {
        if (raw == null) return
        try {
            if (store.read(KEY_CORRUPT_BACKUP) == null) store.write(KEY_CORRUPT_BACKUP, raw)
        } catch (ignored: Exception) {
            // Raw bytes remain under KEY_STATE; preservation is retried on the next access.
        }
    }

    private suspend fun persistLocked(eventIds: Set<String>): Boolean =
        try {
            store.write(KEY_STATE, ReminderSeenKeyCodec.encode(eventIds))
            _revision.value += 1
            true
        } catch (error: Exception) {
            false
        }

    private companion object {
        const val KEY_STATE = "reminder_seen_keys_v1"
        const val KEY_CORRUPT_BACKUP = "reminder_seen_keys_v1_corrupt_backup"
    }
}
