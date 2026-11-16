package com.example.chat.ui.chats.detail

/** MR-R3-024: exact prior UI snap policy, including nearest-second half-even rounding
 * and sequential minimum500ms endpoint constraints. Does not set exported timestamps. */
internal fun trimSnappedRange(duration: Long, start: Float, end: Float): ClosedFloatingPointRange<Float> {
    if (duration <= 0L) return start..end
    val second = 1000f / duration
    val half = 500f / duration
    fun snap(value: Float): Float {
        val nearest = kotlin.math.round(value / second) * second
        return if (kotlin.math.abs(nearest - value) <= half) nearest.coerceIn(0f, 1f) else value
    }
    val snappedStart = snap(start).coerceAtMost((end - half).coerceAtLeast(0f))
    val snappedEnd = snap(end).coerceAtLeast((snappedStart + half).coerceAtMost(1f))
    return snappedStart..snappedEnd
}
