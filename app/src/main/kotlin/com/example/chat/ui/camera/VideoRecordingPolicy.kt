package com.example.chat.ui.camera

internal fun shouldRecordAudio(requested: Boolean, microphoneGranted: Boolean): Boolean = requested && microphoneGranted
internal fun canReviewVideo(hasFinalizeError: Boolean, bytes: Long, durationMs: Long?, hasVideo: Boolean): Boolean =
    !hasFinalizeError && bytes > 0 && durationMs != null && durationMs > 0 && hasVideo

/** CAM-R3-039: reserve500MiB before creating a video output; unknown space fails closed. */
internal fun hasCameraVideoStorage(availableBytes: Long): Boolean = availableBytes >= 500L * 1024L * 1024L

/** CAM-R2-035: conservative native recording budgets, not an upload-size promise. */
internal fun cameraVideoDurationLimitMillis(story: Boolean): Long = if (story) 60_000L else 300_000L
internal fun cameraVideoRemainingSeconds(elapsedSeconds: Int, limitMillis: Long): Int =
    (limitMillis / 1_000L - elapsedSeconds.coerceAtLeast(0).toLong()).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

internal enum class CameraRecorderPhase { Idle, Starting, Recording, Pausing, Paused, Resuming, Stopping }
internal fun canPauseCameraRecorder(phase: CameraRecorderPhase): Boolean = phase == CameraRecorderPhase.Recording
internal fun canResumeCameraRecorder(phase: CameraRecorderPhase): Boolean = phase == CameraRecorderPhase.Paused
internal fun canStopCameraRecorder(phase: CameraRecorderPhase): Boolean = phase !in setOf(CameraRecorderPhase.Idle, CameraRecorderPhase.Stopping)

/** Unknown/out-of-range battery readings are not evidence of a low battery. */
internal fun shouldWarnCameraLowBattery(capacityPercent: Int): Boolean = capacityPercent in 0..9
