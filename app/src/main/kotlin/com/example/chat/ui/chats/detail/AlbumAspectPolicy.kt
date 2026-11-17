package com.example.chat.ui.chats.detail

/** Equal-height two-column rows: width weights equal each tile's width/height ratio.
 * Missing dimensions keep the previous square-pair/wide-tail fallback. Bound extreme images
 * so a panoramic or very tall input cannot consume an unbounded conversation viewport. */
object AlbumAspectPolicy {
    const val MIN_PAIRED_RATIO = 0.75f
    const val MAX_PAIRED_RATIO = 1.8f
    const val MIN_SINGLE_RATIO = 1f
    const val MAX_SINGLE_RATIO = 2f
    fun reservedRowHeight(availableWidth: Float, gap: Float): Float =
        ((availableWidth - gap).coerceAtLeast(0f) / 2f)
    fun tileRatio(measured: Float?, columns: Int): Float {
        require(columns in 1..2)
        val fallback = if (columns == 1) MAX_SINGLE_RATIO else 1f
        val value = measured?.takeIf { it.isFinite() && it > 0f } ?: return fallback
        return if (columns == 1) value.coerceIn(MIN_SINGLE_RATIO, MAX_SINGLE_RATIO)
            else value.coerceIn(MIN_PAIRED_RATIO, MAX_PAIRED_RATIO)
    }
}
