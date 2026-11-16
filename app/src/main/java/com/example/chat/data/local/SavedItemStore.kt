package com.example.chat.data.local

import com.example.chat.ui.chats.home.HomeSavedItemKind
import com.example.chat.ui.chats.home.HomeSavedItemRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/** Thrown when a stored saved-item payload cannot be decoded exactly. The raw
 *  payload is always preserved (see SavedItemStore); nothing is rewritten. */
class SavedItemCodecException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Versioned JSON codec for saved-item references.
 *
 * PRODUCT DECISION B (approved for this wave): only Attachment and Link refs
 * are storable. The shared model enum HomeSavedItemKind still declares a
 * Message constant (the existing patch-40 file is intentionally untouched),
 * but message bookmarks belong solely to the server-backed Star
 * (ChatMessage.isStarred via the set_message_user_state RPC). Every entry
 * point here - encode, decode, and the store's save - rejects Message refs;
 * there is no batch or import path. Decode is strict: a stored Message or
 * unknown kind, blank identity, a missing attachment id on an Attachment
 * ref, an attachment id on a Link ref, or a duplicate id throws
 * SavedItemCodecException so the store preserves the raw payload instead of
 * silently dropping or converting records.
 */
object SavedItemCodec {
    const val VERSION = 1

    private const val KIND_ATTACHMENT = "Attachment"
    private const val KIND_LINK = "Link"

    fun encode(items: List<HomeSavedItemRef>): String {
        val array = JSONArray()
        items.forEach { item ->
            require(item.kind != HomeSavedItemKind.Message) {
                "Message-kind saved items are rejected: message bookmarks belong to the server-backed Star only."
            }
            require(
                item.id.isNotBlank() && item.conversationId.isNotBlank() && item.messageId.isNotBlank()
            ) { "Saved-item refs require non-blank id, conversationId and messageId." }
            when (item.kind) {
                HomeSavedItemKind.Attachment ->
                    require(!item.attachmentId.isNullOrBlank()) {
                        "Attachment-kind saved items require a non-blank attachmentId."
                    }
                HomeSavedItemKind.Link ->
                    require(item.attachmentId == null) {
                        "Link-kind saved items must not carry an attachmentId."
                    }
                else -> Unit // Message already rejected above.
            }
            val entry = JSONObject()
            entry.put("id", item.id)
            entry.put("conversation_id", item.conversationId)
            entry.put("message_id", item.messageId)
            entry.put(
                "kind",
                when (item.kind) {
                    HomeSavedItemKind.Attachment -> KIND_ATTACHMENT
                    HomeSavedItemKind.Link -> KIND_LINK
                    else -> throw IllegalArgumentException("Message kind is not encodable.")
                },
            )
            item.attachmentId?.let { entry.put("attachment_id", it) }
            array.put(entry)
        }
        return JSONObject().put("version", VERSION).put("items", array).toString()
    }

    fun decode(raw: String?): List<HomeSavedItemRef> {
        if (raw == null) return emptyList()
        try {
            val root = JSONObject(raw)
            if (root.optInt("version", -1) != VERSION) {
                throw SavedItemCodecException("Unsupported saved-item payload version.")
            }
            val array = root.getJSONArray("items")
            val result = ArrayList<HomeSavedItemRef>(array.length())
            val seenIds = HashSet<String>(array.length())
            for (index in 0 until array.length()) {
                val entry = array.getJSONObject(index)
                val id = entry.getString("id")
                val conversationId = entry.getString("conversation_id")
                val messageId = entry.getString("message_id")
                if (id.isBlank() || conversationId.isBlank() || messageId.isBlank()) {
                    throw SavedItemCodecException("Saved-item record with blank id, conversation or message.")
                }
                if (!seenIds.add(id)) {
                    throw SavedItemCodecException("Duplicate saved-item id in payload.")
                }
                val kindName = entry.getString("kind")
                val kind = when (kindName) {
                    KIND_ATTACHMENT -> HomeSavedItemKind.Attachment
                    KIND_LINK -> HomeSavedItemKind.Link
                    // A stored Message ref is rejected, never dropped or converted.
                    else -> throw SavedItemCodecException(
                        "Unsupported saved-item kind in payload: $kindName"
                    )
                }
                val attachmentId = entry.optString("attachment_id").takeIf { it.isNotBlank() }
                if (kind == HomeSavedItemKind.Attachment && attachmentId == null) {
                    throw SavedItemCodecException("Attachment-kind record without an attachment id.")
                }
                if (kind == HomeSavedItemKind.Link && entry.has("attachment_id")) {
                    throw SavedItemCodecException("Link-kind record carrying an attachment id.")
                }
                result.add(
                    HomeSavedItemRef(
                        id = id,
                        conversationId = conversationId,
                        messageId = messageId,
                        kind = kind,
                        attachmentId = attachmentId,
                    )
                )
            }
            return result
        } catch (error: SavedItemCodecException) {
            throw error
        } catch (error: Exception) {
            throw SavedItemCodecException("Malformed saved-item payload.", error)
        }
    }
}

/**
 * Decision 4A (wave 5, source-only; deletion held): local-only, per-account
 * saved-item references on top of EncryptedAccountStore. References by id
 * only - no attachment bytes are ever copied, downloaded or duplicated.
 *
 * PRODUCT DECISION B (approved): Message-kind refs are rejected at the store
 * boundary (save returns false, nothing is written) and at the codec
 * boundary (encode/decode throw). Message bookmarks stay under the single
 * server-backed Star; this store never reads or writes is_starred and never
 * issues RPC writes. R3-17: bookmark removal is pending explicit deletion release;
 * no removal API is exposed.
 *
 * Corruption posture: raw bytes preserved under a backup key, reads
 * empty, and writes refused while corrupted. Recovery is pending a later
 * explicit release; no reset or deletion API is exposed.
 */
class SavedItemStore(private val store: EncryptedAccountStore) {

    private val lock = Mutex()
    private val _revision = MutableStateFlow(0)

    /** Bumped after every durable write so observers re-read. */
    val revision: StateFlow<Int> = _revision

    @Volatile
    private var corrupted = false

    fun isCorrupted(): Boolean = corrupted

    suspend fun list(): List<HomeSavedItemRef> = lock.withLock {
        // Holding this Mutex across the suspending encrypted-prefs I/O below is intentional and
        // acceptable: it keeps each load/modify/persist sequence atomic for this account.
        // Mutex is not reentrant, so no method calls another locking method while holding it.
        loadLocked()
    }

    /** Saves one reference, replacing any existing ref with the same id.
     *  Returns false - writing nothing - for a Message-kind ref, for invalid
     *  input, on durable-write failure, or while corrupted. */
    suspend fun save(item: HomeSavedItemRef): Boolean = lock.withLock {
        // Holding this Mutex across the suspending encrypted-prefs I/O below is intentional and
        // acceptable: it keeps each load/modify/persist sequence atomic for this account.
        // Mutex is not reentrant, so no method calls another locking method while holding it.
        if (corrupted) return@withLock false
        if (item.kind == HomeSavedItemKind.Message) return@withLock false
        if (item.id.isBlank() || item.conversationId.isBlank() || item.messageId.isBlank()) {
            return@withLock false
        }
        if (item.kind == HomeSavedItemKind.Attachment && item.attachmentId.isNullOrBlank()) {
            return@withLock false
        }
        if (item.kind == HomeSavedItemKind.Link && item.attachmentId != null) {
            return@withLock false
        }
        val current = loadLocked()
        if (corrupted) return@withLock false
        val index = current.indexOfFirst { it.id == item.id }
        val next = if (index >= 0) {
            current.toMutableList().apply { this[index] = item }
        } else {
            current + item
        }
        persistLocked(next)
    }

    private suspend fun loadLocked(): List<HomeSavedItemRef> {
        val raw = store.read(KEY_STATE) ?: return emptyList()
        return try {
            SavedItemCodec.decode(raw)
        } catch (error: SavedItemCodecException) {
            corrupted = true
            preserveRawLocked(raw)
            emptyList()
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

    private suspend fun persistLocked(items: List<HomeSavedItemRef>): Boolean =
        try {
            store.write(KEY_STATE, SavedItemCodec.encode(items))
            _revision.value += 1
            true
        } catch (error: Exception) {
            false
        }

    private companion object {
        const val KEY_STATE = "saved_items_v1"
        const val KEY_CORRUPT_BACKUP = "saved_items_v1_corrupt_backup"
    }
}
