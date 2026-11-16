package com.example.chat.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decision 1B primitive (wave 5, source-only; deletion held): per-account
 * encrypted key-value storage backing ONLY the new Home stores
 * (FollowUpStore, SavedItemStore, ConversationNoteStore, ReminderSeenKeyStore).
 *
 * One EncryptedSharedPreferences file per account, named
 * "secure_acct_" + sha256(accountKey). The SHA-256 hex digest expression is
 * exactly the one ChatFolderStore uses; the "secure_acct_" prefix is new and
 * deliberately distinct from ChatFolderStore's "chat_folders_" so the two
 * families never share a file. Keys use AES256_SIV and values AES256_GCM
 * under a platform-keystore MasterKey, the same schemes as
 * SecureKeyValueStoreImpl.
 *
 * Scope limits (NEW code only):
 * - The global SecureKeyValueStoreImpl and the folder prefs keep their
 *   current bytes; nothing here reads or writes them.
 * - No plain-to-encrypted migration of any existing data.
 * - No sync, no backup/restore wiring, no exact alarms, no deletion on
 *   sign-out or account removal. No deletion primitive is exposed; deletion
 *   is pending explicit release.
 *
 * Every mutation waits for a durable commit() and throws when Android
 * reports the write failed (same contract as SecureKeyValueStoreImpl).
 */
class EncryptedAccountStore(context: Context, accountKey: String) {

    init {
        require(accountKey.isNotBlank()) { "An account-scoped store requires a non-blank account key." }
    }

    /** Name of this account's on-disk encrypted preferences file. */
    val scopeFileName: String = PREFS_PREFIX + sha256Hex(accountKey)

    private val prefs = EncryptedSharedPreferences.create(
        context.applicationContext,
        scopeFileName,
        MasterKey.Builder(context.applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    suspend fun read(key: String): String? = withContext(Dispatchers.IO) {
        prefs.getString(key, null)
    }

    suspend fun readAll(): Map<String, String> = withContext(Dispatchers.IO) {
        prefs.all.mapNotNull { (key, value) -> if (value is String) key to value else null }.toMap()
    }

    suspend fun write(key: String, value: String): Unit = withContext(Dispatchers.IO) {
        check(prefs.edit().putString(key, value).commit()) { "Unable to persist encrypted account value." }
    }

    suspend fun writeAll(values: Map<String, String>): Unit = withContext(Dispatchers.IO) {
        val editor = prefs.edit()
        values.forEach { (key, value) -> editor.putString(key, value) }
        check(editor.commit()) { "Unable to persist encrypted account values." }
    }

    private companion object {
        const val PREFS_PREFIX = "secure_acct_"

        /** Exact digest expression used by ChatFolderStore. */
        fun sha256Hex(accountKey: String): String =
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(accountKey.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
