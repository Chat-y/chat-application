package com.example.chat

import com.example.chat.core.audio.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Test-only native port fake. No service/controller production edits; NOT RUN. */
class AudioCapabilitySnapshotGapTest {
    private class PlatformFake : ChatyAudioPlatform {
        var route = AudioOutputRoute.systemDefault
        var requests = 0
        override val capabilityChanges: Flow<AudioCapabilities> = emptyFlow()
        override val routeChanges: Flow<AudioOutputRoute> = emptyFlow()
        override suspend fun getCapabilities(useCase: AudioUseCase) = defaultAudioCapabilities(useCase).copy(activeRoute = route)
        override suspend fun getCurrentRoute(useCase: AudioUseCase) = route
        override suspend fun setPreferredRoute(useCase: AudioUseCase, route: AudioOutputRoute): AudioCapabilities {
            requests++; this.route = route
            return getCapabilities(useCase)
        }
        override suspend fun getDualOutputStatus(useCase: AudioUseCase) = DualOutputStatus.notVerified
        override suspend fun releaseSession(useCase: AudioUseCase) { route = AudioOutputRoute.systemDefault }
    }
    @Test fun capabilitySnapshotTracksExplicitRouteAndRelease() = runTest {
        val port = PlatformFake()
        val service = AudioCapabilityService(port, this,
            routeDispatcher = coroutineContext[kotlin.coroutines.ContinuationInterceptor] as kotlinx.coroutines.CoroutineDispatcher)
        assertEquals(AudioOutputRoute.systemDefault, service.activeCommunicationRoute)
        service.setPreferredRoute(AudioUseCase.communication, AudioOutputRoute.wiredHeadset)
        assertEquals(AudioOutputRoute.wiredHeadset, service.activeCommunicationRoute)
        assertEquals(1, port.requests)
        service.releaseSession(AudioUseCase.communication)
        assertEquals(AudioOutputRoute.systemDefault, service.activeCommunicationRoute)
    }
}
