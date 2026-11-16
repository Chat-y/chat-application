package com.example.chat.ui.chats.newchat

/** Screen-local rules. They do not authorize callbacks or prove remote permissions. */
internal object NewChatPresentationRules {
    private val requiredSections = listOf("search", "selected", "contacts")

    fun sections(configured: List<String>): List<String> =
        (configured.filter { it in requiredSections }.distinct() + requiredSections).distinct()

    /** Preserve the existing UTF-16 length contract, but never leave half a surrogate at the boundary. */
    fun clampTitle(value: String, maxCodeUnits: Int): String {
        val singleLine = value.replace('\r', ' ').replace('\n', ' ')
        val limit = maxCodeUnits.coerceAtLeast(1)
        var end = minOf(singleLine.length, limit)
        if (end > 0 && end < singleLine.length && Character.isHighSurrogate(singleLine[end - 1]) &&
            Character.isLowSurrogate(singleLine[end])) end--
        return singleLine.substring(0, end)
    }

    /** Display only. Show suspicious direction/control characters visibly, never rewrite IDs/callback arguments. */
    fun identityText(value: String): String {
        val result = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val point = value.codePointAt(index)
            index += Character.charCount(point)
            val directionalControl = point in 0x202A..0x202E || point in 0x2066..0x2069 ||
                point == 0x200E || point == 0x200F || point == 0x061C
            if (Character.isISOControl(point) || directionalControl || point in 0xD800..0xDFFF) result.append('�')
            else result.appendCodePoint(point)
        }
        return result.toString()
    }

    fun publicError(error: Throwable): String = when (error) {
        is java.io.IOException -> "Unable to connect. Check your connection and try again."
        is SecurityException -> "This action is not permitted. Check your account access."
        else -> "Unable to complete this request. Please try again."
    }
}
