package com.example.chat.ui.chats.detail

import java.io.File
import kotlinx.coroutines.CancellationException

/** Only review-created copies may be added. Never register an imported original. */
internal class ReviewOwnedFiles : AutoCloseable {
    private val files = mutableSetOf<File>()
    private var closed = false

    @Synchronized operator fun plusAssign(file: File) {
        if (closed) {
            file.delete()
            throw CancellationException("Media review closed")
        }
        files.add(file)
    }

    @Synchronized fun discardExcept(paths: Set<String>) {
        val discarded = files.filter { it.path !in paths }
        discarded.forEach { it.delete() }
        files.removeAll(discarded.toSet())
    }

    /** The accepted sender now owns these files, including retry lifetime. */
    @Synchronized fun handOff() { closed = true; files.clear() }

    @Synchronized override fun close() {
        closed = true
        files.forEach { it.delete() }
        files.clear()
    }
}

/** Reserve the ellipsis bytes and never split a UTF-16 surrogate pair. */
internal fun reviewCaptionWithinByteLimit(value: String, limit: Int): String {
    require(limit >= 3)
    if (value.toByteArray(Charsets.UTF_8).size <= limit) return value
    var end = 0
    var bytes = 0
    while (end < value.length) {
        val next = value.offsetByCodePoints(end, 1)
        val width = value.substring(end, next).toByteArray(Charsets.UTF_8).size
        if (bytes + width > limit - 3) break
        bytes += width
        end = next
    }
    return value.substring(0, end).trimEnd() + "\u2026"
}

/** API24-compatible bounded header read. The caller still owns/ closes the stream. */
internal fun readReviewPrefix(input: java.io.InputStream, limit: Int): ByteArray {
    require(limit in 1..256 * 1024)
    val bytes = ByteArray(limit)
    var used = 0
    while (used < limit) {
        val count = input.read(bytes, used, limit - used)
        if (count < 0) break
        if (count == 0) {
            val next = input.read()
            if (next < 0) break
            bytes[used++] = next.toByte()
        } else used += count
    }
    return bytes.copyOf(used)
}
