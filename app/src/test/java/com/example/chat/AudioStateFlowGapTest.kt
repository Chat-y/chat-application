package com.example.chat

import com.example.chat.core.audio.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.*
import org.junit.Test

/** NOT RUN. Fake synchronous snapshots only, not Compose lifecycle/hardware coverage. */
class AudioStateFlowGapTest {
    @Test fun stateFlowsPublishRoutePreferenceAndOwnershipWithoutUiVersionCounter() = runBlocking {
        val platform = object : ChatyAudioPlatform {
            override val capabilityChanges: Flow<AudioCapabilities> = emptyFlow()
            override val routeChanges: Flow<AudioOutputRoute> = emptyFlow()
            override suspend fun getCapabilities(useCase: AudioUseCase) = defaultAudioCapabilities(useCase)
            override suspend fun getCurrentRoute(useCase: AudioUseCase) = AudioOutputRoute.systemDefault
            override suspend fun getDualOutputStatus(useCase: AudioUseCase) = DualOutputStatus.notVerified
            override suspend fun releaseSession(useCase: AudioUseCase) {}
            override suspend fun setPreferredRoute(useCase: AudioUseCase, route: AudioOutputRoute) =
                defaultAudioCapabilities(useCase).copy(activeRoute = route)
        }
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val service = AudioCapabilityService(platform, scope)
            val coordinator = AudioPolicyCoordinator(service)
            val settings = AudioSettingsController(service, coordinator)
            assertNull(service.communicationCapabilities.value)
            service.getCapabilities(AudioUseCase.communication)
            assertNotNull(service.communicationCapabilities.value)
            settings.setPreferenceMode(AudioPreferenceMode.preferStereo)
            assertEquals(AudioPreferenceMode.preferStereo, settings.preferenceModeState.value)
            coordinator.startCallSession(AudioOutputRoute.receiver)
            assertEquals(AudioUseCase.communication, coordinator.policyState.value.activeOwner)
            assertEquals(AudioOutputRoute.receiver, service.communicationCapabilities.value?.activeRoute)
        } finally { scope.cancel() }
    }
}
