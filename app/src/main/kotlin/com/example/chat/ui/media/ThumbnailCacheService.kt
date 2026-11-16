package com.example.chat.ui.media

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Bounded 120-path FIFO cache. Decoding is supplied by the imageDecoder seam.
 * Cached byte arrays belong to the cache, never its callers. */
class ThumbnailCacheService(
    private val imageDecoder: suspend (path: String, targetWidth: Int) -> ByteArray? = { _, _ -> null },
) {
    // Previous cache stored caller-owned arrays without synchronization:
    // private val memoryCache = LinkedHashMap<String, ByteArray>()
    private data class Entry(val bytes: ByteArray, val targetWidth: Int?)
    private val memoryCache = LinkedHashMap<String, Entry>()
    private val cacheLock = Any()
    private var clearGeneration = 0L

    companion object {
        const val MAX_CACHE_ENTRIES = 120
    }

    // Previous direct map reads exposed mutable arrays and raced with clear/put.
    // fun hasThumbnail(filePath: String): Boolean = memoryCache.containsKey(filePath)
    // fun getThumbnailBytes(filePath: String): ByteArray? = memoryCache[filePath]
    fun hasThumbnail(filePath: String): Boolean = synchronized(cacheLock) {
        memoryCache.containsKey(filePath)
    }

    fun getThumbnailBytes(filePath: String): ByteArray? = synchronized(cacheLock) {
        memoryCache[filePath]?.bytes?.copyOf()
    }

    fun putThumbnailBytes(filePath: String, bytes: ByteArray) {
        synchronized(cacheLock) { putLocked(filePath, bytes, null) }
    }

    private fun putLocked(filePath: String, bytes: ByteArray, targetWidth: Int?) {
        // Previously every put at capacity evicted the oldest item, even on replacement:
        // if (memoryCache.size >= MAX_CACHE_ENTRIES) {
        //     val firstKey = memoryCache.keys.first()
        //     memoryCache.remove(firstKey)
        // }
        // memoryCache[filePath] = bytes
        if (!memoryCache.containsKey(filePath) && memoryCache.size >= MAX_CACHE_ENTRIES) {
            memoryCache.remove(memoryCache.keys.first())
        }
        memoryCache[filePath] = Entry(bytes.copyOf(), targetWidth)
    }

    fun clear() {
        // Previous: memoryCache.clear()
        synchronized(cacheLock) {
            clearGeneration++
            memoryCache.clear()
        }
    }

    suspend fun generateImageThumbnail(imagePath: String, targetWidth: Int = 180): ByteArray? {
        require(targetWidth > 0) { "Thumbnail width must be positive" }
        currentCoroutineContext().ensureActive()
        val generation: Long
        synchronized(cacheLock) {
            // Previous path-only hit returned thumbnails generated for any requested width:
            // if (memoryCache.containsKey(imagePath)) return memoryCache[imagePath]
            val cached = memoryCache[imagePath]
            if (cached?.targetWidth == targetWidth) return cached.bytes.copyOf()
            generation = clearGeneration
        }

        try {
            val thumbBytes = imageDecoder(imagePath, targetWidth)
            currentCoroutineContext().ensureActive()
            synchronized(cacheLock) {
                // clear/logout invalidates pending decodes as well as existing entries.
                if (generation != clearGeneration) return null
                if (thumbBytes != null) {
                    // Previous: putThumbnailBytes(imagePath, thumbBytes); return thumbBytes
                    putLocked(imagePath, thumbBytes, targetWidth)
                    return thumbBytes.copyOf()
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Previous catch-all also swallowed coroutine cancellation. Ordinary decode failures
            // still return null so the caller can show its existing fallback.
        }
        return null
    }
}
