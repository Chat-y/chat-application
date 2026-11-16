package com.example.chat

import com.example.chat.ui.updates.StatusMediaReadRules
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CancellationException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source fixtures only. Not run. Open-file unlink behavior also needs Android verification. */
class StatusMediaSpoolingTest {
    private fun withCache(block: (File) -> Unit) {
        val cache = Files.createTempDirectory("status-spool-fixture").toFile()
        try { block(cache) } finally { cache.deleteRecursively() }
    }

    @Test fun preservesMultipleChunksWithoutNamedStagingContent() = withCache { cache ->
        val payload = ByteArray(131079) { (it % 251).toByte() }
        val input = object : ByteArrayInputStream(payload) {
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                assertEquals(0, cache.listFiles()!!.size)
                return super.read(bytes, offset, minOf(length, 17))
            }
        }
        assertArrayEquals(payload, StatusMediaReadRules.readSpooling(input, cache, payload.size.toLong() + 1) {})
        assertEquals(0, cache.listFiles()!!.size)
    }

    @Test fun emptyAndExactLimitAreAccepted() = withCache { cache ->
        assertTrue(StatusMediaReadRules.readSpooling(ByteArrayInputStream(byteArrayOf()), cache, 3) {}.isEmpty())
        assertArrayEquals(byteArrayOf(1, 2, 3), StatusMediaReadRules.readSpooling(ByteArrayInputStream(byteArrayOf(1, 2, 3)), cache, 3) {})
    }

    @Test fun overflowConsumesOnlyOneExtraByteAndLeavesNoPath() = withCache { cache ->
        val input = ByteArrayInputStream(ByteArray(10))
        var failed = false
        try { StatusMediaReadRules.readSpooling(input, cache, 3) {} }
        catch (_: IllegalStateException) { failed = true }
        assertTrue(failed)
        assertEquals(6, input.available())
        assertEquals(0, cache.listFiles()!!.size)
    }

    @Test fun zeroBulkReadStillMakesProgress() = withCache { cache ->
        val input = object : ByteArrayInputStream(byteArrayOf(1, 2, 3)) {
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int = 0
        }
        assertArrayEquals(byteArrayOf(1, 2, 3), StatusMediaReadRules.readSpooling(input, cache, 4) {})
    }

    @Test fun cancellationAfterStagingStartsEscapesAndLeavesNoPath() = withCache { cache ->
        var checks = 0
        var cancelled = false
        try {
            StatusMediaReadRules.readSpooling(ByteArrayInputStream(ByteArray(131072)), cache, 200000) {
                if (++checks == 3) throw CancellationException()
            }
        } catch (_: CancellationException) { cancelled = true }
        assertTrue(cancelled)
        assertEquals(0, cache.listFiles()!!.size)
    }

    @Test fun providerFailureEscapesAndLeavesNoPath() = withCache { cache ->
        val input = object : ByteArrayInputStream(byteArrayOf(1)) {
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int = throw IOException("fixture")
        }
        var failed = false
        try { StatusMediaReadRules.readSpooling(input, cache, 3) {} }
        catch (_: IOException) { failed = true }
        assertTrue(failed)
        assertEquals(0, cache.listFiles()!!.size)
    }
}
