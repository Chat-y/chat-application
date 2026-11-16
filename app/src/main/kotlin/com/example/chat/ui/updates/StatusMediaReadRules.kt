package com.example.chat.ui.updates

import java.io.InputStream

/** IO boundary only: input ownership/closure stays with the caller; no file paths or metadata are trusted. */
internal object StatusMediaReadRules {
    fun read(input: InputStream, maxBytes: Long, checkCancellation: () -> Unit): ByteArray {
        require(maxBytes in 1..Int.MAX_VALUE.toLong())
        // Fixed chunks avoid ByteArrayOutputStream's geometric backing-array growth.
        // The service still requires a final ByteArray; this is not streaming upload.
        val chunks = ArrayList<Pair<ByteArray, Int>>()
        var total = 0
        var eof = false
        while (total.toLong() < maxBytes && !eof) {
            checkCancellation()
            val chunk = ByteArray(minOf(64 * 1024L, maxBytes - total).toInt())
            var used = 0
            while (used < chunk.size) {
                checkCancellation()
                val count = input.read(chunk, used, chunk.size - used)
                if (count < 0) { eof = true; break }
                if (count == 0) {
                    // A zero bulk read is not EOF. Force progress without a busy loop.
                    checkCancellation()
                    val next = input.read()
                    if (next < 0) { eof = true; break }
                    chunk[used++] = next.toByte()
                    total++
                } else {
                    used += count
                    total += count
                }
            }
            if (used > 0) chunks.add(chunk to used)
        }
        checkCancellation()
        // At the cap, inspect exactly one extra byte without retaining it.
        if (!eof) check(input.read() < 0) { "Selected media exceeds the storage limit." }
        checkCancellation()
        if (chunks.size == 1 && chunks[0].second == chunks[0].first.size) return chunks[0].first
        val bytes = ByteArray(total)
        var offset = 0
        for ((chunk, used) in chunks) {
            checkCancellation()
            chunk.copyInto(bytes, destinationOffset = offset, endIndex = used)
            offset += used
        }
        checkCancellation()
        return bytes
    }

    /** Display-only bounded basename. Never use the result as a storage key or an authorization identifier. */
    fun displayName(raw: String, maxCodePoints: Int = 160): String {
        require(maxCodePoints > 0)
        val name = raw.substringAfterLast('/').substringAfterLast('\\')
        val result = StringBuilder()
        var index = 0
        var length = 0
        while (index < name.length && length < maxCodePoints) {
            val point = name.codePointAt(index)
            index += Character.charCount(point)
            // Preserve script marks and emoji joiners, but remove controls and explicit BiDi overrides.
            val bidi = point in 0x202A..0x202E || point in 0x2066..0x2069 || point == 0x200E || point == 0x200F || point == 0x061C
            if (!Character.isISOControl(point) && !bidi) {
                if (point in 0xD800..0xDFFF) result.append('�') else result.appendCodePoint(point)
                length++
            }
        }
        val label = result.toString().trim().ifEmpty { "file" }
        return if (index < name.length) "$label…" else label
    }
}
