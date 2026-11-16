package com.example.chat.ui.camera

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class CameraCaptureTimingTest {
    @Test fun `one second waits once before exactly one capture`() = runBlocking {
        val events = mutableListOf<String>()
        runCameraCountdown(1, onTick = { events += "tick:$it" }, wait = { events += "wait:$it" }) { events += "capture" }
        assertEquals(listOf("tick:1", "wait:1000", "tick:0", "capture", "tick:0"), events)
    }
    @Test fun `zero timer captures without a wait`() = runBlocking {
        var captured = 0
        runCameraCountdown(0, onTick = { assertEquals(0, it) }, wait = { fail("No delay for zero") }) { captured++ }
        assertEquals(1, captured)
    }
    @Test fun `canceled wait never captures and resets countdown`() = runBlocking {
        val ticks = mutableListOf<Int>()
        var captured = false
        try { runCameraCountdown(3, onTick = { ticks += it }, wait = { throw CancellationException("fixture") }) { captured = true } }
        catch (_: CancellationException) { }
        assertFalse(captured); assertEquals(listOf(3, 0), ticks)
    }
    @Test fun `minimum zoom tolerates float neighbors and both bounds`() {
        assertEquals(.6f, clampCameraZoom(.6f, .6f, 4f), 0f)
        assertEquals(.6f, clampCameraZoom(Math.nextDown(.6f), .6f, 4f), 0f)
        assertEquals(4f, clampCameraZoom(Math.nextUp(4f), .6f, 4f), 0f)
    }
    @Test fun `central frame intervals keep existing throttles`() {
        assertEquals(66_000_000L, CAMERA_FACE_INTERVAL_NS)
        assertEquals(150L, CAMERA_SEGMENT_INTERVAL_MS)
        assertFalse(shouldAnalyzeCameraFrame(true, CAMERA_FACE_INTERVAL_NS - 1, 0))
        assertTrue(shouldAnalyzeCameraFrame(true, CAMERA_FACE_INTERVAL_NS, 0))
    }
}
