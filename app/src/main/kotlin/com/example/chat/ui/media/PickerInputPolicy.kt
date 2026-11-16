package com.example.chat.ui.media

import java.io.InputStream

/** OpenDocument/OpenMultipleDocuments accept content providers, never network or executable URIs. */
internal fun requirePickerContentScheme(scheme: String?) {
    require(scheme.equals("content", ignoreCase = true)) { "Unsupported document source. Choose a file from device storage." }
}
internal const val MAX_PICKER_QUERY_CHARS = 512
internal fun boundedPickerText(value: String, limit: Int = MAX_PICKER_QUERY_CHARS): String {
    require(limit > 0)
    if (value.length <= limit) return value
    var end = limit
    if (value[end - 1].isHighSurrogate() && value[end].isLowSurrogate()) end--
    return value.substring(0, end)
}
// Original display returned bounded raw text, allowing hidden directional overrides.
// internal fun pickerFileLabel(value: String): String = boundedPickerText(value.ifBlank { "Unnamed file" })
internal fun pickerFileLabel(value: String): String {
    val bounded = boundedPickerText(value)
    val visible = buildString(bounded.length) {
        for (character in bounded) append(when {
            character == '\u061C' || character in '\u200E'..'\u200F' ||
                character in '\u202A'..'\u202E' || character in '\u2066'..'\u2069' -> '\uFFFD'
            character.isISOControl() -> ' '
            else -> character
        })
    }
    return visible.ifBlank { "Unnamed file" }
}

/** Provider/file streams can short-read. Never assumes read() fills its target buffer. */
internal fun readPickerHeader(input: InputStream, limit: Int = FileTypeDetector.HEADER_BYTES_TO_READ): ByteArray {
    require(limit in 1..4096)
    val bytes = ByteArray(limit)
    var used = 0
    while (used < limit) {
        val count = input.read(bytes, used, limit - used)
        if (count < 0) break
        if (count == 0) {
            val one = input.read()
            if (one < 0) break
            bytes[used++] = one.toByte()
        } else used += count
    }
    return bytes.copyOf(used)
}

/** A corrupt descriptor cannot make attachment totals negative or wrap Long. */
internal fun pickerTotalBytes(files: List<MediaFileDescriptor>): Long = files.fold(0L) { total, file ->
    val size = file.sizeBytes.coerceAtLeast(0L)
    if (size > Long.MAX_VALUE - total) Long.MAX_VALUE else total + size
}
