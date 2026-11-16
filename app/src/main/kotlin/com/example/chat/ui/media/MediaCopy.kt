package com.example.chat.ui.media

import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Streaming copy used for private media imports/exports. Caller owns rollback and dispatcher. */
internal suspend fun copyMediaBounded(input: InputStream, output: OutputStream, maxBytes: Long, onProgress: suspend (Long) -> Unit = {}): Long {
    require(maxBytes > 0)
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        currentCoroutineContext().ensureActive()
        val count = input.read(buffer)
        if (count < 0) break
        if (count == 0) {
            // A legal zero bulk read must not spin forever without bounded forward progress.
            currentCoroutineContext().ensureActive()
            val one = input.read()
            if (one < 0) break
            require(total < maxBytes) { "Media exceeds the permitted file size." }
            output.write(one)
            total++
            onProgress(total)
            continue
        }
        require(count.toLong() <= maxBytes - total) { "Media exceeds the permitted file size." }
        output.write(buffer, 0, count)
        total += count
        onProgress(total)
    }
    currentCoroutineContext().ensureActive()
    return total
}

/** Versioned, session-only lineage. UUID filenames remain unchanged; never persist or log source URIs. */
internal class MediaExportLineage(private val maxEntries: Int = 20) : AutoCloseable {
    data class Entry(val schemaVersion: Int, val sequence: Long, val outputPath: String, val source: String)
    private val entries = LinkedHashMap<String, Entry>()
    private var sequence = 0L
    private var closed = false
    init { require(maxEntries in 1..100) }
    @Synchronized fun register(output: java.io.File, source: String) {
        check(!closed) { "Export session ended." }
        val path = output.canonicalPath
        entries.remove(path)
        entries[path] = Entry(1, ++sequence, path, source)
        while (entries.size > maxEntries) entries.remove(entries.keys.first())
    }
    @Synchronized fun forget(output: java.io.File) { entries.remove(output.canonicalPath) }
    @Synchronized fun snapshot(): List<Entry> = entries.values.toList()
    @Synchronized override fun close() { closed = true; entries.clear() }
}
