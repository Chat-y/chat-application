package com.example.chat.ui.media

/** MV-R3-047: one display clock for media surfaces; does not change playback positions. */
internal object TimeFormatters {
    fun playbackSeconds(seconds: Long): String {
        val total = seconds.coerceAtLeast(0L)
        val minutes = total / 60 % 60
        val remainder = (total % 60).toString().padStart(2, '0')
        return if (total >= 3600L) "${total / 3600}:${minutes.toString().padStart(2, '0')}:$remainder"
        else "${total / 60}:$remainder"
    }

    fun playbackMillis(milliseconds: Long): String = playbackSeconds(milliseconds.coerceAtLeast(0L) / 1000L)
}
