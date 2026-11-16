package com.example.chat.ui.media

/** Provider labels are untrusted metadata, never a relative path or evidence of file type. */
internal fun requirePickerProviderName(name: String) {
    require('/' !in name && '\\' !in name && '\u0000' !in name) { "Unsupported document name." }
}
internal fun pickerDetectedImportName(sanitizedName: String, detected: FileClassification): String {
    val stem = sanitizedName.substringBeforeLast('.', sanitizedName).ifBlank { "attachment" }
    val suffix = detected.extension.takeIf { it in setOf("pdf", "png", "jpg", "gif", "zip", "mp3", "mp4", "webp") } ?: "bin"
    // UUID cache prefix plus multibyte label must also stay below a filesystem component limit.
    val boundedStem = buildString {
        var offset = 0
        var bytes = 0
        while (offset < stem.length) {
            val codepoint = stem.codePointAt(offset)
            val text = String(Character.toChars(codepoint))
            val size = text.toByteArray(Charsets.UTF_8).size
            if (bytes + size > 140 || length + text.length > 95) break
            append(text); bytes += size; offset += Character.charCount(codepoint)
        }
    }.trimEnd('.').ifBlank { "attachment" }
    return boundedStem + ".$suffix"
}
