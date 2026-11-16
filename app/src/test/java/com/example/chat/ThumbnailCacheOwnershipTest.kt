package com.example.chat

import com.example.chat.ui.media.ThumbnailCacheService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Source regression cases only; not executed in this patch-only pass. */
class ThumbnailCacheOwnershipTest {
    @Test fun `replacement at capacity retains unrelated entries and FIFO order`() {
        val cache = ThumbnailCacheService()
        repeat(120) { cache.putThumbnailBytes("f$it", byteArrayOf(it.toByte())) }
        cache.putThumbnailBytes("f119", byteArrayOf(7))
        assertTrue(cache.hasThumbnail("f0"))
        cache.putThumbnailBytes("next", byteArrayOf(8))
        assertFalse(cache.hasThumbnail("f0"))
        assertTrue(cache.hasThumbnail("f1"))
        assertArrayEquals(byteArrayOf(7), cache.getThumbnailBytes("f119"))
    }

    @Test fun `cache owns bytes on insertion and retrieval`() {
        val cache = ThumbnailCacheService()
        val original = byteArrayOf(1)
        cache.putThumbnailBytes("photo", original)
        original[0] = 2
        val returned = cache.getThumbnailBytes("photo")!!
        assertArrayEquals(byteArrayOf(1), returned)
        returned[0] = 3
        assertArrayEquals(byteArrayOf(1), cache.getThumbnailBytes("photo"))
    }

    @Test fun `generated hits match requested width and do not expose decoder bytes`() = runBlocking {
        var calls = 0
        val decoded = byteArrayOf(1)
        val cache = ThumbnailCacheService { _, _ -> calls++; decoded }
        val first = cache.generateImageThumbnail("photo", 180)!!
        first[0] = 2
        decoded[0] = 3
        assertArrayEquals(byteArrayOf(1), cache.generateImageThumbnail("photo", 180))
        assertEquals(1, calls)
        assertArrayEquals(byteArrayOf(3), cache.generateImageThumbnail("photo", 360))
        assertEquals(2, calls)
    }

    @Test fun `clear invalidates suspended decode publication`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val cache = ThumbnailCacheService { _, _ ->
            started.complete(Unit)
            finish.await()
            byteArrayOf(1)
        }
        val pending = async { cache.generateImageThumbnail("photo") }
        started.await()
        cache.clear()
        finish.complete(Unit)
        assertNull(pending.await())
        assertFalse(cache.hasThumbnail("photo"))
    }

    @Test fun `decode cancellation is not treated as missing thumbnail`() = runBlocking {
        val cache = ThumbnailCacheService { _, _ -> throw CancellationException("cancelled") }
        var cancelled = false
        try { cache.generateImageThumbnail("photo") }
        catch (_: CancellationException) { cancelled = true }
        assertTrue(cancelled)
        assertFalse(cache.hasThumbnail("photo"))
    }

    @Test fun `ordinary decode failure remains a cache miss`() = runBlocking {
        val cache = ThumbnailCacheService { _, _ -> error("decode failed") }
        assertNull(cache.generateImageThumbnail("photo"))
        assertFalse(cache.hasThumbnail("photo"))
    }
}
