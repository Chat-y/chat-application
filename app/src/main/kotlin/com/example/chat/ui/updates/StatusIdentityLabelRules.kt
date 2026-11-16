package com.example.chat.ui.updates

/** Display-only labels, never account lookup, comparison, key or authorization input. */
internal object StatusIdentityLabelRules {
    fun display(value: String): String {
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
}
