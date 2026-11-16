package com.example.chat.ui.kit

/** Read-only32-bit ARGB policy for opaque conversation chrome/text overrides.
 * Accept unsigned ARGB or sign-extended Android Int. Invalid width follows theme; storage unchanged.
 * Encoding masks are not a fixed theme color. */
internal fun opaqueConversationArgb(value: Long?): Long? {
    if (value == null || value < Int.MIN_VALUE.toLong() || value > UInt.MAX_VALUE.toLong()) return null
    val opaqueAlpha = UByte.MAX_VALUE.toLong() shl (Byte.SIZE_BITS * 3)
    val rgbMask = (1L shl (Byte.SIZE_BITS * 3)) - 1
    return (value and rgbMask) or opaqueAlpha
}
