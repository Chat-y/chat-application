package com.example.chat

import com.example.chat.core.audio.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.*
import org.junit.Test

/** NOT RUN. Cooperative suspend fake only; blocking Binder/HAL can ignore interruption. */
class AudioRouteTimeoutGapTest {
    @Test fun timeoutDoesNotPublishSuccessfulCacheAndReleasesMutexForNextRequest() = runBlocking {
        var block = true
        val platform = object : ChatyAudioPlatform {
            override val capabilityChanges: Flow<AudioCapabilities> = emptyFlow()
            override val routeChanges: Flow<AudioOutputRoute> = emptyFlow()
            override suspend fun getCapabilities(useCase: AudioUseCase) = defaultAudioCapabilities(useCase)
            override suspend fun getCurrentRoute(useCase: AudioUseCase) = AudioOutputRoute.systemDefault
            override suspend fun getDualOutputStatus(useCase: AudioUseCase) = DualOutputStatus.notVerified
            override suspend fun releaseSession(useCase: AudioUseCase) {}
            override suspend fun setPreferredRoute(useCase: AudioUseCase, route: AudioOutputRoute): AudioCapabilities {
                if (block) delay(5000)
                return defaultAudioCapabilities(useCase).copy(activeRoute = route)
            }
        }
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val service = AudioCapabilityService(platform, scope, routeTimeoutMillis = 300L)
            val failure = runCatching { service.setPreferredRoute(AudioUseCase.communication, AudioOutputRoute.speaker) }.exceptionOrNull()
            assertTrue(failure is TimeoutCancellationException)
            assertNull(service.communicationCapabilities.value)
            block = false
            service.setPreferredRoute(AudioUseCase.communication, AudioOutputRoute.receiver)
            assertEquals(AudioOutputRoute.receiver, service.communicationCapabilities.value?.activeRoute)
        } finally { scope.cancel() }
    }
}
