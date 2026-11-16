package com.example.chat

import com.example.chat.core.audio.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** NOT RUN. Verifies a screen-port fake needs no AudioManager or service subclass. */
class AudioCapabilitySourceGapTest {
    @Test fun fakeSourcePublishesRequestedRouteWithoutNativeDependencies() = runBlocking {
        val state = MutableStateFlow<AudioCapabilities?>(defaultAudioCapabilities(AudioUseCase.communication))
        val source: AudioCapabilitySource = object : AudioCapabilitySource {
            override val communicationCapabilities = state
            override suspend fun getCapabilities(useCase: AudioUseCase) = state.value!!
            override suspend fun setPreferredRoute(useCase: AudioUseCase, route: AudioOutputRoute): AudioCapabilities {
                val update = getCapabilities(useCase).copy(activeRoute = route)
                state.value = update
                return update
            }
        }
        source.setPreferredRoute(AudioUseCase.communication, AudioOutputRoute.speaker)
        assertEquals(AudioOutputRoute.speaker, source.communicationCapabilities.value?.activeRoute)
    }
}
