package com.example.chat

import com.example.chat.core.audio.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** NOT RUN. Local enum persistence seams only; no real disk or account/backup guarantees. */
@OptIn(ExperimentalCoroutinesApi::class)
class AudioPreferencePersistenceGapTest {
    private class Platform : ChatyAudioPlatform {
        override val capabilityChanges = emptyFlow<AudioCapabilities>()
        override val routeChanges = emptyFlow<AudioOutputRoute>()
        override suspend fun getCapabilities(useCase: AudioUseCase) = defaultAudioCapabilities(useCase)
        override suspend fun getCurrentRoute(useCase: AudioUseCase) = AudioOutputRoute.systemDefault
        override suspend fun setPreferredRoute(useCase: AudioUseCase, route: AudioOutputRoute) = defaultAudioCapabilities(useCase)
        override suspend fun getDualOutputStatus(useCase: AudioUseCase) = DualOutputStatus.notVerified
        override suspend fun releaseSession(useCase: AudioUseCase) {}
    }
    private class Store(var stored: String?) : AudioPreferenceStore {
        var readGate: CompletableDeferred<Unit>? = null
        var failWrite = false
        val writes = mutableListOf<String>()
        override suspend fun readMode(): String? { readGate?.await(); return stored }
        override suspend fun writeMode(enumName: String) {
            if (failWrite) error("fixture disk failure")
            writes += enumName; stored = enumName
        }
    }
    @Test fun unknownStoredModeBecomesAutomaticAndNormalizesEnumName() = runTest {
        val caps = AudioCapabilityService(Platform(), backgroundScope)
        val store = Store("future-mode")
        val settings = AudioSettingsController(caps, AudioPolicyCoordinator(caps), store, backgroundScope)
        runCurrent()
        assertEquals(AudioPreferenceMode.automatic, settings.preferenceMode)
        assertEquals(listOf("automatic"), store.writes)
        assertFalse(settings.saveError.value)
    }
    @Test fun slowLoadCannotOverwriteNewUserSelection() = runTest {
        val caps = AudioCapabilityService(Platform(), backgroundScope)
        val store = Store("automatic").apply { readGate = CompletableDeferred() }
        val settings = AudioSettingsController(caps, AudioPolicyCoordinator(caps), store, backgroundScope)
        runCurrent()
        settings.setPreferenceMode(AudioPreferenceMode.preferStereo)
        store.readGate!!.complete(Unit); runCurrent()
        assertEquals(AudioPreferenceMode.preferStereo, settings.preferenceMode)
        assertEquals("preferStereo", store.stored)
    }
    @Test fun saveFailureIsVisibleAndRetryWritesCurrentMode() = runTest {
        val caps = AudioCapabilityService(Platform(), backgroundScope)
        val store = Store("automatic")
        val settings = AudioSettingsController(caps, AudioPolicyCoordinator(caps), store, backgroundScope)
        runCurrent(); store.failWrite = true
        settings.setPreferenceMode(AudioPreferenceMode.preferStereo); runCurrent()
        assertTrue(settings.saveError.value)
        assertEquals("automatic", store.stored)
        store.failWrite = false; settings.retryPreferenceSave(); runCurrent()
        assertFalse(settings.saveError.value)
        assertEquals("preferStereo", store.stored)
    }
    @Test fun canonicalNamesParseAndUnknownsFailToAutomatic() {
        for (mode in AudioPreferenceMode.values()) assertEquals(mode, parseAudioPreferenceMode(mode.name))
        for (value in listOf(null, "", "PREFERSTEREO", "unknown"))
            assertEquals(AudioPreferenceMode.automatic, parseAudioPreferenceMode(value))
    }
    @Test fun explicitDefaultTapAlsoWinsOverSlowSavedExperimentalMode() = runTest {
        val caps = AudioCapabilityService(Platform(), backgroundScope)
        val store = Store("experimentalDualOutput").apply { readGate = CompletableDeferred() }
        val settings = AudioSettingsController(caps, AudioPolicyCoordinator(caps), store, backgroundScope)
        runCurrent()
        settings.setPreferenceMode(AudioPreferenceMode.automatic)
        store.readGate!!.complete(Unit); runCurrent()
        assertEquals(AudioPreferenceMode.automatic, settings.preferenceMode)
        assertEquals("automatic", store.stored)
    }

}
