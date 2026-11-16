package com.example.chat.ui.media

import android.content.Context
import android.graphics.BitmapFactory
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

private const val MAX_MEDIA_SOURCE_PIXELS = 100_000_000L
private const val MAX_IMAGE_HEADER_BYTES = 1_048_576L

internal fun isAllowedMediaImageDimensions(width: Int, height: Int): Boolean =
    width > 0 && height > 0 && width.toLong() * height.toLong() <= MAX_MEDIA_SOURCE_PIXELS

/** MR-R2-013: bounds-only preflight, no bitmap allocation or plaintext staging file.
 * Refuses unknown/unsupported bounds and limits metadata input to1MB. This is a pixel
 * budget, not proof a decoder or mutable provider is safe. Caller must keep output sizing. */
internal fun readAllowedMediaImageDimensions(context: Context, source: String): Pair<Int, Int>? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    openMediaInput(context, source).use { input ->
        ImageHeaderBudget(input).use { BitmapFactory.decodeStream(it, null, bounds) }
    }
    if (isAllowedMediaImageDimensions(bounds.outWidth, bounds.outHeight)) bounds.outWidth to bounds.outHeight else null
} catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
catch (_: Exception) { null }

private class ImageHeaderBudget(input: InputStream) : FilterInputStream(input) {
    private var remaining = MAX_IMAGE_HEADER_BYTES
    private fun requireBudget() { if (remaining <= 0) throw IOException("Image metadata limit exceeded") }
    override fun read(): Int {
        requireBudget()
        return `in`.read().also { if (it >= 0) remaining-- }
    }
    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        requireBudget()
        return `in`.read(bytes, offset, minOf(length.toLong(), remaining).toInt()).also { if (it > 0) remaining -= it }
    }
    override fun skip(count: Long): Long {
        if (count <= 0) return 0
        requireBudget()
        return `in`.skip(minOf(count, remaining)).also { remaining -= it }
    }
}
