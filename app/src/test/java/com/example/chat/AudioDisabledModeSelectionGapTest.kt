package com.example.chat

import com.example.chat.core.audio.*
import com.example.chat.ui.settings.audioModeSelectionEnabled
import com.example.chat.ui.settings.selectAudioModeIfEnabled
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** NOT RUN. Pure selection gate/controller state only, no hardware or rendered-screen claim. */
@OptIn(ExperimentalCoroutinesApi::class)
class AudioDisabledModeSelectionGapTest {
    private class Platform : ChatyAudioPlatform {
        override val capabilityChanges: Flow<AudioCapabilities> = emptyFlow()
        override val routeChanges: Flow<AudioOutputRoute> = emptyFlow()
        override suspend fun getCapabilities(useCase: AudioUseCase) = defaultAudioCapabilities(useCase)
        override suspend fun getCurrentRoute(useCase: AudioUseCase) = AudioOutputRoute.systemDefault
        override suspend fun getDualOutputStatus(useCase: AudioUseCase) = DualOutputStatus.notVerified
        override suspend fun setPreferredRoute(useCase: AudioUseCase, route: AudioOutputRoute) =
            defaultAudioCapabilities(useCase).copy(activeRoute = route)
        override suspend fun releaseSession(useCase: AudioUseCase) {}
    }

    @Test fun blockedStatusesCannotSelectExperimentalModeOrNotifyPreferenceListeners() = runTest {
        val caps = AudioCapabilityService(Platform(), backgroundScope)
        val settings = AudioSettingsController(caps, AudioPolicyCoordinator(caps))
        var changes = 0
        settings.addListener { changes++ }
        for (status in listOf(DualOutputStatus.unsupported, DualOutputStatus.unavailable, DualOutputStatus.failed)) {
            assertFalse(audioModeSelectionEnabled(AudioPreferenceMode.experimentalDualOutput, status))
            assertFalse(selectAudioModeIfEnabled(settings, AudioPreferenceMode.experimentalDualOutput, status))
            assertEquals(AudioPreferenceMode.automatic, settings.preferenceMode)
        }
        assertEquals(0, changes)
    }

    @Test fun disabledExperimentalChoiceDoesNotTrapPreviouslySelectedMode() = runTest {
        val caps = AudioCapabilityService(Platform(), backgroundScope)
        val settings = AudioSettingsController(caps, AudioPolicyCoordinator(caps))
        assertTrue(selectAudioModeIfEnabled(settings, AudioPreferenceMode.experimentalDualOutput, DualOutputStatus.notVerified))
        assertEquals(AudioPreferenceMode.experimentalDualOutput, settings.preferenceMode)
        // Capability loss must not silently rewrite a stored selection, but another choice remains available.
        assertFalse(selectAudioModeIfEnabled(settings, AudioPreferenceMode.experimentalDualOutput, DualOutputStatus.failed))
        assertEquals(AudioPreferenceMode.experimentalDualOutput, settings.preferenceMode)
        assertTrue(selectAudioModeIfEnabled(settings, AudioPreferenceMode.automatic, DualOutputStatus.failed))
        assertEquals(AudioPreferenceMode.automatic, settings.preferenceMode)
    }

    @Test fun ordinaryModesRemainSelectableForEveryCapabilityStatus() {
        for (status in DualOutputStatus.values()) {
            assertTrue(audioModeSelectionEnabled(AudioPreferenceMode.automatic, status))
            assertTrue(audioModeSelectionEnabled(AudioPreferenceMode.preferStereo, status))
        }
        assertTrue(audioModeSelectionEnabled(AudioPreferenceMode.experimentalDualOutput, DualOutputStatus.active))
        assertTrue(audioModeSelectionEnabled(AudioPreferenceMode.experimentalDualOutput, DualOutputStatus.notVerified))
    }
}
