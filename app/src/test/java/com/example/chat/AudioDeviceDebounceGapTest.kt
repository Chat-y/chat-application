package com.example.chat

import com.example.chat.core.audio.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.assertEquals
import org.junit.Test

/** NOT RUN. Coroutine flow fixture only, no real Bluetooth timing guarantee. */
class AudioDeviceDebounceGapTest {
    @Test fun quickAvailabilityFlapPublishesOnlyLatestAfterQuietWindow() = runBlocking {
        val input = MutableSharedFlow<AudioCapabilities>()
        val output = mutableListOf<AudioCapabilities>()
        val first = defaultAudioCapabilities(AudioUseCase.communication)
        val latest = first.copy(availableRoutes = first.availableRoutes + AudioOutputRoute.bluetooth)
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            input.stableAudioCapabilities().take(1).toList(output)
        }
        try {
            yield()
            input.emit(first)
            input.emit(latest)
            withTimeout(2000) { job.join() }
            assertEquals(listOf(latest), output)
        } finally { job.cancel() }
    }
}
