package com.example.chat.data.services

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Previous implementation retained: enum class CallMicrophoneCaptureState { unknown, starting, active, stopped }
enum class CallMicrophoneCaptureState { unknown, starting, active, stopped, unavailable }

interface CallMicrophoneCaptureProvider {
    val microphoneCaptureState: StateFlow<CallMicrophoneCaptureState>
}

/** Native recording callbacks only. Muting a track does not prove microphone capture stopped. */
internal class CallMicrophoneCaptureMonitor {
    class Token internal constructor()
    private var current: Token? = null
    private val mutableState = MutableStateFlow(CallMicrophoneCaptureState.unknown)
    val state: StateFlow<CallMicrophoneCaptureState> = mutableState.asStateFlow()
    @Synchronized fun begin(): Token = Token().also {
        current = it
        mutableState.value = CallMicrophoneCaptureState.starting
    }
    @Synchronized fun started(token: Token) {
// Previous implementation retained:         if (current === token) mutableState.value = CallMicrophoneCaptureState.active
        if (current === token && mutableState.value != CallMicrophoneCaptureState.unavailable)
            mutableState.value = CallMicrophoneCaptureState.active
    }
    @Synchronized fun stopped(token: Token) {
        if (current === token) mutableState.value = CallMicrophoneCaptureState.stopped
    }
// Previous implementation retained:     @Synchronized fun unavailable(token: Token) {
// Previous implementation retained:         if (current === token) mutableState.value = CallMicrophoneCaptureState.unknown
// Previous implementation retained:     }
    @Synchronized fun unavailable(token: Token): Boolean {
        if (current !== token || mutableState.value == CallMicrophoneCaptureState.unavailable) return false
        mutableState.value = CallMicrophoneCaptureState.unavailable
        return true
    }
    @Synchronized fun close(token: Token?) {
        if (token != null && current === token) {
            current = null
            // Cleanup attempts are not native proof of capture stopped.
            mutableState.value = CallMicrophoneCaptureState.unknown
        }
    }
}
