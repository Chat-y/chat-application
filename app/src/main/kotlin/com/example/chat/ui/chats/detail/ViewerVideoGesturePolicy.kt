package com.example.chat.ui.chats.detail

/** MV-R1-051: physical video thirds, no RTL reversal of the media time axis. */
internal fun viewerVideoTapDirection(x: Float, width: Float): Int = when {
    !x.isFinite() || !width.isFinite() || width <= 0f -> 0
    x < width / 3f -> -1
    x > width * (2f / 3f) -> 1
    else -> 0
}
internal fun viewerVideoGestureSeek(positionMs: Int, durationMs: Int, direction: Int): Int =
    (positionMs.toLong() + direction.coerceIn(-1, 1).toLong() * 10_000L)
        .coerceIn(0L, durationMs.coerceAtLeast(0).toLong()).toInt()
