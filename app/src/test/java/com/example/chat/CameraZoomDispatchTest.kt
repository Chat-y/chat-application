package com.example.chat

import com.example.chat.ui.camera.dispatchCameraZoomRequests
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Source fixtures NOT RUN. Native performance/acceptance requires Android tests. */
class CameraZoomDispatchTest {
    @Test fun queuedInputsConflateToLatest() = runBlocking {
        val channel = Channel<() -> Unit>(Channel.CONFLATED)
        val seen = mutableListOf<Int>(); val waits = mutableListOf<Long>()
        channel.trySend { seen += 1 }; channel.trySend { seen += 2 }; channel.close()
        dispatchCameraZoomRequests(channel) { waits += it }
        assertEquals(listOf(2), seen); assertEquals(listOf(34L), waits)
    }
    @Test fun continuousInputKeepsTrailingLatestAfterRateWindow() = runBlocking {
        val channel = Channel<() -> Unit>(Channel.CONFLATED)
        val seen = mutableListOf<Int>()
        channel.trySend { seen += 1; channel.trySend { seen += 2 }; channel.trySend { seen += 3 }; channel.close() }
        dispatchCameraZoomRequests(channel) { }
        assertEquals(listOf(1, 3), seen)
    }
}
