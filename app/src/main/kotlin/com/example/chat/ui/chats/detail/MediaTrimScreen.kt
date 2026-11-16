package com.example.chat.ui.chats.detail

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.example.chat.ui.kit.rememberChatyReduceMotion
import com.example.chat.ui.media.stageMediaSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import java.io.File
import kotlin.math.roundToLong
import androidx.compose.ui.semantics.*

/** Formats a clip position as m:ss.cs from whole milliseconds (single clock source, MR-R4-019).
 * Pure function so tests can pin it with table-driven cases (MR-R5-038). */
internal fun formatClipTime(ms: Long): String {
    val total = ms.coerceAtLeast(0)
    // MR-R4-019 original tenths-only formatter: return "%d:%02d.%d".format(total / 60000, total / 1000 % 60, total / 100 % 10)
    return "%d:%02d.%02d".format(total / 60000, total / 1000 % 60, total / 10 % 100)
}

/** MR-R4-019: rounded display clock only; does not change export cut positions. */
internal fun clipLabelMillis(duration: Long, fraction: Float): Long =
    (duration.toDouble() * fraction.toDouble()).roundToLong()

/** Number of filmstrip frames extracted for the trim timeline (MR-R1-056). */
private const val TRIM_STRIP_FRAMES = 10
/** Filmstrip thumbnails are bounded to this height in px; ten of them stay under ~2 MB (MR-R4-032). */
private const val TRIM_STRIP_THUMB_HEIGHT_PX = 160

/** MR-R2-025: cached filmstrip entry for one source. Frames form an immutable list that is never
 * recycled - the cache never recycles on eviction because composition may still show them. */
internal class TrimStripCacheEntry(val durationMs: Long, val frameCount: Int, val thumbHeightPx: Int, val frames: List<Bitmap>)

/** Export-copy editor, honest about keyframe alignment and compatible codecs. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun MediaTrimScreen(
    source: String,
    video: Boolean,
    onCancel: () -> Unit,
    onApply: (MediaTrimResult) -> Unit,
    outputFile: java.io.File? = null,
    headers: Map<String, String>? = null,
    errorItemLabel: String = "Clip",
    stripCache: LruCache<String, TrimStripCacheEntry>? = null,
) {
    // Replacement disposes screen-owned jobs/state without changing the shared trim cache owner.
    androidx.compose.runtime.key(source, outputFile) {
    val context = LocalContext.current
    val view = LocalView.current
    // MR-R2-037: reduced-motion flag gates the trim timeline strip fling below.
    val reduceMotion = rememberChatyReduceMotion()
    val scope = rememberCoroutineScope()
    var duration by remember(source) { mutableLongStateOf(0) }
    var rotationHint by remember(source) { mutableStateOf<Int?>(null) }
    var durationUnknown by remember(source) { mutableStateOf(false) }
    var scanningDuration by remember(source) { mutableStateOf(false) }
    // MR-R5-001: the range survives rotation/process recreation as two plain floats.
    var rangeStart by rememberSaveable(source) { mutableStateOf(0f) }
    var rangeEnd by rememberSaveable(source) { mutableStateOf(1f) }
    var exportJob by remember { mutableStateOf<Job?>(null) }
    var active by remember { mutableStateOf(true) }
    DisposableEffect(Unit) { onDispose { active = false; exportJob?.cancel() } }
    fun cancelTrim() { exportJob?.cancel(); onCancel() }
    var busy by remember { mutableStateOf(false) }
    var error by remember(source) { mutableStateOf<String?>(null) }
    var lastFailureAt by remember { mutableLongStateOf(0L) }
    var samplesWritten by remember(source) { mutableIntStateOf(0) }
    var stagedSource by remember(source) { mutableStateOf<File?>(null) }
    var staging by remember(source) { mutableStateOf(false) }
    DisposableEffect(source) { onDispose { stagedSource?.delete() } }
    val effectiveSource = stagedSource?.let { Uri.fromFile(it).toString() } ?: source

    LaunchedEffect(source) {
        try {
            // MR-R4-021 / MR-R4-022: remote and provider sources are copied into app-private
            // storage once, with progress, so the trim never depends on a transient grant or a
            // signed URL outliving the export.
            val scheme = Uri.parse(source).scheme?.lowercase(java.util.Locale.ROOT)
            if (scheme == "http" || scheme == "https" || scheme == "content") {
                staging = true
                stagedSource = com.example.chat.ui.media.stageMediaSource(context, source, "chaty_trim_src_")
                staging = false
            }
            // MR-R1-031: duration AND rotation come from this single retriever open; the exporter
            // receives the rotation as a parameter instead of parsing the container again.
            val probed = withContext(Dispatchers.IO) {
                val retriever = MediaMetadataRetriever()
                try {
                    // Read the staging state inside this coroutine: the composition-time
                    // effectiveSource is stale after the stage above completes.
                    val probedSource = stagedSource?.let { Uri.fromFile(it).toString() } ?: source
                    retriever.setDataSource(context, Uri.parse(probedSource))
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() to
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull()
                } finally { retriever.release() }
            }
            rotationHint = probed.second?.takeIf { it in setOf(0, 90, 180, 270) }
            duration = probed.first ?: 0
            if (duration <= 0) durationUnknown = true
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "Cannot read this media. Check access and try a supported local file." }
        finally { staging = false }
    }
    androidx.activity.compose.BackHandler(onBack = ::cancelTrim)

    // MR-R1-056 / MR-R2-025 / MR-R2-027: one lazily extracted, bounded, path-cached filmstrip.
    // Two retrievers split the timestamps so a long clip does not seek end-to-end on one parser.
    var strip by remember(effectiveSource, duration) { mutableStateOf<List<Bitmap>>(emptyList()) }
    LaunchedEffect(effectiveSource, duration) {
        if (!video || duration <= 0) return@LaunchedEffect
        // MR-R2-025: reuse a complete strip fetched earlier in this flow for the same source and
        // frame contract. Keyed by the original source, never the staged temp path.
        val cachedStrip = stripCache?.get(source)
        if (cachedStrip != null && cachedStrip.durationMs == duration && cachedStrip.frameCount == TRIM_STRIP_FRAMES && cachedStrip.thumbHeightPx == TRIM_STRIP_THUMB_HEIGHT_PX) {
            strip = cachedStrip.frames
            return@LaunchedEffect
        }
        strip = try {
            val fetched = withContext(Dispatchers.IO) {
                coroutineScope {
                    val halves = listOf(0 until TRIM_STRIP_FRAMES / 2, TRIM_STRIP_FRAMES / 2 until TRIM_STRIP_FRAMES)
                    halves.map { half ->
                        async {
                            val retriever = MediaMetadataRetriever()
                            try {
                                retriever.setDataSource(context, Uri.parse(effectiveSource))
                                half.map { i ->
                                    coroutineContext.ensureActive()
                                    val timeUs = (duration * 1000L) * i / TRIM_STRIP_FRAMES
                                    val frame = if (Build.VERSION.SDK_INT >= 27) {
                                        retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 4096, TRIM_STRIP_THUMB_HEIGHT_PX)
                                    } else {
                                        @Suppress("DEPRECATION")
                                        retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                                    }
                                    frame?.let { raw ->
                                        if (raw.height <= TRIM_STRIP_THUMB_HEIGHT_PX) raw
                                        else {
                                            val scaled = Bitmap.createScaledBitmap(raw, (raw.width * (TRIM_STRIP_THUMB_HEIGHT_PX.toFloat() / raw.height)).toInt().coerceAtLeast(1), TRIM_STRIP_THUMB_HEIGHT_PX, true)
                                            if (scaled != raw) raw.recycle()
                                            scaled
                                        }
                                    }
                                }
                            } finally { retriever.release() }
                        }
                    }.flatMap { it.await() }.filterNotNull()
                }
            }
            // Cache only complete strips and check cancellation immediately before insertion.
            // Cancellation can race this put; the cache is flow-local, with no persistence.
            // The list is immutable after this point and frames are never recycled.
            currentCoroutineContext().ensureActive()
            if (fetched.size == TRIM_STRIP_FRAMES) stripCache?.put(source, TrimStripCacheEntry(duration, TRIM_STRIP_FRAMES, TRIM_STRIP_THUMB_HEIGHT_PX, fetched))
            fetched
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { emptyList() }
    }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                // MR-R4-043 prior: TextButton(::cancelTrim, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
                TextButton(::cancelTrim, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(com.example.chat.R.string.media_released_trim_cancel)) }
                // MR-R4-043 prior: Text("Trim a copy", Modifier.padding(12.dp))
                Text(stringResource(com.example.chat.R.string.media_released_trim_title), Modifier.padding(12.dp))
                TextButton(onClick = {
                    if (busy) return@TextButton
                    // MR-R5-018: ignore a retry tapped within 1 s of the previous failure.
                    val now = System.currentTimeMillis()
                    if (now - lastFailureAt < 1_000L) return@TextButton
                    busy = true
                    error = null
                    samplesWritten = 0
                    exportJob = scope.launch {
                        var output: MediaTrimResult? = null
                        var handedOff = false
                        var failed = false
                        try {
                            // MR-R1-035: refuse early with the missing number instead of an
                            // IOException mid-export.
                            val sourceBytes = stagedSource?.length()
                                ?: runCatching { File(Uri.parse(source).path.orEmpty()).length() }.getOrNull() ?: 0L
                            if (sourceBytes > 0) {
                                val available = android.os.StatFs(context.cacheDir.path).availableBytes
                                val needed = (sourceBytes * 1.1).toLong()
                                check(available >= needed) {
                                    "Not enough space: this copy needs about ${needed / (1024 * 1024)} MB but only ${available / (1024 * 1024)} MB is free."
                                }
                            }
                            val start = (duration * rangeStart).toLong()
                            // MR-R5-005: an end at the file end is inclusive of the last sample.
                            val end = if (rangeEnd >= 1f) duration + 60_000L else (duration * rangeEnd).toLong()
                            output = trimMediaCopy(context, effectiveSource, start, end, video, onSampleWritten = { count ->
                                if (count == 1 || count % 250 == 0) withContext(Dispatchers.Main) { samplesWritten = count }
                            }, outputFile = outputFile, headers = headers, rotationHint = rotationHint)
                            coroutineContext.ensureActive()
                            if (active) { onApply(output!!); handedOff = true }
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { failed = true; error = e.message?.takeIf { it.startsWith("Not enough space") } ?: "Unable to trim this range or format. Original kept unchanged." }
                        finally {
                            if (!handedOff) output?.file?.delete()
                            if (failed) lastFailureAt = System.currentTimeMillis()
                            busy = false
                        }
                    }
                }, modifier = Modifier.heightIn(min = 48.dp), enabled = duration > 0 && !busy && !staging && (duration * (rangeEnd - rangeStart)).toLong() >= 500L) {
                    // MR-R3-027: the button disables and morphs on tap.
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    // MR-R4-043 prior: Text(if (busy) "Exporting" else "Apply")
                    Text(if (busy) stringResource(com.example.chat.R.string.media_released_exporting) else stringResource(com.example.chat.R.string.media_released_apply))
                }
            }
            // MR-R3-048: identical keyframe-honesty wording at both trim entry points.
            // MR-R4-043 prior: Text(if (video) "MP4 video/AAC audio copy. Video starts at the previous keyframe, so the cut may begin slightly earlier. Subtitles and chapters are not carried into the copy."
            Text(if (video) stringResource(com.example.chat.R.string.media_released_trim_video_note)
                 // MR-R4-043 prior: else "AAC audio to M4A copy. Other audio formats are kept unchanged and report unsupported.")
                 else stringResource(com.example.chat.R.string.media_released_trim_audio_note))
            // MR-R4-043 prior: if (staging) Text("Downloading a local copy before trimming", modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            if (staging) Text(stringResource(com.example.chat.R.string.media_released_trim_downloading), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                // MR-R4-043 prior: Text("Exporting copy: $samplesWritten samples written", modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                Text(stringResource(com.example.chat.R.string.media_released_trim_samples, samplesWritten), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            // MR-R4-043 prior: Text("Minimum clip length: 0.5 seconds. A clip ending at the file end keeps every sample.", style = MaterialTheme.typography.bodySmall)
            Text(stringResource(com.example.chat.R.string.media_released_trim_minimum), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(24.dp))
            if (duration > 0) {
                // MR-R4-019 previous display: val startMs = (duration * rangeStart).toLong()
                val startMs = clipLabelMillis(duration, rangeStart)
                // MR-R4-019 previous display: val endMs = (duration * rangeEnd).toLong()
                val endMs = clipLabelMillis(duration, rangeEnd)
                val timelineDescription = stringResource(com.example.chat.R.string.media_released_trim_timeline)
                val rangeDescription = stringResource(com.example.chat.R.string.media_released_trim_range_label)
                val rangeState = stringResource(com.example.chat.R.string.media_released_trim_range_state, formatClipTime(startMs), formatClipTime(endMs))
                // MR-R2-036: the strip is informational; the steppers are the operable control.
                if (strip.isNotEmpty()) {
                    LazyRow(
                        // MR-R4-043 prior: Modifier.fillMaxWidth().height(56.dp).semantics { contentDescription = "Timeline" },
                        Modifier.fillMaxWidth().height(56.dp).semantics { contentDescription = timelineDescription },
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        // MR-R2-037: reduced motion stops kinetic travel at lift on the trim timeline,
                        // reusing the existing review-strip policy (171); direct dragging is unchanged.
                        flingBehavior = if (reduceMotion) ReviewStripStopFlingBehavior else androidx.compose.foundation.gestures.ScrollableDefaults.flingBehavior(),
                    ) {
                        items(strip.size) { i ->
                            Image(strip[i].asImageBitmap(), null, Modifier.fillMaxHeight(), contentScale = ContentScale.Crop)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                // MR-R4-043 prior: Text("Start: ${formatClipTime(startMs)}   End: ${formatClipTime(endMs)}")
                Text(stringResource(com.example.chat.R.string.media_released_trim_boundaries, formatClipTime(startMs), formatClipTime(endMs)))
                // MR-R5-025 prior wall-clock-only throttle:
                // var lastBoundaryAnnouncement by remember { mutableLongStateOf(0L) }
                val boundaryAnnouncements = remember(source, duration) { TrimBoundaryAnnouncementGate(startMs, endMs) }
                val startThumbInteraction = remember(source) { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val endThumbInteraction = remember(source) { androidx.compose.foundation.interaction.MutableInteractionSource() }
                RangeSlider(
                    value = rangeStart..rangeEnd,
                    onValueChange = { range ->
                        rangeStart = range.start
                        rangeEnd = range.endInclusive
                        // MR-R5-025 previous: val now = System.currentTimeMillis()
                        // if (now - lastBoundaryAnnouncement >= 1_000L) { lastBoundaryAnnouncement = now
                        // Only an actual whole-second crossing may consume the1/s speech budget.
                        if (boundaryAnnouncements.shouldAnnounce(clipLabelMillis(duration, range.start),
                                clipLabelMillis(duration, range.endInclusive), android.os.SystemClock.elapsedRealtime())) {
                            // MR-R4-019 previous display: view.announceForAccessibility("Trim from ${formatClipTime((duration * range.start).toLong())} to ${formatClipTime((duration * range.endInclusive).toLong())}")
                            // MR-R4-043 prior: view.announceForAccessibility("Trim from ${formatClipTime(clipLabelMillis(duration, range.start))} to ${formatClipTime(clipLabelMillis(duration, range.endInclusive))}")
                            view.announceForAccessibility(context.getString(com.example.chat.R.string.media_released_trim_announce, formatClipTime(clipLabelMillis(duration, range.start)), formatClipTime(clipLabelMillis(duration, range.endInclusive))))
                        }
                    },
                    onValueChangeFinished = {
                        // MR-R3-024: snap to a 1 s grid when the thumb lands within 0.5 s of it.
                        val snapped = trimSnappedRange(duration, rangeStart, rangeEnd)
                        rangeStart = snapped.start
                        rangeEnd = snapped.endInclusive
                        // MR-R3-024 prior inline policy retained:
                        // if (duration > 0) {
                        // val second = 1000f / duration
                        // val half = 500f / duration
                        // fun snap(value: Float): Float {
                        // val nearest = kotlin.math.round(value / second) * second
                        // return if (kotlin.math.abs(nearest - value) <= half) nearest.coerceIn(0f, 1f) else value
                        // }
                        // rangeStart = snap(rangeStart).coerceAtMost((rangeEnd - 500f / duration).coerceAtLeast(0f))
                        // rangeEnd = snap(rangeEnd).coerceAtLeast((rangeStart + 500f / duration).coerceAtMost(1f))
                        // }

                    },
                    enabled = !busy, valueRange = 0f..1f,
                    // MR-R1-043: child stateDescription merges into Material's existing focusable thumb.
                    // Keep Material progress actions/ranges; never clear their semantics or add a new focus node.
                    startInteractionSource = startThumbInteraction,
                    endInteractionSource = endThumbInteraction,
                    startThumb = { slider ->
                        val startState = stringResource(com.example.chat.R.string.media_released_trim_start_state, formatClipTime(clipLabelMillis(duration, slider.activeRangeStart)))
                        SliderDefaults.Thumb(interactionSource = startThumbInteraction, enabled = !busy,
                        // MR-R4-043 prior: modifier = Modifier.semantics { stateDescription = "Start ${formatClipTime(clipLabelMillis(duration, slider.activeRangeStart))}" }) },
                        modifier = Modifier.semantics { stateDescription = startState }) },
                    endThumb = { slider ->
                        val endState = stringResource(com.example.chat.R.string.media_released_trim_end_state, formatClipTime(clipLabelMillis(duration, slider.activeRangeEnd)))
                        SliderDefaults.Thumb(interactionSource = endThumbInteraction, enabled = !busy,
                        // MR-R4-043 prior: modifier = Modifier.semantics { stateDescription = "End ${formatClipTime(clipLabelMillis(duration, slider.activeRangeEnd))}" }) },
                        modifier = Modifier.semantics { stateDescription = endState }) },
                    // MR-R3-024 prior modifier: modifier = Modifier.semantics {
                    modifier = Modifier.heightIn(min = 48.dp).semantics {
                        // MR-R4-043 prior: contentDescription = "Trim start and end"
                        contentDescription = rangeDescription
                        // MR-R4-043 prior: stateDescription = "From ${formatClipTime(startMs)} to ${formatClipTime(endMs)}"
                        stateDescription = rangeState
                    })
                // MR-R4-043 prior: Text("Clip: ${formatClipTime(endMs - startMs)} of ${formatClipTime(duration)}", fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                Text(stringResource(com.example.chat.R.string.media_released_trim_clip_duration, formatClipTime(endMs - startMs), formatClipTime(duration)), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                Row(Modifier.fillMaxWidth()) {
                    // MR-R4-043 prior: TextButton(enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp), onClick = { rangeStart = (rangeStart - 1000f / duration).coerceAtLeast(0f) }) { Text("Start -1s") }
                    TextButton(enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp), onClick = { rangeStart = (rangeStart - 1000f / duration).coerceAtLeast(0f) }) { Text(stringResource(com.example.chat.R.string.media_released_trim_start_minus)) }
                    // MR-R4-043 prior: TextButton(enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp), onClick = { rangeStart = (rangeStart + 1000f / duration).coerceAtMost((rangeEnd - 500f / duration).coerceAtLeast(0f)) }) { Text("Start +1s") }
                    TextButton(enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp), onClick = { rangeStart = (rangeStart + 1000f / duration).coerceAtMost((rangeEnd - 500f / duration).coerceAtLeast(0f)) }) { Text(stringResource(com.example.chat.R.string.media_released_trim_start_plus)) }
                }
                Row(Modifier.fillMaxWidth()) {
                    // MR-R4-043 prior: TextButton(enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp), onClick = { rangeEnd = (rangeEnd - 1000f / duration).coerceAtLeast((rangeStart + 500f / duration).coerceAtMost(1f)) }) { Text("End -1s") }
                    TextButton(enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp), onClick = { rangeEnd = (rangeEnd - 1000f / duration).coerceAtLeast((rangeStart + 500f / duration).coerceAtMost(1f)) }) { Text(stringResource(com.example.chat.R.string.media_released_trim_end_minus)) }
                    // MR-R4-043 prior: TextButton(enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp), onClick = { rangeEnd = (rangeEnd + 1000f / duration).coerceAtMost(1f) }) { Text("End +1s") }
                    TextButton(enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp), onClick = { rangeEnd = (rangeEnd + 1000f / duration).coerceAtMost(1f) }) { Text(stringResource(com.example.chat.R.string.media_released_trim_end_plus)) }
                }
            } else if (durationUnknown && error == null) {
                // MR-R2-012: duration metadata can lie; offer a bounded scan instead of failing.
                Column {
                    // MR-R4-043 prior: Text("This file does not report its duration.")
                    Text(stringResource(com.example.chat.R.string.media_released_trim_no_duration))
                    TextButton(
                        enabled = !scanningDuration,
                        modifier = Modifier.heightIn(min = 48.dp),
                        onClick = {
                            if (scanningDuration) return@TextButton
                            scanningDuration = true
                            scope.launch {
                                try {
                                    val scanned = withContext(Dispatchers.IO) { scanDurationMs(context, effectiveSource) }
                                    if (scanned != null && scanned > 0) { duration = scanned; durationUnknown = false }
                                    else error = "Cannot read this media. Check access and try a supported local file."
                                } catch (e: CancellationException) { throw e }
                                finally { scanningDuration = false }
                            }
                        },
                    // MR-R4-043 prior: ) { Text(if (scanningDuration) "Scanning" else "Scan file for duration") }
                    ) { Text(if (scanningDuration) stringResource(com.example.chat.R.string.media_released_trim_scanning) else stringResource(com.example.chat.R.string.media_released_trim_scan_duration)) }
                }
            } else if (error == null) CircularProgressIndicator()
            // MR-R3-044 previous error presentation retained:
            // error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 16.dp).semantics { liveRegion = LiveRegionMode.Polite }) }
            // MR-R4-043 prior: error?.let { Text(mediaItemError(errorItemLabel, it, "Try again or choose another range."), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 16.dp).semantics { liveRegion = LiveRegionMode.Polite }) }
            error?.let { Text(mediaItemError(errorItemLabel, it, stringResource(com.example.chat.R.string.media_released_trim_retry_hint)), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 16.dp).semantics { liveRegion = LiveRegionMode.Polite }) }
            Spacer(Modifier.weight(1f))
            // MR-R4-043 prior: Text("Apply creates a new local file. Your original message and file are not changed.", style = MaterialTheme.typography.bodySmall)
            Text(stringResource(com.example.chat.R.string.media_released_trim_copy_note), style = MaterialTheme.typography.bodySmall)
        }
    }
    } // Trim input/output identity boundary.
}
