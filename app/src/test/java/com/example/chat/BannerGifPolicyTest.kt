package com.example.chat

import com.example.chat.data.services.BannerGifPolicy
import org.junit.Assert.*
import org.junit.Test

/** Authored only, not executed. Tiny valid one-pixel GIF frames plus structural edge cases. */
class BannerGifPolicyTest {
    private fun gif(frames: Int, delayCs: Int = 10, width: Int = 1, height: Int = 1): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        fun emit(vararg values: Int) = values.forEach { out.write(it) }
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        emit(width and 255, width shr 8, height and 255, height shr 8, 0x80, 0, 0,
            0, 0, 0, 255, 255, 255)
        repeat(frames) {
            emit(0x21, 0xF9, 4, 0, delayCs and 255, delayCs shr 8, 0, 0)
            emit(0x2C, 0, 0, 0, 0, 1, 0, 1, 0, 0, 2, 2, 0x44, 0x01, 0)
        }
        emit(0x3B)
        return out.toByteArray()
    }
    private fun rejects(bytes: ByteArray) {
        try { BannerGifPolicy.inspect(bytes); fail("Expected rejection") } catch (_: IllegalArgumentException) { }
        catch (_: IllegalStateException) { }
    }
    @Test fun realFrameDelaysAndTenSecondBoundary() {
        assertEquals(10_000L, BannerGifPolicy.inspect(gif(100)).durationMs)
        rejects(gif(101))
    }
    @Test fun decoderMinimumFrameDelayMatchesDurationBound() {
        assertEquals(100L, BannerGifPolicy.inspect(gif(1, 0)).durationMs)
        assertEquals(100L, BannerGifPolicy.inspect(gif(1, 1)).durationMs)
        assertEquals(20L, BannerGifPolicy.inspect(gif(1, 2)).durationMs)
        rejects(gif(101, 0))
    }
    @Test fun invalidSignatureTruncationTrailerAndFrameBoundsRejected() {
        rejects("not a gif banner".toByteArray())
        val valid = gif(1)
        rejects(valid.copyOf(valid.size - 1))
        rejects(valid + byteArrayOf(0))
        rejects(gif(1, width = 0))
        rejects(gif(1, width = 721))
    }
    @Test fun dimensionPixelAndFrameWorkLimits() {
        rejects(gif(1, width = 720, height = 720))
        rejects(gif(201, delayCs = 2))
        rejects(gif(150, delayCs = 2, width = 512, height = 512))
        rejects(ByteArray(BannerGifPolicy.MAX_BYTES + 1))
    }
    @Test fun positiveStillGifAndSignatureRecognition() {
        val bytes = gif(1)
        assertTrue(BannerGifPolicy.isGif(bytes.copyOf(6)))
        assertEquals(BannerGifPolicy.Info(1, 1, 1, 100), BannerGifPolicy.inspect(bytes))
        assertFalse(BannerGifPolicy.isGif(byteArrayOf(1, 2)))
    }
}
