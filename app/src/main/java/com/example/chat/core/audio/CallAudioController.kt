package com.example.chat.core.audio

import com.example.chat.data.services.AudioRouteType
import com.example.chat.data.services.CallSignalingService
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Integrates Chaty's [CallSignalingService] and WebRTC media with the native
 * audio subsystem. Exact port of lib/core/audio/call_audio_controller.dart.
 *
 * Preserves acoustic echo cancellation, microphone noise suppression, and
 * voice processing, while providing capability-aware route switching
 * (earpiece, speaker, bluetooth, wired).
 *
 * Note: this is the core-audio coordination controller - distinct from
 * com.example.chat.data.services.CallAudioController, which owns the
 * AudioManager/WebRTC mechanics driven by the signaling state machine.
 */
interface CallAudioSessionDriver {
    val activeRoute: AudioOutputRoute
    suspend fun initializeCallAudio(isVideo: Boolean = false): Boolean
    suspend fun switchMediaRoute(targetRoute: AudioOutputRoute): Boolean
    suspend fun terminateCallAudio()
}

class CallAudioController(
    private val coordinator: AudioPolicyGate,
    private val capabilityService: AudioCapabilityService,
    private val signalingService: CallSignalingService? = null,
// Previous implementation retained: ) {
) : CallAudioSessionDriver {
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

// Previous implementation retained:     val activeRoute: AudioOutputRoute get() = coordinator.activeRoute
    override val activeRoute: AudioOutputRoute get() = coordinator.activeRoute
    val sessionState: AudioSessionState get() = coordinator.state
    val isDualActive: Boolean get() = capabilityService.isDualOutputActive

    fun addListener(listener: () -> Unit) { listeners.addIfAbsent(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
    private fun notifyListeners() { listeners.forEach { it() } }

    /** Start audio session coordinated with call engine. */
// Previous implementation retained:     suspend fun initializeCallAudio(isVideo: Boolean = false): Boolean {
    override suspend fun initializeCallAudio(isVideo: Boolean): Boolean {
        val defaultRoute =
            if (isVideo) AudioOutputRoute.speaker else AudioOutputRoute.receiver
        val ok = coordinator.startCallSession(initialRoute = defaultRoute)
        if (ok && signalingService != null) {
            val targetSignalingRoute =
                if (isVideo) AudioRouteType.speaker else AudioRouteType.earpiece
            try {
                signalingService.setAudioRoute(targetSignalingRoute)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            }
        }
        notifyListeners()
        return ok
    }

    /** Switch audio route during an active call. */
// Previous implementation retained:     suspend fun switchRoute(targetRoute: AudioOutputRoute): Boolean {
    suspend fun switchRoute(targetRoute: AudioOutputRoute): Boolean {
        val success = coordinator.setCallRoute(targetRoute)
        if (success && signalingService != null) {
            if (targetRoute == AudioOutputRoute.speaker) {
                signalingService.setAudioRoute(AudioRouteType.speaker)
            } else if (targetRoute == AudioOutputRoute.receiver) {
                signalingService.setAudioRoute(AudioRouteType.earpiece)
            }
        }
        notifyListeners()
        return success
    }

    /** Media path must not select a different output after a failed route request. */
    override suspend fun switchMediaRoute(targetRoute: AudioOutputRoute): Boolean {
        val switched = (coordinator as? NoFallbackCallRouteGate)?.setCallRouteWithoutFallback(targetRoute) == true
        notifyListeners()
        return switched
    }

    /** Teardown call audio session deterministically. */
// Previous implementation retained:     suspend fun terminateCallAudio() {
    override suspend fun terminateCallAudio() {
        coordinator.endCallSession()
        notifyListeners()
    }
}
