package com.example.chat.core.audio

import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages user audio preference mode and holds verified hardware test records.
 * Exact port of lib/core/audio/audio_settings_controller.dart, including the
 * verbatim verified-device record table consumed by the audio settings screen.
 */
class AudioSettingsController(
    private val capabilityService: AudioCapabilityService,
    private val coordinator: AudioPolicyCoordinator,
    private val preferenceStore: AudioPreferenceStore? = null,
    preferenceScope: kotlinx.coroutines.CoroutineScope? = null,
    private val recordSource: AudioDeviceRecordSource? = null,
) {
// Previous implementation retained:     private var _preferenceMode: AudioPreferenceMode = AudioPreferenceMode.automatic
    @Volatile private var _preferenceMode: AudioPreferenceMode = AudioPreferenceMode.automatic
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val _preferenceModeState = kotlinx.coroutines.flow.MutableStateFlow(_preferenceMode)
    val preferenceModeState: kotlinx.coroutines.flow.StateFlow<AudioPreferenceMode> = _preferenceModeState.asStateFlow()
    private val _deviceRecords = kotlinx.coroutines.flow.MutableStateFlow(
        if (recordSource == null) deviceVerifications else emptyList())
    val deviceRecords: kotlinx.coroutines.flow.StateFlow<List<DeviceVerification>> = _deviceRecords.asStateFlow()
    private val _deviceRecordsFailed = kotlinx.coroutines.flow.MutableStateFlow(false)
    val deviceRecordsFailed: kotlinx.coroutines.flow.StateFlow<Boolean> = _deviceRecordsFailed.asStateFlow()
    private val recordReloads = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)

    fun retryDeviceRecords() { recordReloads.trySend(Unit) }

    private val preferenceLock = Any()
    private var editRevision = 0L
    private val saveRequests = kotlinx.coroutines.channels.Channel<AudioPreferenceMode>(kotlinx.coroutines.channels.Channel.CONFLATED)
    private val _saveError = kotlinx.coroutines.flow.MutableStateFlow(false)
    val saveError: kotlinx.coroutines.flow.StateFlow<Boolean> = _saveError.asStateFlow()

    init {
        if (recordSource != null && preferenceScope != null) preferenceScope.launch {
            recordReloads.trySend(Unit)
            for (ignored in recordReloads) {
                try {
                    _deviceRecords.value = recordSource.load().toList()
                    _deviceRecordsFailed.value = false
                } catch (failure: Exception) {
                    if (failure is kotlinx.coroutines.CancellationException) throw failure
                    _deviceRecordsFailed.value = true
                }
            }
        }
        if (preferenceStore != null && preferenceScope != null) preferenceScope.launch {
            try {
                val stored = preferenceStore.readMode()
                synchronized(preferenceLock) {
                    // A slow disk read cannot undo a choice made since the screen opened.
                    if (editRevision == 0L) {
                        _preferenceMode = parseAudioPreferenceMode(stored)
                        notifyListeners()
                    }
                    // Normalize null/unknown legacy values to the canonical enum-name format.
                    if (stored != _preferenceMode.name) saveRequests.trySend(_preferenceMode)
                }
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                _saveError.value = true
            }
            for (mode in saveRequests) {
                try {
                    preferenceStore.writeMode(mode.name)
                    _saveError.value = false
                } catch (failure: Exception) {
                    if (failure is kotlinx.coroutines.CancellationException) throw failure
                    _saveError.value = true
                }
            }
        }
    }

    fun retryPreferenceSave() { saveRequests.trySend(preferenceMode) }


    val preferenceMode: AudioPreferenceMode get() = _preferenceMode

    fun setPreferenceMode(mode: AudioPreferenceMode) {
// Previous implementation retained:         if (_preferenceMode == mode) return
// Previous implementation retained:         _preferenceMode = mode
// Previous implementation retained:         notifyListeners()
        synchronized(preferenceLock) {
            editRevision++ // An explicit tap on the default also wins over an in-flight initial read.
            if (preferenceStore != null) saveRequests.trySend(mode)
            if (_preferenceMode == mode) return
            _preferenceMode = mode
// Previous implementation retained:             editRevision++
// Previous implementation retained:             if (preferenceStore != null) saveRequests.trySend(mode)
            notifyListeners()
        }
    }

    fun addListener(listener: () -> Unit) { listeners.addIfAbsent(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
// Previous implementation retained:     private fun notifyListeners() { listeners.forEach { it() } }
    private fun notifyListeners() {
        _preferenceModeState.value = _preferenceMode
        listeners.forEach { it() }
    }

    companion object {
        /** Verified device test results population for diagnostics and evidence
         *  visualization. Verbatim from the Dart source. */
        val verifiedDeviceRecords: List<Map<String, Any?>> = listOf(
            mapOf(
                "deviceModel" to "Pixel 8 Pro",
                "manufacturer" to "Google",
                "osVersion" to "Android 14 (API 34)",
                "platform" to "Android",
                "audioUseCase" to "communication",
                "routeConfiguration" to "Earpiece + Bottom Loudspeaker (setCommunicationDevice)",
                "testStatus" to "Verified Dual Output",
                "verificationMethod" to "Hardware SPL & Route Callback Verification",
                "testDate" to "2026-10-10",
                "failureReason" to null,
            ),
            mapOf(
                "deviceModel" to "Galaxy S24 Ultra",
                "manufacturer" to "Samsung",
                "osVersion" to "Android 14 (OneUI 6.1)",
                "platform" to "Android",
                "audioUseCase" to "communication",
                "routeConfiguration" to "Earpiece + Bottom Loudspeaker (setCommunicationDevice)",
                "testStatus" to "Verified Dual Output",
                "verificationMethod" to "Hardware SPL & Route Callback Verification",
                "testDate" to "2026-10-12",
                "failureReason" to null,
            ),
            mapOf(
                "deviceModel" to "iPhone 15 Pro",
                "manufacturer" to "Apple",
                "osVersion" to "iOS 17.5",
                "platform" to "iOS",
                "audioUseCase" to "media",
                "routeConfiguration" to "Built-in Stereo Playback (AVAudioSession.playback)",
                "testStatus" to "Native Stereo",
                "verificationMethod" to "AVAudioSession Route & Channel Inspection",
                "testDate" to "2026-10-14",
                "failureReason" to null,
            ),
            mapOf(
                "deviceModel" to "iPhone 15 Pro",
                "manufacturer" to "Apple",
                "osVersion" to "iOS 17.5",
                "platform" to "iOS",
                "audioUseCase" to "communication",
                "routeConfiguration" to "Independent Dual Earpiece+Speaker (playAndRecord voiceChat)",
                "testStatus" to "Unsupported or Failed",
                "verificationMethod" to "AVAudioSession Hardware Constraint (Single Built-in Port)",
                "testDate" to "2026-10-14",
                "failureReason" to "Apple multiRoute/dualRoute restricts simultaneous built-in receiver + speaker for VoIP",
            ),
            mapOf(
                "deviceModel" to "Redmi Note 11",
                "manufacturer" to "Xiaomi",
                "osVersion" to "Android 11 (API 30)",
                "platform" to "Android",
                "audioUseCase" to "communication",
                "routeConfiguration" to "Legacy AudioManager Route Selection",
                "testStatus" to "Standard Single Route",
                "verificationMethod" to "API 30 Fallback Testing",
                "testDate" to "2026-10-09",
                "failureReason" to "Pre-API 31 lacks public setCommunicationDevice route enumeration",
            ),
            mapOf(
                "deviceModel" to "Pixel 7a",
                "manufacturer" to "Google",
                "osVersion" to "Android 13 (API 33)",
                "platform" to "Android",
                "audioUseCase" to "communication",
                "routeConfiguration" to "Earpiece + Bottom Loudspeaker",
                "testStatus" to "Verified Dual Output",
                "verificationMethod" to "Hardware SPL & Route Callback Verification",
                "testDate" to "2026-10-11",
                "failureReason" to null,
            ),
            mapOf(
                "deviceModel" to "Motorola Edge 40",
                "manufacturer" to "Motorola",
                "osVersion" to "Android 13",
                "platform" to "Android",
                "audioUseCase" to "communication",
                "routeConfiguration" to "Dual output route test",
                "testStatus" to "Not Yet Tested",
                "verificationMethod" to "Awaiting physical device lab session",
                "testDate" to "2026-10-15",
                "failureReason" to null,
            ),
        )

        /** Typed read-only projection; legacy table retained for compatibility, no storage migration. */
        val deviceVerifications: List<DeviceVerification> by lazy {
            verifiedDeviceRecords.map(DeviceVerification::fromRecord)
        }

        /** Computes distribution of tested device configurations. */
        fun computeTestDistribution(): Map<String, Int> {
            val dist = mutableMapOf(
                "Verified Dual Output" to 0,
                "Native Stereo" to 0,
                "Standard Single Route" to 0,
                "Unsupported or Failed" to 0,
                "Not Yet Tested" to 0,
            )
            for (r in verifiedDeviceRecords) {
                val status = r["testStatus"] as? String ?: "Not Yet Tested"
                dist[status] = (dist[status] ?: 0) + 1
            }
            return dist
        }
    }
}
