package com.example.chat.ui.chats.detail

/** MR-R3-037: 320dp controls rail at600dp+, or in landscape as the released row specifies.
 * Narrow landscape can leave little preview width; actual fit/font/IME/device proof is open.
 */
internal fun reviewUsesTwoPanes(availableWidthDp: Float, landscape: Boolean): Boolean =
    availableWidthDp.isFinite() && availableWidthDp > 0f && (availableWidthDp >= 600f || landscape)
