package com.example.chat.ui.calls

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.toMutablePreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first

/** One process-wide device store; imports old values once without clearing the old store. */
class AndroidCallPresentationPrefsStorage(context: Context) : CallPresentationPrefsStorage {
    private val store = sharedStore(context.applicationContext)

    override suspend fun getBool(key: String): Boolean? = store.data.first()[booleanPreferencesKey(key)]
    override suspend fun getString(key: String): String? = store.data.first()[stringPreferencesKey(key)]
    override suspend fun putBool(key: String, value: Boolean): Boolean {
        store.edit { it[booleanPreferencesKey(key)] = value }
        return true
    }
    override suspend fun putString(key: String, value: String): Boolean {
        store.edit { it[stringPreferencesKey(key)] = value }
        return true
    }

    override suspend fun putCallSettingsSnapshot(value: CallPresentationPreferences): Boolean {
        store.edit { next ->
            next[stringPreferencesKey(CallVideoQuality.STORAGE_KEY)] = value.capture.videoQuality.name
            for (option in CallCaptureOption.entries)
                next[booleanPreferencesKey(option.storageKey)] = option.selected(value.capture)
            next[booleanPreferencesKey(CallPresentationPreferencesStore.DYNAMIC_ISLAND_KEY)] = value.dynamicIslandEnabled
            next[booleanPreferencesKey(CallPresentationPreferencesStore.PICTURE_IN_PICTURE_KEY)] = value.pictureInPictureEnabled
            next[booleanPreferencesKey(CallPresentationPreferencesStore.LOW_DATA_USAGE_KEY)] = value.lowDataUsageEnabled
            next[stringPreferencesKey(CallPresentationPreferencesStore.BUNDLE_SCOPES_KEY)] =
                value.callExperienceBundleId + "|" + value.messageExperienceBundleId
        }
        return true
    }

    companion object {
        @Volatile private var instance: DataStore<Preferences>? = null
        private fun sharedStore(context: Context): DataStore<Preferences> = instance ?: synchronized(this) {
            instance ?: PreferenceDataStoreFactory.create(
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                migrations = listOf(object : DataMigration<Preferences> {
                    private val imported = booleanPreferencesKey("legacy_call_preferences_imported_v1")
                    override suspend fun shouldMigrate(currentData: Preferences) = currentData[imported] != true
                    override suspend fun migrate(currentData: Preferences): Preferences {
                        val legacy = context.getSharedPreferences("chaty_call_presentation", Context.MODE_PRIVATE)
                        val next = currentData.toMutablePreferences()
                        val boolKeys = listOf(
                            CallPresentationPreferencesStore.DYNAMIC_ISLAND_KEY,
                            CallPresentationPreferencesStore.PICTURE_IN_PICTURE_KEY,
                            CallPresentationPreferencesStore.LOW_DATA_USAGE_KEY,
                        )
                        val textKeys = listOf(
                            CallPresentationPreferencesStore.INCOMING_TEMPLATE_KEY,
                            CallPresentationPreferencesStore.ONGOING_TEMPLATE_KEY,
                            CallPresentationPreferencesStore.BUNDLE_SCOPES_KEY,
                            CallPresentationPreferencesStore.CALL_EXPERIENCE_BUNDLE_KEY,
                        )
                        // Preserve unknown keys in the old file; do not silently coerce malformed values.
                        val values = legacy.all
                        for (name in boolKeys) {
                            val key = booleanPreferencesKey(name)
                            if (currentData[key] == null && values.containsKey(name)) {
                                val value = values[name]
                                check(value is Boolean) { "Legacy call preference $name has an invalid type; import was not completed." }
                                next[key] = value
                            }
                            // Prior skip: if (currentData[key] == null && values[name] is Boolean) next[key] = values[name] as Boolean
                        }
                        for (name in textKeys) {
                            val key = stringPreferencesKey(name)
                            if (currentData[key] == null && values.containsKey(name)) {
                                val value = values[name]
                                check(value is String) { "Legacy call preference $name has an invalid type; import was not completed." }
                                next[key] = value
                            }
                            // Prior skip: if (currentData[key] == null && values[name] is String) next[key] = values[name] as String
                        }
                        next[imported] = true
                        return next
                    }
                    override suspend fun cleanUp() = Unit // Legacy file deliberately retained.
                }),
                produceFile = { context.preferencesDataStoreFile("chaty_call_presentation_v2") },
            ).also { instance = it }
        }
    }
}

// Previous SharedPreferences adapter retained inactive:
// package com.example.chat.ui.calls
//
// import android.content.Context
// import kotlinx.coroutines.Dispatchers
// import kotlinx.coroutines.withContext
//
// /** SharedPreferences-backed storage for CallPresentationPreferencesStore;
//  *  same keys as the Dart shared_preferences writes. */
// class AndroidCallPresentationPrefsStorage(context: Context) : CallPresentationPrefsStorage {
//     private val prefs = context.applicationContext
//         .getSharedPreferences("chaty_call_presentation", Context.MODE_PRIVATE)
//
//     // Previous main-dispatcher read: if (prefs.contains(key)) prefs.getBoolean(key, false) else null
//     override suspend fun getBool(key: String): Boolean? = withContext(Dispatchers.IO) {
//         if (prefs.contains(key)) prefs.getBoolean(key, false) else null
//     }
//
//     // Previous main-dispatcher read: if (prefs.contains(key)) prefs.getString(key, null) else null
//     override suspend fun getString(key: String): String? = withContext(Dispatchers.IO) {
//         if (prefs.contains(key)) prefs.getString(key, null) else null
//     }
//
//     // Previous main-dispatcher commit: prefs.edit().putBoolean(key, value).commit()
//     override suspend fun putBool(key: String, value: Boolean): Boolean = withContext(Dispatchers.IO) {
//         prefs.edit().putBoolean(key, value).commit()
//     }
//
//     // Previous main-dispatcher commit: prefs.edit().putString(key, value).commit()
//     override suspend fun putString(key: String, value: String): Boolean = withContext(Dispatchers.IO) {
//         prefs.edit().putString(key, value).commit()
//     }
// }
