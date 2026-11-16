package com.example.chat.ui.chats.detail

/** MV-R2-036: display/control target only, no autoplay or external action. */
internal fun viewerKeyboardSeek(positionMs: Int, durationMs: Int, forward: Boolean): Int =
    (positionMs.toLong() + if (forward) 5000L else -5000L)
        .coerceIn(0L, durationMs.coerceAtLeast(0).toLong()).toInt()
