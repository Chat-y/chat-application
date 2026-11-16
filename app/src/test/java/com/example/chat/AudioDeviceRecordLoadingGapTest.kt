package com.example.chat

import com.example.chat.core.audio.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** NOT RUN. Injected asset source publication/retry only; JSON parser tested in Android fixtures. */
@OptIn(ExperimentalCoroutinesApi::class)
class AudioDeviceRecordLoadingGapTest {
    private class Platform : ChatyAudioPlatform {
        override val capabilityChanges = emptyFlow<AudioCapabilities>()
        override val routeChanges = emptyFlow<AudioOutputRoute>()
        override suspend fun getCapabilities(useCase: AudioUseCase) = defaultAudioCapabilities(useCase)
        override suspend fun getCurrentRoute(useCase: AudioUseCase) = AudioOutputRoute.systemDefault
        override suspend fun setPreferredRoute(useCase: AudioUseCase, route: AudioOutputRoute) = defaultAudioCapabilities(useCase)
        override suspend fun getDualOutputStatus(useCase: AudioUseCase) = DualOutputStatus.notVerified
        override suspend fun releaseSession(useCase: AudioUseCase) {}
    }
    @Test fun packagedSourceIsUsedAndRetryClearsFailureWithoutInventingLabResults() = runTest {
        val caps = AudioCapabilityService(Platform(), backgroundScope)
        var calls = 0
        val expected = DeviceVerification.fromRecord(mapOf("deviceModel" to "Synthetic asset fixture"))
        val source = AudioDeviceRecordSource {
            calls++
            if (calls == 1) error("fixture unreadable asset")
            listOf(expected)
        }
        val settings = AudioSettingsController(caps, AudioPolicyCoordinator(caps),
            preferenceScope = backgroundScope, recordSource = source)
        assertTrue(settings.deviceRecords.value.isEmpty())
        runCurrent()
        assertTrue(settings.deviceRecordsFailed.value)
        assertTrue(settings.deviceRecords.value.isEmpty())
        settings.retryDeviceRecords(); runCurrent()
        assertFalse(settings.deviceRecordsFailed.value)
        assertEquals(listOf(expected), settings.deviceRecords.value)
        assertEquals(DeviceVerificationStatus.unknown, settings.deviceRecords.value.single().status)
    }
}
