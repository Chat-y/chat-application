package com.example.chat.ui.camera

import kotlinx.coroutines.delay

/** A countdown owns no capture until every wait has completed. Cancellation resets its UI. */
internal suspend fun runCameraCountdown(
    seconds: Int,
    onTick: (Int) -> Unit,
    wait: suspend (Long) -> Unit = { delay(it) },
    onCapture: () -> Unit,
) {
    try {
        for (remaining in seconds.coerceAtLeast(0) downTo 1) {
            onTick(remaining)
            wait(1000)
        }
        onTick(0)
        onCapture()
    } finally { onTick(0) }
}

/** Native JPEG completion time is not detection time. Freshness is checked at the shutter. */
internal fun isCameraFaceSolveFresh(nowElapsedMs: Long, seenElapsedMs: Long?): Boolean =
    seenElapsedMs != null && seenElapsedMs >= 0L && nowElapsedMs >= seenElapsedMs &&
        nowElapsedMs - seenElapsedMs <= 500L
