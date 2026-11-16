package com.example.chat

import com.example.chat.ui.updates.StatusMediaReadRules
import java.io.ByteArrayInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusMediaReadRulesTest {
    @Test fun exactBoundSucceedsAndEmptyStreamRemainsEmpty() {
        assertArrayEquals(byteArrayOf(1, 2, 3), StatusMediaReadRules.read(ByteArrayInputStream(byteArrayOf(1, 2, 3)), 3) {})
        assertTrue(StatusMediaReadRules.read(ByteArrayInputStream(byteArrayOf()), 3) {}.isEmpty())
    }
    @Test(expected = IllegalStateException::class) fun excessByteFailsEvenWithoutSizeMetadata() {
        StatusMediaReadRules.read(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)), 3) {}
    }
    @Test fun zeroBulkReadMakesProgressViaSingleByteRead() {
        val input = object : ByteArrayInputStream(byteArrayOf(1, 2, 3)) {
            var zero = true
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (zero) { zero = false; return 0 }
                return super.read(buffer, offset, length)
            }
        }
        assertArrayEquals(byteArrayOf(1, 2, 3), StatusMediaReadRules.read(input, 3) {})
    }
    @Test(expected = java.util.concurrent.CancellationException::class) fun cancellationEscapesWithoutConversion() {
        StatusMediaReadRules.read(ByteArrayInputStream(byteArrayOf(1)), 3) { throw java.util.concurrent.CancellationException() }
    }
    @Test fun multiChunkContentKeepsExactOrderAcrossBoundary() {
        val bytes = ByteArray(2 * 64 * 1024 + 7) { (it % 251).toByte() }
        assertArrayEquals(bytes, StatusMediaReadRules.read(ByteArrayInputStream(bytes), bytes.size.toLong()) {})
    }
    @Test fun providerShortReadsAreCombinedWithoutPadding() {
        val bytes = ByteArray(64 * 1024 + 3) { (it % 199).toByte() }
        val input = object : ByteArrayInputStream(bytes) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = super.read(buffer, offset, minOf(length, 7))
        }
        assertArrayEquals(bytes, StatusMediaReadRules.read(input, bytes.size.toLong() + 100) {})
    }
    @Test fun overLimitConsumesAtMostOneExcessByte() {
        val input = ByteArrayInputStream(ByteArray(100))
        try {
            StatusMediaReadRules.read(input, 3) {}
            throw AssertionError("expected rejection")
        } catch (_: IllegalStateException) {
            assertEquals(96, input.available())
        }
    }
    @Test(expected = java.util.concurrent.CancellationException::class)
    fun cancellationDuringFinalAssemblyEscapes() {
        var eofSeen = false
        val input = object : ByteArrayInputStream(ByteArray(64 * 1024 + 3)) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = super.read(buffer, offset, length).also { if (it < 0) eofSeen = true }
        }
        var checksAfterEof = 0
        StatusMediaReadRules.read(input, 100_000) {
            if (eofSeen && ++checksAfterEof >= 3) throw java.util.concurrent.CancellationException()
        }
    }
    @Test fun displayLabelsAreBoundedAndNeverInterpretProviderPaths() {
        assertEquals("photo.jpg", StatusMediaReadRules.displayName("../../photo.jpg"))
        assertEquals("photo.jpg", StatusMediaReadRules.displayName("C:\\private\\photo.jpg"))
        assertEquals("safegnp.exe", StatusMediaReadRules.displayName("safe\u202Egnp.exe"))
        assertEquals("file", StatusMediaReadRules.displayName("\u0000\n"))
        assertEquals("😀😀…", StatusMediaReadRules.displayName("😀😀😀", 2))
        assertEquals("మాయ", StatusMediaReadRules.displayName("మాయ"))
    }
    @Test fun brokenFilenameSurrogatesAreVisibleWithoutSplittingValidEmoji() {
        assertEquals("bad�.jpg", StatusMediaReadRules.displayName("bad\uD800.jpg"))
        assertEquals("�…", StatusMediaReadRules.displayName("\uDC00😀", 1))
        assertEquals("😀.jpg", StatusMediaReadRules.displayName("😀.jpg"))
    }

    @org.junit.Test fun knownSizeReadsIntoExactPayload() {
        val bytes = ByteArray(150_000) { (it % 127).toByte() }
        assertArrayEquals(bytes, StatusMediaReadRules.readKnownSize(ByteArrayInputStream(bytes), bytes.size.toLong(), 200_000) {})
    }
    @org.junit.Test fun knownEmptySizeRequiresActualEof() {
        assertTrue(StatusMediaReadRules.readKnownSize(ByteArrayInputStream(byteArrayOf()), 0, 3) {}.isEmpty())
    }
    @org.junit.Test(expected = IllegalStateException::class) fun declaredSizeCannotExceedCap() {
        StatusMediaReadRules.readKnownSize(ByteArrayInputStream(byteArrayOf()), 4, 3) {}
    }
    @org.junit.Test(expected = IllegalStateException::class) fun staleLargerMetadataCannotProducePaddedPayload() {
        StatusMediaReadRules.readKnownSize(ByteArrayInputStream(byteArrayOf(1)), 3, 3) {}
    }
    @org.junit.Test(expected = IllegalStateException::class) fun staleSmallerMetadataCannotTruncatePayload() {
        StatusMediaReadRules.readKnownSize(ByteArrayInputStream(byteArrayOf(1, 2, 3)), 2, 3) {}
    }
    @org.junit.Test(expected = java.util.concurrent.CancellationException::class) fun knownSizeChecksCancellationBeforeAllocation() {
        StatusMediaReadRules.readKnownSize(ByteArrayInputStream(byteArrayOf(1)), 1, 3) {
            throw java.util.concurrent.CancellationException()
        }
    }
    @org.junit.Test fun knownSizeZeroBulkReadForcesProgress() {
        val source = ByteArrayInputStream(byteArrayOf(1, 2, 3))
        val input = object : java.io.InputStream() {
            override fun read(): Int = source.read()
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = 0
        }
        assertArrayEquals(byteArrayOf(1, 2, 3), StatusMediaReadRules.readKnownSize(input, 3, 3) {})
    }

}
