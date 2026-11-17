package com.example.chat.ui.chats.detail

import com.example.chat.ui.kit.chatyPredictiveBack

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.ui.semantics.*
import com.example.chat.ui.media.stageMediaSource
import com.example.chat.ui.media.copyMediaBounded
import com.example.chat.ui.media.openMediaInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import android.net.Uri
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.ui.geometry.Rect
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.ui.theme.ChatyAsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Port of lib/features/messages/media_viewer_screen.dart: signed-url
 *  resolution via ChatMediaService.createSignedUrl (3600s expiry), image
 *  viewer with InteractiveViewer zoom (0.7..5), video playback with tap
 *  toggle / play overlay / scrubbable progress, and the audio/document/file
 *  fallback card with open-externally + share actions.
 *
 *  Seams: [createSignedUrl] must be wired to ChatMediaService.createSignedUrl
 *  (integration owner); the default pass-through matches that method's
 *  http(s)/data: branches so direct URLs work without the service. The viewer now
 *  validates that result separately: HTTPS/content/app-directory files only; data/HTTP
 *  and arbitrary device files are rejected. Video
 *  uses android.widget.VideoView (no new Gradle dependency; media3 is not
 *  on the branch).
 *  MV-R1-041: video controls remain persistent, intentionally avoiding a timed escape
 *  or playback affordance. Any future auto-hide needs an assistive-technology alternative.
 *  MV-R2-050 / MV-R5-040: current owner/handoff table and known gaps live in
 *  docs/media-viewer-player-ownership.md; the table is not a new cleanup/security policy.
 *  MV-R3-031: accessibility decision: opening a video never starts playback; only the
 *  explicit toggle crosses deliverPlayback. Prepared media is nonlooping and remains pausable.
 *  MV-R4-033 known limitation: no described-video/audio-description track pipeline is
 *  provided by this viewer. Do not claim WCAG 1.2.5 support from ordinary audio playback.
 *
 *  MV-R1-020 privacy choice: private image requests below disable Coil memory and disk
 *  caches deliberately, trading repeat decode work for less cache retention. Do not
 *  enable caching here as a performance fix. A future public-content exception needs
 *  an explicit separate content classification, not a guessed URL or filename rule.
 *  This does not mean the viewer, leases or explicit exports never use private files.
 *
 *  MV-R1-016 diagnostic rule: signed URLs are bearer credentials. Never log the source,
 *  query, fragment, raw exception message or attachment path in analytics/crash extras.
 *  Use a fixed reason code and, only when needed, the hostname with no path or token.
 *  UI failures use fixed text. Do not uncomment the obsolete URL-forwarding code below.
 *
 *  MV-R4-045 viewer threat note: protect attachment bytes, leased/cache files, signed
 *  URLs, gallery writes and exported copies against hostile sources, stale consumers,
 *  unintended external disclosure and retention. Controls here include source checks,
 *  consumer identity isolation, explicit playback/actions, private image cache policy,
 *  bounded export copies and read-only FileProvider grants. Gallery export deliberately
 *  creates a user-visible copy outside private retention. Restricted view-once hides
 *  export actions and requests a protected window; that is not a guarantee against all
 *  capture. Native renderer redirects, provider behavior, decode safety, cleanup and
 *  owned-action callback enforcement require separate verification. This note claims
 *  neither backend revocation nor complete viewer/player security.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MediaViewerScreen(
    theme: ThemeConfig,
    title: String,
    type: String,
    size: String,
    storagePath: String? = null,
    modifier: Modifier = Modifier,
    createSignedUrl: suspend (objectPath: String, expiresInSeconds: Int) -> String = { path, _ -> path },
    onBack: () -> Unit = {},
    sourceBounds: Rect? = null,
    restrictedViewOnce: Boolean = false,
    providedModel: Any? = null,
    ownedActions: Boolean = false,
    onOwnedEdit: (() -> Unit)? = null,
    onOwnedShare: (() -> Unit)? = null,
    onOwnedOpen: (() -> Unit)? = null,
    onOwnedSaveToGallery: (() -> Unit)? = null,
    registerStopReader: (((() -> Unit)?) -> Unit)? = null,
    // Owned viewers must cross the same short account/epoch barrier as inline playback.
    deliverPlayback: ((() -> Unit) -> Boolean)? = null,
    ownedActionBusy: Boolean = false,
) {
    // MV-R2-020: replacing a consumer must dispose the previous reader, requests and copies.
    // A storage path alone is not the identity of a provided leased/edited model.
    key(storagePath, type, providedModel) {
    com.example.chat.ui.kit.ProtectSensitiveWindow(active = restrictedViewOnce)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val viewerActive = remember { java.util.concurrent.atomic.AtomicBoolean(true) }
    DisposableEffect(Unit) { onDispose { viewerActive.set(false) } }
    var providedSourceChecked by remember(providedModel, storagePath) { mutableStateOf(false) }
    // A provided source cannot reach Coil or an action while its path check is pending.
    val renderProvidedModel = if (providedSourceChecked) providedModel else null

    // Live policy also works on minSdk24; direct areAnimatorsEnabled() is API26+.
    val reduceMotion = com.example.chat.ui.kit.rememberChatyReduceMotion()
    val opening = remember(storagePath) { Animatable(if (reduceMotion) 1f else 0f) }
    var targetBounds by remember { mutableStateOf<Rect?>(null) }
    var reloadAttempt by remember(storagePath) { mutableIntStateOf(0) }
    // Previous readiness key: var imageReady by remember(storagePath) { mutableStateOf(false) }
    var imageReady by remember(storagePath, type, reloadAttempt, providedModel) { mutableStateOf(false) }
    val openingReady = type != "image" || imageReady
    LaunchedEffect(storagePath, reduceMotion, targetBounds != null, openingReady) {
        if (reduceMotion || sourceBounds == null) opening.snapTo(1f)
        else if (targetBounds != null && openingReady) opening.animateTo(1f, tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing))
    }
    var trimming by remember(storagePath) { mutableStateOf(false) }
    var editing by remember(storagePath) { mutableStateOf(false) }
    var editedCopy by remember(storagePath) { mutableStateOf<java.io.File?>(null) }
    // MV-R2-007: never display original byte size for a new derivative while stat is pending.
    var editedSizeLabel by remember(storagePath, editedCopy) { mutableStateOf<String?>(null) }
    LaunchedEffect(storagePath, editedCopy) {
        val copy = editedCopy
        editedSizeLabel = if (copy == null) null else withContext(Dispatchers.IO) {
            runCatching {
                if (copy.isFile) android.text.format.Formatter.formatFileSize(context, copy.length()) else null
            }.getOrNull()
        }
    }
    val displayMetadata = viewerDisplayMetadata(title, size, editedCopy != null, editedSizeLabel)
    val ownedCopies = remember(storagePath) { mutableListOf<java.io.File>() }
    DisposableEffect(storagePath) { onDispose { ownedCopies.forEach { it.delete() } } }
    var externalActionBusy by remember(storagePath) { mutableStateOf(false) }
    var savingToGallery by remember(storagePath) { mutableStateOf(false) }
    var trimSource by remember(storagePath) { mutableStateOf<String?>(null) }
    var error by remember(storagePath) { mutableStateOf<String?>(null) }
    val viewerErrorText = error?.let { message ->
        if (com.example.chat.ui.media.viewerRetryDelayMillis(reloadAttempt + 1) == null)
            "This attachment is still unavailable. Close and reopen it from the chat."
        else message
    }
    var signedUrl by remember(storagePath) { mutableStateOf<String?>(null) }

    // Video state (Dart VideoPlayerController value).
    var videoReady by remember(storagePath) { mutableStateOf(false) }
    var videoFailed by remember(storagePath) { mutableStateOf(false) }
    var isPlaying by remember(storagePath) { mutableStateOf(false) }
    // MV-R1-052 UI request state, never claims first frame/Media3 buffering.
    var videoPlayPending by remember(storagePath) { mutableStateOf(false) }
    var durationMs by remember(storagePath) { mutableIntStateOf(0) }
    var positionMs by remember(storagePath) { mutableIntStateOf(0) }
    var bufferedPct by remember(storagePath) { mutableIntStateOf(0) }
    var videoWidth by remember(storagePath) { mutableIntStateOf(0) }
    var videoHeight by remember(storagePath) { mutableIntStateOf(0) }
    var videoView by remember(storagePath) { mutableStateOf<VideoView?>(null) }
    // MV-R4-009: opt-in loop toggle for short videos, default OFF (previous fixed behavior).
    // videoMediaPlayer is BORROWED: VideoView owns the player and may release it on surface
    // teardown or stopPlayback; we never release, stop, or re-prepare it. A toggle landing
    // after teardown is deferred to the next onPrepared, which re-reads loopVideo.
    var loopVideo by remember(storagePath) { mutableStateOf(false) }
    var videoMediaPlayer by remember(storagePath) { mutableStateOf<android.media.MediaPlayer?>(null) }
    val loopVideoStateOn = androidx.compose.ui.res.stringResource(com.example.chat.R.string.media_viewer_loop_state_on)
    val loopVideoStateOff = androidx.compose.ui.res.stringResource(com.example.chat.R.string.media_viewer_loop_state_off)
    // MV-R1-051: UI gesture feedback only, never auto-starts a paused reader.
    var seekGestureDirection by remember(storagePath) { mutableIntStateOf(0) }
    var seekGestureSequence by remember(storagePath) { mutableIntStateOf(0) }
    val seekRipple = remember(storagePath) { Animatable(0f) }
    LaunchedEffect(storagePath, seekGestureSequence, reduceMotion) {
        if (seekGestureSequence > 0) {
            seekRipple.snapTo(if (reduceMotion) 1f else 0f)
            if (!reduceMotion) seekRipple.animateTo(1f, tween(400))
            delay(if (reduceMotion) 800L else 400L)
            seekGestureDirection = 0
        }
    }

    fun stopVideoReader(reader: VideoView? = videoView): Boolean {
        // A failed native stop must not erase the only reference available to retry.
        val stopped = reader == null || runCatching { reader.stopPlayback() }.isSuccess ||
            runCatching { reader.suspend() }.isSuccess
        if (videoView === reader) {
            isPlaying = false; videoPlayPending = false; videoReady = false
            if (stopped) {
                videoView = null
                videoMediaPlayer = null
                videoFailed = false
                durationMs = 0; positionMs = 0; bufferedPct = 0
                videoWidth = 0; videoHeight = 0
            } else {
                videoFailed = true
                error = "Could not stop this attachment. Close and reopen it before playing another copy."
            }
        }
        return stopped
    }
    androidx.compose.runtime.SideEffect {
        registerStopReader?.invoke { check(stopVideoReader()) { "Attachment reader cleanup failed" } }
    }
    // Retry state declared before image readiness so every attempt starts unready.
    // Dart _load.
    LaunchedEffect(storagePath, type, reloadAttempt, providedModel) {
        if (!stopVideoReader()) return@LaunchedEffect // Do not replace a reader whose teardown failed.
        providedSourceChecked = false
        error = null
        signedUrl = null
        videoReady = false
        videoFailed = false
        isPlaying = false
        videoPlayPending = false
        val retryDelay = com.example.chat.ui.media.viewerRetryDelayMillis(reloadAttempt)
        if (retryDelay == null) {
            error = "This attachment is still unavailable. Close and reopen it from the chat."
            return@LaunchedEffect
        }
        // Explicit user retries only. No automatic network retry loop.
        if (retryDelay > 0) delay(retryDelay)
        val modelAllowed = withContext(Dispatchers.IO) {
            val roots = listOf(context.cacheDir, context.filesDir, context.noBackupFilesDir)
            when (val model = providedModel) {
                null -> true
                is ByteArray -> model.isNotEmpty()
                is java.io.File -> com.example.chat.ui.media.isAllowedViewerSource(model.toURI().toString(), roots)
                is Uri -> com.example.chat.ui.media.isAllowedViewerSource(model.toString(), roots)
                is String -> com.example.chat.ui.media.isAllowedViewerSource(model, roots)
                else -> false
            }
        }
        if (!modelAllowed) {
            error = "Could not load this attachment. Reopen it from the chat."
            return@LaunchedEffect
        }
        providedSourceChecked = true
        // Leased image bytes are already the consumer model; the placeholder is not a URL.
        if (providedModel != null) {
            signedUrl = when (providedModel) {
                is java.io.File -> providedModel.toURI().toString()
                is Uri -> providedModel.toString()
                is String -> providedModel
                else -> null
            }
            if (signedUrl == null && type != "image") error = "Could not load this attachment. Reopen it from the chat."
            return@LaunchedEffect
        }
        val path = storagePath
        if (path.isNullOrEmpty()) {
            // MV-R5-031 previous jargon: error = "This attachment does not have a valid storage path."
            error = "This attachment is unavailable. Try reopening it from the chat."
            return@LaunchedEffect
        }
        try {
            // Dart: video keeps the spinner until controller.initialize()
            // resolves; the video branch overlays its own spinner until
            // onPrepared fires.
            // MV-R5-035 previous: signedUrl = createSignedUrl(path, 3600)
            // MV-R2-020 prior: signedUrl = kotlinx.coroutines.withTimeout(30_000L) { createSignedUrl(path, 3600) }
            val resolvedSource = kotlinx.coroutines.withTimeout(30_000L) { createSignedUrl(path, 3600) }
            val allowedSource = withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.example.chat.ui.media.isAllowedViewerSource(resolvedSource, listOf(context.cacheDir, context.filesDir, context.noBackupFilesDir))
            }
            require(allowedSource) { "Unsupported attachment source" }
            signedUrl = resolvedSource
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            error = "This attachment is taking too long to load. Retry or close and try again."
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = "Could not load this attachment. Check your connection and reopen it." }
    }

    // MV-R5-035: whole visual-load deadline also covers image decode/video preparation.
    // Read current readiness after waiting, not a captured initial Boolean.
    val attachmentLoadFinished by androidx.compose.runtime.rememberUpdatedState(
        error != null || videoFailed || when (type) {
            "image" -> imageReady
            "video" -> videoReady
            else -> signedUrl != null || renderProvidedModel != null
        },
    )
    LaunchedEffect(storagePath, type, reloadAttempt, providedModel) {
        delay(30_000L)
        if (!attachmentLoadFinished) {
            error = "This attachment is taking too long to load. Retry or close and try again."
        }
    }

    val mediaLifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var mediaForeground by remember(mediaLifecycleOwner) {
        mutableStateOf(mediaLifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))
    }
    DisposableEffect(mediaLifecycleOwner, videoView) {
        val reader = videoView
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            mediaForeground = mediaLifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                runCatching { reader?.pause() }
                isPlaying = false
                videoPlayPending = false
            }
        }
        mediaLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { mediaLifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(videoView, isPlaying, mediaForeground) {
        val reader = videoView
        reader?.keepScreenOn = isPlaying && mediaForeground
        onDispose { reader?.keepScreenOn = false }
    }
    // Poll playback position/state (Dart controller value listener).
    // MV-R1-052 previous keys omitted explicit play-pending state.
    LaunchedEffect(videoView, videoReady, mediaForeground, videoPlayPending) {
        val vv = videoView
        if (vv == null || !videoReady || !mediaForeground) return@LaunchedEffect
        while (true) {
            positionMs = vv.currentPosition
            bufferedPct = vv.bufferPercentage
            isPlaying = vv.isPlaying
            if (isPlaying) videoPlayPending = false
            // Prior idle delay: delay(if (isPlaying) 250 else 1_000)
            delay(if (isPlaying || videoPlayPending) 250 else 1_000)
        }
    }

    DisposableEffect(videoView) {
        // Capture this effect's reader. Reading delegated state from onDispose can
        // stop the NEW view when the initial null-key effect is replaced.
        val reader = videoView
        onDispose {
            stopVideoReader(reader)
        }
    }

    // Old URL forwarding implementation retained below, disabled because signed URLs are bearer tokens.
//
//     // Dart _openExternally (url_launcher externalApplication).
//     fun openExternally() {
//         if (restrictedViewOnce || error != null) return
//         if (ownedActions) { onOwnedOpen?.invoke(); return }
//         val url = signedUrl ?: return
//         try {
//             val local = editedCopy
//             val intent = if (local != null) {
//                 val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", local)
//                 Intent(Intent.ACTION_VIEW).setDataAndType(uri, when (local.extension) {
//                     "mp4" -> "video/mp4"; "m4a" -> "audio/mp4"; else -> "image/jpeg"
//                 }).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
//             } else Intent(Intent.ACTION_VIEW, Uri.parse(url))
//             context.startActivity(
//                 intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
//             )
//         } catch (e: ActivityNotFoundException) {
//             scope.launch {
//                 snackbarHostState.showSnackbar("No application could open this attachment.")
//             }
//         }
//     }
//
//     // Dart _share (share_plus text + subject).
//     fun share() {
//         if (restrictedViewOnce || error != null) return
//         if (ownedActions) { onOwnedShare?.invoke(); return }
//         val local = editedCopy
//         if (local != null) {
//             val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", local)
//             context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
//                 this.type = when { local.extension == "mp4" -> "video/mp4"; local.extension == "m4a" -> "audio/mp4"; else -> "image/jpeg" }; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
//             }, "Share edited copy"))
//             return
//         }
//         val url = signedUrl ?: return
//         val send = Intent(Intent.ACTION_SEND).apply {
//             this.type = "text/plain"
//             putExtra(Intent.EXTRA_TEXT, url)
//             putExtra(Intent.EXTRA_SUBJECT, title)
//         }
//         try {
//             context.startActivity(
//                 Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
//             )
//         } catch (e: ActivityNotFoundException) {
//             // share_plus silently no-ops without a target; keep parity.
//         }
//     }
//
//
    // MV-R5-014: an explicit download gets a fresh token, not the one retained since open.
    // Local derivatives/leases are already snapshots and must not be replaced by originals.
    suspend fun freshDownloadSource(source: String): String {
        if (providedModel != null || !source.startsWith("https:", ignoreCase = true)) return source
        val originalPath = storagePath?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Attachment source unavailable")
        val refreshed = try {
            kotlinx.coroutines.withTimeout(30_000L) { createSignedUrl(originalPath, 3600) }
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            throw IllegalStateException("Attachment refresh timed out")
        }
        val allowed = withContext(Dispatchers.IO) {
            com.example.chat.ui.media.isAllowedViewerSource(refreshed,
                listOf(context.cacheDir, context.filesDir, context.noBackupFilesDir))
        }
        require(allowed) { "Unsupported attachment source" }
        return refreshed
    }

    val shareAvailable = !ownedActions || onOwnedShare != null
    val openAvailable = !ownedActions || onOwnedOpen != null
    val editAvailable = !ownedActions || onOwnedEdit != null
    fun launchExternal(share: Boolean) {
        if (if (share) !shareAvailable else !openAvailable) return
        if (restrictedViewOnce || error != null || externalActionBusy || savingToGallery || ownedActionBusy || !viewerActive.get()) return
        if (ownedActions) { if (share) onOwnedShare?.invoke() else onOwnedOpen?.invoke(); return }
        val source = editedCopy?.toURI()?.toString()
            ?: (renderProvidedModel as? java.io.File)?.toURI()?.toString()
            ?: signedUrl ?: return
        externalActionBusy = true
        scope.launch {
            var export: java.io.File? = null
            var handedOff = false
            try {
                // Previous stale-token route: export = stageMediaSource(context, source, "chaty_shared_")
                export = stageMediaSource(context, freshDownloadSource(source), "chaty_shared_")
                coroutineContext.ensureActive()
                val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", export!!)
                val classification = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.example.chat.ui.media.FileTypeDetector.classifyFile(
                        headerBytesOf = { export!!.inputStream().use { input ->
                            val bytes = ByteArray(32); val count = input.read(bytes)
                            if (count > 0) bytes.copyOf(count) else byteArrayOf()
                        } }, filePath = export!!.path)
                }
                val mime = classification.mimeType.takeUnless { it == "application/octet-stream" }
                    ?: when (type) { "image" -> "image/*"; "video" -> "video/*"; else -> "application/octet-stream" }
                val intent = Intent(if (share) Intent.ACTION_SEND else Intent.ACTION_VIEW).apply {
                    if (share) { this.type = mime; putExtra(Intent.EXTRA_STREAM, uri) } else setDataAndType(uri, mime)
                    clipData = android.content.ClipData.newRawUri("Attachment", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, if (share) "Share attachment" else "Open attachment"))
                // External recipients may read later. Keep the exported snapshot for normal cache expiry.
                handedOff = true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { snackbarHostState.showSnackbar("Could not prepare this attachment. Check access and the 100 MB file limit, then try again.") }
            finally { if (!handedOff) export?.delete(); externalActionBusy = false }
        }
    }
    // MV-R4-025: until a trusted per-type allowlist exists, warn for EVERY external open.
    // This covers owned callbacks too; a filename or declared media type is not proof of safety.
    var externalOpenConfirmation by remember(storagePath) { mutableStateOf(false) }
    // Previous direct handoff: fun openExternally() = launchExternal(false)
    fun openExternally() {
        if (openAvailable && !restrictedViewOnce && error == null && !externalActionBusy && !savingToGallery && !ownedActionBusy &&
            (signedUrl != null || renderProvidedModel != null)) externalOpenConfirmation = true
    }
    fun share() = launchExternal(true)
    if (externalOpenConfirmation && !restrictedViewOnce && error == null) androidx.compose.material3.AlertDialog(
        onDismissRequest = { externalOpenConfirmation = false },
        title = { Text("Open in another app?") },
        text = { Text("The app you choose can read and may keep this attachment. Some file types can run active content in that app. Open only if you trust the attachment and the app.") },
        confirmButton = { TextButton(onClick = {
            externalOpenConfirmation = false
            launchExternal(false)
        }) { Text("Continue to app chooser") } },
        dismissButton = { TextButton(onClick = { externalOpenConfirmation = false }) { Text("Cancel") } },
    )

    // Dart _toggleVideo.
    // GB media-preview option: save the viewed image/video to the device gallery.
    var galleryConfirmation by remember(storagePath) { mutableStateOf(false) }
    val gallerySaveAvailable = !ownedActions || onOwnedSaveToGallery != null
    fun performGallerySave() {
        if (restrictedViewOnce || error != null || savingToGallery || externalActionBusy || ownedActionBusy || !viewerActive.get() || !gallerySaveAvailable) return
        if (ownedActions) { onOwnedSaveToGallery?.invoke(); return }
        val source = editedCopy?.absolutePath
            ?: (renderProvidedModel as? java.io.File)?.absolutePath
            ?: signedUrl
            ?: return
        savingToGallery = true
        scope.launch {
            try {
                val message = try {
                    val refreshed = freshDownloadSource(source)
                    withContext(kotlinx.coroutines.Dispatchers.IO) {
                        saveMediaToGallery(context, refreshed, title, type == "video", viewerActive::get)
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { "Could not save attachment. Try again." }
                snackbarHostState.showSnackbar(message)
            } finally { savingToGallery = false }
        }
    }
    fun saveToGallery() {
        if (restrictedViewOnce || error != null || savingToGallery || externalActionBusy || ownedActionBusy || !gallerySaveAvailable) return
        val hidden = runCatching {
            val prefs = com.example.chat.ServiceLocator.preferencesController.universal
            when {
                type == "video" -> prefs.hideVideosFromGallery
                title.endsWith(".gif", true) -> prefs.hideGifsFromGallery
                else -> prefs.hidePhotosFromGallery
            }
        }.getOrDefault(true)
        if (hidden) galleryConfirmation = true else performGallerySave()
    }
    if (galleryConfirmation && !restrictedViewOnce) androidx.compose.material3.AlertDialog(
        onDismissRequest = { galleryConfirmation = false },
        title = { Text("Save a visible gallery copy?") },
        text = { Text("Hide media is enabled for this media type. Save to gallery creates a shared copy visible in your phone gallery and to other apps with gallery access. The original stays in Chaty.") },
        confirmButton = { TextButton(onClick = { galleryConfirmation = false; performGallerySave() }) { Text("Save visible copy") } },
        dismissButton = { TextButton(onClick = { galleryConfirmation = false }) { Text("Cancel") } },
    )

    fun toggleVideo() {
        val vv = videoView ?: return
        if (!videoReady || !mediaLifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) return
        if (vv.isPlaying) {
            videoPlayPending = false
            vv.pause()
        } else {
            videoPlayPending = true
            val start = { vv.start() }
            val accepted = deliverPlayback?.invoke(start) ?: if (ownedActions) false else { start(); true }
            if (!accepted) {
                vv.stopPlayback()
                videoReady = false
                videoPlayPending = false
                error = "Attachment session changed. Reopen it from the chat."
                return
            }
        }
        isPlaying = vv.isPlaying
        if (isPlaying) videoPlayPending = false
    }

    var closing by remember(storagePath) { mutableStateOf(false) }
    fun toggleLoopVideo() {
        loopVideo = !loopVideo
        if (!videoReady) return
        val mp = videoMediaPlayer ?: return
        try {
            mp.isLooping = loopVideo
        } catch (expected: IllegalStateException) {
            // VideoView released the player behind the borrowed reference; drop it. The flag
            // persists in loopVideo and is applied by the next onPrepared. Only the expected
            // stale-reference failure is caught; anything else still fails loudly.
            videoMediaPlayer = null
        }
    }
    fun closeViewer() {
        if (closing) return
        closing = true
        scope.launch {
            if (!reduceMotion && sourceBounds != null && openingReady) opening.animateTo(0f, tween(220))
            onBack()
        }
    }
    val predictiveBack = com.example.chat.ui.kit.rememberChatyBackPresentation(storagePath)
    // Previous registration retained inactive: androidx.activity.compose.BackHandler { closeViewer() }
    com.example.chat.ui.kit.ChatyPredictiveBackHandler(presentation = predictiveBack) { closeViewer() }
    val elevated = Color(theme.cardColor)
    val foreground = Color(theme.primaryTextColor)
    val foregroundSecondary = Color(theme.secondaryTextColor)
    val primary = Color(theme.accentColor)
    val borderSubtle = Color(theme.dividerColor)

    LaunchedEffect(trimming, storagePath) {
        if (trimming) {
            val source = editedCopy?.toURI()?.toString() ?: (renderProvidedModel as? java.io.File)?.toURI()?.toString() ?: signedUrl
            if (source != null) try {
                val copy = stageMediaSource(context, source, "chaty_trim_source_")
                ownedCopies += copy
                trimSource = copy.toURI().toString()
            // Previous failure text: Could not prepare a local trim copy.
            } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { trimming = false; snackbarHostState.showSnackbar("Could not prepare this attachment for trimming. Reopen it from the chat and try again.") }
        } else trimSource = null
    }
    if (trimming && !restrictedViewOnce) {
        androidx.compose.runtime.SideEffect { stopVideoReader() }
        val localTrimSource = trimSource
        if (localTrimSource == null) {
            Surface(Modifier.fillMaxSize()) { Column(Modifier.safeDrawingPadding().padding(24.dp)) {
                CircularProgressIndicator(); Text("Preparing local trim copy")
                TextButton(onClick = { trimming = false }) { Text("Cancel") }
            } }
            return
        }
        MediaTrimScreen(localTrimSource, type == "video",
            onCancel = { trimming = false },
            onApply = { result -> ownedCopies += result.file; editedCopy = result.file; signedUrl = result.file.toURI().toString(); trimming = false },
        )
        return
    }
    if (editing && signedUrl != null && !restrictedViewOnce) {
        androidx.compose.runtime.SideEffect { stopVideoReader() }
        ImageEditorScreen(editedCopy?.toURI()?.toString() ?: signedUrl!!,
            onCancel = { editing = false },
            onApply = { file -> ownedCopies += file; editedCopy = file; editing = false },
        )
        return
    }
    if (error != null) androidx.compose.material3.AlertDialog(
        onDismissRequest = { closeViewer() },
        title = { Text("Attachment unavailable") },
        // Previous unbounded action: TextButton(onClick = { reloadAttempt++ }) { Text("Retry") }
        text = { Text(viewerErrorText!!) },
        confirmButton = {
            if (com.example.chat.ui.media.viewerRetryDelayMillis(reloadAttempt + 1) != null) {
                TextButton(onClick = {
                    if (com.example.chat.ui.media.viewerRetryDelayMillis(reloadAttempt + 1) != null) reloadAttempt++
                }) { Text("Retry") }
            }
        },
        dismissButton = { TextButton(onClick = { closeViewer() }) { Text("Close") } },
    )
    if (externalActionBusy || savingToGallery || ownedActionBusy) androidx.compose.material3.AlertDialog(
        onDismissRequest = {},
        title = { Text(if (savingToGallery) "Saving gallery copy" else "Preparing attachment") },
        text = { CircularProgressIndicator() },
        confirmButton = {},
    )
    var keyboardHelp by remember(storagePath) { mutableStateOf(false) }
    if (keyboardHelp) androidx.compose.material3.AlertDialog(
        onDismissRequest = { keyboardHelp = false },
        title = { Text("Viewer keyboard shortcuts") },
        text = { Text("Space: play or pause video\nLeft/Right: seek video 5 seconds\nEscape: close viewer\n?: show these shortcuts\nFocused controls keep their own keys.") },
        confirmButton = { TextButton(onClick = { keyboardHelp = false }) { Text("Close shortcuts") } },
    )
    // MV-R2-045: measure the actual host window, preserving the caller modifier outside Scaffold.
    BoxWithConstraints(modifier.chatyPredictiveBack(predictiveBack)) {
    val wideActionRail = viewerUsesActionRail(maxWidth.value)
    // MV-R4-037: overflow menu state hoisted from the top bar so an image long-press can open
    // the same menu; the compact classification of the same window width preserves reset semantics.
    var actionsExpanded by remember(storagePath, viewerUsesCompactToolbar(maxWidth.value)) { mutableStateOf(false) }
    Scaffold(
        // MV-R2-036 previous root: modifier = modifier,
        // Bubble phase lets focused buttons/sliders handle their own keys first.
        // MV-R2-045 prior modifier = modifier.onKeyEvent { event ->
        modifier = Modifier.onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown || keyboardHelp || externalActionBusy || savingToGallery || ownedActionBusy || error != null) false
            else when {
                event.key == Key.Slash && event.isShiftPressed -> { keyboardHelp = true; true }
                event.key == Key.Escape -> { closeViewer(); true }
                type == "video" && !restrictedViewOnce && videoReady && event.key == Key.Spacebar -> { toggleVideo(); true }
                type == "video" && !restrictedViewOnce && videoReady && durationMs > 0 &&
                    (event.key == Key.DirectionLeft || event.key == Key.DirectionRight) -> {
                    val target = viewerKeyboardSeek(positionMs, durationMs, event.key == Key.DirectionRight)
                    videoView?.seekTo(target)
                    positionMs = target
                    true
                }
                else -> false
            }
        }.focusable(),
        containerColor = elevated,
        // MV-R5-045 original: snackbarHost = { SnackbarHost(snackbarHostState) },
        snackbarHost = { MediaReviewStatusHost(snackbarHostState) },
        topBar = {
            // MV-R1-049/MV-R5-029: measure available window, not device screen width.
            BoxWithConstraints {
            val compactToolbar = viewerUsesCompactToolbar(maxWidth.value)
            // MV-R4-037 hoisted: var actionsExpanded by remember(storagePath, compactToolbar) { mutableStateOf(false) }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .background(elevated.copy(alpha = 0.85f))
                    // MV-R2-044: strengthen the top edge with the existing theme surface.
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(elevated.copy(alpha = 0.60f), elevated.copy(alpha = 0f)),
                    ))
                    .padding(start = 4.dp, end = 4.dp),
            ) {
                // MV-R5-024 existing node/action retained, tooltip shares exact semantic text.
                com.example.chat.ui.media.AudioPlayerTooltip("Back") {
                IconButton(onClick = ::closeViewer) {
                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.back,
                        semanticLabel = "Back",
                        color = foreground,
                    )
                }
                }
                if (!compactToolbar) Column(Modifier.widthIn(min = 96.dp, max = 240.dp)) {
                    Text(
                        // MV-R2-007 previous: title,
                        displayMetadata.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = foreground,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.W600,
                    )
                    Text(
                        // MV-R2-007 previous: size,
                        displayMetadata.size,
                        color = foregroundSecondary,
                        fontSize = 12.sp,
                    )
                }
                else Spacer(Modifier.width(8.dp))
                // MV-R1-049: primary actions remain Save/Share; editing and external Open live in overflow.
                // Existing handlers/readiness/restrictions remain the source of each action's behavior.
                val actionsVisible = !restrictedViewOnce && error == null
                val actionsReady = (signedUrl != null || renderProvidedModel != null) &&
                    (ownedActions || signedUrl != null || renderProvidedModel is java.io.File) &&
                    !externalActionBusy && !savingToGallery && !ownedActionBusy
                // MV-R2-045 previous: if (actionsVisible && !compactToolbar) {
                if (actionsVisible && !compactToolbar && !wideActionRail) {
                    // MV-R5-024 prior direct icon buttons retained within tooltip wrappers.
                    if (type in setOf("image", "video")) com.example.chat.ui.media.AudioPlayerTooltip("Save to gallery") {
                        IconButton(onClick = ::saveToGallery, enabled = actionsReady && gallerySaveAvailable) {
                            com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.download,
                                semanticLabel = "Save to gallery", color = foreground)
                        }
                    }
                    com.example.chat.ui.media.AudioPlayerTooltip("Share attachment") {
                        IconButton(onClick = ::share, enabled = actionsReady && shareAvailable) {
                            com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.share,
                                semanticLabel = "Share attachment", color = foreground)
                        }
                    }
                    // MV-R5-024 prior direct buttons retained inactive:
                    // if (type in setOf("image", "video")) IconButton(onClick = ::saveToGallery, enabled = actionsReady) {
                    //     // MV-R4-048 previous: Icon(Icons.Rounded.Download, contentDescription = "Save to gallery", tint = foreground)
                    //     com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.download,
                    //         semanticLabel = "Save to gallery", color = foreground)
                    // }
                    // IconButton(onClick = ::share, enabled = actionsReady) {
                    //     com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.share,
                    //         semanticLabel = "Share attachment", color = foreground)
                    // }

                }
                // MV-R2-045 previous: if (actionsVisible) Box {
                // MV-R4-037: the existing menu also composes in wide mode so an image long-press
                // can open it there; the More icon itself stays hidden while the rail shows actions.
                // MV-R4-037 prior: if (actionsVisible && !wideActionRail) Box {
                if (actionsVisible) Box {
                    if (!wideActionRail) {
                    com.example.chat.ui.media.AudioPlayerTooltip("More media actions") {
                    IconButton(onClick = { actionsExpanded = true }) {

//Icon(Icons.Rounded.MoreVert, contentDescription = "More media actions", tint = foreground)
                        com.example.chat.ui.icons.ChatyPurposeIcon(com.example.chat.ui.icons.ChatyIconId.moreOptions, Icons.Rounded.MoreVert, contentDescription = "More media actions", tint = foreground)
                    }
                    }
                    }
                    DropdownMenu(expanded = actionsExpanded, onDismissRequest = { actionsExpanded = false }) {
                        if (type in setOf("video", "audio")) DropdownMenuItem(
                            text = { Text("Trim a copy") }, modifier = Modifier.heightIn(min = 48.dp), enabled = actionsReady && editAvailable,
                            onClick = { actionsExpanded = false; if (ownedActions) onOwnedEdit?.invoke() else if (stopVideoReader()) trimming = true },
                        )
                        if (type == "video") DropdownMenuItem(
                            text = { Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.media_viewer_loop_video)) },
                            modifier = Modifier.heightIn(min = 48.dp).semantics {
                                stateDescription = if (loopVideo) loopVideoStateOn else loopVideoStateOff
                            },
                            enabled = actionsReady && videoReady && !videoFailed,
                            onClick = { actionsExpanded = false; toggleLoopVideo() },
                        )
                        if (type == "image" && !title.endsWith(".gif", ignoreCase = true)) DropdownMenuItem(
                            text = { Text("Edit a copy") }, modifier = Modifier.heightIn(min = 48.dp), enabled = actionsReady && editAvailable,
                            onClick = { actionsExpanded = false; if (ownedActions) onOwnedEdit?.invoke() else if (stopVideoReader()) editing = true },
                        )
                        DropdownMenuItem(text = { Text("Open with app") }, modifier = Modifier.heightIn(min = 48.dp),
                            enabled = actionsReady && openAvailable, onClick = { actionsExpanded = false; openExternally() })
                        if (compactToolbar) {
                            if (type in setOf("image", "video")) DropdownMenuItem(
                                text = { Text("Save to gallery") }, modifier = Modifier.heightIn(min = 48.dp), enabled = actionsReady && gallerySaveAvailable,
                                onClick = { actionsExpanded = false; saveToGallery() },
                            )
                            DropdownMenuItem(text = { Text("Share attachment") }, modifier = Modifier.heightIn(min = 48.dp),
                                enabled = actionsReady && shareAvailable, onClick = { actionsExpanded = false; share() })
                        }
                    }
                }
                // Prior complete action block retained inactive:
                // // MV-R3-022 original: if (type in setOf("video", "audio") && !restrictedViewOnce && error == null) TextButton(onClick = { if (ownedActions) onOwnedEdit?.invoke() else if (stopVideoReader()) trimming = true }, enabled = signedUrl != null || renderProvidedModel != null) { Text("Trim") }
                // // MR-R3-042 previous visible label: if (type in setOf("video", "audio") && !restrictedViewOnce && error == null) TextButton(modifier = Modifier.heightIn(min = 48.dp), onClick = { if (ownedActions) onOwnedEdit?.invoke() else if (stopVideoReader()) trimming = true }, enabled = signedUrl != null || renderProvidedModel != null) { Text("Trim") }
                // if (type in setOf("video", "audio") && !restrictedViewOnce && error == null) TextButton(modifier = Modifier.heightIn(min = 48.dp), onClick = { if (ownedActions) onOwnedEdit?.invoke() else if (stopVideoReader()) trimming = true }, enabled = signedUrl != null || renderProvidedModel != null) { Text("Trim a copy") }
                // if (type == "image" && !title.endsWith(".gif", ignoreCase = true) && !restrictedViewOnce && error == null) IconButton(onClick = { if (ownedActions) onOwnedEdit?.invoke() else if (stopVideoReader()) editing = true }, enabled = signedUrl != null || renderProvidedModel != null) {
                // com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.edit, semanticLabel = "Edit a copy", color = foreground)
                // }
                // if (!restrictedViewOnce && error == null) IconButton(onClick = ::openExternally, enabled = signedUrl != null || renderProvidedModel != null) {
                // com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.openInNew,
                // semanticLabel = "Open with app",
                // color = foreground,
                // )
                // }
                // if (type in setOf("image", "video") && !restrictedViewOnce && error == null) IconButton(onClick = ::saveToGallery, enabled = signedUrl != null || renderProvidedModel != null) {
                // Icon(
                // Icons.Rounded.Download,
                // contentDescription = "Save to gallery",
                // tint = foreground,
                // )
                // }
                // if (!restrictedViewOnce && error == null) IconButton(onClick = ::share, enabled = signedUrl != null || renderProvidedModel != null) {
                // com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.share,
                // semanticLabel = "Share attachment",
                // color = foreground,
                // )
                // }
                Spacer(Modifier.width(4.dp)) // ChatySpacing.xs
            }
            }
        },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
        Box(
            Modifier
                // MV-R2-045 previous: .fillMaxSize().padding(padding)
                .weight(1f).fillMaxHeight()
                // MV-R5-044 original: .onGloballyPositioned { targetBounds = it.boundsInWindow() },
                .onGloballyPositioned {
                    val measured = it.boundsInWindow()
                    if (viewerBoundsChanged(targetBounds, measured)) targetBounds = measured
                },
            contentAlignment = Alignment.Center,
        ) {
        Box(Modifier.fillMaxSize().graphicsLayer {
                    val source = sourceBounds
                    val target = targetBounds
                    val transform = if (source != null && target != null && openingReady) mediaOpeningTransform(
                        source.width, source.center.x, source.center.y, target.width,
                        target.center.x, target.center.y, opening.value) else MediaOpeningTransform(1f, 0f, 0f)
                    scaleX = transform.scale
                    scaleY = transform.scale
                    translationX = transform.x
                    translationY = transform.y
                },
            contentAlignment = Alignment.Center,
        ) {
            when {
                // MV-R1-050 prior error Text was not in a downward-dismiss region.
                error != null -> Box(
                    Modifier.fillMaxSize().then(if (!restrictedViewOnce) Modifier.viewerImageDismissGesture(
                        identity = "error:$storagePath:$reloadAttempt",
                        reduceMotion = reduceMotion,
                        canDismiss = { !closing }, onDismiss = ::closeViewer,
                    ) else Modifier), contentAlignment = Alignment.Center,
                ) { Text(
                    viewerErrorText!!,
                    textAlign = TextAlign.Center,
                    color = foregroundSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(20.dp), // ChatySpacing.lg
                )

                } // Error gesture region, no action controls.

                // A provided image model can render without a URL. VideoView cannot:
                // its URI is resolved asynchronously, even for a leased local file.
                // MV-R1-050 previous loading branch was only CircularProgressIndicator.
                signedUrl == null && (type == "video" || renderProvidedModel == null) -> Box(
                    Modifier.fillMaxSize().then(if (!restrictedViewOnce) Modifier.viewerImageDismissGesture(
                        identity = "loading:$storagePath:$reloadAttempt",
                        reduceMotion = reduceMotion,
                        canDismiss = { !closing }, onDismiss = ::closeViewer,
                    ) else Modifier), contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(strokeWidth = 2.2.dp, color = primary) }

                // MV-R5-035: retry recreates provided/local image requests too.
                type == "image" -> key(reloadAttempt) {
                    ImageViewerContent(
                        model = editedCopy?.toURI()?.toString() ?: renderProvidedModel ?: signedUrl!!,
                        title = title,
                        primary = primary,
                        foregroundSecondary = foregroundSecondary,
                        theme = theme,
                        onReady = { imageReady = true },
                        // Ordinary image UI only. Restricted-view behavior remains unchanged.
                        onDismiss = if (restrictedViewOnce) null else ::closeViewer,
                        // MV-R4-037: ordinary images open the existing overflow menu on long-press;
                        // restricted view-once keeps long-press disabled, mirroring its hidden actions.
                        onLongPressActions = if (restrictedViewOnce) null else ({ actionsExpanded = true }),
                        menuOpen = { actionsExpanded },
                    )
                }
                // Previous image branch retained inactive:
                // type == "image" -> ImageViewerContent(
                // model = renderProvidedModel ?: editedCopy?.toURI()?.toString() ?: signedUrl!!,
                // title = title,
                // primary = primary,
                // foregroundSecondary = foregroundSecondary,
                // theme = theme,
                // onReady = { imageReady = true },
                // )

                type == "video" -> {
                    val url = signedUrl!!
                    key(url) {
                    Column(
                        Modifier.fillMaxSize(),
                    ) {
                        Box(
                            // MV-R1-050 prior preview modifier: Modifier.weight(1f).fillMaxWidth()
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .then(if (!restrictedViewOnce) Modifier.viewerImageDismissGesture(
                                    identity = url,
                                    reduceMotion = reduceMotion,
                                    canDismiss = { !closing },
                                    onDismiss = ::closeViewer,
                                ) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            val aspect = if (videoWidth == 0 || videoHeight == 0) {
                                16f / 9f
                            } else {
                                videoWidth.toFloat() / videoHeight.toFloat()
                            }
                            Box(
                                Modifier
                                    .aspectRatio(aspect)
                                    .clipToBounds()
                                    // MV-R1-051 previous: .pointerInput(videoReady) { detectTapGestures { toggleVideo() } },
                                    .pointerInput(videoReady, durationMs, restrictedViewOnce) {
                                        detectTapGestures(
                                            onTap = { toggleVideo() },
                                            onDoubleTap = { tap ->
                                                // Previous shadowed size: viewerVideoTapDirection(tap.x, size.width.toFloat())
                                                val direction = viewerVideoTapDirection(tap.x, this.size.width.toFloat())
                                                val reader = videoView
                                                if (direction == 0) toggleVideo()
                                                else if (!restrictedViewOnce && videoReady && !videoFailed && durationMs > 0 && reader != null &&
                                                    mediaLifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                                                    val target = viewerVideoGestureSeek(reader.currentPosition, durationMs, direction)
                                                    reader.seekTo(target)
                                                    positionMs = target
                                                    seekGestureDirection = direction
                                                    seekGestureSequence++
                                                }
                                            },
                                        )
                                    },
                            ) {
                                AndroidView(
                                    factory = { ctx ->
                                        VideoView(ctx).apply {
                                            setVideoURI(Uri.parse(url))
                                            setOnPreparedListener { mp ->
                                                if (videoView !== this || trimming || editing) {
                                                    stopPlayback()
                                                    return@setOnPreparedListener
                                                }
                                                // MV-R4-009: applies the per-open loop flag (default OFF).
                                                // Previous fixed behavior: mp.isLooping = false
                                                mp.isLooping = loopVideo
                                                videoMediaPlayer = mp
                                                videoWidth = mp.videoWidth
                                                videoHeight = mp.videoHeight
                                                durationMs = mp.duration
                                                videoReady = true
                                            }
                                            setOnErrorListener { _, _, _ ->
                                                if (videoView !== this) return@setOnErrorListener true
                                                videoMediaPlayer = null
                                                isPlaying = false
                                                videoReady = false
                                                videoFailed = true
                                                videoPlayPending = false
                                                true
                                            }
                                            setOnCompletionListener {
                                                if (videoView !== this) return@setOnCompletionListener
                                                // MV-R2-005: leave a replayable start position, never autoplay.
                                                // Previous statement retained with same behavior: isPlaying = false
                                                isPlaying = false
                                                videoPlayPending = false
                                                seekTo(0)
                                                positionMs = 0
                                            }
                                            videoView = this
                                        }
                                    },
                                    onRelease = { released -> stopVideoReader(released) },
                                    modifier = Modifier.fillMaxSize(),
                                )
                                if (seekGestureDirection != 0 && videoReady && !videoFailed) {
                                    val badgeAlignment = if (seekGestureDirection < 0) Alignment.CenterStart else Alignment.CenterEnd
                                    Box(Modifier.align(badgeAlignment).size(128.dp), contentAlignment = Alignment.Center) {
                                        if (!reduceMotion) Canvas(Modifier.fillMaxSize()) {
                                            drawCircle(primary.copy(alpha = (1f - seekRipple.value).coerceIn(0f, 1f) * 0.25f),
                                                // Previous shadowed size: radius = size.minDimension * 0.5f * seekRipple.value
                                                radius = this.size.minDimension * 0.5f * seekRipple.value)
                                        }
                                        Surface(shape = CircleShape, color = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer) {
                                            Text(if (seekGestureDirection < 0) "Back 10s" else "Forward 10s",
                                                modifier = Modifier.padding(12.dp), color = androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer)
                                        }
                                    }
                                }
                                if (videoFailed) {
                                    // Dart fallback when the controller never
                                    // initializes.
                                    MediaViewerFallbackCard(
                                        icon = Icons.Rounded.VideocamOff,
                                        text = "Unable to initialize this video.",
                                        foregroundSecondary = foregroundSecondary,
                                        theme = theme,
                                        actionLabel = if (restrictedViewOnce || error != null || !openAvailable) null else "Open Securely",
                                        actionEnabled = !externalActionBusy && !savingToGallery && !ownedActionBusy,
                                        onAction = ::openExternally,
                                    )
                                } else if (!videoReady) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.2.dp,
                                        color = primary,
                                    )
                                } else {
                                    // Dart AnimatedOpacity play overlay (160ms).
                                    // The unscoped AnimatedVisibility is
                                    // deprecated-hidden in animation 1.7, so
                                    // a content-sized Column provides the
                                    // ColumnScope receiver at the same spot.
                                    Column(modifier = Modifier.align(Alignment.Center)) {
                                        AnimatedVisibility(
                                            visible = !isPlaying,
                                            enter = fadeIn(tween(if (reduceMotion) 0 else 160)),
                                            exit = fadeOut(tween(if (reduceMotion) 0 else 160)),
                                        ) {
                                            Box(
                                                Modifier
                                                    .background(
                                                        Color(theme.surfaceColor).copy(alpha = 0.7f),
                                                        CircleShape,
                                                    )
                                                    .padding(12.dp),
                                            ) {
                                                // MV-R3-042: retain overlay label while sharing the player's glyph set.
                                                // MV-R1-052 previous Play-only overlay retained inactive:
                                                // Box(Modifier.semantics { contentDescription = "Play" }) {
                                                //     com.example.chat.ui.media.icons.ChatyMediaIcon(icon = com.example.chat.ui.media.icons.ChatyIconType.play, size = 54.dp, color = foreground)
                                                // }
                                                if (videoPlayPending) {
                                                    Box(Modifier.size(54.dp).semantics {
                                                        contentDescription = "Starting video"
                                                        liveRegion = LiveRegionMode.Polite
                                                    }, contentAlignment = Alignment.Center) {
                                                        CircularProgressIndicator(Modifier.size(32.dp), color = primary, strokeWidth = 2.dp)
                                                    }
                                                } else Box(Modifier.semantics { contentDescription = "Play" }) {
                                                    com.example.chat.ui.media.icons.ChatyMediaIcon(
                                                        icon = com.example.chat.ui.media.icons.ChatyIconType.play,
                                                        size = 54.dp, color = foreground,
                                                    )
                                                }
                                                // com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.playArrow,
                                                // semanticLabel = "Play",
                                                // color = foreground,
                                                // modifier = Modifier.size(54.dp),
                                                // )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (videoReady && !videoFailed) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 18.dp, top = 10.dp, end = 18.dp, bottom = 18.dp),
                            ) {
                                // MV-R3-042 prior Material glyph button retained below.
                                val videoToggleLabel = if (isPlaying) "Pause" else "Play"
                                com.example.chat.ui.media.AudioPlayerTooltip(videoToggleLabel) {
                                // MV-R5-024 prior direct dynamic contentDescription: if (isPlaying) "Pause" else "Play".
                                IconButton(onClick = ::toggleVideo, modifier = Modifier.semantics {
                                    contentDescription = videoToggleLabel
                                }) {
                                    com.example.chat.ui.media.icons.ChatyMediaIcon(
                                        icon = if (isPlaying) com.example.chat.ui.media.icons.ChatyIconType.pause else com.example.chat.ui.media.icons.ChatyIconType.play,
                                        size = 24.dp, color = foreground,
                                    )
                                }
                                } // Tooltip leaves the original transport node/action owner unchanged.
                                // IconButton(onClick = ::toggleVideo) {
                                // Icon(
                                // if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                // contentDescription = if (isPlaying) "Pause" else "Play",
                                // tint = foreground,
                                // )
                                // }
                                // MV-R2-046 prior timer style: Text(formatPlaybackTime(positionMs), color = foregroundSecondary, fontSize = 12.sp)
                                Text(formatPlaybackTime(positionMs), color = foregroundSecondary, fontSize = 12.sp, style = androidx.compose.material3.LocalTextStyle.current.copy(fontFeatureSettings = "tnum", letterSpacing = 0.2.sp))
                                Spacer(Modifier.width(8.dp))
                                VideoProgressBar(
                                    durationMs = durationMs,
                                    position = if (durationMs > 0) {
                                        positionMs.toFloat() / durationMs.toFloat()
                                    } else {
                                        0f
                                    },
                                    buffered = bufferedPct / 100f,
                                    playedColor = primary,
                                    bufferedColor = primary.copy(alpha = 0.3f),
                                    backgroundColor = borderSubtle,
                                    onSeek = { fraction ->
                                        val vv = videoView ?: return@VideoProgressBar
                                        val target = (fraction.coerceIn(0f, 1f) * durationMs).toInt()
                                        vv.seekTo(target)
                                        positionMs = target
                                    },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                // MV-R2-046 prior timer style: Text(formatPlaybackTime(durationMs), color = foregroundSecondary, fontSize = 12.sp)
                                Text(formatPlaybackTime(durationMs), color = foregroundSecondary, fontSize = 12.sp, style = androidx.compose.material3.LocalTextStyle.current.copy(fontFeatureSettings = "tnum", letterSpacing = 0.2.sp))
                            }
                        }
                    }
                    }
                }

                else -> {
                    val icon = when (type) {
                        "audio" -> Icons.Rounded.GraphicEq
                        "document" -> Icons.Rounded.Description
                        else -> Icons.Rounded.InsertDriveFile
                    }
                    MediaViewerFallbackCard(
                        icon = icon,
                        text = if (restrictedViewOnce) "This restricted attachment cannot be opened in another app." else "This private $type is ready to open securely.",
                        foregroundSecondary = foregroundSecondary,
                        theme = theme,
                        actionLabel = if (restrictedViewOnce || error != null || !openAvailable) null else "Open Securely",
                        actionEnabled = !externalActionBusy && !savingToGallery && !ownedActionBusy,
                        onAction = ::openExternally,
                        visualModifier = if (!restrictedViewOnce) Modifier.viewerImageDismissGesture(
                            identity = "fallback:$storagePath:$reloadAttempt:$type",
                            reduceMotion = reduceMotion,
                            canDismiss = { !closing }, onDismiss = ::closeViewer,
                        ) else Modifier,
                    )
                }
            }
        }
        }
        if (wideActionRail && !restrictedViewOnce && error == null) {
            val actionsReady = (signedUrl != null || renderProvidedModel != null) &&
                    (ownedActions || signedUrl != null || renderProvidedModel is java.io.File) &&
                    !externalActionBusy && !savingToGallery && !ownedActionBusy
            Surface(color = elevated, modifier = Modifier.width(160.dp).fillMaxHeight()) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Same handlers and capability gates as the compact top-bar actions.
                    if (type in setOf("image", "video")) TextButton(onClick = ::saveToGallery, enabled = actionsReady && gallerySaveAvailable,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Save to gallery") }
                    TextButton(onClick = ::share, enabled = actionsReady && shareAvailable,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Share attachment") }
                    if (type in setOf("video", "audio")) TextButton(enabled = actionsReady && editAvailable,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        onClick = { if (ownedActions) onOwnedEdit?.invoke() else if (stopVideoReader()) trimming = true }) { Text("Trim a copy") }
                    if (type == "video") TextButton(enabled = actionsReady && videoReady && !videoFailed,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
                            stateDescription = if (loopVideo) loopVideoStateOn else loopVideoStateOff
                        },
                        onClick = { toggleLoopVideo() }) { Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.media_viewer_loop_video)) }
                    if (type == "image" && !title.endsWith(".gif", ignoreCase = true)) TextButton(enabled = actionsReady && editAvailable,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        onClick = { if (ownedActions) onOwnedEdit?.invoke() else if (stopVideoReader()) editing = true }) { Text("Edit a copy") }
                    TextButton(onClick = ::openExternally, enabled = actionsReady && openAvailable,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Open with app") }
                }
            }
        }
        } // Media and action rail.
    }
    } // Measured host.
    } // Consumer identity boundary.
}


/** Dart InteractiveViewer (minScale 0.7, maxScale 5) + Image.network with
 *  loading spinner and error fallback.
 *
 *  Transform parity with Flutter's InteractiveViewer (verified against
 *  packages/flutter/lib/src/widgets/interactive_viewer.dart, stable):
 *  the viewer maps a scene point x to f(x) = s*x + t with the child laid
 *  out at viewport size (constrained=true). graphicsLayer with
 *  transformOrigin (0,0) is exactly f(x) = s*x + t, so the same (s, t)
 *  state reproduces it.
 *  Scale (_matrixScale): totalScale = max(current * gesture,
 *  max(viewport.w / boundary.w, viewport.h / boundary.h)), then clamped to
 *  [minScale, maxScale]. boundaryMargin is zero and the child is
 *  viewport-sized, so the cover factor is 1.0 and the effective minimum
 *  scale is 1.0 — the declared minScale 0.7 never permits sub-viewport
 *  scaling ("scaling below 1.0 will not be allowed ... without first
 *  increasing the boundaryMargin", minScale doc).
 *  Zoom keeps the gesture centroid fixed: t' = c - s'*(c - t)/s;
 *  incremental pan adds.
 *  Translation (_exceedsBy): the transformed viewport must stay inside the
 *  child rect, so per axis t in [(1-s)*W, 0] (reachable domain s >= 1),
 *  W the viewport extent. Rotation is hard-disabled upstream
 *  (_rotateEnabled = false); dropped here too. */
@Composable
private fun ImageViewerContent(
    model: Any,
    title: String,
    primary: Color,
    foregroundSecondary: Color,
    theme: ThemeConfig,
    onReady: () -> Unit = {},
    onDismiss: (() -> Unit)? = null,
    // MV-R4-037: long-press opens the caller's existing overflow menu; dismiss is gated while it is open.
    onLongPressActions: (() -> Unit)? = null,
    menuOpen: () -> Boolean = { false },
) {
    var scale by remember(model) { mutableStateOf(1f) }
    var offset by remember(model) { mutableStateOf(Offset.Zero) }
    val gestureScope = rememberCoroutineScope()
    val reduceMotion = com.example.chat.ui.kit.rememberChatyReduceMotion()
    var zoomAnimation by remember(model) { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val zoomAccessibility = androidx.compose.ui.platform.LocalView.current
    var zoomGestureActive by remember(model) { mutableStateOf(false) }
    var zoomGestureEnd by remember(model) { mutableIntStateOf(0) }
    var lastAnnouncedZoom by remember(model) { mutableIntStateOf(100) }
    // MV-R4-037: holder so the installed detector lambda invokes the CURRENT callback, not a
    // stale capture from when the pointerInput coroutine started.
    val currentOnLongPressActions by androidx.compose.runtime.rememberUpdatedState(onLongPressActions)
    // MV-R1-042: wait for lift/settle, never announce every animation or pinch frame.
    LaunchedEffect(model, zoomGestureActive, zoomGestureEnd) {
        if (!zoomGestureActive && zoomGestureEnd > 0) {
            delay(250L)
            val percent = viewerZoomPercent(scale)
            if (percent != lastAnnouncedZoom) {
                lastAnnouncedZoom = percent
                zoomAccessibility.announceForAccessibility("Zoom $percent percent")
            }
        }
    }
    // MV-R1-011 initial model-only cleanup extended below for viewport/motion changes.
    // DisposableEffect(model) { onDispose { zoomAnimation?.cancel() } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewportW = constraints.maxWidth.toFloat()
        val viewportH = constraints.maxHeight.toFloat()
        val decodeWidth = viewerDecodeExtent(constraints.maxWidth)
        val decodeHeight = viewerDecodeExtent(constraints.maxHeight)
        val imageContext = LocalContext.current
        val imageRequest = remember(imageContext, model, decodeWidth, decodeHeight) {
            coil.request.ImageRequest.Builder(imageContext).data(model)
                .size(decodeWidth, decodeHeight)
                .memoryCachePolicy(coil.request.CachePolicy.DISABLED)
                .diskCachePolicy(coil.request.CachePolicy.DISABLED).build()
        }
        // Decode bounds are captured above, outside the nested BoxScope.
        LaunchedEffect(model, viewportW, viewportH) {
            zoomAnimation?.cancel()
            val clamped = viewerZoomTransform(scale, offset.x, offset.y,
                0f, 0f, 0f, 0f, 1f, viewportW, viewportH)
            scale = clamped.scale
            offset = Offset(clamped.x, clamped.y)
        }
        DisposableEffect(model, viewportW, viewportH, reduceMotion) {
            onDispose { zoomAnimation?.cancel() }
        }
        Box(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                // MV-R1-050 image-only dismiss region excludes all transport/seek/toolbars.
                .then(if (onDismiss != null) Modifier.viewerImageDismissGesture(
                    identity = model,
                    reduceMotion = reduceMotion,
                    // MV-R4-037 prior: canDismiss = { scale <= 1.001f && zoomAnimation?.isActive != true },
                    canDismiss = { !menuOpen() && scale <= 1.001f && zoomAnimation?.isActive != true },
                    onDismiss = onDismiss,
                ) else Modifier)
                // MV-R1-042: observe consumed events at Final pass without consuming them.
                .pointerInput(model) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Final)
                        zoomGestureActive = true
                        try {
                            var pressed = true
                            while (pressed) {
                                pressed = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Final)
                                    .changes.any { it.pressed }
                            }
                        } finally { zoomGestureActive = false }
                        zoomGestureEnd++
                    }
                }
                // MV-R1-011: separate tap detector preserves pinch/pan gesture handling.
                // MV-R4-037 prior: .pointerInput(model, viewportW, viewportH, reduceMotion) {
                // Prior detector entry: detectTapGestures(onDoubleTap = { tap ->
                .pointerInput(model, viewportW, viewportH, reduceMotion, onLongPressActions != null) {
                    // MV-R4-037: long-press only opens the existing menu; double-tap zoom unchanged.
                    // A real null (restricted view-once) leaves the long-press branch disarmed:
                    // TapGestureDetector arms the long-press timeout only when onLongPress != null.
                    detectTapGestures(
                        onLongPress = if (onLongPressActions != null) ({ _: Offset -> currentOnLongPressActions?.invoke() }) else null,
                        onDoubleTap = { tap ->
                        zoomAnimation?.cancel()
                        val startScale = scale
                        val startOffset = offset
                        val target = viewerDoubleTapTransform(
                            startScale, startOffset.x, startOffset.y,
                            tap.x, tap.y, viewportW, viewportH,
                        )
                        if (reduceMotion) {
                            scale = target.scale
                            offset = Offset(target.x, target.y)
                        } else {
                            zoomAnimation = gestureScope.launch {
                                androidx.compose.animation.core.animate(
                                    initialValue = 0f, targetValue = 1f,
                                    animationSpec = tween(durationMillis = 200),
                                ) { fraction, _ ->
                                    scale = startScale + (target.scale - startScale) * fraction
                                    offset = Offset(
                                        startOffset.x + (target.x - startOffset.x) * fraction,
                                        startOffset.y + (target.y - startOffset.y) * fraction,
                                    )
                                }
                            }
                        }
                    })
                }
                .pointerInput(model, viewportW, viewportH) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        zoomAnimation?.cancel()
                        // MV-R5-038: same centroid-fixed/clamped transform, now independently testable.
                        val transformed = viewerZoomTransform(
                            scale, offset.x, offset.y, centroid.x, centroid.y,
                            pan.x, pan.y, zoom, viewportW, viewportH,
                        )
                        scale = transformed.scale
                        offset = Offset(transformed.x, transformed.y)
                        // Previous inline implementation retained below.
                        // val oldScale = scale
                        // // _matrixScale: max(current * gesture, cover) then
                        // // clamp to [minScale, maxScale]. Child is
                        // // viewport-sized (constrained=true) and
                        // // boundaryMargin is zero, so cover ==
                        // // max(viewport.w / child.w, viewport.h / child.h)
                        // // == 1f and the declared 0.7 minimum never applies.
                        // val coverScale = 1f
                        // val newScale = (oldScale * zoom)
                        // .coerceAtLeast(coverScale)
                        // .coerceIn(0.7f, 5f)
                        // scale = newScale
                        // val factor = newScale / oldScale
                        // val zoomed = Offset(
                        // x = centroid.x - (centroid.x - offset.x) * factor,
                        // y = centroid.y - (centroid.y - offset.y) * factor,
                        // ) + pan
                        // // _exceedsBy clamp: transformed viewport must stay
                        // // inside the child rect; on the reachable domain
                        // // (s >= cover == 1) this is t in [(1-s)*W, 0].
                        // val minX = minOf(0f, (1f - newScale) * viewportW)
                        // val maxX = maxOf(0f, (1f - newScale) * viewportW)
                        // val minY = minOf(0f, (1f - newScale) * viewportH)
                        // val maxY = maxOf(0f, (1f - newScale) * viewportH)
                        // offset = Offset(
                        // zoomed.x.coerceIn(minX, maxX),
                        // zoomed.y.coerceIn(minY, maxY),
                        // )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
        coil.compose.SubcomposeAsyncImage(
            model = imageRequest,
            contentDescription = title,
            onSuccess = { onReady() },
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                    transformOrigin = TransformOrigin(0f, 0f)
                },
            loading = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.2.dp, color = primary)
                }
            },
            error = {
                MediaViewerFallbackCard(
                    icon = Icons.Rounded.BrokenImage,
                    text = "Unable to display this image.",
                    foregroundSecondary = foregroundSecondary,
                    theme = theme,
                )
            },
        )
        }
    }
}

/** Dart VideoProgressIndicator (allowScrubbing) with played/buffered/
 *  background colors. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun VideoProgressBar(
    durationMs: Int,
    position: Float,
    buffered: Float,
    playedColor: Color,
    bufferedColor: Color,
    backgroundColor: Color,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember { mutableStateOf<Float?>(null) }
    val seekInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val seekColors = androidx.compose.material3.SliderDefaults.colors(activeTrackColor = playedColor, thumbColor = playedColor, inactiveTrackColor = backgroundColor)
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(3.dp)) {
            drawRect(backgroundColor)
            drawRect(bufferedColor, size = Size(size.width * buffered.coerceIn(0f, 1f), size.height))
        }
        androidx.compose.material3.Slider(
            value = draft ?: position.coerceIn(0f, 1f),
            onValueChange = { draft = it },
            onValueChangeFinished = { draft?.let(onSeek); draft = null },
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = "Video playback position" },
            // MV-R3-038 old colors = SliderDefaults.colors(activeTrackColor = playedColor, thumbColor = playedColor, inactiveTrackColor = backgroundColor)
            colors = seekColors,
            interactionSource = seekInteraction,
            thumb = { sliderState -> com.example.chat.ui.media.SeekTimeBubbleThumb(seekInteraction, seekColors, true,
                formatPlaybackTime((sliderState.value.coerceIn(0f, 1f) * durationMs.coerceAtLeast(0)).toInt())) },
        )
    }
}

/** Dart _fallbackCard: 76dp icon, message, optional 'Open Securely'
 *  primary button (width 180, open_in_new icon). */
@Composable
private fun MediaViewerFallbackCard(
    icon: ImageVector,
    text: String,
    foregroundSecondary: Color,
    theme: ThemeConfig,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    visualModifier: Modifier = Modifier,
    actionEnabled: Boolean = true,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        // MV-R3-035 original: modifier = Modifier.padding(24.dp).semantics { liveRegion = LiveRegionMode.Polite },
        modifier = Modifier.widthIn(max = 328.dp).verticalScroll(rememberScrollState()).padding(24.dp).semantics { liveRegion = LiveRegionMode.Polite }, // ChatySpacing.xl
    ) {
        // MV-R1-050 only passive icon/text is draggable, never the Open action below.
        Column(visualModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            icon,
            contentDescription = null,
            tint = foregroundSecondary,
            modifier = Modifier.size(76.dp),
        )
        Spacer(Modifier.height(20.dp)) // ChatySpacing.lg
        Text(
            text,
            textAlign = TextAlign.Center,
            color = foregroundSecondary,
            fontSize = 14.5.sp,
        )
        } // Passive fallback gesture region.
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onAction,
                enabled = actionEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(theme.accentColor),
                    contentColor = Color(theme.onAccentColor),
                ),
                // MV-R3-040 original fixed width: modifier = Modifier.width(180.dp),
                modifier = Modifier.widthIn(min = 180.dp),
            ) {
                com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.openInNew,
                    semanticLabel = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(actionLabel)
            }
        }
    }
}

// MV-R3-047 prior independent formatter (retained, inactive):
// private fun formatPlaybackTime(milliseconds: Int): String {
//     val seconds = milliseconds.coerceAtLeast(0) / 1000
//     return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
//     else "%d:%02d".format(seconds / 60, seconds % 60)
// }
private fun formatPlaybackTime(milliseconds: Int): String =
    com.example.chat.ui.media.TimeFormatters.playbackMillis(milliseconds.toLong())


/** Writes an allowlisted [source] into the gallery via MediaStore.
 *  MV-R2-014: deliberately refuses pre-Android-10 gallery writes. No legacy broad
 *  WRITE_EXTERNAL_STORAGE permission or unscoped filesystem fallback is requested.
 *  MV-R2-019: returns fixed, actionable text only, never exception/path/URL contents.
 *  Success is reported only after publishing exactly one pending gallery row.
 *  This is not a startup orphan sweep (MV-R5-011 remains separate). */
internal suspend fun saveMediaToGallery(context: android.content.Context, source: String, displayName: String,
    isVideo: Boolean, isOwnerCurrent: () -> Boolean = { true }): String {
    if (android.os.Build.VERSION.SDK_INT < 29) {
        // Previous refusal: Saving to gallery needs Android 10 or newer.
        return "Saving to gallery needs Android 10 or newer. Use Share attachment to choose another app."
    }
    val safeName = com.example.chat.ui.media.FileSanitizationService { context.cacheDir }.sanitizeFileName(displayName)
    val extension = safeName.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
    val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        ?: if (isVideo) "video/mp4" else "image/jpeg"
    val collection = if (isVideo) {
        android.provider.MediaStore.Video.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        android.provider.MediaStore.Images.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
    }
    val values = android.content.ContentValues().apply {
        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, safeName)
        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime)
        // MV-R2-015 prior: put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, (if (isVideo) "Movies" else "Pictures") + "/Chaty")
        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, com.example.chat.ui.media.galleryRelativePath(isVideo))
        put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val resolver = context.contentResolver
    val saveContext = kotlinx.coroutines.currentCoroutineContext()
    fun checkCurrentSave() {
        saveContext.ensureActive()
        check(isOwnerCurrent()) { "Attachment session changed." }
    }
    // Previous insert failure: Save failed: the gallery refused the new item.
    checkCurrentSave()
    val uri = resolver.insert(collection, values) ?: return "Could not add this attachment to the gallery. Check gallery access and try again."
    try {
        checkCurrentSave()
        resolver.openOutputStream(uri)?.use { out ->
            openMediaInput(context, source).use { input ->
                val buffer = ByteArray(32 * 1024)
                var copied = 0L
                val limit = com.example.chat.ui.media.FileSanitizationService.MAX_SAFE_FILE_SIZE.toLong()
                while (true) {
                    checkCurrentSave()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) {
                        // A provider may return zero from a bulk read. Make bounded progress.
                        val single = input.read()
                        if (single < 0) break
                        checkCurrentSave()
                        check(copied < limit) { "Attachment exceeds gallery copy limit" }
                        out.write(single)
                        copied++
                        continue
                    }
                    checkCurrentSave()
                    check(copied + count <= limit) { "Attachment exceeds gallery copy limit" }
                    out.write(buffer, 0, count)
                    copied += count
                }
                require(copied > 0) { "Attachment is empty" }
            }
        } ?: error("could not open the gallery item for writing")
        checkCurrentSave()
        values.clear()
        values.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
        // Provider operations are not transactions with account changes. Recheck after
        // publication and roll back on invalidation, cancellation or failed publication.
        check(resolver.update(uri, values, null, null) == 1) { "Gallery publication failed" }
        checkCurrentSave()
    } catch (cancelled: CancellationException) {
        // Rollback refusal must not replace the original cancellation.
        runCatching { resolver.delete(uri, null, null) }
        throw cancelled
    } catch (_: Exception) {
        runCatching { resolver.delete(uri, null, null) }
        // Previous failure: Could not save attachment. Check access and the 100 MB file limit.
        return "Could not save attachment. Check access and the 100 MB file limit, then try again."
    }
    return if (isVideo) "Video saved to gallery" else "Photo saved to gallery"
}

//  Legacy source opener (replaced by openMediaInput to close HTTP connections and handle file URIs):
// private fun openMediaSource(context: android.content.Context, source: String): java.io.InputStream = when {
//     source.startsWith("http://") || source.startsWith("https://") -> {
//         val connection = java.net.URL(source).openConnection() as java.net.HttpURLConnection
//         connection.connectTimeout = 15000
//         connection.readTimeout = 30000
//         connection.connect()
//         if (connection.responseCode !in 200..299) error("download failed (HTTP ${connection.responseCode})")
//         connection.inputStream
//     }
//     source.startsWith("content://") ->
//         context.contentResolver.openInputStream(Uri.parse(source)) ?: error("could not open the source")
//     else -> java.io.File(source).inputStream()
// }
//

// Source-preservation review: inactive baseline statements retained verbatim.
// modifier = Modifier.padding(24.dp).semantics { liveRegion = LiveRegionMode.Polite }, // ChatySpacing.xl

/* Previous implementation fragments retained (net225):
    androidx.compose.runtime.SideEffect { registerStopReader?.invoke { videoView?.stopPlayback(); videoView = null } }
            reader?.stopPlayback()
            if (videoView === reader) videoView = null
                        model = renderProvidedModel ?: editedCopy?.toURI()?.toString() ?: signedUrl!!,
                                    onRelease = { videoMediaPlayer = null },
*/

/* Previous implementation fragments retained (net227):
        if (restrictedViewOnce || error != null || savingToGallery || externalActionBusy || ownedActionBusy || !gallerySaveAvailable) return
                        IconButton(onClick = ::saveToGallery, enabled = actionsReady) {
                                text = { Text("Save to gallery") }, modifier = Modifier.heightIn(min = 48.dp), enabled = actionsReady,
                    if (type in setOf("image", "video")) TextButton(onClick = ::saveToGallery, enabled = actionsReady,
private suspend fun saveMediaToGallery(context: android.content.Context, source: String, displayName: String, isVideo: Boolean): String {
*/

/* Previous implementation fragments retained (net228):
            Row(
                verticalAlignment = Alignment.CenterVertically,
                    .height(56.dp)
                if (!compactToolbar) Column(Modifier.weight(1f)) {
                else Spacer(Modifier.weight(1f))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f),
        modifier = Modifier.widthIn(max = 328.dp).padding(24.dp).semantics { liveRegion = LiveRegionMode.Polite }, // ChatySpacing.xl
*/

// Previous source retained for source-only media polish:
//         if (type == "image" && providedModel is ByteArray) return@LaunchedEffect
//         if (!source.startsWith("https:", ignoreCase = true)) return source
//         if (restrictedViewOnce || error != null || externalActionBusy) return
//         if (openAvailable && !restrictedViewOnce && error == null && !externalActionBusy &&
//     var savingToGallery by remember(storagePath) { mutableStateOf(false) }
//         if (restrictedViewOnce || savingToGallery || !gallerySaveAvailable) return
//                         saveMediaToGallery(context, refreshed, title, type == "video")
//         if (restrictedViewOnce || savingToGallery) return
//     if (externalActionBusy || savingToGallery) androidx.compose.material3.AlertDialog(
//             if (event.type != KeyEventType.KeyDown || keyboardHelp || externalActionBusy || savingToGallery || error != null) false
//                 val actionsReady = signedUrl != null || renderProvidedModel != null
//                     com.example.chat.ui.media.AudioPlayerTooltip("Share file link") {
//                                 semanticLabel = "Share file link", color = foreground)
//                     //         semanticLabel = "Share file link", color = foreground)
//                             DropdownMenuItem(text = { Text("Share file link") }, modifier = Modifier.heightIn(min = 48.dp),
//                 // semanticLabel = "Share file link",
//             val actionsReady = signedUrl != null || renderProvidedModel != null
//                         modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Share file link") }
//         return "Saving to gallery needs Android 10 or newer. Use Share file link to choose another app."
//         if (restrictedViewOnce || savingToGallery) return

// Previous cleanup source retained:
//     fun stopVideoReader(reader: VideoView? = videoView) {
//         try { reader?.stopPlayback() }
//         finally {
//             if (videoView === reader) {
//                 videoReady = false; videoFailed = false
//                 isPlaying = false; videoPlayPending = false
//     androidx.compose.runtime.SideEffect { registerStopReader?.invoke { stopVideoReader() } }
//         stopVideoReader() // Retry/source replacement never leaves an old native URL/player alive.

// Previous gallery-copy source retained:
//     check(isOwnerCurrent()) { "Attachment session changed." }
//         check(isOwnerCurrent()) { "Attachment session changed." }
//             openMediaInput(context, source).use { input -> require(copyMediaBounded(input, out, com.example.chat.ui.media.FileSanitizationService.MAX_SAFE_FILE_SIZE.toLong()) > 0) }
//         check(isOwnerCurrent()) { "Attachment session changed before gallery publication." }
//         // Do not claim a visible gallery copy when the provider published no row.
//         // Previous unchecked call: resolver.update(uri, values, null, null)
//         check(isOwnerCurrent()) { "Attachment session changed during gallery publication." }

// Previous click-state source retained for review:
//         if (!restrictedViewOnce && error == null && !externalActionBusy && !savingToGallery && !ownedActionBusy &&
//                         IconButton(onClick = ::share, enabled = actionsReady) {
//                             text = { Text("Trim a copy") }, modifier = Modifier.heightIn(min = 48.dp), enabled = actionsReady,
//                             text = { Text("Edit a copy") }, modifier = Modifier.heightIn(min = 48.dp), enabled = actionsReady,
//                             enabled = actionsReady, onClick = { actionsExpanded = false; openExternally() })
//                                 enabled = actionsReady, onClick = { actionsExpanded = false; share() })
//                                         actionLabel = if (restrictedViewOnce || error != null) null else "Open Securely",
//                         actionLabel = if (restrictedViewOnce || error != null) null else "Open Securely",
//                     TextButton(onClick = ::share, enabled = actionsReady,
//                     if (type in setOf("video", "audio")) TextButton(enabled = actionsReady,
//                     if (type == "image" && !title.endsWith(".gif", ignoreCase = true)) TextButton(enabled = actionsReady,
//                     TextButton(onClick = ::openExternally, enabled = actionsReady,
// //         if (!restrictedViewOnce && error == null && !externalActionBusy &&

// Previous click-state source retained for review:
//             model = coil.request.ImageRequest.Builder(LocalContext.current).data(model)
//                 // MV-R2-025 previous unbounded request:
//                 // .memoryCachePolicy(coil.request.CachePolicy.DISABLED).diskCachePolicy(coil.request.CachePolicy.DISABLED).build(),
//                 // Previous implicit receiver: .size(viewerDecodeExtent(constraints.maxWidth), viewerDecodeExtent(constraints.maxHeight))
//                 .size(decodeWidth, decodeHeight)
//                 .memoryCachePolicy(coil.request.CachePolicy.DISABLED).diskCachePolicy(coil.request.CachePolicy.DISABLED).build(),
