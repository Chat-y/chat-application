package com.example.chat.ui.chats.detail

/** MR-R1-036 requested decode bounds, not a resident bitmap/cache memory guarantee. */
internal fun reviewPreviewDecodeSize(currentPage: Boolean, widthPx: Int, heightPx: Int): Pair<Int, Int> {
    val cap = if (currentPage) 2048 else 192
    return widthPx.coerceIn(1, cap) to heightPx.coerceIn(1, cap)
}
