package com.example.chat

import com.example.chat.core.audio.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Test

/** NOT RUN. Fake coroutine sequencing only, no native callback/HAL verification. */
class AudioRouteSerializationGapTest {
    @Test fun directRouteRequestsAndReleaseNeverOverlap() = runBlocking {
        var active = 0
        var peak = 0
        val events = mutableListOf<String>()
        val platform = object : ChatyAudioPlatform {
            override val capabilityChanges: Flow<AudioCapabilities> = emptyFlow()
            override val routeChanges: Flow<AudioOutputRoute> = emptyFlow()
            override suspend fun getCapabilities(useCase: AudioUseCase) = defaultAudioCapabilities(useCase)
            override suspend fun getCurrentRoute(useCase: AudioUseCase) = AudioOutputRoute.systemDefault
            override suspend fun getDualOutputStatus(useCase: AudioUseCase) = DualOutputStatus.notVerified
            override suspend fun setPreferredRoute(useCase: AudioUseCase, route: AudioOutputRoute): AudioCapabilities {
                active++; peak = maxOf(peak, active)
                try { events += route.name; delay(10) } finally { active-- }
                return defaultAudioCapabilities(useCase).copy(activeRoute = route)
            }
            override suspend fun releaseSession(useCase: AudioUseCase) {
                active++; peak = maxOf(peak, active)
                try { events += "release"; delay(10) } finally { active-- }
            }
        }
        val serviceScope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val service = AudioCapabilityService(platform, serviceScope)
            val first = launch(start = CoroutineStart.UNDISPATCHED) { service.setPreferredRoute(AudioUseCase.communication, AudioOutputRoute.speaker) }
            val second = launch(start = CoroutineStart.UNDISPATCHED) { service.setPreferredRoute(AudioUseCase.communication, AudioOutputRoute.receiver) }
            val release = launch(start = CoroutineStart.UNDISPATCHED) { service.releaseSession(AudioUseCase.communication) }
            joinAll(first, second, release)
            assertEquals(1, peak)
            assertEquals(listOf("speaker", "receiver", "release"), events)
            assertEquals(AudioOutputRoute.systemDefault, service.activeCommunicationRoute)
            val held = launch(start = CoroutineStart.UNDISPATCHED) {
                service.setPreferredRoute(AudioUseCase.communication, AudioOutputRoute.speaker)
            }
            val cancelledWaiter = launch(start = CoroutineStart.UNDISPATCHED) {
                service.setPreferredRoute(AudioUseCase.communication, AudioOutputRoute.receiver)
            }
            cancelledWaiter.cancelAndJoin()
            held.join()
            assertEquals(listOf("speaker", "receiver", "release", "speaker"), events)

        } finally { serviceScope.cancel() }
    }
}
