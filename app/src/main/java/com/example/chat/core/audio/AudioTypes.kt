package com.example.chat.core.audio

/**
 * Exact Kotlin port of lib/core/audio/audio_types.dart (api branch, anchor
 * 3bee780d0e5a0e1df2e44f6d5b9f6c691d10a682). Pure JVM - no Flutter/Android deps.
 */

/** Target use case for audio session routing. */
enum class AudioUseCase { media, communication, notification }

/** Logical audio output route. */
// Previous implementation retained: enum class AudioOutputRoute { systemDefault, receiver, speaker, bluetooth, wiredHeadset, usb }
enum class AudioOutputRoute { systemDefault, receiver, speaker, bluetooth, wiredHeadset, usb, hearingAid }

/** Status of dual-output (earpiece + loudspeaker) capability. */
enum class DualOutputStatus { active, unavailable, unsupported, notVerified, failed }

/** Typed local failure without native exception messages or device identifiers. */
enum class AudioFailureKind { permissionDenied, invalidState, platformUnavailable }

internal fun classifyAudioFailure(failure: Exception): AudioFailureKind = when (failure) {
    is SecurityException -> AudioFailureKind.permissionDenied
    is IllegalStateException -> AudioFailureKind.invalidState
    else -> AudioFailureKind.platformUnavailable
}

fun failedAudioCapabilities(useCase: AudioUseCase, failure: Exception): AudioCapabilities =
    defaultAudioCapabilities(useCase).copy(platform = "android", failureKind = classifyAudioFailure(failure),
        failureReason = when (classifyAudioFailure(failure)) {
            AudioFailureKind.permissionDenied -> "Audio permission is not available"
            AudioFailureKind.invalidState -> "Audio output is not available in the current state"
            AudioFailureKind.platformUnavailable -> "Audio service is not available"
        })

/** Capability report for an audio use case. */
data class AudioCapabilities(
    val availableRoutes: List<AudioOutputRoute>,
    val activeRoute: AudioOutputRoute,
    val dualOutputStatus: DualOutputStatus,
    val failureReason: String? = null,
    val isStereoSupported: Boolean = false,
    val platform: String = "unknown",
    val failureKind: AudioFailureKind? = null,
)

/** State machine lifecycle states for Chaty audio coordination. */
enum class AudioSessionState {
    idle,
    mediaActive,
    callStarting,
    receiverCall,
    speakerphoneCall,
    externalCall,
    interrupted,
    recovering,
    failed,
    releasing,
}

/** Diagnostic log event for capability and route transitions. */
data class AudioPolicyDiagnosticEvent(
    val message: String,
    val useCase: AudioUseCase,
    val state: AudioSessionState,
    val route: AudioOutputRoute? = null,
    val error: String? = null,
    val timestampMillis: Long = System.currentTimeMillis(),
)

/** User audio preference mode in settings. */
enum class AudioPreferenceMode { automatic, preferStereo, experimentalDualOutput }
