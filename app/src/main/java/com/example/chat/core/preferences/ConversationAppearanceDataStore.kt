package com.example.chat.core.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.migrations.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File

/** Conversation appearance only. Security, auth, theme and unrelated groups stay in their old store.
 * SharedPreferences is retained as a rollback-compatible mirror, never cleaned up by migration.
 * One singleton per account-scoped file prevents duplicate DataStore instances in the process. */
internal class ConversationAppearanceDataStore private constructor(
    private val legacy: SharedPreferences,
    private val bucket: String,
    file: File,
) {
    private val lock = Mutex()
    private val appearanceKey = stringPreferencesKey("conversation_json_v1")
    private val legacyKey = stringPreferencesKey("legacy_conversation_snapshot_v1")
    private fun legacyConversation(): String? = conversationJsonFromLegacy(legacy.getString(bucket,null))
    private val delegateMigration = SharedPreferencesMigration<Preferences>(
        produceSharedPreferences = { legacy },keysToMigrate = setOf(bucket),
        shouldRunMigration = { current -> current[appearanceKey] == null },
        migrate = { view,current ->
            val value = conversationJsonFromLegacy(view.getString(bucket,null))
            current.toMutablePreferences().apply {
                if (value != null) { this[appearanceKey] = value;this[legacyKey] = value }
            }
        },
    )
    private val retainingMigration = object : DataMigration<Preferences> {
        override suspend fun shouldMigrate(currentData: Preferences): Boolean = delegateMigration.shouldMigrate(currentData)
        override suspend fun migrate(currentData: Preferences): Preferences = delegateMigration.migrate(currentData)
        // Deliberate deviation from SharedPreferencesMigration cleanup: retain every existing key.
        override suspend fun cleanUp() = Unit
    }
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        migrations = listOf(retainingMigration),produceFile = { file },
    )

    /** A legacy-only app may have changed the mirror after rollback. Import that change on read.
     * Absence/malformed legacy data never resurrects a stale DataStore account snapshot. */
    suspend fun readConversation(): Map<String,Any?>? = lock.withLock {
        val legacyValue = legacyConversation() ?: return@withLock null
        val current = dataStore.data.first()
        val value = if (current[legacyKey] != legacyValue || current[appearanceKey] == null) {
            dataStore.edit { next -> next[appearanceKey] = legacyValue;next[legacyKey] = legacyValue }
            legacyValue
        } else current[appearanceKey] ?: legacyValue
        decodeConversationJson(value)
    }

    /** Called only after the legacy IO commit succeeded. Failure is reported by the caller, while
     * the retained mirror remains readable. No second writer or unawaited launch is introduced. */
    suspend fun mirrorCommittedConversation(): Unit = lock.withLock {
        val value = legacyConversation() ?: return@withLock
        dataStore.edit { next -> next[appearanceKey] = value;next[legacyKey] = value }
        Unit
    }

    companion object {
        private val instances = mutableMapOf<String,ConversationAppearanceDataStore>()
        fun get(context: Context,legacy: SharedPreferences,bucket: String): ConversationAppearanceDataStore {
            val file = File(context.applicationContext.filesDir,"datastore/conversation_${bucketDigest(bucket)}.preferences_pb")
            return synchronized(instances) { instances.getOrPut(file.absolutePath) { ConversationAppearanceDataStore(legacy,bucket,file) } }
        }
        private fun bucketDigest(value: String): String = java.security.MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}

/** Fixed appearance-only allowlist: never migrate the surrounding account/security blob or
 * arbitrary strings/unknown fields. The complete original JSON remains in SharedPreferences. */
internal fun conversationJsonFromLegacy(raw: String?): String? = try {
    raw?.let {
        val source = JSONObject(it).optJSONObject(PreferenceKeys.conversation) ?: return@let null
        val result = JSONObject()
        val booleanKeys = setOf("hideProfilePicture","hideContactName","hideCallButton","disableContactStatus")
        val colorKeys = setOf("actionBarColor","contactStatusBgColor","contactStatusTextColor",
            "emojiHeaderColor","emojiHeaderIconsColor","emojiPickerBgColor","hyperlinksColor",
            "infoBalloonsTextColor","infoBalloonsBgColor","groupParticipantNameColor",
            "voiceNotePlayingBarColor","voiceNotePlayButtonColor")
        fun validColor(value: Any?): Boolean = value == JSONObject.NULL || value is Number &&
            value.toDouble().isFinite() && value.toDouble() % 1.0 == 0.0 && value.toDouble() >= Int.MIN_VALUE.toDouble() && value.toDouble() <= UInt.MAX_VALUE.toDouble()
        booleanKeys.forEach { key -> if (source.has(key) && source.opt(key) is Boolean) result.put(key,source.get(key)) }
        colorKeys.forEach { key -> if (source.has(key) && validColor(source.opt(key))) result.put(key,source.get(key)) }
        source.optJSONObject("emojiPopupStyle")?.let { style ->
            val keys = setOf("header","icons","background")
            if (keys.all { key -> style.has(key) && validColor(style.opt(key)) }) {
                val clean = JSONObject();keys.forEach { key -> clean.put(key,style.get(key)) }
                result.put("emojiPopupStyle",clean)
            }
        }
        result.toString()
    }
} catch (_: org.json.JSONException) { null }

internal fun decodeConversationJson(raw: String): Map<String,Any?>? = try {
    @Suppress("UNCHECKED_CAST")
    (LocalPreferencesStorage.unwrap(JSONObject(raw)) as? Map<String,Any?>)
} catch (_: org.json.JSONException) { null }
