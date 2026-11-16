package com.example.chat.core.audio

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

fun interface AudioDeviceRecordSource {
    suspend fun load(): List<DeviceVerification>
}

/** Packaged historical evidence, not current hardware measurements and never remote config. */
class AssetAudioDeviceRecordSource(context: Context) : AudioDeviceRecordSource {
    private val appContext = context.applicationContext
    override suspend fun load(): List<DeviceVerification> = withContext(Dispatchers.IO) {
        val raw = appContext.assets.open("audio/device_verifications.json")
            .bufferedReader().use { it.readText() }
        parseAudioDeviceRecords(raw)
    }
}

internal fun parseAudioDeviceRecords(raw: String): List<DeviceVerification> {
    val array = JSONArray(raw)
    require(array.length() <= 500) { "Audio device record asset is too large" }
    return (0 until array.length()).mapNotNull { index ->
        val entry = array.optJSONObject(index) ?: return@mapNotNull null
        val record = mutableMapOf<String, Any?>()
        val keys = entry.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = entry.opt(key)
            record[key] = value as? String // Null/objects/booleans never become fabricated labels.
        }
        DeviceVerification.fromRecord(record)
    }
}
