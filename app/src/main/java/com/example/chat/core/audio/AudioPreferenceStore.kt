package com.example.chat.core.audio

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface AudioPreferenceStore {
    suspend fun readMode(): String?
    suspend fun writeMode(enumName: String)
}

/** Only an enum name is stored; no device name, Bluetooth address or contact identifier. */
class AndroidAudioPreferenceStore(context: Context) : AudioPreferenceStore {
    private val appContext = context.applicationContext
    override suspend fun readMode(): String? = withContext(Dispatchers.IO) {
        appContext.getSharedPreferences("chaty_audio_preferences", Context.MODE_PRIVATE)
            .all["mode"] as? String
    }
    override suspend fun writeMode(enumName: String) = withContext(Dispatchers.IO) {
        check(appContext.getSharedPreferences("chaty_audio_preferences", Context.MODE_PRIVATE)
            .edit().putString("mode", enumName).commit()) { "Audio preference could not be saved" }
    }
}

internal fun parseAudioPreferenceMode(stored: String?): AudioPreferenceMode =
    AudioPreferenceMode.values().firstOrNull { it.name == stored } ?: AudioPreferenceMode.automatic
