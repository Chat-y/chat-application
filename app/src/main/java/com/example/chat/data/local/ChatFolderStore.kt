package com.example.chat.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

data class ChatFolder(val id: String, val name: String)

/** R2-34: result of a folder-import dry run. [accepted] is true only when no
 *  errors were found. A dry run never writes anything. */
data class FolderImportDryRun(
    val accepted: Boolean,
    val errors: List<String>,
    val warnings: List<String>,
    val folderCount: Int,
    val membershipCount: Int,
    val nameCollisions: List<String>,
)

/** Content-free integrity categories. The legacy reader remains available unchanged. */
enum class FolderIntegrityIssue {
    MALFORMED_DOCUMENT, UNSUPPORTED_FIELDS, UNSUPPORTED_VERSION,
    DUPLICATE_FOLDER_ID, ORPHAN_MEMBERSHIP, INVALID_MEMBER_ID,
}

/** Opaque, process-local optimistic edit token. Never persist or log it. */
class FolderEditToken internal constructor(
    internal val ownerIdentity: Any,
    internal val generation: Long,
    internal val rawState: String?,
)

data class FolderEditSnapshot(
    val folders: List<ChatFolder>,
    val members: Map<String, Set<String>>,
    val issues: Set<FolderIntegrityIssue>,
    val token: FolderEditToken,
)

enum class FolderSaveStatus {
    ACCEPTED_IN_MEMORY, DURABLE, STORAGE_FAILURE, CONFLICT, INVALID_DOCUMENT,
    INVALID_NAME, INVALID_MEMBERS, NOT_FOUND,
}

/** Only DURABLE confirms commit success. STORAGE_FAILURE may still have changed
 *  SharedPreferences memory; retain the editor input and retry only with retryToken.
 *  A later write invalidates that token. No automatic retry or rollback is done. */
data class FolderSaveResult(
    val status: FolderSaveStatus,
    val folderId: String? = null,
    val retryToken: FolderEditToken? = null,
)

/** Local, account-scoped chat folders. Membership is client-side only: one
 *  SharedPreferences file per account, never synced.
 *  Purge on sign-out is not wired yet. */
class ChatFolderStore(context: Context, accountKey: String) {

    private data class State(val folders: List<ChatFolder>, val members: Map<String, Set<String>>)

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_PREFIX + java.security.MessageDigest.getInstance("SHA-256").digest(accountKey.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) },
        Context.MODE_PRIVATE,
    )

    /** SHA-256 of the account key, the same digest used for the prefs file name
     *  above. Lets export/import bind payloads to this account without exposing
     *  the raw account key. Must stay in sync with that digest expression. */
    private val accountScopeHash: String = java.security.MessageDigest.getInstance("SHA-256").digest(accountKey.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
    // R5-19: every store instance for this account shares its lock, cache and flows.
    // This is process-local ownership, not SharedPreferences multi-process support.
    private class Owner {
        val lock = Any()
        var corrupted = false
        var cached: State? = null
        var generation = 0L
        val revision = MutableStateFlow(0)
        val lastSave = MutableStateFlow<FolderSaveResult?>(null)
    }
    private val owner = synchronized(owners) { owners.getOrPut(accountScopeHash) { Owner() } }
    private val lock get() = owner.lock
    private var corrupted: Boolean
        get() = owner.corrupted
        set(value) { owner.corrupted = value }
    private var cached: State?
        get() = owner.cached
        set(value) { owner.cached = value }
    /** R5-20: legacy writes are accepted in memory, not confirmed durable saves. */
    val lastSave: StateFlow<FolderSaveResult?> = owner.lastSave
    fun isCorrupted():Boolean = synchronized(lock) {read();corrupted}
    /** Explicit user-confirmed reset only. Never discard corrupt data automatically. */
    fun resetCorruptedStore():Boolean=synchronized(lock) {
        if(!corrupted) return@synchronized true
        val backup=prefs.getString(KEY_STATE,null)
        if(backup!=null && !prefs.edit().putString("recovery_backup",backup).commit()) return@synchronized false
        corrupted=false; cached=State(emptyList(),emptyMap());write(cached!!)
    }

    /** Bumped on every write so Compose observers re-read. */
    private val _revision = owner.revision
    val revision: StateFlow<Int> = _revision

    fun folders(): List<ChatFolder> = synchronized(lock) { read().folders }

    fun memberIds(folderId: String): Set<String> =
        synchronized(lock) { read().members[folderId] ?: emptySet() }

    /** Null when the name is blank or already used (case-insensitive). */
    fun createFolder(name: String): ChatFolder? {
        val trimmed = boundedFolderName(name)
        if (trimmed.isEmpty()) return null
        synchronized(lock) {
            val state = read()
            if (state.folders.any { it.name.equals(trimmed, ignoreCase = true) }) return null
            val folder = ChatFolder(id = "f_" + java.util.UUID.randomUUID().toString(), name = trimmed)
            return if (write(state.copy(folders = state.folders + folder))) folder else null
        }
    }

    fun renameFolder(folderId: String, name: String):Boolean {
        val trimmed = boundedFolderName(name)
        if (trimmed.isEmpty()) return false
        return synchronized(lock) {
            val state = read()
            if (state.folders.none { it.id == folderId }) return@synchronized false
            if (state.folders.any { it.id != folderId && it.name.equals(trimmed, ignoreCase = true) }) return@synchronized false
            write(state.copy(folders = state.folders.map {
                if (it.id == folderId) it.copy(name = trimmed) else it
            }))
        }
    }

    fun deleteFolder(folderId: String) {
        synchronized(lock) {
            val state = read()
            write(state.copy(
                folders = state.folders.filterNot { it.id == folderId },
                members = state.members - folderId,
            ))
        }
    }

    fun setMembers(folderId: String, conversationIds: Set<String>) {
        synchronized(lock) {
            val state = read()
            if (state.folders.none { it.id == folderId }) return
            write(state.copy(members = state.members + (folderId to conversationIds)))
        }
    }

    fun toggleMember(folderId: String, conversationId: String) {
        synchronized(lock) {
            val state = read()
            if (state.folders.none { it.id == folderId }) return
            val current = state.members[folderId] ?: emptySet()
            val next = if (conversationId in current) current - conversationId else current + conversationId
            write(state.copy(members = state.members + (folderId to next)))
        }
    }

    /** R1-19: deliberate cleanup for account removal. Erases every folder and
     *  membership stored for this account on this device, including a recovery
     *  backup left by resetCorruptedStore. Folder data is device-local only
     *  (never synced), so this is the sole retention control; call it from the
     *  account-removal flow, never automatically on transient errors. */
    fun purgeAccountData(): Boolean = synchronized(lock) {
        cached = State(emptyList(), emptyMap())
        corrupted = false
        val cleared = prefs.edit().remove(KEY_STATE).remove("recovery_backup").commit()
        owner.generation++
        _revision.value = _revision.value + 1
        cleared
    }

    /** R2-33: minimal local export for the user to inspect BEFORE saving. Folder
     *  names and conversation ids can be private, so show this preview and
     *  require an explicit destination. Nothing is shared or saved here; this
     *  only returns the payload text. */
    fun buildExportPreview(): String = synchronized(lock) {
        val state = read()
        val root = JSONObject()
        root.put("format", EXPORT_FORMAT)
        root.put("version", EXPORT_VERSION)
        root.put("accountHash", accountScopeHash)
        root.put("sensitivityNote", EXPORT_SENSITIVITY_NOTE)
        root.put("folders", JSONArray().apply {
            state.folders.forEach { put(JSONObject().put("id", it.id).put("name", it.name)) }
        })
        root.put("members", JSONObject().apply {
            state.members.forEach { (k, v) -> put(k, JSONArray(v.toList())) }
        })
        root.toString(2)
    }

    /** R2-34: validates an export payload WITHOUT importing anything. Checks the
     *  schema, the account binding and name collisions against the current
     *  state. Payloads bound to another account are rejected outright; untrusted
     *  ids are never merged into this account by name matching. Whether member
     *  conversation ids still exist needs the conversations repository and
     *  stays outside this store. */
    fun dryRunImport(payload: String): FolderImportDryRun {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val nameCollisions = mutableListOf<String>()
        var folderCount = 0
        var membershipCount = 0
        val root = runCatching { JSONObject(payload) }.getOrElse {
            return FolderImportDryRun(false, listOf("payload is not valid JSON"), emptyList(), 0, 0, emptyList())
        }
        if (root.optString("format") != EXPORT_FORMAT) errors += "unrecognized export format"
        val version = root.optInt("version", -1)
        if (version != EXPORT_VERSION) errors += "unsupported export version: $version"
        val payloadAccount = root.optString("accountHash", "")
        if (payloadAccount.isBlank()) {
            errors += "missing account binding"
        } else if (payloadAccount != accountScopeHash) {
            errors += "payload belongs to another account; cross-account import is never merged by name matching"
        }
        val folderIds = mutableSetOf<String>()
        val folderNames = mutableSetOf<String>()
        val folders = root.optJSONArray("folders")
        if (folders == null) {
            errors += "missing folders array"
        } else {
            for (i in 0 until folders.length()) {
                val o = folders.optJSONObject(i)
                if (o == null) { errors += "folder entry $i is not an object"; continue }
                val id = o.optString("id", "").trim()
                val name = o.optString("name", "").trim()
                if (id.isEmpty() || name.isEmpty()) { errors += "folder entry $i needs a non-blank id and name"; continue }
                if (!folderIds.add(id)) errors += "duplicate folder id in payload: $id"
                if (!folderNames.add(name.lowercase())) errors += "duplicate folder name in payload: $name"
                folderCount++
            }
        }
        val members = root.optJSONObject("members")
        if (members != null) {
            for (key in members.keys()) {
                if (key !in folderIds) { errors += "members entry references unknown folder id: $key"; continue }
                val arr = members.optJSONArray(key)
                if (arr == null) { errors += "members entry for $key is not an array"; continue }
                for (i in 0 until arr.length()) {
                    val cid = arr.optString(i, "").trim()
                    if (cid.isEmpty()) { errors += "blank conversation id in members of $key"; continue }
                    membershipCount++
                }
            }
        }
        synchronized(lock) {
            val existing = read().folders
            for (name in folderNames) {
                existing.firstOrNull { it.name.lowercase() == name }?.let { nameCollisions += it.name }
            }
        }
        if (nameCollisions.isNotEmpty()) warnings += "folder names already used in this account: ${nameCollisions.joinToString()}"
        return FolderImportDryRun(errors.isEmpty(), errors, warnings, folderCount, membershipCount, nameCollisions)
    }

    /** R5-12..14: opt-in checked decode for editors. Does not modify stored bytes,
     *  repair records, clear markers, or change the legacy getters' readable values.
     *  Missing folders/members are not recoverable in this checked path; only an
     *  absent state key means a new empty store. Unknown fields block rewriting. */
    fun editSnapshot(): FolderEditSnapshot = synchronized(lock) {
        val raw = prefs.getString(KEY_STATE, null)
        val checked = checkDocument(raw)
        val state = checked.first
        FolderEditSnapshot(
            state?.folders?.toList() ?: emptyList(),
            state?.members?.mapValues { it.value.toSet() } ?: emptyMap(),
            checked.second.toSet(),
            FolderEditToken(owner, owner.generation, raw),
        )
    }

    /** R5-15/18/20/30/49: create or atomically edit a folder using the legacy JSON
     *  layout. folderId == null creates a new ID. knownConversationIds MUST come
     *  from the active account repository, not restored/imported preferences.
     *  Run off the main thread: commit is synchronous. Keep input open unless the
     *  result is DURABLE. On failure retry with the returned token AND folderId
     *  (including null for a create not accepted in memory). CONFLICT requires
     *  reload or a separately reviewed merge.
     *  No recovery reset, migration, deletion, or automatic retry happens here. */
    fun saveFolderDurably(
        name: String,
        conversationIds: Set<String>,
        knownConversationIds: Set<String>,
        expected: FolderEditToken,
        folderId: String? = null,
    ): FolderSaveResult = synchronized(lock) {
        val raw = prefs.getString(KEY_STATE, null)
        if (expected.ownerIdentity !== owner || expected.generation != owner.generation || expected.rawState != raw) {
            return@synchronized FolderSaveResult(FolderSaveStatus.CONFLICT, folderId)
        }
        val checked = checkDocument(raw)
        val state = checked.first
        if (state == null || checked.second.isNotEmpty() || corrupted) {
            return@synchronized FolderSaveResult(FolderSaveStatus.INVALID_DOCUMENT, folderId)
        }
        if (folderId != null && state.folders.none { it.id == folderId }) {
            return@synchronized FolderSaveResult(FolderSaveStatus.NOT_FOUND, folderId)
        }
        val trimmed = boundedFolderName(name)
        if (trimmed.isBlank() || state.folders.any { it.id != folderId && it.name.equals(trimmed, ignoreCase = true) }) {
            return@synchronized FolderSaveResult(FolderSaveStatus.INVALID_NAME, folderId)
        }
        // Copy the candidate set before validating; never silently trim/drop IDs.
        val members = conversationIds.toSet()
        if (members.any { it.isBlank() || it !in knownConversationIds }) {
            return@synchronized FolderSaveResult(FolderSaveStatus.INVALID_MEMBERS, folderId)
        }
        val id = folderId ?: ("f_" + java.util.UUID.randomUUID().toString())
        val folders = if (folderId == null) state.folders + ChatFolder(id, trimmed) else state.folders.map {
            if (it.id == id) it.copy(name = trimmed) else it
        }
        val next = State(folders, state.members + (id to members))
        val encoded = encode(next)
        // commit(false) still updates the preferences' in-memory map. Publish that
        // accepted state honestly, without claiming it reached disk or rolling back.
        val durable = try {
            prefs.edit().putString(KEY_STATE, encoded).commit()
        } catch (_: RuntimeException) {
            false
        }
        cached = null
        owner.generation++
        _revision.value = _revision.value + 1
        val acceptedRaw = prefs.getString(KEY_STATE, null)
        val result = FolderSaveResult(
            if (durable) FolderSaveStatus.DURABLE else FolderSaveStatus.STORAGE_FAILURE,
            if (durable || acceptedRaw == encoded) id else folderId,
            if (durable) null else FolderEditToken(owner, owner.generation, acceptedRaw),
        )
        owner.lastSave.value = result
        result
    }

    /** R5-16: ICU character boundaries (API 24, matching minSdk). Existing names
     *  are not rewritten. Create/rename/edit share a 24-grapheme policy. */
    private fun boundedFolderName(name: String): String {
        val trimmed = name.trim()
        val iterator = android.icu.text.BreakIterator.getCharacterInstance(java.util.Locale.ROOT)
        iterator.setText(trimmed)
        var end = iterator.first()
        repeat(24) {
            val next = iterator.next()
            if (next == android.icu.text.BreakIterator.DONE) return trimmed
            end = next
        }
        return trimmed.substring(0, end)
    }

    /** No user names, member IDs, raw payloads or exception text in diagnostics. */
    private fun checkDocument(raw: String?): Pair<State?, Set<FolderIntegrityIssue>> {
        if (raw == null) return State(emptyList(), emptyMap()) to emptySet()
        val issues = linkedSetOf<FolderIntegrityIssue>()
        return try {
            val tokener = JSONTokener(raw)
            val root = tokener.nextValue() as? JSONObject ?: error("root type")
            if (tokener.nextClean() != '\u0000') error("trailing data")
            for (key in root.keys()) {
                if (key == "version" || key == "schemaVersion") issues += FolderIntegrityIssue.UNSUPPORTED_VERSION
                else if (key != "folders" && key != "members") issues += FolderIntegrityIssue.UNSUPPORTED_FIELDS
            }
            val folders = root.getJSONArray("folders")
            val ids = mutableSetOf<String>()
            val list = (0 until folders.length()).map { index ->
                val item = folders.getJSONObject(index)
                for (key in item.keys()) {
                    if (key != "id" && key != "name") issues += FolderIntegrityIssue.UNSUPPORTED_FIELDS
                }
                val id = item.get("id") as? String ?: error("id type")
                val name = item.get("name") as? String ?: error("name type")
                if (id.isBlank() || name.isBlank()) error("blank folder")
                if (!ids.add(id)) issues += FolderIntegrityIssue.DUPLICATE_FOLDER_ID
                ChatFolder(id, name)
            }
            val membersObject = root.getJSONObject("members")
            val members = linkedMapOf<String, Set<String>>()
            for (key in membersObject.keys()) {
                if (key !in ids) issues += FolderIntegrityIssue.ORPHAN_MEMBERSHIP
                val array = membersObject.getJSONArray(key)
                members[key] = (0 until array.length()).map { index ->
                    val id = array.get(index) as? String ?: error("member type")
                    if (id.isBlank()) issues += FolderIntegrityIssue.INVALID_MEMBER_ID
                    id
                }.toSet()
            }
            val state = if (issues.isEmpty()) State(list, members) else null
            state to issues
        } catch (_: Exception) {
            issues += FolderIntegrityIssue.MALFORMED_DOCUMENT
            null to issues
        }
    }

    // Same keys and representation as write(); deliberately no version or metadata.
    private fun encode(state: State): String = JSONObject().apply {
        put("folders", JSONArray().apply {
            state.folders.forEach { put(JSONObject().put("id", it.id).put("name", it.name)) }
        })
        put("members", JSONObject().apply {
            state.members.forEach { (key, value) -> put(key, JSONArray(value.toList())) }
        })
    }.toString()

    private fun read(): State {
        cached?.let {return it}
        val raw = prefs.getString(KEY_STATE, null) ?: return State(emptyList(), emptyMap())
        return runCatching {
            val root = JSONObject(raw)
            val folders = root.optJSONArray("folders") ?: JSONArray()
            val folderList = (0 until folders.length()).mapNotNull { i ->
                val o = folders.optJSONObject(i) ?: return@mapNotNull null
                ChatFolder(id = o.optString("id"), name = o.optString("name"))
                    .takeIf { it.id.isNotBlank() && it.name.isNotBlank() }
            }
            val membersObj = root.optJSONObject("members") ?: JSONObject()
            val members = mutableMapOf<String, Set<String>>()
            for (key in membersObj.keys()) {
                val arr = membersObj.optJSONArray(key) ?: continue
                members[key] = (0 until arr.length()).mapNotNull { arr.optString(it, null) }.toSet()
            }
            State(folderList, members)
        }.getOrElse { corrupted = true; State(emptyList(), emptyMap()) }.also {cached=it}
    }

    private fun write(state: State): Boolean {
        if (corrupted) return false // Preserve unreadable data rather than silently overwriting it.
        val root = JSONObject()
        root.put("folders", JSONArray().apply {
            state.folders.forEach { put(JSONObject().put("id", it.id).put("name", it.name)) }
        })
        root.put("members", JSONObject().apply {
            state.members.forEach { (k, v) -> put(k, JSONArray(v.toList())) }
        })
        prefs.edit().putString(KEY_STATE, root.toString()).apply()
        cached=state
        owner.generation++
        owner.lastSave.value = FolderSaveResult(FolderSaveStatus.ACCEPTED_IN_MEMORY)
        _revision.value = _revision.value + 1
        return true
    }

    private companion object {
        val owners = mutableMapOf<String, Owner>()
        const val PREFS_PREFIX = "chat_folders_"
        const val KEY_STATE = "state"

        /** R1-19: user-facing disclosure that folder labels and membership are
         *  stored on this device only and kept until purgeAccountData runs. */
        const val DEVICE_LOCAL_PERSISTENCE_DISCLOSURE =
            "Chat folders are stored only on this device, are never synced, and are kept until the account is removed."

        /** R2-33/R2-34: export payload identity and the privacy note shown with previews. */
        const val EXPORT_FORMAT = "chaty-folder-export"
        const val EXPORT_VERSION = 1
        const val EXPORT_SENSITIVITY_NOTE =
            "Folder names and conversation IDs can be private. Save only to a destination you choose; nothing is shared automatically."
    }
}
