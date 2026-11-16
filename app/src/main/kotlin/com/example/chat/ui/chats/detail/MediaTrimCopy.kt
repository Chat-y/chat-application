package com.example.chat.ui.chats.detail

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.media.MediaCodec
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.coroutines.coroutineContext

internal data class MediaTrimResult(
    val file: File,
    val actualStartMs: Long,
    val durationMs: Long,
    /** True when the source declared spherical/360 metadata the MPEG-4 copy does not carry (MR-R5-009). */
    val sphericalMetadataDropped: Boolean = false,
    /** Samples whose rebased timestamps were clamped to zero (B-frame negative CTS guard, MR-R5-007). */
    val clampedTimestamps: Int = 0,
)

/** Lossless sample-copy trim. Video starts at a sync sample, not an invented frame-exact point.
 * Supports MP4 AVC/HEVC/MPEG4 video and AAC audio only. Unsupported formats fail explicitly.
 * Original is read-only; failed/cancelled outputs are deleted. No account or upload operation.
 *
 * Hard rules for every exporter on this flow (MR-R2-016, MR-R4-031, MR-R5-048):
 * - Derivative media stays under context.cacheDir with random names; it must never be placed where
 *   a gallery scan or backup can pick it up, and never on a world-accessible temp dir (MR-R5-017).
 * - Exports are short-lived and screen-owned; when the screen leaves, work is cancelled instead of
 *   finishing in the background (MR-R2-029). Do not add a silent background-export path.
 * - Failure paths stay log-free: callers get typed results/errors, never URLs or file paths in logs
 *   (MR-R2-018, MR-R2-022, MR-R2-023 picker-URI rule). */
internal suspend fun trimMediaCopy(
    context: Context,
    source: String,
    startMs: Long,
    endMs: Long,
    video: Boolean,
    onSampleWritten: suspend (Int) -> Unit = {},
    outputFile: File? = null,
    /** Optional auth headers for http(s) sources (MR-R1-019). */
    headers: Map<String, String>? = null,
    /** Rotation already read by the caller, so the retriever is not opened twice per trim (MR-R1-031). */
    rotationHint: Int? = null,
    /** Drop every audio track from the copy (mute toggle, MR-R1-051). */
    dropAudio: Boolean = false,
): MediaTrimResult {
    var pendingOutput: File? = null
    try { return withContext(Dispatchers.IO) {
    require(startMs >= 0 && endMs > startMs) { "End must be after start." }
    val extractor = MediaExtractor()
    val out = outputFile ?: File(context.cacheDir, "chaty-outgoing-trimmed-${java.util.UUID.randomUUID()}.${if (video) "mp4" else "m4a"}")
    // Dictated files are created/owned by EditSession. Never point this at an original.
    require(out.canonicalPath != runCatching { File(Uri.parse(source).path.orEmpty()).canonicalPath }.getOrNull()) { "Output cannot overwrite the source." }
    run {
        // MR-R5-017: a dictated output must still live in app-private storage.
        val outCanonical = out.canonicalPath
        val allowedRoots = listOf(context.cacheDir, context.filesDir).map { it.canonicalPath.trimEnd(File.separatorChar) + File.separator }
        require(allowedRoots.any { outCanonical.startsWith(it) }) { "Output must stay inside app-private storage." }
    }
    pendingOutput = out
    var muxer: MediaMuxer? = null
    var started = false
    var spherical = false
    var clamped = 0
    try {
        val uri = Uri.parse(source)
        if (uri.scheme in listOf("http", "https")) {
            // MR-R1-019: header-authorized endpoints are supported; signed query URLs keep working.
            if (headers != null) extractor.setDataSource(source, headers) else extractor.setDataSource(source)
        } else extractor.setDataSource(context, uri, null)
        // MR-R2-019: a cache sweep or the OS can evict the output parent between staging and export;
        // re-create it once before failing.
        val outputMuxer = try {
            MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        } catch (first: Exception) {
            val enoent = generateSequence(first as Throwable) { it.cause }.any { it.message?.contains("ENOENT") == true }
            if (!enoent) throw first
            check(out.parentFile?.let { it.isDirectory || it.mkdirs() } == true) { "Export storage unavailable." }
            MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        }
        muxer = outputMuxer
        val tracks = mutableMapOf<Int, Int>()
        var hasVideo = false
        var videoIndex: Int? = null
        var trackRotation: Int? = null
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            // MR-R5-009: best-effort spherical/360 detection. The copy does not preserve it.
            if (mime.contains("spherical", ignoreCase = true) || format.toString().let { it.contains("spherical", true) || it.contains("equirect", true) }) spherical = true
            if (!mime.startsWith("video/") && !mime.startsWith("audio/")) continue
            if (!video && mime.startsWith("video/")) continue
            if (dropAudio && mime.startsWith("audio/")) continue
            require(mime in setOf("video/avc", "video/hevc", "video/mp4v-es", "audio/mp4a-latm")) { "This format ($mime) cannot be trimmed losslessly here. Original kept unchanged." }
            if (mime.startsWith("video/")) {
                hasVideo = true; videoIndex = index
                if (format.containsKey(MediaFormat.KEY_ROTATION)) trackRotation = runCatching { format.getInteger(MediaFormat.KEY_ROTATION) }.getOrNull()
            }
            extractor.selectTrack(index)
            tracks[index] = outputMuxer.addTrack(format)
        }
        require(tracks.isNotEmpty() && (!video || hasVideo)) { "No supported media tracks found." }
        if (video) {
            // MR-R1-031: the screen reads rotation (and duration) in its single retriever open and
            // passes it here; only open a second retriever when the caller could not.
            val hint = rotationHint ?: run {
                val metadata = MediaMetadataRetriever()
                try {
                    if (uri.scheme in listOf("http", "https")) {
                        if (headers != null) metadata.setDataSource(source, headers) else metadata.setDataSource(source)
                    } else metadata.setDataSource(context, uri)
                    metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull()
                } finally { metadata.release() }
            }
            (hint ?: trackRotation)?.takeIf { it in setOf(0, 90, 180, 270) }?.let { outputMuxer.setOrientationHint(it) }
        }
        // Determine video cut from the VIDEO sync sample alone. Interleaved audio
        // must not shift the origin earlier than the chosen decodable video frame.
        val primaryVideo = videoIndex
        if (primaryVideo != null) tracks.keys.filter { it != primaryVideo }.forEach { extractor.unselectTrack(it) }
        extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        val actualStartUs = extractor.sampleTime.coerceAtLeast(0)
        if (primaryVideo != null) {
            require(extractor.sampleTrackIndex == primaryVideo && extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                // MR-R5-008: very short videos may hold a single keyframe; tell the sender where a cut can start.
                "No decodable video keyframe at this cut. Try a later start; cuts can only begin on a keyframe."
            }
            tracks.keys.filter { it != primaryVideo }.forEach { extractor.selectTrack(it) }
            extractor.seekTo(actualStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        }
        require(actualStartUs < endMs * 1000) { "No sample in this range." }
        outputMuxer.start(); started = true
        val buffer = ByteBuffer.allocateDirect(8 * 1024 * 1024)
        val info = MediaCodec.BufferInfo()
        var lastUs = actualStartUs
        var firstVideoWritten = false
        var writtenSamples = 0
        while (extractor.sampleTrackIndex >= 0) {
            coroutineContext.ensureActive()
            val sampleUs = extractor.sampleTime
            if (sampleUs < 0 || sampleUs >= endMs * 1000) break
            require(extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED == 0) { "Protected media cannot be trimmed here." }
            buffer.clear()
            val count = extractor.readSampleData(buffer, 0)
            if (count < 0) break
            require(count <= buffer.capacity()) { "This sample is too large to trim safely." }
            val target = tracks[extractor.sampleTrackIndex]
            if (target != null && sampleUs >= actualStartUs) {
                val isVideoSample = extractor.sampleTrackIndex == primaryVideo
                // MR-R1-012: when the video anchor landed before the chosen cut, drop audio frames
                // that would otherwise lead in ahead of the cut (within one audio frame of slack).
                if (!isVideoSample && primaryVideo != null && sampleUs < startMs * 1000) {
                    if (!extractor.advance()) break
                    continue
                }
                // MR-R5-007: B-frames can produce a negative rebased CTS; clamp and count instead
                // of writing an invalid timestamp.
                val presentationUs = (sampleUs - actualStartUs).coerceAtLeast(0)
                if (presentationUs != sampleUs - actualStartUs) clamped++
                info.set(0, count, presentationUs,
                    if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                if (isVideoSample && !firstVideoWritten) {
                    require(extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) { "First video sample is not a keyframe." }
                    firstVideoWritten = true
                }
                outputMuxer.writeSampleData(target, buffer, info)
                writtenSamples++
                onSampleWritten(writtenSamples)
                coroutineContext.ensureActive()
                lastUs = maxOf(lastUs, sampleUs)
            }
            if (!extractor.advance()) break
        }
        require(writtenSamples > 0 && (!video || firstVideoWritten)) { "No decodable samples in this range." }
        coroutineContext.ensureActive()
        outputMuxer.stop(); started = false
        require(out.length() > 0) { "The trimmed file is empty." }
        val validation = MediaMetadataRetriever()
        val outputDuration = try {
            validation.setDataSource(out.absolutePath)
            validation.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?.takeIf { it > 0 } ?: error("Trim output has no readable duration")
        } finally { validation.release() }
        MediaTrimResult(out, actualStartUs / 1000, outputDuration, sphericalMetadataDropped = spherical, clampedTimestamps = clamped)
    } catch (e: Throwable) {
        if (started) runCatching { muxer?.stop() }
        out.delete()
        throw e
    } finally {
        extractor.release()
        muxer?.release()
    }

    } } catch (e: Throwable) {
        // Includes cancellation delivered while withContext returns to the UI dispatcher.
        pendingOutput?.delete()
        throw e
    }
}

/** Scans a container for its true duration when metadata lies or is absent (MR-R2-012).
 * Bounded by [maxSamples] so a hostile file cannot hold the caller; returns null when unknown. */
internal fun scanDurationMs(context: Context, source: String, maxSamples: Int = 200_000): Long? {
    val extractor = MediaExtractor()
    return try {
        val uri = Uri.parse(source)
        if (uri.scheme in listOf("http", "https")) extractor.setDataSource(source)
        else extractor.setDataSource(context, uri, null)
        for (index in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("video/") || mime.startsWith("audio/")) extractor.selectTrack(index)
        }
        var lastUs = -1L
        var seen = 0
        while (extractor.sampleTrackIndex >= 0 && seen < maxSamples) {
            val sampleUs = extractor.sampleTime
            if (sampleUs >= 0) lastUs = maxOf(lastUs, sampleUs)
            seen++
            if (!extractor.advance()) break
        }
        lastUs.takeIf { it > 0 }?.let { it / 1000 }
    } catch (_: Throwable) {
        null
    } finally {
        extractor.release()
    }
}
