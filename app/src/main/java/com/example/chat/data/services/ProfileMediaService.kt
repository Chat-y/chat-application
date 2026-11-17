package com.example.chat.data.services

import com.example.chat.data.remote.MediaStorageBackend
import java.util.UUID

/** Shared decode/upload bounds; no UI geometry or server authority implied. */
internal object ProfileBannerVideoLimits {
    const val MAX_BYTES = 8 * 1024 * 1024
    const val MAX_DURATION_MS = 10_000L
    const val MAX_DIMENSION_PX = 1920
    const val POSTER_WIDTH = 480
    const val POSTER_HEIGHT = 270
}

/** Source of the media: device camera or the system gallery. */
enum class ProfileMediaSource { camera, gallery }

/** Picker/cropper cancellation (Dart _CancelledException; toString 'cancelled'). */
class ProfileMediaCancelledException : Exception("cancelled")

/**
 * Exact port of lib/data/services/profile_media_service.dart's validation,
 * compression and upload pipeline (uploadAvatar / uploadBanner /
 * _pickCompressUpload / isSupportedImageMime). The ImagePicker, cropper
 * dialog and flutter_image_compress are platform/UI concerns: callers hand
 * in the picked (already UI-cropped) image bytes and the [imageCompressor]
 * seam normalizes to JPEG at the target square; a null return keeps the
 * original file, exactly like the Dart `compressed != null ? ... : workingFile`.
 */
class ProfileMediaService(
    private val storage: MediaStorageBackend,
    private val currentUserId: () -> String?,
    /** jpeg compression seam: (bytes, square, quality) -> bytes or null. */
    private val imageCompressor: suspend (bytes: ByteArray, square: Int, quality: Int) -> ByteArray? = { _, _, _ -> null },
) {
    // Previous uploadAvatar directly entered the image compressor.
    suspend fun uploadAvatar(bytes: ByteArray): String {
        require(!BannerGifPolicy.isGif(bytes)) { "GIF profile photos are not supported. Choose a still image." }
        return compressUpload(bytes, folder = "avatars", square = 512, quality = 88)
    }

    suspend fun uploadBanner(bytes: ByteArray): String =
        compressUpload(bytes, folder = "banners", square = 1280, quality = 85)

    /** Input already checked by BannerVideoInput; server MUST independently enforce these limits. */
    suspend fun uploadBannerVideo(bytes: ByteArray): String {
        require(bytes.isNotEmpty() && bytes.size <= ProfileBannerVideoLimits.MAX_BYTES) { "Choose a banner video under 8 MB." }
        val userId = currentUserId() ?: error("Sign in again before changing your banner.")
        val objectPath = "$userId/banners/${UUID.randomUUID()}.mp4"
        storage.upload(BUCKET, objectPath, bytes, "video/mp4")
        val url = storage.getPublicUrl(BUCKET, objectPath)
        require(runCatching { java.net.URI(url).path.orEmpty().endsWith(".mp4", true) }.getOrDefault(false)) {
            "Server video URLs must preserve the MP4 extension. Banner was not saved."
        }
        return url
    }

    suspend fun uploadBannerGif(bytes: ByteArray): String {
        BannerGifPolicy.inspect(bytes)
        val userId = currentUserId() ?: error("Sign in again before changing your banner.")
        val objectPath = "$userId/banners/${UUID.randomUUID()}.gif"
        storage.upload(BUCKET, objectPath, bytes, "image/gif")
        val url = storage.getPublicUrl(BUCKET, objectPath)
        require(runCatching { java.net.URI(url).path.orEmpty().endsWith(".gif", true) }.getOrDefault(false)) {
            "Server GIF URLs must preserve the GIF extension. Banner was not saved."
        }
        return url
    }

    private suspend fun compressUpload(
        bytes: ByteArray,
        folder: String,
        square: Int,
        quality: Int,
    ): String {
        val userId = currentUserId() ?: "local_user"

        // Previous raw-crop rejection, before the JPEG compressor had a chance to shrink PNGs:
        // if (bytes.size > MAX_BYTES_BEFORE_COMPRESS) {
        //     throw IllegalStateException("Image is too large (max 6 MB before compression).")
        // }

        val compressed = imageCompressor(bytes, square, quality)
        val file = compressed ?: bytes
        // Legacy constant name retained for compatibility; apply the bound to processed bytes.
        if (file.size > MAX_BYTES_BEFORE_COMPRESS) {
            throw IllegalStateException("Processed image is too large (max 6 MB).")
        }
        if (file.isEmpty()) {
            throw IllegalStateException("Image processing failed. Try a different picture.")
        }
        if (file.size > MAX_BYTES_AFTER_COMPRESS) {
            throw IllegalStateException("Processed image is still too large. Try another one.")
        }

        val objectPath = "$userId/$folder/${UUID.randomUUID()}.jpg"
        storage.upload(BUCKET, objectPath, file, "image/jpeg")
        return storage.getPublicUrl(BUCKET, objectPath)
    }

    companion object {
        const val BUCKET = "profile-media"
        const val MAX_BYTES_BEFORE_COMPRESS = 6 * 1024 * 1024
        const val MAX_BYTES_AFTER_COMPRESS = 2 * 1024 * 1024
        val ALLOWED_MIME = listOf("image/jpeg", "image/png", "image/webp")

        /** Exact port of isSupportedImageMime. */
        fun isSupportedImageMime(mime: String): Boolean = ALLOWED_MIME.contains(mime)
    }
}
