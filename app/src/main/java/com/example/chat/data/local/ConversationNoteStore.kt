package com.example.chat.data.local

import com.example.chat.ui.chats.home.HomeNotePolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/** Thrown when a stored notes payload cannot be decoded exactly. The raw
 *  payload is always preserved (see ConversationNoteStore). */
class ConversationNoteCodecException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Versioned JSON codec for per-conversation private notes. Strict by design:
 * any malformed, unsupported-version or blank-key payload throws
 * ConversationNoteCodecException so the store preserves the raw bytes.
 */
object ConversationNoteCodec {
    const val VERSION = 1

    fun encode(notes: Map<String, String>): String {
        val objectNode = JSONObject()
        notes.forEach { (conversationId, text) ->
            require(conversationId.isNotBlank()) { "Notes require a non-blank conversation id." }
            objectNode.put(conversationId, text)
        }
        return JSONObject().put("version", VERSION).put("notes", objectNode).toString()
    }

    fun decode(raw: String?): Map<String, String> {
        if (raw == null) return emptyMap()
        try {
            val root = JSONObject(raw)
            if (root.optInt("version", -1) != VERSION) {
                throw ConversationNoteCodecException("Unsupported notes payload version.")
            }
            val notesNode = root.getJSONObject("notes")
            val result = LinkedHashMap<String, String>()
            val keys = notesNode.keys()
            while (keys.hasNext()) {
                val conversationId = keys.next()
                if (conversationId.isBlank()) {
                    throw ConversationNoteCodecException("Note record with a blank conversation id.")
                }
                result[conversationId] = notesNode.getString(conversationId)
            }
            return result
        } catch (error: ConversationNoteCodecException) {
            throw error
        } catch (error: Exception) {
            throw ConversationNoteCodecException("Malformed notes payload.", error)
        }
    }
}

/**
 * Decision 4A, R3-10/R3-42 (wave 5, source-only; deletion held): local-only,
 * per-account private notes on top of EncryptedAccountStore. A note is never
 * sent to the contact and never leaves this store.
 *
 * R3-10: note text is bounded by HomeNotePolicy before persisting.
 * R3-42: note deletion is pending explicit deletion release; no deletion
 *  API is exposed. The conversation, its messages and any saved items remain
 *  out of scope by construction (this class can reach nothing else).
 *
 * Corruption posture: raw bytes preserved under a backup key, reads
 * empty, and writes refused while corrupted. Recovery is pending a later
 * explicit release; no reset or deletion API is exposed.
 */
class ConversationNoteStore(private val store: EncryptedAccountStore) {

    private val lock = Mutex()
    private val _revision = MutableStateFlow(0)

    /** Bumped after every durable write so observers re-read. */
    val revision: StateFlow<Int> = _revision

    @Volatile
    private var corrupted = false

    fun isCorrupted(): Boolean = corrupted

    suspend fun noteFor(conversationId: String): String? = lock.withLock {
        // Holding this Mutex across the suspending encrypted-prefs I/O below is intentional and
        // acceptable: it keeps each load/modify/persist sequence atomic for this account.
        // Mutex is not reentrant, so no method calls another locking method while holding it.
        if (conversationId.isBlank()) return@withLock null
        loadLocked()[conversationId]
    }

    /** Stores the note bounded by HomeNotePolicy. Returns false - writing
     *  nothing - for a blank conversation id, a blank note,
     *  on durable-write failure, or while corrupted. */
    suspend fun putNote(conversationId: String, text: String): Boolean = lock.withLock {
        // Holding this Mutex across the suspending encrypted-prefs I/O below is intentional and
        // acceptable: it keeps each load/modify/persist sequence atomic for this account.
        // Mutex is not reentrant, so no method calls another locking method while holding it.
        if (corrupted || conversationId.isBlank()) return@withLock false
        val bounded = HomeNotePolicy.bounded(text)
        if (bounded.isBlank()) return@withLock false
        val current = loadLocked()
        if (corrupted) return@withLock false
        persistLocked(current + (conversationId to bounded))
    }

    private suspend fun loadLocked(): Map<String, String> {
        val raw = store.read(KEY_STATE) ?: return emptyMap()
        return try {
            ConversationNoteCodec.decode(raw)
        } catch (error: ConversationNoteCodecException) {
            corrupted = true
            preserveRawLocked(raw)
            emptyMap()
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

    private suspend fun persistLocked(notes: Map<String, String>): Boolean =
        try {
            store.write(KEY_STATE, ConversationNoteCodec.encode(notes))
            _revision.value += 1
            true
        } catch (error: Exception) {
            false
        }

    private companion object {
        const val KEY_STATE = "conversation_notes_v1"
        const val KEY_CORRUPT_BACKUP = "conversation_notes_v1_corrupt_backup"
    }
}
