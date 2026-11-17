package com.example.chat

import com.example.chat.data.services.*
import com.example.chat.ui.calls.microphoneCaptureLabelResource
import org.junit.Assert.*
import org.junit.Test

/** NOT RUN. Callback-state ownership and presentation only; no microphone/HAL/Android indicator proof. */
class CallMicrophoneCaptureGapTest {
    @Test fun onlyNativeStartConfirmsCaptureAndNativeStopConfirmsStopped() {
        val monitor = CallMicrophoneCaptureMonitor()
        assertEquals(CallMicrophoneCaptureState.unknown, monitor.state.value)
        val token = monitor.begin()
        assertEquals(CallMicrophoneCaptureState.starting, monitor.state.value)
        monitor.started(token)
        assertEquals(CallMicrophoneCaptureState.active, monitor.state.value)
        monitor.unavailable(token)
// Previous implementation retained:         assertEquals(CallMicrophoneCaptureState.unknown, monitor.state.value)
        assertEquals(CallMicrophoneCaptureState.unavailable, monitor.state.value)
        monitor.started(token)
        assertEquals(CallMicrophoneCaptureState.unavailable, monitor.state.value)
        monitor.stopped(token)
        assertEquals(CallMicrophoneCaptureState.stopped, monitor.state.value)
        monitor.close(token)
        assertEquals(CallMicrophoneCaptureState.unknown, monitor.state.value)
    }
    @Test fun staleCallbacksCannotTurnReplacementCaptureOnOrOff() {
        val monitor = CallMicrophoneCaptureMonitor()
        val old = monitor.begin()
        val next = monitor.begin()
        monitor.started(old)
        assertEquals(CallMicrophoneCaptureState.starting, monitor.state.value)
        monitor.started(next)
        monitor.stopped(old)
        monitor.close(old)
        assertEquals(CallMicrophoneCaptureState.active, monitor.state.value)
        monitor.close(next)
        monitor.started(next)
        assertEquals(CallMicrophoneCaptureState.unknown, monitor.state.value)
    }
    @Test fun muteNeverClaimsCaptureStopped() {
        assertEquals(R.string.call_microphone_in_use_muted,
            microphoneCaptureLabelResource(CallMicrophoneCaptureState.active, true))
        assertEquals(R.string.call_microphone_unknown,
            microphoneCaptureLabelResource(CallMicrophoneCaptureState.unknown, true))
        assertNotEquals(R.string.call_microphone_stopped,
            microphoneCaptureLabelResource(CallMicrophoneCaptureState.active, true))
    }
}
