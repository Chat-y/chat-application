package com.example.chat.ui.settings.conversation

/** UI read policy only. Accept unsigned ARGB or sign-extended Android Int; never rewrite storage.
 * Invalid-width values fall back to theme. Valid colors are made opaque as these rows promise.
 * Bit masks describe the ARGB encoding, not a fixed production color or theme choice. */
internal fun opaqueConversationStoredColor(value: Long?): Long? = com.example.chat.ui.kit.opaqueConversationArgb(value)

/* Previous local implementation, centralized to keep real header/link renderers consistent.
internal fun opaqueConversationStoredColor(value: Long?): Long? {
    if (value == null || value < Int.MIN_VALUE.toLong() || value > UInt.MAX_VALUE.toLong()) return null
    val opaqueAlpha = UByte.MAX_VALUE.toLong() shl (Byte.SIZE_BITS * 3)
    val rgbMask = (1L shl (Byte.SIZE_BITS * 3)) - 1
    return (value and rgbMask) or opaqueAlpha
}

*/
