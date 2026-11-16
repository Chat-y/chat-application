package com.example.chat.ui.chats.detail

import java.util.Locale

/** Encoded bytes, destination suffix and MIME must agree. Does not establish file ownership. */
internal enum class EditorOutputFormat(val extension: String, val mimeType: String, val flattenAlpha: Boolean) {
    PNG("png", "image/png", false), WEBP("webp", "image/webp", false), JPEG("jpg", "image/jpeg", true),
}

/** Header evidence outranks provider type/name. Reads at most the caller's short header. */
internal fun editorHeaderMime(header: ByteArray): String? {
    fun bytes(offset: Int, vararg expected: Int) = header.size >= offset + expected.size &&
        expected.indices.all { (header[offset + it].toInt() and 255) == expected[it] }
    return when {
        bytes(0, 137, 80, 78, 71, 13, 10, 26, 10) -> "image/png"
        bytes(0, 255, 216, 255) -> "image/jpeg"
        bytes(0, 82, 73, 70, 70) && bytes(8, 87, 69, 66, 80) -> "image/webp"
        else -> null
    }
}

internal fun editorOutputFormat(sourceMime: String?, sourceExtension: String?, hasAlpha: Boolean,
    destinationExtension: String? = null): EditorOutputFormat {
    // A caller-owned destination is a fixed codec contract, never write JPEG bytes to a .png file.
    if (destinationExtension != null) return when (destinationExtension.lowercase(Locale.ROOT)) {
        "png" -> EditorOutputFormat.PNG
        "webp" -> EditorOutputFormat.WEBP
        "jpg", "jpeg" -> EditorOutputFormat.JPEG
        else -> throw IllegalArgumentException("Unsupported edited-copy format.")
    }
    val mime = sourceMime?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
    return when {
        mime == "image/png" -> EditorOutputFormat.PNG
        mime == "image/webp" -> EditorOutputFormat.WEBP
        mime == "image/jpeg" -> if (hasAlpha) EditorOutputFormat.PNG else EditorOutputFormat.JPEG
        sourceExtension.equals("png", true) -> EditorOutputFormat.PNG
        sourceExtension.equals("webp", true) -> EditorOutputFormat.WEBP
        hasAlpha -> EditorOutputFormat.PNG
        else -> EditorOutputFormat.JPEG
    }
}
