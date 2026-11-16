package com.example.chat.ui.chats.detail

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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Description
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
 *  http(s)/data: branches so direct URLs work without the service. Video
 *  uses android.widget.VideoView (no new Gradle dependency; media3 is not
 *  on the branch). */
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
    registerStopReader: (((() -> Unit)?) -> Unit)? = null,
    // Owned viewers must cross the same short account/epoch barrier as inline playback.
    deliverPlayback: ((() -> Unit) -> Boolean)? = null,
) {
    com.example.chat.ui.kit.ProtectSensitiveWindow(active = restrictedViewOnce)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Live policy also works on minSdk24; direct areAnimatorsEnabled() is API26+.
    val reduceMotion = com.example.chat.ui.kit.rememberChatyReduceMotion()
    val opening = remember(storagePath) { Animatable(if (reduceMotion) 1f else 0f) }
    var targetBounds by remember { mutableStateOf<Rect?>(null) }
    var imageReady by remember(storagePath) { mutableStateOf(false) }
    val openingReady = type != "image" || imageReady
    LaunchedEffect(storagePath, reduceMotion, targetBounds != null, openingReady) {
        if (reduceMotion || sourceBounds == null) opening.snapTo(1f)
        else if (targetBounds != null && openingReady) opening.animateTo(1f, tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing))
    }
    var trimming by remember(storagePath) { mutableStateOf(false) }
    var editing by remember(storagePath) { mutableStateOf(false) }
    var editedCopy by remember(storagePath) { mutableStateOf<java.io.File?>(null) }
    val ownedCopies = remember(storagePath) { mutableListOf<java.io.File>() }
    DisposableEffect(storagePath) { onDispose { ownedCopies.forEach { it.delete() } } }
    var externalActionBusy by remember(storagePath) { mutableStateOf(false) }
    var trimSource by remember(storagePath) { mutableStateOf<String?>(null) }
    var error by remember(storagePath) { mutableStateOf<String?>(null) }
    var signedUrl by remember(storagePath) { mutableStateOf<String?>(null) }

    // Video state (Dart VideoPlayerController value).
    var videoReady by remember(storagePath) { mutableStateOf(false) }
    var videoFailed by remember(storagePath) { mutableStateOf(false) }
    var isPlaying by remember(storagePath) { mutableStateOf(false) }
    var durationMs by remember(storagePath) { mutableIntStateOf(0) }
    var positionMs by remember(storagePath) { mutableIntStateOf(0) }
    var bufferedPct by remember(storagePath) { mutableIntStateOf(0) }
    var videoWidth by remember(storagePath) { mutableIntStateOf(0) }
    var videoHeight by remember(storagePath) { mutableIntStateOf(0) }
    var videoView by remember(storagePath) { mutableStateOf<VideoView?>(null) }

    androidx.compose.runtime.SideEffect { registerStopReader?.invoke { videoView?.stopPlayback(); videoView = null } }
    var reloadAttempt by remember(storagePath) { mutableIntStateOf(0) }
    // Dart _load.
    LaunchedEffect(storagePath, type, reloadAttempt) {
        error = null
        signedUrl = null
        videoReady = false
        videoFailed = false
        isPlaying = false
        val path = storagePath
        if (path.isNullOrEmpty()) {
            error = "This attachment does not have a valid storage path."
            return@LaunchedEffect
        }
        try {
            // Dart: video keeps the spinner until controller.initialize()
            // resolves; the video branch overlays its own spinner until
            // onPrepared fires.
            signedUrl = createSignedUrl(path, 3600)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = "Could not load this attachment. Check your connection and reopen it." }
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
    LaunchedEffect(videoView, videoReady, mediaForeground) {
        val vv = videoView
        if (vv == null || !videoReady || !mediaForeground) return@LaunchedEffect
        while (true) {
            positionMs = vv.currentPosition
            bufferedPct = vv.bufferPercentage
            isPlaying = vv.isPlaying
            delay(if (isPlaying) 250 else 1_000)
        }
    }

    DisposableEffect(videoView) {
        // Capture this effect's reader. Reading delegated state from onDispose can
        // stop the NEW view when the initial null-key effect is replaced.
        val reader = videoView
        onDispose {
            reader?.stopPlayback()
            if (videoView === reader) videoView = null
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
    fun launchExternal(share: Boolean) {
        if (restrictedViewOnce || error != null || externalActionBusy) return
        if (ownedActions) { if (share) onOwnedShare?.invoke() else onOwnedOpen?.invoke(); return }
        val source = editedCopy?.toURI()?.toString()
            ?: (providedModel as? java.io.File)?.toURI()?.toString()
            ?: signedUrl ?: return
        externalActionBusy = true
        scope.launch {
            var export: java.io.File? = null
            var handedOff = false
            try {
                export = stageMediaSource(context, source, "chaty_shared_")
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
    fun openExternally() = launchExternal(false)
    fun share() = launchExternal(true)

    // Dart _toggleVideo.
    // GB media-preview option: save the viewed image/video to the device gallery.
    var galleryConfirmation by remember(storagePath) { mutableStateOf(false) }
    var savingToGallery by remember(storagePath) { mutableStateOf(false) }
    fun performGallerySave() {
        if (restrictedViewOnce || savingToGallery) return
        val source = editedCopy?.absolutePath
            ?: (providedModel as? java.io.File)?.absolutePath
            ?: signedUrl
            ?: return
        savingToGallery = true
        scope.launch {
            try {
                val message = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try { saveMediaToGallery(context, source, title, type == "video") }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { "Could not save attachment. Try again." }
                }
                snackbarHostState.showSnackbar(message)
            } finally { savingToGallery = false }
        }
    }
    fun saveToGallery() {
        if (restrictedViewOnce || savingToGallery) return
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
        if (vv.isPlaying) vv.pause() else {
            val start = { vv.start() }
            val accepted = deliverPlayback?.invoke(start) ?: if (ownedActions) false else { start(); true }
            if (!accepted) {
                vv.stopPlayback()
                videoReady = false
                error = "Attachment session changed. Reopen it from the chat."
                return
            }
        }
        isPlaying = vv.isPlaying
    }

    var closing by remember(storagePath) { mutableStateOf(false) }
    fun closeViewer() {
        if (closing) return
        closing = true
        scope.launch {
            if (!reduceMotion && sourceBounds != null && openingReady) opening.animateTo(0f, tween(220))
            onBack()
        }
    }
    androidx.activity.compose.BackHandler { closeViewer() }
    val elevated = Color(theme.cardColor)
    val foreground = Color(theme.primaryTextColor)
    val foregroundSecondary = Color(theme.secondaryTextColor)
    val primary = Color(theme.accentColor)
    val borderSubtle = Color(theme.dividerColor)

    LaunchedEffect(trimming, storagePath) {
        if (trimming) {
            val source = editedCopy?.toURI()?.toString() ?: (providedModel as? java.io.File)?.toURI()?.toString() ?: signedUrl
            if (source != null) try {
                val copy = stageMediaSource(context, source, "chaty_trim_source_")
                ownedCopies += copy
                trimSource = copy.toURI().toString()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { trimming = false; snackbarHostState.showSnackbar("Could not prepare a local trim copy.") }
        } else trimSource = null
    }
    if (trimming && !restrictedViewOnce) {
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
        ImageEditorScreen(editedCopy?.toURI()?.toString() ?: signedUrl!!,
            onCancel = { editing = false },
            onApply = { file -> ownedCopies += file; editedCopy = file; editing = false },
        )
        return
    }
    if (error != null) androidx.compose.material3.AlertDialog(
        onDismissRequest = { closeViewer() },
        title = { Text("Attachment unavailable") },
        text = { Text(error!!) },
        confirmButton = { TextButton(onClick = { reloadAttempt++ }) { Text("Retry") } },
        dismissButton = { TextButton(onClick = { closeViewer() }) { Text("Close") } },
    )
    if (externalActionBusy || savingToGallery) androidx.compose.material3.AlertDialog(
        onDismissRequest = {},
        title = { Text(if (savingToGallery) "Saving gallery copy" else "Preparing attachment") },
        text = { CircularProgressIndicator() },
        confirmButton = {},
    )
    Scaffold(
        modifier = modifier,
        containerColor = elevated,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(elevated.copy(alpha = 0.85f))
                    .padding(start = 4.dp, end = 4.dp),
            ) {
                IconButton(onClick = ::closeViewer) {
                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.back,
                        semanticLabel = "Back",
                        color = foreground,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = foreground,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.W600,
                    )
                    Text(
                        size,
                        color = foregroundSecondary,
                        fontSize = 12.sp,
                    )
                }
                if (type in setOf("video", "audio") && !restrictedViewOnce && error == null) TextButton(onClick = { if (ownedActions) onOwnedEdit?.invoke() else trimming = true }, enabled = signedUrl != null || providedModel != null) { Text("Trim") }
                if (type == "image" && !title.endsWith(".gif", ignoreCase = true) && !restrictedViewOnce && error == null) IconButton(onClick = { if (ownedActions) onOwnedEdit?.invoke() else editing = true }, enabled = signedUrl != null || providedModel != null) {
                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.edit, semanticLabel = "Edit a copy", color = foreground)
                }
                if (!restrictedViewOnce && error == null) IconButton(onClick = ::openExternally, enabled = signedUrl != null || providedModel != null) {
                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.openInNew,
                        semanticLabel = "Open with app",
                        color = foreground,
                    )
                }
                if (type in setOf("image", "video") && !restrictedViewOnce && error == null) IconButton(onClick = ::saveToGallery, enabled = signedUrl != null || providedModel != null) {
                    Icon(
                        Icons.Rounded.Download,
                        contentDescription = "Save to gallery",
                        tint = foreground,
                    )
                }
                if (!restrictedViewOnce && error == null) IconButton(onClick = ::share, enabled = signedUrl != null || providedModel != null) {
                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.share,
                        semanticLabel = "Share file link",
                        color = foreground,
                    )
                }
                Spacer(Modifier.width(4.dp)) // ChatySpacing.xs
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .onGloballyPositioned { targetBounds = it.boundsInWindow() },
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
                error != null -> Text(
                    error!!,
                    textAlign = TextAlign.Center,
                    color = foregroundSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(20.dp), // ChatySpacing.lg
                )

                // A provided image model can render without a URL. VideoView cannot:
                // its URI is resolved asynchronously, even for a leased local file.
                signedUrl == null && (type == "video" || providedModel == null) -> CircularProgressIndicator(
                    strokeWidth = 2.2.dp,
                    color = primary,
                )

                type == "image" -> ImageViewerContent(
                    model = providedModel ?: editedCopy?.toURI()?.toString() ?: signedUrl!!,
                    title = title,
                    primary = primary,
                    foregroundSecondary = foregroundSecondary,
                    theme = theme,
                    onReady = { imageReady = true },
                )

                type == "video" -> {
                    val url = signedUrl!!
                    key(url) {
                    Column(
                        Modifier.fillMaxSize(),
                    ) {
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth(),
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
                                    .pointerInput(videoReady) {
                                        detectTapGestures { toggleVideo() }
                                    },
                            ) {
                                AndroidView(
                                    factory = { ctx ->
                                        VideoView(ctx).apply {
                                            setVideoURI(Uri.parse(url))
                                            setOnPreparedListener { mp ->
                                                mp.isLooping = false
                                                videoWidth = mp.videoWidth
                                                videoHeight = mp.videoHeight
                                                durationMs = mp.duration
                                                videoReady = true
                                            }
                                            setOnErrorListener { _, _, _ ->
                                                videoFailed = true
                                                true
                                            }
                                            setOnCompletionListener {
                                                isPlaying = false
                                            }
                                            videoView = this
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                                if (videoFailed) {
                                    // Dart fallback when the controller never
                                    // initializes.
                                    MediaViewerFallbackCard(
                                        icon = Icons.Rounded.VideocamOff,
                                        text = "Unable to initialize this video.",
                                        foregroundSecondary = foregroundSecondary,
                                        theme = theme,
                                        actionLabel = if (restrictedViewOnce || error != null) null else "Open Securely",
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
                                                com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.playArrow,
                                                    semanticLabel = "Play",
                                                    color = foreground,
                                                    modifier = Modifier.size(54.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (videoReady && !videoFailed) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 18.dp, top = 10.dp, end = 18.dp, bottom = 18.dp),
                            ) {
                                IconButton(onClick = ::toggleVideo) {
                                    Icon(
                                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        tint = foreground,
                                    )
                                }
                                Text(formatPlaybackTime(positionMs), color = foregroundSecondary, fontSize = 12.sp)
                                Spacer(Modifier.width(8.dp))
                                VideoProgressBar(
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
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(formatPlaybackTime(durationMs), color = foregroundSecondary, fontSize = 12.sp)
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
                        actionLabel = if (restrictedViewOnce || error != null) null else "Open Securely",
                        onAction = ::openExternally,
                    )
                }
            }
        }
        }
    }
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
) {
    var scale by remember(model) { mutableStateOf(1f) }
    var offset by remember(model) { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewportW = constraints.maxWidth.toFloat()
        val viewportH = constraints.maxHeight.toFloat()
        Box(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(model, viewportW, viewportH) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val oldScale = scale
                        // _matrixScale: max(current * gesture, cover) then
                        // clamp to [minScale, maxScale]. Child is
                        // viewport-sized (constrained=true) and
                        // boundaryMargin is zero, so cover ==
                        // max(viewport.w / child.w, viewport.h / child.h)
                        // == 1f and the declared 0.7 minimum never applies.
                        val coverScale = 1f
                        val newScale = (oldScale * zoom)
                            .coerceAtLeast(coverScale)
                            .coerceIn(0.7f, 5f)
                        scale = newScale
                        val factor = newScale / oldScale
                        val zoomed = Offset(
                            x = centroid.x - (centroid.x - offset.x) * factor,
                            y = centroid.y - (centroid.y - offset.y) * factor,
                        ) + pan
                        // _exceedsBy clamp: transformed viewport must stay
                        // inside the child rect; on the reachable domain
                        // (s >= cover == 1) this is t in [(1-s)*W, 0].
                        val minX = minOf(0f, (1f - newScale) * viewportW)
                        val maxX = maxOf(0f, (1f - newScale) * viewportW)
                        val minY = minOf(0f, (1f - newScale) * viewportH)
                        val maxY = maxOf(0f, (1f - newScale) * viewportH)
                        offset = Offset(
                            zoomed.x.coerceIn(minX, maxX),
                            zoomed.y.coerceIn(minY, maxY),
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
        coil.compose.SubcomposeAsyncImage(
            model = coil.request.ImageRequest.Builder(LocalContext.current).data(model)
                .memoryCachePolicy(coil.request.CachePolicy.DISABLED).diskCachePolicy(coil.request.CachePolicy.DISABLED).build(),
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
@Composable
private fun VideoProgressBar(
    position: Float,
    buffered: Float,
    playedColor: Color,
    bufferedColor: Color,
    backgroundColor: Color,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember { mutableStateOf<Float?>(null) }
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
            colors = androidx.compose.material3.SliderDefaults.colors(activeTrackColor = playedColor, thumbColor = playedColor, inactiveTrackColor = backgroundColor),
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
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(24.dp).semantics { liveRegion = LiveRegionMode.Polite }, // ChatySpacing.xl
    ) {
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
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(theme.accentColor),
                    contentColor = Color(theme.onAccentColor),
                ),
                modifier = Modifier.width(180.dp),
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

private fun formatPlaybackTime(milliseconds: Int): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    else "%d:%02d".format(seconds / 60, seconds % 60)
}

/** Writes [source] (http(s) URL, content:// URI, or absolute file path) into the
 *  device gallery via MediaStore and returns the user-facing result text
 *  (GB copy: "Photo saved to gallery" / "Video saved to gallery"). */
private suspend fun saveMediaToGallery(context: android.content.Context, source: String, displayName: String, isVideo: Boolean): String {
    if (android.os.Build.VERSION.SDK_INT < 29) {
        return "Saving to gallery needs Android 10 or newer."
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
        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, (if (isVideo) "Movies" else "Pictures") + "/Chaty")
        put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(collection, values) ?: return "Save failed: the gallery refused the new item."
    try {
        resolver.openOutputStream(uri)?.use { out ->
            openMediaInput(context, source).use { input -> require(copyMediaBounded(input, out, com.example.chat.ui.media.FileSanitizationService.MAX_SAFE_FILE_SIZE.toLong()) > 0) }
        } ?: error("could not open the gallery item for writing")
        values.clear()
        values.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    } catch (cancelled: CancellationException) {
        resolver.delete(uri, null, null)
        throw cancelled
    } catch (_: Exception) {
        resolver.delete(uri, null, null)
        return "Could not save attachment. Check access and the 100 MB file limit."
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
