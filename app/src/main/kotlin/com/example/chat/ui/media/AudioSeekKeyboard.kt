package com.example.chat.ui.media

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*

/** MV-R3-041 UI target arithmetic, same5second convention as viewer keyboard seek. */
internal fun audioKeyboardSeekTarget(positionMs: Long, durationMs: Long, forward: Boolean): Long {
    val duration = durationMs.coerceAtLeast(0L)
    val current = positionMs.coerceIn(0L, duration)
    return if (forward) current + minOf(5_000L, duration - current)
        else current - minOf(5_000L, current)
}

/** Intercept only focused slider left/right; Tab/Back/up/down stay platform navigation. */
internal fun Modifier.audioSeekKeyboard(
    positionMs: Long,
    durationMs: Long,
    enabled: Boolean,
    onSeek: (Long) -> Unit,
): Modifier = onPreviewKeyEvent { event ->
    if (!enabled || durationMs <= 0L || event.type != KeyEventType.KeyDown ||
        (event.key != Key.DirectionLeft && event.key != Key.DirectionRight)) false
    else {
        onSeek(audioKeyboardSeekTarget(positionMs, durationMs, event.key == Key.DirectionRight))
        true
    }
}
