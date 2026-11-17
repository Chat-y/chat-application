package com.example.chat.ui.profile

import com.example.chat.data.services.ProfileBannerVideoLimits as Limits
import android.content.Context
import android.net.Uri
import android.media.MediaMetadataRetriever
import android.media.MediaExtractor
import android.media.MediaFormat
import android.graphics.Bitmap
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Still-image pick/crop is unchanged. Video is bounded and validated before any upload. */
internal object BannerVideoInput {
    const val MAX_BYTES = Limits.MAX_BYTES
    suspend fun copy(context: Context, uri: Uri, files: ProfilePickFiles): File = withContext(Dispatchers.IO) {
        val file = File.createTempFile("chaty_banner_", ".mp4", context.cacheDir)
        try {
            files.track(file)
            files.begin(file)
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var total = 0L
                        while (true) {
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= MAX_BYTES) { "Choose a banner video under 8 MB." }
                            output.write(buffer, 0, count)
                        }
                    }
                } ?: error("Could not read banner video.")
                validate(file)
            } finally { files.end(file) }
            file
        } catch (failure: Throwable) { files.remove(file); throw failure }
    }
    fun validate(file: File) {
        require(file.length() in 1..MAX_BYTES.toLong()) { "Choose a banner video under 8 MB." }
        val metadata = MediaMetadataRetriever()
        try {
            metadata.setDataSource(file.absolutePath)
            val duration = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            require(duration != null && duration in 1..Limits.MAX_DURATION_MS) { "Banner videos must be 10 seconds or shorter." }
            val width = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            require(width > 0 && height > 0 && maxOf(width, height) <= Limits.MAX_DIMENSION_PX) { "Choose a banner video up to 1920 pixels on its longest side." }
            val mime = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE).orEmpty()
            require(mime == "video/mp4") { "Choose an MP4 banner video." }
        } finally { metadata.release() }
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            var videoTracks = 0
            for (index in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/")) {
                    videoTracks++
                    require(mime == "video/avc") { "Choose an H.264 MP4 banner video." }
                }
            }
            require(videoTracks == 1) { "Choose a video with one video track." }
        } finally { extractor.release() }
    }
    fun poster(file: File): ByteArray? {
        val metadata = MediaMetadataRetriever()
        return try {
            metadata.setDataSource(file.absolutePath)
            // Bounded extraction API is 27+. On 24-26 avoid full-frame allocation; use themed fallback.
            if (android.os.Build.VERSION.SDK_INT < 27) return null
            val bitmap = metadata.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, Limits.POSTER_WIDTH, Limits.POSTER_HEIGHT) ?: return null
            try { java.io.ByteArrayOutputStream().use { out ->
                if (bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)) out.toByteArray() else null
            } } finally { bitmap.recycle() }
        } catch (_: Exception) { null } finally { metadata.release() }
    }
}

/** At most four small poster JPEGs, process/account lifetime only. No disk or cross-account reuse. */
internal object BannerVideoPosters {
    private var owner: Any? = null
    private val posters = linkedMapOf<String, ByteArray>()
    @Synchronized fun put(account: Any, url: String, bytes: ByteArray?) {
        if (owner !== account) { posters.clear(); owner = account }
        if (bytes == null || bytes.size > 256 * 1024) return
        posters[url] = bytes
        while (posters.size > 4) posters.remove(posters.keys.first())
    }
    @Synchronized fun get(account: Any, url: String): ByteArray? {
        if (owner !== account) { posters.clear(); owner = account }
        return posters[url]
    }
}
