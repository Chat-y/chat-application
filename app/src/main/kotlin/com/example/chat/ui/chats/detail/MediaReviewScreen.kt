package com.example.chat.ui.chats.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.LooksOne
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.*
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File
import kotlinx.coroutines.delay
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.example.chat.ui.kit.ChatyHapticsPort
import com.example.chat.ui.kit.rememberChatyReduceMotion
import com.example.chat.ui.media.FileTypeDetector
import com.example.chat.ui.media.stageMediaSource
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.semantics.*

/** Documented caps that protect the upload flow (MR-R1-022, Miller). */
private const val MAX_REVIEW_ITEMS = 30
private const val MAX_REVIEW_BYTES = 500L * 1024 * 1024
/** Captions are validated in UTF-8 bytes for the wire, not just characters (MR-R4-017). */
private const val MAX_CAPTION_BYTES = 4096
/** Videos larger than this get an honest 'may take a while' chip (MR-R4-018). */
private const val LARGE_VIDEO_BYTES = 100L * 1024 * 1024
private const val LONG_VIDEO_MS = 10L * 60 * 1000

/** MR-R4-020: one typed view of the media kind; free strings stay at the picker boundary. */
internal enum class ReviewMediaType {
    IMAGE, VIDEO, AUDIO, GIF, OTHER;
    companion object {
        fun of(type: String, mimeType: String?) = when {
            mimeType == "image/gif" || type == "gif" -> GIF
            type == "image" -> IMAGE
            type == "video" -> VIDEO
            type == "audio" -> AUDIO
            else -> OTHER
        }
    }
}

internal data class MediaReviewItem(val source: String, val file: PickedFile, val type: String, val stableId: String = source + ":" + (file.bytes?.size?.toLong() ?: file.path?.let { File(it).length() })) {
    val mediaType: ReviewMediaType get() = ReviewMediaType.of(type, file.mimeType)
}
internal data class ReviewedMedia(
    val items: List<MediaReviewItem>,
    val caption: String,
    val viewOnce: Boolean,
    /** Per-item view-once, keyed by MediaReviewItem.stableId (MR-R1-003). Empty = none. */
    val viewOnceIds: Set<String> = emptySet(),
    /** Per-item captions keyed by stableId (MR-R1-058). [caption] stays the batch/shared caption. */
    val captions: Map<String, String> = emptyMap(),
)

/** MR-R4-048: the screen is a small state machine; mutation lives in the editor overlays. */
private sealed interface ReviewState {
    data object Reviewing : ReviewState
    data class Editing(val item: MediaReviewItem) : ReviewState
    data class Trimming(val item: MediaReviewItem) : ReviewState
}

/** Picking and editing stay local. No upload starts until Send is explicitly tapped.
 *
 * File ownership (MR-R5-040): inputs are caller-owned or staged copies (deleted on close);
 * edit/trim outputs are owned by this screen, deleted unless handed to the sender on Send.
 * Derivatives live only in app-private cache (MR-R2-016, MR-R5-017); abandoned chaty-outgoing-*
 * and chaty_capture_* files older than 24 h are swept on open (MR-R1-016).
 *
 * Design rules for this flow:
 * - Threat model (MR-R4-049): inputs are untrusted bytes from other apps; derivatives never
 *   leave app-private storage before Send; logs and errors report codes, never URIs or paths;
 *   this flow requests no mic/location permissions (MR-R3-050). Play Integrity and root
 *   detection are explicitly out of scope for this screen (MR-R5-041).
 * - This screen is 'Review': choose, caption, view-once, send. Mutation lives in the Edit and
 *   Trim overlays, which are overlays, not destinations (MR-R5-033, MR-R5-047).
 * - Bottom-bar order is fixed: caption, view-once, Send; new toggles go to the header or the
 *   thumbnail strip, never between caption and Send (MR-R2-043). Send stays the only filled
 *   control; header actions keep text-button weight (MR-R3-011).
 * - No sound design: the screen plays no UI sounds (MR-R5-034). Haptics come from
 *   ChatyHapticsPort only (MR-R3-030).
 * - No silent background export: a trim or edit that outlives the screen is cancelled with it;
 *   adding a foreground-service export is a separate, documented decision (MR-R4-031).
 * - The view-once NUX stays modal and rare; add a 'Learn more' link only if a help page exists
 *   (MR-R3-017). All types in this file are internal; that boundary is deliberate (MR-R5-048).
 * - Keyboard/DPAD: Left/Right page items, Ctrl+Enter sends, Delete removes the current item
 *   (MR-R2-042); while the caption field is focused it owns those keys. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaReviewScreen(items: List<MediaReviewItem>, onCancel: () -> Unit, onSend: (ReviewedMedia) -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    // MR-R1-016: process death skips onDispose, so stale derivatives are swept here instead.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
            context.cacheDir.listFiles()?.forEach { stale ->
                if (stale.isFile && stale.lastModified() < cutoff &&
                    (stale.name.startsWith("chaty-outgoing-") || stale.name.startsWith("chaty_capture_") || stale.name.startsWith("chaty_trim_src_") || stale.name.startsWith("chaty_review_stage_"))) {
                    stale.delete()
                }
            }
        }
    }

    // MR-R2-020 / MR-R4-022: provider grants are transient; copy content:// picks into app-owned
    // cache before any editor or sender can see them.
    var stagedItems by remember(items) { mutableStateOf<List<MediaReviewItem>?>(null) }
    val stagedCopies = remember(items) { mutableListOf<File>() }
    var submitted by remember(items) { mutableStateOf(false) }
    DisposableEffect(items) { onDispose { if (!submitted) stagedCopies.forEach { it.delete() } } }
    LaunchedEffect(items) {
        stagedItems = withContext(Dispatchers.IO) {
            items.map { item ->
                val scheme = android.net.Uri.parse(item.source).scheme?.lowercase(java.util.Locale.ROOT)
                if (scheme == "content" && item.file.bytes == null) {
                    runCatching {
                        val copy = stageMediaSource(context, item.source, "chaty_review_stage_")
                        stagedCopies += copy
                        item.copy(source = copy.toURI().toString(),
                            file = PickedFile(name = item.file.name, path = copy.path, mimeType = item.file.mimeType))
                    }.getOrDefault(item)
                } else item
            }
        }
    }

    val baseItems = stagedItems ?: items
    val uniqueItems = remember(baseItems) { baseItems.distinctBy { picked ->
        (picked.file.path ?: picked.source) to (picked.file.bytes?.size?.toLong() ?: picked.file.path?.let { File(it).length() })
    } }
    // MR-R1-022: the batch is bounded so Send never hands the pipeline an unbounded job.
    val boundedItems = remember(uniqueItems) {
        var total = 0L
        uniqueItems.filter { picked ->
            val size = picked.file.bytes?.size?.toLong() ?: picked.file.path?.let { File(it).length() } ?: 0L
            (total + size <= MAX_REVIEW_BYTES).also { if (it) total += size }
        }.take(MAX_REVIEW_ITEMS)
    }
    var current by remember(uniqueItems) { mutableStateOf(boundedItems) }
    var index by remember(uniqueItems) { mutableIntStateOf(0) }
    // MR-R1-058 / MR-R2-010: per-item state is keyed by stableId, never by position.
    var captions by remember(uniqueItems) { mutableStateOf<Map<String, String>>(emptyMap()) }
    val draftMutex = remember { Mutex() }
    var draftReady by remember(uniqueItems) { mutableStateOf(false) }
    var draftClosed by remember(uniqueItems) { mutableStateOf(false) }
    // Finish deleting the draft before leaving, so navigation cannot cancel the cleanup.
    fun discardDraftAndClose() {
        draftClosed = true
        scope.launch {
            withContext(Dispatchers.IO) { draftMutex.withLock { reviewDraftFile(context).delete() } }
            onCancel()
        }
    }

    // MR-R1-003: view-once is per item; the caption-row toggle stays as an apply-to-all shortcut.
    var viewOnceIds by remember(uniqueItems) { mutableStateOf<Set<String>>(emptySet()) }
    var mutedIds by remember(uniqueItems) { mutableStateOf<Set<String>>(emptySet()) }
    var state by remember(uniqueItems) { mutableStateOf<ReviewState>(ReviewState.Reviewing) }
    // MR-R2-015: plain (unedited) photos are re-encoded at send to drop EXIF; on by default.
    var stripLocation by remember(uniqueItems) { mutableStateOf(true) }
    var stripLocationExplained by remember { mutableStateOf(false) }
    val ownedEdits = remember(items) { mutableListOf<File>() }
    DisposableEffect(items) { onDispose { if (!submitted) ownedEdits.forEach { it.delete() } } }
    LaunchedEffect(uniqueItems) {
        if (uniqueItems.size < items.size) snackbar.showSnackbar("Already added. Duplicate attachments were removed.")
        if (boundedItems.size < uniqueItems.size) snackbar.showSnackbar("Some attachments were left out: up to $MAX_REVIEW_ITEMS items and 500 MB per send.")
    }
    // MR-R2-011: unreadable picks are rejected at the door with one honest count.
    LaunchedEffect(uniqueItems) {
        val unreadable = withContext(Dispatchers.IO) {
            uniqueItems.filter { picked ->
                val size = picked.file.bytes?.size?.toLong() ?: picked.file.path?.let { File(it).takeIf { f -> f.isFile && f.canRead() }?.length() }
                size == null || size <= 0L
            }
        }
        if (unreadable.isNotEmpty()) {
            current = current.filter { it.stableId !in unreadable.map { u -> u.stableId }.toSet() }
            snackbar.showSnackbar("${unreadable.size} attachment(s) could not be read and were left out.")
        }
    }
    // GB/stock parity: the sender sees the view-once explainer exactly once.
    val viewOnceNuxStore = remember(context) { com.example.chat.data.media.ViewOnceNuxStore(context) }
    var viewOnceSenderNux by remember { mutableStateOf(false) }
    var sendError by remember(items) { mutableStateOf<String?>(null) }
    var sending by remember(items) { mutableStateOf(false) }
    var discardPrompt by remember(items) { mutableStateOf(false) }
    val anyEdits = current.any { edited -> uniqueItems.firstOrNull { it.stableId == edited.stableId }?.source != edited.source }
    fun requestCancel() {
        if (anyEdits || captions.values.any { it.isNotBlank() } || viewOnceIds.isNotEmpty()) discardPrompt = true else onCancel()
    }
    androidx.activity.compose.BackHandler(onBack = ::requestCancel)
    if (discardPrompt) AlertDialog(
        onDismissRequest = { discardPrompt = false },
        title = { Text("Discard edits?") },
        text = { Text("Your original files are unchanged. Local edits and captions will be discarded.") },
        confirmButton = { TextButton(onClick = { discardPrompt = false; discardDraftAndClose() }) { Text("Discard", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { discardPrompt = false }) { Text("Keep editing") } },
    )
    // Empty batches are handled below; never clamp against the empty 0..-1 range.
    val currentItem = current.getOrNull(index)
    com.example.chat.ui.kit.ProtectSensitiveWindow(active = currentItem?.let { it.stableId in viewOnceIds } == true)
    if (current.isEmpty()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No attachments selected")
            Text("All attachments were removed. Use Undo to restore the last one or go back to choose again.")
            TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back to files") }
            SnackbarHost(snackbar)
        }
        return
    }
    val item = current[index.coerceIn(current.indices)]
    var previewDurationMs by remember(item.source) { mutableStateOf<Long?>(null) }
    LaunchedEffect(item.source) {
        if (item.mediaType == ReviewMediaType.VIDEO) previewDurationMs = withContext(Dispatchers.IO) {
            val retriever = android.media.MediaMetadataRetriever()
            try { retriever.setDataSource(context, android.net.Uri.parse(item.source)); retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() }
            catch (_: Exception) { null } finally { retriever.release() }
        }
    }
    // MR-R4-024: warn when an original carries location metadata.
    var hasLocation by remember(item.stableId) { mutableStateOf(false) }
    LaunchedEffect(item.stableId) {
        if (item.mediaType != ReviewMediaType.IMAGE) { hasLocation = false; return@LaunchedEffect }
        hasLocation = withContext(Dispatchers.IO) {
            runCatching {
                com.example.chat.ui.media.openMediaInput(context, item.source).use { input ->
                    androidx.exifinterface.media.ExifInterface(input).latLong != null
                }
            }.getOrDefault(false)
        }
    }
    // MR-R5-016: format decode support is a device fact; say so instead of failing later.
    LaunchedEffect(item.stableId) {
        val notice = when (val mime = item.file.mimeType?.lowercase(java.util.Locale.ROOT)) {
            "image/heic", "image/heif" -> if (android.os.Build.VERSION.SDK_INT < 28) "HEIC photos are not supported on this Android version. The original is unchanged." else null
            "image/avif" -> if (android.os.Build.VERSION.SDK_INT < 31) "AVIF photos are not supported on this Android version. The original is unchanged." else null
            else -> null
        }
        if (notice != null) snackbar.showSnackbar(notice)
    }
    fun removeItem(position: Int) {
        if (position !in current.indices || sending) return
        val removed = current[position]
        val selectedId = current.getOrNull(index)?.stableId
        current = current.filterIndexed { i, _ -> i != position }
        index = current.indexOfFirst { it.stableId == selectedId }.takeIf { it >= 0 }
            ?: index.coerceIn(0, (current.size - 1).coerceAtLeast(0))
        scope.launch {
            val undo = snackbar.showSnackbar("Attachment removed", actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (undo == SnackbarResult.ActionPerformed && !sending && !submitted && current.none { it.stableId == removed.stableId }) {
                current = current.toMutableList().apply { add(position.coerceAtMost(size), removed) }
                index = current.indexOfFirst { it.stableId == removed.stableId }
            }
        }
    }
    fun attemptSend() {
        if (sending || submitted) return
        if (current.any { picked -> picked.file.bytes?.isNotEmpty() != true && picked.file.path?.let { !File(it).isFile || !File(it).canRead() || File(it).length() == 0L } != false }) {
            sendError = "Attachments: one file is no longer available - pick it again before sending."
            return
        }
        sending = true
        sendError = null
        ChatyHapticsPort.success(view)
        scope.launch {
            var outgoing = current
            try {
                // MR-R1-051: muted videos are exported as audio-free copies at send time.
                val muted = outgoing.filter { it.stableId in mutedIds && it.mediaType == ReviewMediaType.VIDEO }
                if (muted.isNotEmpty()) {
                    outgoing = withContext(Dispatchers.IO) {
                        outgoing.map { picked ->
                            if (picked.stableId in mutedIds && picked.mediaType == ReviewMediaType.VIDEO) {
                                val copy = trimMediaCopy(context, picked.source, 0, Long.MAX_VALUE / 4, true, dropAudio = true)
                                ownedEdits += copy.file
                                picked.copy(source = copy.file.toURI().toString(), file = PickedFile(name = copy.file.name, path = copy.file.path, mimeType = "video/mp4"))
                            } else picked
                        }
                    }
                }
                // MR-R2-015: unedited photos are re-encoded to drop EXIF/location; edited
                // copies already carry no metadata. Videos are not touched.
                if (stripLocation) {
                    outgoing = withContext(Dispatchers.Default) {
                        outgoing.map { picked ->
                            val edited = uniqueItems.firstOrNull { it.stableId == picked.stableId }?.source != picked.source
                            if (!edited && picked.mediaType == ReviewMediaType.IMAGE) {
                                stripExifCopy(context, picked)?.let { copy ->
                                    ownedEdits += copy
                                    picked.copy(source = copy.toURI().toString(), file = PickedFile(name = copy.name, path = copy.path, mimeType = picked.file.mimeType))
                                } ?: picked
                            } else picked
                        }
                    }
                }
                // Keep only final files for the receiving upload flow; release superseded local edits.
                val finalPaths = outgoing.mapNotNull { it.file.path }.toSet()
                // MR-R4-033: batch deletion of owned edits runs off the main thread.
                withContext(Dispatchers.IO) { ownedEdits.filter { it.path !in finalPaths }.forEach { it.delete() } }
                // MR-R4-017: the wire limit is bytes; an overlong caption is trimmed with a note.
                val batchCaption = captions[""] ?: outgoing.mapNotNull { captions[it.stableId] }.firstOrNull { it.isNotBlank() } ?: ""
                val trimmedCaption = batchCaption.trim().let { cap ->
                    if (cap.toByteArray(Charsets.UTF_8).size <= MAX_CAPTION_BYTES) cap
                    else {
                        scope.launch { snackbar.showSnackbar("Caption shortened to fit the send limit.") }
                        var end = cap.length
                        while (end > 0 && cap.take(end).toByteArray(Charsets.UTF_8).size > MAX_CAPTION_BYTES) end--
                        cap.take(end).trimEnd() + "\u2026"
                    }
                }
                draftClosed = true
                withContext(Dispatchers.IO) { draftMutex.withLock { reviewDraftFile(context).delete() } }
                onSend(ReviewedMedia(outgoing.toList(), trimmedCaption,
                    viewOnce = outgoing.isNotEmpty() && outgoing.all { it.stableId in viewOnceIds },
                    viewOnceIds = viewOnceIds, captions = captions.filterKeys { key -> outgoing.any { it.stableId == key } }))
                submitted = true
                // Draft deletion completed before the navigation/send callback above.
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { sendError = "Attachments: could not send - try again." }
            finally { sending = false }
        }
    }
    // MR-R5-030: motor-friendly reorder without dragging (WCAG 2.5.7); also the undo path for order mistakes.
    var reorderDialog by remember { mutableStateOf(false) }
    fun moveItem(from: Int, to: Int) {
        if (from !in current.indices || to !in current.indices || from == to || sending) return
        val selectedId = current.getOrNull(index)?.stableId
        current = current.toMutableList().apply { add(to, removeAt(from)) }
        index = current.indexOfFirst { it.stableId == selectedId }.takeIf { it >= 0 } ?: to
    }
    /** MR-R5-010: a motion photo's edited copy keeps only the still frame; say so first. */
    fun startEdit(media: MediaReviewItem) {
        scope.launch {
            if (isMotionPhoto(context, media)) snackbar.showSnackbar("Motion photo. The edited copy keeps the still frame; the motion part is not included.")
            state = ReviewState.Editing(media)
        }
    }
    // MR-R5-002: restore a recent draft (captions + view-once) when the same items come back
    // after process death; drafts expire after 30 minutes and are cleared on send or discard.
    LaunchedEffect(uniqueItems) {
        val draft = withContext(Dispatchers.IO) { draftMutex.withLock { readReviewDraft(context) } }
        val known = uniqueItems.map { it.stableId }.toSet()
        if (draft != null) {
            captions = captions + draft.first.filterKeys { it in known }
            viewOnceIds = viewOnceIds + draft.second.filter { it in known }
        }
        draftReady = true
    }
    // Do not overwrite the saved draft with initial empty state before restoration completes.
    LaunchedEffect(captions, viewOnceIds, draftReady, draftClosed) {
        if (draftReady && !draftClosed) {
            withContext(Dispatchers.IO) {
                draftMutex.withLock { if (!draftClosed) writeReviewDraft(context, captions, viewOnceIds) }
            }
        }
    }
    // Legacy asynchronous deletion could be cancelled by immediate navigation:
    // fun clearReviewDraft() { scope.launch(Dispatchers.IO) { reviewDraftFile(context).delete() } }
    // MR-R4-042: one-time coach mark for Edit/Trim discoverability, shown on the second open only.
    val coachPrefs = remember(context) { context.getSharedPreferences("chaty_review_coach", android.content.Context.MODE_PRIVATE) }
    var coachMark by remember {
        mutableStateOf(coachPrefs.getInt("reviewOpens", 0) == 1 && !coachPrefs.getBoolean("coachSeen", false))
    }
    LaunchedEffect(Unit) {
        coachPrefs.edit().putInt("reviewOpens", coachPrefs.getInt("reviewOpens", 0) + 1).apply()
    }
    if (coachMark) AlertDialog(
        onDismissRequest = { coachMark = false; coachPrefs.edit().putBoolean("coachSeen", true).apply() },
        confirmButton = { TextButton(onClick = { coachMark = false; coachPrefs.edit().putBoolean("coachSeen", true).apply() }) { Text("Got it") } },
        title = { Text("Edit before sending") },
        text = { Text("Use Edit on photos and Trim a copy on videos to adjust a copy before you send. Your originals stay unchanged.") },
    )
    when (val active = state) {
        is ReviewState.Editing -> {
            ImageEditorScreen(active.item.source,
                onCancel = { state = ReviewState.Reviewing },
                onApply = { file ->
                    ownedEdits += file
                    // MR-R1-021: the stored type comes from the bytes, not a hardcoded MIME.
                    val sniffed = FileTypeDetector.classifyFile({ file.inputStream().use { it.readNBytes(FileTypeDetector.HEADER_BYTES_TO_READ) } }, file.name)
                    current = current.mapIndexed { i, old -> if (old.stableId == active.item.stableId) old.copy(source = file.toURI().toString(), file = PickedFile(name = file.name, path = file.path, mimeType = sniffed.mimeType)) else old }
                    state = ReviewState.Reviewing
                })
            return
        }
        is ReviewState.Trimming -> {
            MediaTrimScreen(active.item.source, true, onCancel = { state = ReviewState.Reviewing }, onApply = { result ->
                ownedEdits += result.file
                current = current.mapIndexed { i, old -> if (old.stableId == active.item.stableId) old.copy(source = result.file.toURI().toString(),
                    file = PickedFile(name = result.file.name, path = result.file.path, mimeType = "video/mp4")) else old }
                state = ReviewState.Reviewing
                // MR-R2-003: the keyframe shift is surfaced, not hidden.
                if (result.actualStartMs > 0) scope.launch { snackbar.showSnackbar("Trimmed copy starts at ${formatClipTime(result.actualStartMs)} (previous keyframe).") }
                if (result.sphericalMetadataDropped) scope.launch { snackbar.showSnackbar("360 metadata may be lost in the copy.") }
            })
            return
        }
        ReviewState.Reviewing -> Unit
    }

    val pagerState = rememberPagerState(initialPage = 0) { current.size }
    val reduceMotion = rememberChatyReduceMotion()
    // MR-R1-049: swipe pager, kept in sync with the strip selection both ways.
    LaunchedEffect(pagerState.currentPage) { if (pagerState.currentPage != index && pagerState.currentPage in current.indices) index = pagerState.currentPage }
    LaunchedEffect(index) {
        if (pagerState.currentPage != index && index in 0 until current.size) {
            // MR-R2-037 / MR-R4-037: instant swaps when the system asks for reduced motion.
            if (reduceMotion) pagerState.scrollToPage(index) else pagerState.animateScrollToPage(index)
        }
    }

    // MR-R2-044: swipe-down to dismiss, with a spring back; the Cancel button always remains.
    val dismissOffset = remember { Animatable(0f) }
    // MR-R3-013: chrome defers to content during video play; any tap on the preview, strip or
    // info row restores it. Reduced-motion users keep the chrome (no surprise layout shifts).
    var chromeVisible by remember(item.stableId) { mutableStateOf(true) }
    LaunchedEffect(item.stableId, chromeVisible, reduceMotion) {
        if (item.mediaType == ReviewMediaType.VIDEO && chromeVisible && !reduceMotion) {
            delay(3000)
            chromeVisible = false
        }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
        .graphicsLayer {
            translationY = dismissOffset.value
            alpha = (1f - dismissOffset.value / 1200f).coerceIn(0.4f, 1f)
        }
        .pointerInput(sending) {
            detectVerticalDragGestures(
                onDragEnd = {
                    if (dismissOffset.value > 240f) requestCancel()
                    else scope.launch { if (reduceMotion) dismissOffset.snapTo(0f) else dismissOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                },
            ) { change, amount ->
                if (amount > 0f || dismissOffset.value > 0f) {
                    change.consume()
                    scope.launch { dismissOffset.snapTo((dismissOffset.value + amount).coerceAtLeast(0f)) }
                }
            }
        }
        // MR-R2-042: keyboard/DPAD operability. The focused caption field owns these keys first.
        .onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
            when {
                event.key == Key.DirectionLeft && index > 0 -> {
                    scope.launch { if (reduceMotion) pagerState.scrollToPage(index - 1) else pagerState.animateScrollToPage(index - 1) }
                    true
                }
                event.key == Key.DirectionRight && index < current.size - 1 -> {
                    scope.launch { if (reduceMotion) pagerState.scrollToPage(index + 1) else pagerState.animateScrollToPage(index + 1) }
                    true
                }
                event.key == Key.Enter && event.isCtrlPressed -> { attemptSend(); true }
                event.key == Key.Delete && !sending -> { removeItem(index); true }
                else -> false
            }
        }
        .focusable()
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
        .imePadding().navigationBarsPadding()) {
        // MR-R1-059: balanced header - Cancel and the trailing action row share a minimum width.
        if (chromeVisible) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.widthIn(min = 96.dp)) { TextButton(::requestCancel, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") } }
            Text("Review ${index + 1} of ${current.size}", color = MaterialTheme.colorScheme.onSurface, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite; contentDescription = "Attachment ${index + 1} of ${current.size}, ${item.type}" })
            Row(Modifier.widthIn(min = 96.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                // MR-R3-047: one source of truth for which types get copy-edit actions.
                if (item.mediaType == ReviewMediaType.VIDEO) TextButton({ state = ReviewState.Trimming(item) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Trim a copy") }
                if (item.isCopyEditable()) TextButton({ startEdit(item) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Edit") }
                // MR-R3-004: extra item actions live in a 48dp overflow, not a growing header.
                Box {
                    var overflow by remember { mutableStateOf(false) }
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text("More actions") } },
                        state = rememberTooltipState(),
                    ) {
                        IconButton(onClick = { overflow = true }, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.MoreVert, "More actions") }
                    }
                    DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                        if (item.mediaType == ReviewMediaType.VIDEO) DropdownMenuItem(
                            // MR-R1-051: mute produces a copy without audio tracks at send time.
                            text = { Text(if (item.stableId in mutedIds) "Unmute sound" else "Mute sound") },
                            leadingIcon = { Icon(if (item.stableId in mutedIds) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff, null) },
                            onClick = {
                                overflow = false
                                mutedIds = if (item.stableId in mutedIds) mutedIds - item.stableId else mutedIds + item.stableId
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Reorder attachments") },
                            leadingIcon = { Icon(Icons.Rounded.KeyboardArrowUp, null) },
                            enabled = current.size > 1,
                            onClick = { overflow = false; reorderDialog = true },
                        )
                    }
                }
            }
        }
        if (reorderDialog) AlertDialog(
            onDismissRequest = { reorderDialog = false },
            confirmButton = { TextButton(onClick = { reorderDialog = false }) { Text("Done") } },
            title = { Text("Reorder attachments") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    current.forEachIndexed { position, media ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("${position + 1}. ${media.file.name}", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            IconButton(onClick = { moveItem(position, position - 1) }, enabled = position > 0) { Icon(Icons.Rounded.KeyboardArrowUp, "Move ${media.file.name} earlier") }
                            IconButton(onClick = { moveItem(position, position + 1) }, enabled = position < current.size - 1) { Icon(Icons.Rounded.KeyboardArrowDown, "Move ${media.file.name} later") }
                        }
                    }
                }
            },
        )
        // MR-R2-030: the preview is its own composable keyed by item so a swipe does not
        // rebuild the whole review Column.
        // MR-R3-037: at 600dp+ the preview takes the remaining width and the controls dock
        // into a 320dp side column; below that the classic stacked layout is unchanged.
        @Composable
        fun pagerContent(modifier: Modifier) {
            HorizontalPager(state = pagerState, modifier = modifier, beyondViewportPageCount = 1) { page ->
                val pageItem = current.getOrNull(page) ?: return@HorizontalPager
                key(pageItem.stableId) {
                    ReviewPreview(pageItem, page, current.size, sending, ::removeItem,
                        onTap = { chromeVisible = true },
                        onEdit = if (pageItem.isCopyEditable()) ({ startEdit(pageItem) }) else null)
                }
            }
        }

        @Composable
        fun controlsContent() {
        Text(
            listOfNotNull(
                item.file.bytes?.size?.toLong()?.let { android.text.format.Formatter.formatFileSize(context, it) }
                    ?: item.file.path?.let { android.text.format.Formatter.formatFileSize(context, File(it).length()) },
                previewDurationMs?.let { "${it / 60000}:${"%02d".format(it / 1000 % 60)}" },
            ).joinToString(" · "),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.2.sp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).clickable { chromeVisible = true },
        )
        // MR-R4-018: large or very long videos carry an honest expectation chip.
        val itemBytes = item.file.bytes?.size?.toLong() ?: item.file.path?.let { File(it).length() } ?: 0L
        if (item.mediaType == ReviewMediaType.VIDEO && (itemBytes > LARGE_VIDEO_BYTES || (previewDurationMs ?: 0) > LONG_VIDEO_MS)) {
            Text("Large video. Sending may take a while.", color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2.sp))
        }
        if (hasLocation && stripLocation) {
            Text("Contains location. It will be removed from the sent copy.", color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2.sp))
        }
        // MR-R3-001: the strip docks directly above the caption row at 64dp.
        // MR-R4-035: the strip announces itself as a collection so position reads as 'n of m'.
        LazyRow(Modifier.fillMaxWidth().height(64.dp).semantics { collectionInfo = CollectionInfo(1, current.size) }, contentPadding = PaddingValues(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(current, key = { _, media -> media.stableId }) { i, media ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // MR-R4-038: keyboard focus gets a high-contrast ring on the black stage.
                    var thumbFocused by remember { mutableStateOf(false) }
                    var dragging by remember(media.stableId) { mutableStateOf(false) }
                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                        .onFocusChanged { thumbFocused = it.isFocused }
                        .graphicsLayer {
                            val scale = if (dragging) 1.08f else 1f
                            scaleX = scale; scaleY = scale
                        }
                        .pointerInput(media.stableId) {
                            // MR-R4-001: long-press drag reorders the batch; the dialog (MR-R5-030)
                            // remains the no-drag alternative. Reorder commits live per item step.
                            var accum = 0f
                            detectDragGesturesAfterLongPress(
                                onDragStart = { accum = 0f; dragging = true },
                                onDragEnd = { dragging = false },
                                onDragCancel = { dragging = false },
                            ) { change, drag ->
                                change.consume()
                                accum += drag.x
                                val stepPx = 64.dp.toPx()
                                var moved = true
                                while (moved) {
                                    moved = false
                                    val from = current.indexOfFirst { it.stableId == media.stableId }
                                    if (from < 0) break
                                    if (accum >= stepPx && from < current.size - 1) { moveItem(from, from + 1); accum -= stepPx; moved = true }
                                    else if (accum <= -stepPx && from > 0) { moveItem(from, from - 1); accum += stepPx; moved = true }
                                }
                            }
                        }
                        .then(if (thumbFocused) Modifier.border(1.dp, Color.Black, RoundedCornerShape(8.dp)).border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                            else Modifier.border(if (i == index) 2.dp else 1.dp, if (i == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)))
                        .clickable { index = i; chromeVisible = true }
                        .semantics { selected = i == index; role = Role.Button; contentDescription = "Attachment ${i + 1} of ${current.size}, ${media.type}, ${media.file.name}"; collectionItemInfo = CollectionItemInfo(0, 1, i, 1) }) {
                        if (media.mediaType == ReviewMediaType.IMAGE || media.mediaType == ReviewMediaType.GIF) AsyncImage(
                            model = coil.request.ImageRequest.Builder(context).data(media.source).size(192).build(),
                            contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                        else Text(if (media.mediaType == ReviewMediaType.VIDEO) "Video ${i + 1}" else "File ${i + 1}", modifier = Modifier.align(Alignment.Center), style = MaterialTheme.typography.labelSmall)
                        // MR-R4-011: animated items are identified as GIFs.
                        if (media.mediaType == ReviewMediaType.GIF) {
                            Text("GIF", color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.align(Alignment.BottomStart).background(MaterialTheme.colorScheme.primaryContainer).padding(2.dp))
                        }
                        if (uniqueItems.firstOrNull { it.stableId == media.stableId }?.source != media.source) {
                            Text("Edited", color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.align(Alignment.TopEnd).background(MaterialTheme.colorScheme.primaryContainer).padding(2.dp))
                        }
                    }
                    TextButton(onClick = { removeItem(i) }, enabled = !sending, modifier = Modifier.heightIn(min = 48.dp)) { Text("Remove ${i + 1}", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        if (item.stableId in viewOnceIds) Text("Requests view once. This build cannot prevent capture or enforce deletion on every recipient.", color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2.sp))
        sendError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp).semantics { liveRegion = LiveRegionMode.Polite }) }
        // MR-R3-016: the caption row sits on a tonal surface, not raw media.
        Surface(tonalElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val itemCaption = captions[item.stableId].orEmpty()
            OutlinedTextField(itemCaption, { captions = captions + (item.stableId to it.take(1024)) }, modifier = Modifier.weight(1f), placeholder = { Text(if (current.size > 1) "Caption this item" else "Add a caption") }, label = { Text("Caption") }, supportingText = { Text("${1024 - itemCaption.length} characters remaining", modifier = Modifier.semantics { if (itemCaption.length > 974) liveRegion = LiveRegionMode.Polite }) }, maxLines = 5,
                trailingIcon = {
                    // Per-item view-once toggle (MR-R1-003).
                    val itemViewOnce = item.stableId in viewOnceIds
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text("View once") } },
                        state = rememberTooltipState(),
                    ) {
                    IconToggleButton(itemViewOnce, { enabled ->
                        if (enabled && viewOnceNuxStore.needsSenderNux()) viewOnceSenderNux = true
                        viewOnceIds = if (enabled) viewOnceIds + item.stableId else viewOnceIds - item.stableId
                    }) { Icon(if (itemViewOnce) Icons.Rounded.CheckCircle else Icons.Rounded.LooksOne, if (itemViewOnce) "View once on" else "View once off", tint = if (itemViewOnce) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    if (viewOnceSenderNux) {
                        AlertDialog(
                            onDismissRequest = { viewOnceSenderNux = false; viewOnceNuxStore.markSenderSeen() },
                            confirmButton = {
                                TextButton(onClick = { viewOnceSenderNux = false; viewOnceNuxStore.markSenderSeen() }) { Text("Got it") }
                            },
                            title = { Text("View once") },
                            text = { Text("Recipients are asked to open this photo or video only once. It expires on their device after the first open unless their own settings keep opened items. People can still take screenshots or photos of the screen.") },
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface),
            )
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text("Send") } },
                state = rememberTooltipState(),
            ) {
            FilledIconButton(enabled = !sending && !submitted, onClick = { attemptSend() }, modifier = Modifier.padding(start = 8.dp).size(48.dp)) {
                com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.send, semanticLabel = "Send attachment")
            }
            }
        }
        }
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            if (maxWidth >= 600.dp) {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxHeight()) { pagerContent(Modifier.fillMaxSize()) }
                    Column(Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState())) { controlsContent() }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    pagerContent(Modifier.weight(1f).fillMaxWidth())
                    controlsContent()
                }
            }
        }
        SnackbarHost(snackbar)
    }
}

/** MR-R2-030: isolated preview so a pager swipe does not recompose the whole review screen. */
@Composable
private fun ReviewPreview(item: MediaReviewItem, position: Int, total: Int, sending: Boolean, onRemove: (Int) -> Unit, onTap: () -> Unit = {}, onEdit: (() -> Unit)? = null) {
    // MR-R3-045: TalkBack users get the same item actions as custom actions on the preview.
    Box(Modifier.fillMaxSize().pointerInput(item.stableId) { detectTapGestures { onTap() } }.semantics {
        customActions = listOfNotNull(
            onEdit?.let { edit -> CustomAccessibilityAction("Edit a copy") { edit(); true } },
            CustomAccessibilityAction("Remove attachment") { onRemove(position); true },
        )
    }, contentAlignment = Alignment.Center) {
        if (item.mediaType == ReviewMediaType.IMAGE || item.mediaType == ReviewMediaType.GIF) {
            MediaReviewImage(item.source, "Photo ${position + 1} of $total: ${item.file.name}", Modifier.fillMaxSize())
        } else androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx -> android.widget.VideoView(ctx).apply { contentDescription = "Video preview"; setMediaController(android.widget.MediaController(ctx).also { it.setAnchorView(this) }) } },
            update = { view -> if (view.tag != item.source) { view.tag = item.source; view.setVideoURI(android.net.Uri.parse(item.source)); view.setOnPreparedListener { view.seekTo(1) } } },
            modifier = Modifier.fillMaxSize(),
            onRelease = { it.stopPlayback() },
        )
    }
}

/** MR-R5-002: the review draft is a small JSON in cache and expires after 30 minutes. */
private const val REVIEW_DRAFT_MAX_AGE_MS = 30L * 60 * 1000
private fun reviewDraftFile(context: android.content.Context) = File(context.cacheDir, "chaty_review_draft.json")

private fun writeReviewDraft(context: android.content.Context, captions: Map<String, String>, viewOnceIds: Set<String>) {
    runCatching {
        val caps = org.json.JSONObject()
        captions.forEach { (k, v) -> caps.put(k, v) }
        val json = org.json.JSONObject()
        json.put("captions", caps)
        json.put("viewOnce", org.json.JSONArray(kotlin.collections.ArrayList(viewOnceIds)))
        reviewDraftFile(context).writeText(json.toString())
    }
}

private fun readReviewDraft(context: android.content.Context): Pair<Map<String, String>, Set<String>>? = runCatching {
    val file = reviewDraftFile(context)
    if (!file.isFile || System.currentTimeMillis() - file.lastModified() > REVIEW_DRAFT_MAX_AGE_MS) return null
    val json = org.json.JSONObject(file.readText())
    val capsJson = json.optJSONObject("captions")
    val caps = mutableMapOf<String, String>()
    if (capsJson != null) capsJson.keys().forEach { k -> caps[k] = capsJson.getString(k) }
    val arr = json.optJSONArray("viewOnce")
    val once = mutableSetOf<String>()
    if (arr != null) for (i in 0 until arr.length()) once += arr.getString(i)
    caps.toMap() to once.toSet()
}.getOrNull()

/** MR-R5-010: JPEG+MP4 motion photos carry an XMP GCamera marker in the first bytes. */
private fun isMotionPhoto(context: android.content.Context, item: MediaReviewItem): Boolean = runCatching {
    if (item.mediaType != ReviewMediaType.IMAGE) return false
    com.example.chat.ui.media.openMediaInput(context, item.source).use { input ->
        val head = String(input.readNBytes(256 * 1024), Charsets.US_ASCII)
        head.contains("GCamera") || head.contains("MotionPhoto")
    }
}.getOrDefault(false)

/** MR-R2-015: bounded re-encode of an unedited photo that drops every EXIF tag (incl. location).
 * Returns null when the image cannot be decoded; the caller then sends the original unchanged. */
private suspend fun stripExifCopy(context: android.content.Context, item: MediaReviewItem): File? = withContext(Dispatchers.Default) {
    runCatching {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        com.example.chat.ui.media.openMediaInput(context, item.source).use { android.graphics.BitmapFactory.decodeStream(it.buffered(), null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        // Decode bounded to 4096 px on the long edge; never upscale.
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) > 4096) sample *= 2
        val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = com.example.chat.ui.media.openMediaInput(context, item.source).use {
            android.graphics.BitmapFactory.decodeStream(it.buffered(), null, options)
        } ?: return@runCatching null
        try {
            val out = File(context.cacheDir, "chaty-outgoing-stripped-${java.util.UUID.randomUUID()}.jpg")
            out.outputStream().use { if (!bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, it)) return@runCatching null }
            out
        } finally { bitmap.recycle() }
    }.getOrNull()
}

/** MR-R3-047: single source of truth for which review types get copy-edit actions. */
private fun MediaReviewItem.isCopyEditable(): Boolean = mediaType == ReviewMediaType.IMAGE
