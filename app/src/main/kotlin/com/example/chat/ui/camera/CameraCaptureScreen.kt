package com.example.chat.ui.camera

import androidx.compose.ui.res.stringResource

import android.util.Log

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.chat.ui.kit.ChatyHapticsPort
import com.example.chat.ui.onboarding.ChatyPermissions
import com.example.chat.ui.onboarding.rememberPermissionRequester
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.max
import androidx.compose.ui.geometry.Offset
import androidx.core.content.ContextCompat
import androidx.camera.core.CameraState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** Exact port of lib/features/camera/camera_capture_screen.dart.
 *
 *  Full-screen camera capture, WhatsApp-style: live filtered viewfinder,
 *  shutter / flip / flash / gallery, an effects tray backed by the shared
 *  [EffectRegistry], face lenses via [LensCarousel]/[LensOverlay]/
 *  [FaceAnchorSolver]/[CameraFaceDetectionBridge], and a confirm pane with
 *  caption before anything sends.
 *
 *  Effects are honest end-to-end: in chat mode the look travels as message
 *  metadata and receivers render it too ([ApplyStoredEffect]); in story mode
 *  it is baked into the published image because statuses have no metadata
 *  channel. Document mode bakes the document-scan matrix into the file.
 *
 *  Privacy contract: preview analysis, lenses and effect baking run locally in this path;
 *  this composable does not upload frames. Explicit Send/Post hands confirmed output to
 *  the host, which owns delivery status. Unsent files tracked by the active camera entry
 *  are discarded on retake/close. Crash/process-death orphan cleanup is not verified:
 *  there is no guaranteed 24-hour deletion promise. See docs/media-camera-privacy.md.
 *
 *  CAM-R5-049 sound rule: no custom capture sounds. Leave the platform shutter behavior
 *  to the camera stack; do not add a completion ding or suppress platform sounds here.
 *  Actual platform audio output still needs device verification.
 *
 *  CameraX mapping (camera package -> CameraX):
 *  - CameraController(ResolutionPreset.high, enableAudio false) ->
 *    Preview + ImageCapture(CAPTURE_MODE_MAXIMIZE_QUALITY) +
 *    ImageAnalysis(KEEP_ONLY_LATEST) bound to the lifecycle.
 *  - availableCameras/firstWhere(back) -> CameraSelector.DEFAULT_BACK_CAMERA.
 *  - setFlashMode(torch/off) -> cameraControl.enableTorch; front camera
 *    resets the toggle like the Dart.
 *  - startImageStream throttle 66ms -> ImageAnalysis backpressure + 66ms gate
 *    in the analyzer, frames converted by [CameraFrameSampler]-compatible
 *    readers through [ImageProxy.toFrameData].
 *  - Permission -> canonical ChatyPermissions.camera +
 *    rememberPermissionRequester (ui/onboarding PermissionRequest.kt @ e0233f6+).
 *  - openAppSettings -> ChatyPermissions.openAppSettings.
 *  - PopScope discard -> BackHandler when a capture is staged.
 *  - App lifecycle: CameraX rebinds automatically with the lifecycle owner;
 *    the Dart's manual dispose/re-init maps to provider.unbindAll on dispose. */

enum class ChatyCaptureMode { chat, story }

/** Only the composer camera action selects frontVideo. Attachment/story defaults stay unchanged. */
enum class CameraEntryPreset { photoBack, composerFrontVideo }

enum class CaptureViewMode { photo, document, portrait, night, lenses, video }

/** Local-file result handed back only after explicit confirmation.
 *
 * Ownership table:
 * - Captured/picker copies: CameraOwnedFiles owns only files created by this entry.
 * - Effect-baked copies: registered through onCreated; superseded siblings stay entry-owned.
 * - Confirmed final copy: transfer(path) removes it from entry cleanup and returns ownedResult.
 * - Synchronous host rejection: throw from onResult; this screen closes the transferred result.
 * - Accepted host result: the host owns retry/durable storage and closes ownedResult when safe.
 * - Story bytes handoff: handoffCameraPhotoBytes closes its owned result in finally.
 * - Process-death orphans: in-session cleanup cannot prove deletion; startup sweep is outstanding.
 *
 * The callback is an ownership boundary, not an upload receipt. A host that accepts work for later
 * must retain the owned result until its send/retry policy no longer needs those bytes. The camera
 * supplies local paths, never a signed download URL, and does not enqueue an upload itself.
 */
data class ChatyCaptureResult(
    val path: String,
    val effectId: String,
    val effectIntensity: Double,
    val caption: String = "",
    val ownedResult: CameraOwnedResult? = null,
)

/** Binding phase only. Staging, sheets and native video state are separate guards.
 *
 * | Current | Signal | Next | Required side effect |
 * | Any | Camera permission denied/revoked | permissionDenied | Unbind photo; close video |
 * | permissionDenied/noCamera/ready | Permitted explicit photo bind | initializing | Cancel old bind; clear face generation/poses |
 * | initializing | Photo onReady or video Ready | ready | Refresh supported options and zoom |
 * | initializing | Native bind failure or 15s watchdog | noCamera | Show recovery; watchdog unbinds photo |
 * | ready | Native photo capture failure/video Error | noCamera | Keep Send uncalled; expose Retry/gallery |
 * | noCamera | Retry (no recording/paused draft) | initializing | Keep mode/options; recreate failed session |
 *
 * Staged document/photo paths suppress binds until retake/discard. A photo capture
 * requires ready + resumed lifecycle + no scanner work + no active capture.
 * Video Recording/Paused keep ready as a binding phase but use VideoSessionState
 * for stop/pause/review guards. This table is a source description, not a transition test.
 */
enum class CameraPhase { initializing, ready, permissionDenied, noCamera }

/** CameraX ImageProxy -> the sampler's frame contract (YUV_420_888 planes
 *  with row/pixel strides honored; bytes copied out). */
fun ImageProxy.toFrameData(): FrameData {
    val planesOut = planes.map { plane ->
        val buffer = plane.buffer.duplicate()
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val bytes = ByteArray(rowStride * height)
        // Copy the largest contiguous span the buffer holds; the readers only
        // index within [rowStride * height).
        val remaining = buffer.remaining()
        val count = minOf(remaining, bytes.size)
        buffer.get(bytes, 0, count)
        FramePlane(bytes, rowStride, pixelStride)
    }
    return FrameData(width, height, FrameFormat.yuv420, planesOut)
}

private class CameraSession(
    val context: Context,
    val lifecycleOwner: LifecycleOwner,
) {
    var provider: ProcessCameraProvider? = null
    var camera: androidx.camera.core.Camera? = null
    var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    var imageCapture: ImageCapture? = null
    var analysis: ImageAnalysis? = null
    var analysisAvailable: Boolean = true
        private set
    var preview: Preview? = null
    val analysisExecutor = Executors.newSingleThreadExecutor()
    val captureExecutor = Executors.newSingleThreadExecutor()
    var torchOn: Boolean = false
    // Flash mode for the ImageCapture use case (off/on/auto). The torch flag
    // above lights the LED while composing; this decides whether the shot
    // itself fires the flash (GB three-state flash parity).
    var captureFlashMode: Int = ImageCapture.FLASH_MODE_OFF
        private set

    fun applyCaptureFlashMode(mode: Int) {
        captureFlashMode = mode
        imageCapture?.flashMode = mode
    }
    var stabilization: Boolean = false
    var flashSupported = false
    var stabilizationSupported = false
    var portrait = false
    var portraitSupported = false
    var portraitActive = false
    var night = false
    var nightSupported = false
    var nightActive = false
    var hdr = false
    var hdrSupported = false
    var hdrActive = false
    var extensions: androidx.camera.extensions.ExtensionsManager? = null
    @Volatile var analyzeFaces = false
    @Volatile var analyzeSegment = false
    private var lastAnalysisNs = 0L

    suspend fun ensureProvider(): ProcessCameraProvider {
        provider?.let { return it }
        return withTimeout(12_000) {
            suspendCancellableCoroutine { continuation ->
                val future = ProcessCameraProvider.getInstance(context)
                future.addListener({
                    if (continuation.isActive) {
                        try {
                            val ready = future.get()
                            provider = ready
                            continuation.resume(ready)
                        } catch (error: Exception) {
                            continuation.resumeWithException(error)
                        }
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        }
    }

    suspend fun ensureExtensions() {
        if (extensions != null) return
        val p = provider ?: return
        extensions = withTimeout(8_000) {
            suspendCancellableCoroutine { continuation ->
                val future = androidx.camera.extensions.ExtensionsManager.getInstanceAsync(context,p)
                future.addListener({ if (continuation.isActive) {
                    try { continuation.resume(future.get()) }
                    catch (error:Exception) { continuation.resumeWithException(error) }
                } },ContextCompat.getMainExecutor(context))
            }
        }
    }

    fun bind(
        previewView: PreviewView,
        onFrame: (FrameData, Int) -> Unit,
        onError: (String) -> Unit,
        onReady: () -> Unit,
    ) {
        val provider = provider ?: return
        analysisAvailable = true
        camera?.cameraInfo?.cameraState?.removeObservers(lifecycleOwner)
        unbind()
        val baseSelector = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        val manager = extensions
        nightSupported = manager?.isExtensionAvailable(baseSelector,androidx.camera.extensions.ExtensionMode.NIGHT) == true
        portraitSupported = manager?.isExtensionAvailable(baseSelector,androidx.camera.extensions.ExtensionMode.BOKEH) == true
        nightActive = shouldUseNight(night,nightSupported)
        portraitActive = portrait && portraitSupported
        hdrSupported = manager?.isExtensionAvailable(baseSelector,androidx.camera.extensions.ExtensionMode.HDR) == true
        // Extensions are mutually exclusive; night/portrait win over HDR.
        hdrActive = hdr && hdrSupported && !nightActive && !portraitActive
        val selector = if (nightActive || portraitActive || hdrActive) try {
            manager!!.getExtensionEnabledCameraSelector(baseSelector,
                if(nightActive)androidx.camera.extensions.ExtensionMode.NIGHT
                else if(portraitActive)androidx.camera.extensions.ExtensionMode.BOKEH
                else androidx.camera.extensions.ExtensionMode.HDR)
        } catch (_:Exception) { nightActive=false; nightSupported=false; portraitActive=false; portraitSupported=false; hdrActive=false; hdrSupported=false; baseSelector } else baseSelector
        val info = provider.getCameraInfo(selector)
        flashSupported = info.hasFlashUnit()
        stabilizationSupported = Preview.getPreviewCapabilities(info).isStabilizationSupported
        val preview = Preview.Builder().apply {
            if (stabilizationSupported && !nightActive && !portraitActive) setPreviewStabilizationEnabled(stabilization)
        }.build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setFlashMode(captureFlashMode)
            .build()
        val analyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analyzer.setAnalyzer(analysisExecutor) { proxy ->
            try {
                val now = System.nanoTime()
                if (shouldAnalyzeCameraFrame(analyzeFaces || analyzeSegment,now,lastAnalysisNs)) {
                    lastAnalysisNs = now
                    onFrame(proxy.toFrameData(), proxy.imageInfo.rotationDegrees)
                }
            } finally {
                proxy.close()
            }
        }
        this.preview = preview
        imageCapture = capture
        analysis = analyzer
        try {
            val bound = try {
                if (nightActive || portraitActive) provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                else provider.bindToLifecycle(lifecycleOwner, selector, preview, capture, analyzer)
            } catch (_: IllegalArgumentException) {
                // Some cameras cannot supply all three streams at once. Keep
                // photo capture working without optional face analysis.
                provider.unbind(preview, capture, analyzer)
                analyzer.clearAnalyzer()
                analysisAvailable = false
                provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
            }
            camera = bound
            bound.cameraControl.enableTorch(torchOn && lensFacing == CameraSelector.LENS_FACING_BACK)
            bound.cameraInfo.cameraState.observe(lifecycleOwner) { state ->
                if (state.error != null) {
                    onError("The camera could not open (code ${state.error?.code}). Close another app using the camera, then tap Retry.")
                } else if (state.type == CameraState.Type.OPEN) {
                    onReady()
                }
            }
        } catch (error: Exception) {
            // HDR failures need the same one-time plain-photo retry as night/portrait.
            if (nightActive || portraitActive || hdrActive) {
                night = false; nightActive = false; portrait=false; portraitActive=false; hdr = false; hdrActive = false
                bind(previewView,onFrame,onError,onReady)
            // Previous raw native fallback: onError(error.message ?: "The camera could not start.")
            } else onError(context.getString(com.example.chat.R.string.camera_start_failed_recovery))
        }
    }

    /** Dart setFlashMode(torch/off): torch only applies to the back camera;
     *  a failed call reverts the toggle (caller mirrors the Dart catch). */
    fun setTorch(on: Boolean): Boolean {
        return try {
            if (camera?.cameraInfo?.hasFlashUnit() != true) return false
            camera?.cameraControl?.enableTorch(on) ?: return false
            torchOn = on
            true
        } catch (_: Exception) {
            false
        }
    }

    fun unbind() {
        runCatching { camera?.cameraInfo?.cameraState?.removeObservers(lifecycleOwner) }
        runCatching { preview?.let { provider?.unbind(it) } }
        runCatching { imageCapture?.let { provider?.unbind(it) } }
        runCatching { analysis?.clearAnalyzer() }
        runCatching { analysis?.let { provider?.unbind(it) } }
        camera = null
    }

    fun dispose() {
        runCatching { unbind() }
        runCatching { analysisExecutor.shutdown() }
        runCatching { captureExecutor.shutdown() }
    }
}

@Composable
fun ChatyCameraCaptureScreen(
    mode: ChatyCaptureMode,
    contactName: String? = null,
    deviceTier: DeviceCapabilityTier = DeviceCapabilityTier.midRange,
    callsAllowed: Boolean = true,
    onResult: (ChatyCaptureResult) -> Unit,
    onDismiss: () -> Unit,
    onVideoResult: ((FinalizedVideo) -> Unit)? = null,
    videoAuthPort: VideoAuthPort? = null,
    entryPreset: CameraEntryPreset = CameraEntryPreset.photoBack,
    videoHoldRequest: VideoHoldRequest? = null,
) {
    // Original host contracts crashed in release on a malformed entry:
    // require(videoHoldRequest == null || entryPreset == CameraEntryPreset.composerFrontVideo)
    // require(entryPreset != CameraEntryPreset.composerFrontVideo || videoAuthPort != null)
    val invalidEntry = (videoHoldRequest != null && entryPreset != CameraEntryPreset.composerFrontVideo) ||
        (entryPreset == CameraEntryPreset.composerFrontVideo && videoAuthPort == null)
    if (invalidEntry) {
        LaunchedEffect(videoHoldRequest) { videoHoldRequest?.close() }
        androidx.compose.foundation.layout.Column(
            Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.surface)
                .windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            // CAM-R4-004 prior: androidx.compose.material3.Text("Camera unavailable for this request", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
            androidx.compose.material3.Text(stringResource(com.example.chat.R.string.camera_request_unavailable), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
            // CAM-R4-004 prior: androidx.compose.material3.Text("Go back and try opening the camera again.", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.material3.Text(stringResource(com.example.chat.R.string.camera_request_reopen), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
            // CAM-R4-004 prior: androidx.compose.material3.TextButton(onClick = onDismiss, modifier = Modifier.cameraActionFocusRing().heightIn(min = 48.dp)) { androidx.compose.material3.Text("Go back") }
            androidx.compose.material3.TextButton(onClick = onDismiss, modifier = Modifier.cameraActionFocusRing().heightIn(min = 48.dp)) { androidx.compose.material3.Text(stringResource(com.example.chat.R.string.camera_go_back)) }
        }
        return
    }
    val context = LocalContext.current
    val cameraPowerSaveMode by rememberCameraPowerSaveMode()
    val cameraThermalLimited by rememberCameraThermalLimit()
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val ownedFiles = remember { CameraOwnedFiles() }

    val engine = remember { com.example.chat.ServiceLocator.effectEngine }
    val lensSchedulerScope = rememberCoroutineScope()
    val lensManager = remember {
        val lensPrefs = context.getSharedPreferences("chaty_camera_lens", Context.MODE_PRIVATE)
        LensManager(
            // Recents/favorites survive process death (LensStringListStore contract).
            prefs = object : LensStringListStore {
                override fun getStringList(key: String): List<String>? =
                    lensPrefs.getString(key, null)?.split('|')?.filter { it.isNotBlank() }
                override fun setStringList(key: String, value: List<String>) {
                    lensPrefs.edit().putString(key, value.joinToString("|")).apply()
                }
            },
            // Dart settle Timer -> composition-scoped coroutine delay.
            timerScheduler = LensTimerScheduler { delayMs, action ->
                val job = lensSchedulerScope.launch {
                    kotlinx.coroutines.delay(delayMs)
                    action()
                }
                LensTimerHandle { job.cancel() }
            },
        )
    }
    val faceSolver = remember { FaceAnchorSolver() }
    val faceDetection = remember { CameraFaceDetectionBridge(context) }
    // CAM-R2-044: recreation must restore the user's camera direction so a retry
    // re-binds the same camera, not the entry-preset default. Saveable mirror of
    // session.lensFacing; flipCamera is its only other writer.
    var savedLensFacing by androidx.compose.runtime.saveable.rememberSaveable(entryPreset) { mutableStateOf(if(entryPreset == CameraEntryPreset.composerFrontVideo) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK) }
    val session = remember(context, lifecycleOwner, entryPreset) { CameraSession(context, lifecycleOwner).apply {
        lensFacing = savedLensFacing
    } }

    var phase by remember { mutableStateOf(CameraPhase.initializing) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var flashOn by remember { mutableStateOf(false) }
    // GB three-state flash: off -> on -> auto -> off.
    var flashAuto by remember { mutableStateOf(false) }
    // ShutterOverlay parity: one white flash pulse per photo capture.
    var captureFlashSeq by remember { mutableStateOf(0) }
    val captureFlashAlpha = remember { androidx.compose.animation.core.Animatable(0f) }
    val reduceCaptureMotion = com.example.chat.ui.kit.rememberChatyReduceMotion()
    LaunchedEffect(captureFlashSeq, reduceCaptureMotion) {
        if (reduceCaptureMotion) captureFlashAlpha.snapTo(0f)
        else if (captureFlashSeq > 0) {
            captureFlashAlpha.snapTo(0.55f)
            captureFlashAlpha.animateTo(0f, androidx.compose.animation.core.tween(280))
        }
    }
    // CAM-R1-028: a flip unbinds+rebinds every use case (~300-800 ms frozen
    // preview); a 200 ms crossfade overlay masks the gap. Flip-triggered only:
    // options rebinds, video binds and retry never raise it. Cleared when the
    // bind resolves (ready or noCamera); the render gate also requires
    // phase == initializing. The marker is assigned only after bind preconditions.
    var flipOverlayPending by remember { mutableStateOf(false) }
    LaunchedEffect(phase) {
        if (phase != CameraPhase.initializing) flipOverlayPending = false
    }
    // GB DragGalleryStrip: recent gallery photos above the shutter, revealed by
    // dragging up (or tapping the handle). Hidden without photos permission.
    val recentStripAvailable = ChatyPermissions.isGranted(context, ChatyPermissions.photos)
    var recentStripOpen by remember { mutableStateOf(false) }
    var recentPhotos by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }
    LaunchedEffect(recentStripOpen) {
        if (recentStripOpen && recentPhotos.isEmpty()) {
            val loaded = withContext(Dispatchers.IO) { queryRecentImageUris(context) }
            if (loaded.isEmpty()) recentStripOpen = false else recentPhotos = loaded
        }
    }
    // Photos permission can be revoked in system settings while Chaty is in
    // the background: on resume, drop the cached strip contents and hide it.
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME &&
                !ChatyPermissions.isGranted(context, ChatyPermissions.photos)
            ) {
                recentPhotos = emptyList()
                recentStripOpen = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var effectsOpen by remember { mutableStateOf(false) }
    var lensBrowserOpen by remember { mutableStateOf(false) }
    var lensesDisabled by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    var sendingPhoto by remember { mutableStateOf(false) }
    var sentPhotoPath by remember { mutableStateOf<String?>(null) }
    // CAM-R2-044: options must survive recreation so a retry re-bind keeps the
    // user's capture settings. Small custom Saver; unknown enum names from an
    // older save reset honestly to defaults.
    val cameraCaptureOptionsSaver = androidx.compose.runtime.saveable.listSaver<CameraCaptureOptions, Any>(
        save = { listOf(it.aspect.name, it.timerSeconds, it.grid, it.level, it.stabilization, it.watermark, it.clarity, it.night, it.mirrorSelfies) },
        restore = { runCatching { CameraCaptureOptions(
            aspect = runCatching { CaptureAspect.valueOf(it[0] as String) }.getOrDefault(CaptureAspect.full),
            timerSeconds = it[1] as Int,
            grid = it[2] as Boolean,
            level = it[3] as Boolean,
            stabilization = it[4] as Boolean,
            watermark = it[5] as Boolean,
            clarity = it[6] as Boolean,
            night = it[7] as Boolean,
            mirrorSelfies = it[8] as Boolean,
        ) }.getOrNull() },
    )
    // Original: var options by remember { mutableStateOf(CameraCaptureOptions()) }
    var options by androidx.compose.runtime.saveable.rememberSaveable(stateSaver = cameraCaptureOptionsSaver) { mutableStateOf(CameraCaptureOptions()) }
    var optionsOpen by remember { mutableStateOf(false) }
    var flashSupported by remember { mutableStateOf(false) }
    var stabilizationSupported by remember { mutableStateOf(false) }
    var countdown by remember { mutableStateOf(0) }
    var scannerBusy by remember { mutableStateOf(false) }
    // Original name retained for review: var scannerError by remember { mutableStateOf<String?>(null) }
    // This notice covers the whole camera, not only document scanning.
    var transientNotice by remember { mutableStateOf<String?>(null) }
    val noticeAccessibilityManager = androidx.compose.ui.platform.LocalAccessibilityManager.current
    LaunchedEffect(transientNotice, noticeAccessibilityManager) {
        val shownNotice = transientNotice ?: return@LaunchedEffect
        val timeout = noticeAccessibilityManager?.calculateRecommendedTimeoutMillis(
            originalTimeoutMillis = 4_000L,
            containsIcons = false,
            containsText = true,
            containsControls = true,
        ) ?: 4_000L
        kotlinx.coroutines.delay(timeout.coerceAtLeast(4_000L))
        if (transientNotice == shownNotice) transientNotice = null
    }
    // Highest zoom ratio the camera has actually accepted (user device screenshot
    // 2026-10-30: a rejected preset must not stay offered). Float.MAX_VALUE until
    // a failure proves a lower ceiling.
    var workingMaxZoom by remember { mutableStateOf(Float.MAX_VALUE) }
    var timerJob by remember { mutableStateOf<Job?>(null) }
    var zoomRatio by remember { mutableStateOf(1f) }
    var hdrOn by remember { mutableStateOf(false) }
    var hdrSupported by remember { mutableStateOf(false) }
    var portraitSupported by remember { mutableStateOf(false) }
    var portraitActive by remember { mutableStateOf(false) }
    var nightSupported by remember { mutableStateOf(false) }
    var nightActive by remember { mutableStateOf(false) }
    var minZoom by remember { mutableStateOf(1f) }
    var maxZoom by remember { mutableStateOf(1f) }
    // Prior: var videoOpen by remember(entryPreset) { mutableStateOf(entryPreset == CameraEntryPreset.composerFrontVideo) }
    // CAM-R2-044: the chosen capture mode must survive recreation so a retry
    // re-bind keeps it (e.g. lenses) instead of reverting to the entry preset.
    var captureViewMode by androidx.compose.runtime.saveable.rememberSaveable(
        entryPreset,
        stateSaver = androidx.compose.runtime.saveable.Saver<CaptureViewMode, String>(
            save = { it.name },
            restore = { runCatching { CaptureViewMode.valueOf(it) }.getOrDefault(CaptureViewMode.photo) },
        ),
    ) { mutableStateOf(if(entryPreset == CameraEntryPreset.composerFrontVideo) CaptureViewMode.video else CaptureViewMode.photo) }
    // Keep the transient pipeline flag consistent with the restored mode.
    var videoOpen by remember(entryPreset) { mutableStateOf(captureViewMode == CaptureViewMode.video) }
    // ---- Inline video mode (user redesign, 2026-10-30): recording happens on the
    // shared PreviewView; the pinned auth-lease architecture is preserved and the
    // separate VideoCaptureHost screen is no longer used by this composable.
    var videoSession by remember { mutableStateOf<CameraVideoSession?>(null) }
    var videoState by remember { mutableStateOf<VideoSessionState?>(null) }
    var videoSeconds by remember { mutableStateOf(0) }
    var videoSnapshotAvailable by remember { mutableStateOf(false) }
    var videoLensEffectActive by remember { mutableStateOf(true) }
    var videoExitPrompt by remember { mutableStateOf(false) }
    var videoRetakePrompt by remember { mutableStateOf(false) }
    var videoStopPlayback by remember { mutableStateOf<(() -> Unit)?>(null) }
    val videoInvalidationSignal = remember { kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED) }
    val videoAuth = remember(videoAuthPort) { videoAuthPort?.let { port -> VideoScopeLease(port) { videoInvalidationSignal.trySend(Unit) } } }
    var documentReviewPath by remember { mutableStateOf<String?>(null) }
    var capturedPath by remember { mutableStateOf<String?>(null) }
    var caption by remember { mutableStateOf("") }
    var facePoses by remember { mutableStateOf<List<FacePose>>(emptyList()) }
    var faceHealth by remember { mutableStateOf(TrackingHealth.lost) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var cameraPermissionGranted by remember {
        mutableStateOf(ChatyPermissions.isGranted(context, ChatyPermissions.camera))
    }
    var bindJob by remember { mutableStateOf<Job?>(null) }

    val wantsFaceTracking = capturedPath == null &&
        (captureViewMode == CaptureViewMode.photo || captureViewMode == CaptureViewMode.lenses || captureViewMode == CaptureViewMode.video) &&
        lensManager.selected.requiresFaceTracking

    fun lensIdForEffect(effectId: String): String? = when (effectId) {
        "face_dog_filter" -> "lens_dog_ears"
        "face_sparkles" -> "lens_sparkles"
        "face_hearts_float" -> "lens_hearts_float"
        "face_neon_crown" -> "lens_neon_crown"
        "face_angel_halo" -> "lens_angel_halo"
        "face_sunglasses_aviator" -> "lens_sunglasses_aviator"
        else -> null
    }

    // Face frame pipeline: throttle 66ms, mirror for front camera, solver maps
    // into the cover-cropped preview space.
    var lastFaceFrameMs by remember { mutableStateOf(0L) }
    var lastFaceSeenElapsedMs by remember { mutableStateOf<Long?>(null) }
    var faceFrameGeneration by remember { mutableStateOf(0L) }
    var processingFaceFrame by remember { mutableStateOf(false) }
    var viewportSize by remember { mutableStateOf(LensSize(0f, 0f)) }

    // Live segmented background preview (bg_* effects): the same analysis
    // stream as face frames, throttled harder and composited off the main
    // thread. Failures are silent and leave the viewfinder unfiltered - the
    // capture-time bake stays the exact path. Never runs during night /
    // portrait / video / review, matching the face pipeline's discipline.
    val segmentationBridge = remember { SegmentationBridge(context) }
    val liveSegmentPreview = remember { LiveSegmentPreview(segmentationBridge) }
    DisposableEffect(segmentationBridge) { onDispose { segmentationBridge.dispose() } }
    var segmentPreviewFrame by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    // Ownership for live segment preview bitmaps (see SegmentFrameSlot). A
    // published frame is NEVER recycled - only dereferenced for the GC - while
    // frames rejected before publish (stale effect, disposed slot) are
    // recycled immediately. Before this, replaced frames were dropped with no
    // ownership rule at all.
    val segmentFrames = remember { SegmentFrameSlot<android.graphics.Bitmap> { frame -> frame.recycle() } }
    // Tap-to-focus feedback ring: PreviewView-space point of the last focus
    // tap, auto-cleared shortly after. Reference camera UIs (incl. GB's
    // AutofocusOverlay) show a ring where focus locked; a focus tap with no
    // visible response reads as dead.
    var focusTapPoint by remember { mutableStateOf<Offset?>(null) }
    var focusTapSeq by remember { mutableStateOf(0) }
    LaunchedEffect(focusTapSeq) {
        if (focusTapSeq > 0) {
            kotlinx.coroutines.delay(900)
            focusTapPoint = null
        }
    }
    var segmentPublishedKey by remember { mutableStateOf<String?>(null) }
    var processingSegmentFrame by remember { mutableStateOf(false) }
    var lastSegmentFrameMs by remember { mutableStateOf(0L) }
    val segmentEffectKey = if (engine.activeEffect.isSegmentationRequired &&
        (captureViewMode == CaptureViewMode.photo || captureViewMode == CaptureViewMode.lenses) &&
        !capturing && !videoOpen && capturedPath == null && documentReviewPath == null
    ) engine.activeEffect.shaderKey else null

    fun processSegmentFrame(frame: FrameData, rotationDegrees: Int) {
        // CAM-R3-040: state is read dynamically even by a previously bound callback.
        if (cameraPowerSaveMode || cameraThermalLimited) return
        val key = segmentEffectKey ?: return
        if (processingSegmentFrame) return
        val nowMs = android.os.SystemClock.elapsedRealtime()
        if (nowMs - lastSegmentFrameMs < CAMERA_SEGMENT_INTERVAL_MS) return
        lastSegmentFrameMs = nowMs
        processingSegmentFrame = true
        val intensity = engine.intensity
        val mirrored = session.lensFacing == CameraSelector.LENS_FACING_FRONT
        val renderGeneration = faceFrameGeneration
        // Keep mutable Compose state and cleanup on main; only native render runs on Default.
        // Original: scope.launch(Dispatchers.Default) { produced = liveSegmentPreview.render(...) }
        scope.launch {
            var produced: android.graphics.Bitmap? = null
            try {
                withContext(Dispatchers.Default) {
                    // Assign before the dispatcher return so cancellation still has an owner to clean.
                    produced = liveSegmentPreview.render(frame, rotationDegrees, mirrored, key, intensity)
                }
                val bmp = produced
                if (bmp != null) {
                    // Single-owner transfer: the frame is handed to the slot
                    // exactly once, inside a NonCancellable main-thread hop.
                    // NonCancellable guarantees the handoff runs to completion
                    // even if this render coroutine was cancelled while parked,
                    // so no catch path can recycle a frame the slot now owns.
                    // The slot either shows the frame or - rejected BEFORE
                    // publish (stale effect key, disposed slot) - recycles it.
                    // Never both, never the caller, never a published frame.
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main) {
                        val shown = segmentFrames.publish(bmp, accept = !cameraPowerSaveMode && !cameraThermalLimited && segmentEffectKey == key &&
                            renderGeneration == faceFrameGeneration &&
                            mirrored == (session.lensFacing == CameraSelector.LENS_FACING_FRONT) &&
                            intensity == engine.intensity)
                        if (shown) {
                            segmentPreviewFrame = segmentFrames.visible
                            segmentPublishedKey = key
                        }
                        produced = null // the slot owns the frame in every outcome
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                produced?.recycle()
                throw cancelled
            } catch (_: Exception) {
                // Best-effort preview: never interrupt capture.
                produced?.recycle()
            } finally {
                processingSegmentFrame = false
            }
        }
    }

    fun processFaceFrame(frame: FrameData, rotationDegrees: Int) {
        if (!wantsFaceTracking || capturing || processingFaceFrame) return
        // Use elapsed time so a wall-clock correction cannot keep a stale solve fresh.
        val nowMs = android.os.SystemClock.elapsedRealtime()
        val frameFacing = session.lensFacing
        val frameGeneration = faceFrameGeneration
        // Former redundant 45ms gate differed from the analyzer's actual 66ms budget.
        if (nowMs - lastFaceFrameMs < CAMERA_FACE_INTERVAL_MS) return
        lastFaceFrameMs = nowMs
        processingFaceFrame = true
        scope.launch {
            try {
                // Bitmap conversion already used Default internally, but model creation and
                // native pipeline.detect previously resumed on this screen's main scope.
                val detection = withContext(Dispatchers.Default) { faceDetection.detect(frame, rotationDegrees) }
                if (!wantsFaceTracking || capturing || frameFacing != session.lensFacing || frameGeneration != faceFrameGeneration) return@launch
                if (detection.faces.isNotEmpty()) lastFaceSeenElapsedMs = nowMs
                val preview = if (viewportSize.width <= 0f || viewportSize.height <= 0f) {
                    LensSize(frame.width.toFloat(), frame.height.toFloat())
                } else viewportSize
                val imageW = detection.imageWidth.toFloat()
                val imageH = detection.imageHeight.toFloat()
                val scale = max(preview.width / imageW, preview.height / imageH)
                val scaledW = imageW * scale
                val scaledH = imageH * scale
                val crop = LensRect(
                    (preview.width - scaledW) / 2f,
                    (preview.height - scaledH) / 2f,
                    (preview.width + scaledW) / 2f,
                    (preview.height + scaledH) / 2f,
                )
                val poses = faceSolver.update(
                    nowMs = nowMs,
                    rawFaces = detection.faces,
                    mirrored = session.lensFacing == CameraSelector.LENS_FACING_FRONT,
                    imageExtent = LensSize(imageW, imageH),
                    previewCrop = crop,
                    confidenceScale = 1.0,
                )
                facePoses = poses
                faceHealth = faceSolver.health
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Detection is best-effort. Never interrupt capture.
            } finally {
                processingFaceFrame = false
            }
        }
    }

    androidx.compose.runtime.SideEffect {
        session.analyzeFaces = wantsFaceTracking && !nightActive && !portraitActive && !capturing && !videoOpen && documentReviewPath == null
        // Previous analyzer gate did not consider battery saver.
        session.analyzeSegment = !cameraPowerSaveMode && !cameraThermalLimited && segmentEffectKey != null && !nightActive && !portraitActive
        if (cameraPowerSaveMode || cameraThermalLimited) {
            segmentFrames.clear()
            segmentPreviewFrame = segmentFrames.visible
        }
        // A key change (blur -> office/gradient, or deselect) must never keep the
        // previous effect's composite on screen while its replacement is in flight.
        if (segmentEffectKey != segmentPublishedKey) {
            segmentFrames.clear()
            segmentPreviewFrame = segmentFrames.visible
        }
    }

    val zoomRequests = remember(session) { kotlinx.coroutines.channels.Channel<() -> Unit>(kotlinx.coroutines.channels.Channel.CONFLATED) }
    var zoomRequestSequence by remember(session) { mutableStateOf(0L) }
    LaunchedEffect(zoomRequests) { dispatchCameraZoomRequests(zoomRequests) }
    fun requestCameraZoom(requested: Float) {
        val camera = if (videoOpen) videoSession?.boundCamera() else session.camera
        if (camera == null || phase != CameraPhase.ready || capturing) return
        val sequence = ++zoomRequestSequence
        zoomRequests.trySend(zoomAction@{
            fun current(): Boolean = sequence == zoomRequestSequence && ownedFiles.isOpen() &&
                phase == CameraPhase.ready && !capturing && cameraPermissionGranted &&
                lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                (if (videoOpen) videoSession?.boundCamera() else session.camera) === camera
            if (!current()) return@zoomAction
            val target = clampCameraZoom(requested, minZoom, effectiveZoomMax(maxZoom, workingMaxZoom))
            try {
                val result = camera.cameraControl.setZoomRatio(target)
                result.addListener(zoomResult@{
                    if (!current()) return@zoomResult
                    try { result.get(); zoomRatio = target }
                    catch (failure: Exception) {
                        val cancelled = generateSequence(failure as Throwable?) { it.cause }.any { it is androidx.camera.core.CameraControl.OperationCanceledException }
                        if (!cancelled) {
                            workingMaxZoom = maxOf(minZoom, zoomRatio)
                            transientNotice = "Zoom is unavailable on this camera."
                        }
                    }
                }, ContextCompat.getMainExecutor(context))
            } catch (_: Exception) { if (current()) transientNotice = "Zoom is unavailable on this camera." }
        })
    }

    // Prior power-only notice now shares the same explicit live-preview budget with thermal state.
    LaunchedEffect(cameraPowerSaveMode, cameraThermalLimited, segmentEffectKey) {
        if (segmentEffectKey != null) {
            if (cameraThermalLimited) transientNotice = context.getString(com.example.chat.R.string.camera_background_preview_thermal)
            else if (cameraPowerSaveMode) transientNotice = context.getString(com.example.chat.R.string.camera_background_preview_power_save)
        }
    }

    // Prior: fun bindCamera() {
    fun bindCamera(forFlip: Boolean = false) {
        val pv = previewView ?: return
        if (!cameraPermissionGranted || capturedPath != null || documentReviewPath != null || scannerBusy || videoOpen) return
        // Only an accepted bind owns the flip marker; unrelated binds clear it.
        flipOverlayPending = forFlip
        bindJob?.cancel()
        bindJob = scope.launch {
            faceFrameGeneration++
            faceSolver.reset()
            lastFaceSeenElapsedMs = null
            facePoses = emptyList()
            phase = CameraPhase.initializing
            errorMessage = null
            try {
                session.ensureProvider()
                try { session.ensureExtensions() } catch (_: Exception) { /* Photo stays available. */ }
                val analysisGeneration = faceFrameGeneration
                val pendingMainFrame = java.util.concurrent.atomic.AtomicBoolean(false)
                session.bind(
                    previewView = pv,
                    onFrame = { frame, rotation ->
                        // FrameData owns copied planes and does not depend on ImageProxy lifetime.
                        // Bound the Main queue to one copied frame per binding.
                        if (pendingMainFrame.compareAndSet(false, true)) scope.launch(Dispatchers.Main.immediate) {
                            try {
                                if (ownedFiles.isOpen() && analysisGeneration == faceFrameGeneration) {
                                    processFaceFrame(frame, rotation)
                                    processSegmentFrame(frame, rotation)
                                }
                            } finally { pendingMainFrame.set(false) }
                        }
                    },
                    onError = { message ->
                        errorMessage = message
                        phase = CameraPhase.noCamera
                    },
                    onReady = {
                        phase = CameraPhase.ready
                        lensesDisabled = !session.analysisAvailable
                        if (lensesDisabled) { faceFrameGeneration++; faceSolver.reset(); lastFaceSeenElapsedMs = null; facePoses = emptyList() }
                        portraitSupported = session.portraitSupported
                        portraitActive = session.portraitActive
                        if(captureViewMode==CaptureViewMode.night && !session.nightActive) { captureViewMode=CaptureViewMode.photo; transientNotice="Night unavailable. Photo restored." }
                        if(captureViewMode==CaptureViewMode.portrait && !portraitActive) { captureViewMode=CaptureViewMode.photo; transientNotice="Portrait unavailable. Photo restored." }
                        nightSupported = session.nightSupported
                        nightActive = session.nightActive
                        hdrSupported = session.hdrSupported
                        if (hdrOn && !session.hdrActive) {
                            hdrOn = false
                            // Original notice incorrectly called every mutual-exclusion case unsupported hardware.
                            // transientNotice = "HDR is unavailable on this camera."
                            transientNotice = when {
                                session.nightActive || session.portraitActive -> "HDR is off while Night or Portrait is active."
                                captureViewMode !in listOf(CaptureViewMode.photo, CaptureViewMode.lenses) -> "HDR is off in this capture mode."
                                else -> "HDR is unavailable on this camera."
                            }
                        }
                        if (nightActive) { faceFrameGeneration++; faceSolver.reset(); lastFaceSeenElapsedMs = null; facePoses = emptyList() }
                        if (options.night && !nightActive) { options = options.copy(night=false); transientNotice = "Night mode unavailable. Photo restored." }
                        flashSupported = session.flashSupported
                        stabilizationSupported = session.stabilizationSupported
                        val zoom = session.camera?.cameraInfo?.zoomState?.value
                        zoomRatio = zoom?.zoomRatio ?: 1f
                        minZoom = zoom?.minZoomRatio ?: 1f
                        maxZoom = zoom?.maxZoomRatio ?: 1f
                    },
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // Previous raw native fallback: errorMessage = error.message ?: "The camera could not start. Try reopening it."
                errorMessage = context.getString(com.example.chat.R.string.camera_start_failed_recovery)
                phase = CameraPhase.noCamera
            }
        }
    }

    val requestPermission = rememberPermissionRequester { granted, _ ->
        cameraPermissionGranted = granted
        if (!granted) phase = CameraPhase.permissionDenied
    }
    /** Inline video session on the shared PreviewView. The image session must be
     *  released first; CameraVideoSession owns the camera while video mode is open. */
    fun bindVideo() {
        val pv = previewView ?: return
        val lease = videoAuth ?: return
        if (!cameraPermissionGranted || capturedPath != null || documentReviewPath != null || scannerBusy || !videoOpen) return
        // Idempotence: every caller that legitimately re-binds (permission return,
        // leaving video mode, auth invalidation) closes and nulls the session
        // first. A live session must NOT be replaced: a duplicate bind closes the
        // session that may already hold the composer hold token's one-shot Start,
        // and the surviving session never receives it (117 RealHostVideoHoldProof
        // evidence: recording finalized ERROR_NO_VALID_DATA 3ms after start when a
        // second bind closed its session, then 20s of preview with no Recording).
        if (videoSession != null) return
        bindJob?.cancel()
        phase = CameraPhase.initializing
        errorMessage = null
        val sessionSelector = if (session.lensFacing == CameraSelector.LENS_FACING_FRONT) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        val vs = CameraVideoSession(pv.context, lifecycleOwner, pv, sessionSelector, options.stabilization, lease, { viewportSize }, { lensManager.selected }, { facePoses }, { engine.activeColorMatrix()?.let { m -> FloatArray(m.size) { i -> m[i].toFloat() } } }, maxDurationMillis = cameraVideoDurationLimitMillis(mode == ChatyCaptureMode.story)) { next ->
            videoState = next
            if (next is VideoSessionState.Ready || next is VideoSessionState.Recording || next is VideoSessionState.Paused) phase = CameraPhase.ready
            if (next is VideoSessionState.Ready) {
                // The video camera can expose a different zoom range than the photo session.
                videoSession?.boundCamera()?.cameraInfo?.zoomState?.value?.let { zoom ->
                    zoomRatio = zoom.zoomRatio; minZoom = zoom.minZoomRatio; maxZoom = zoom.maxZoomRatio
                }
                videoSnapshotAvailable = next.snapshotAvailable
                videoLensEffectActive = next.lensEffectActive
            }
            if (next is VideoSessionState.Error) { errorMessage = next.message; phase = CameraPhase.noCamera }
        }
        videoSession = vs
        vs.bind()
    }

    // Recording timer feeding the top-center time. The key stays stable across
    // pause/resume so pausing freezes the count instead of resetting it.
    val videoTimerActive = videoState is VideoSessionState.Recording || videoState is VideoSessionState.Paused
    LaunchedEffect(videoTimerActive) {
        if (videoTimerActive) {
            videoSeconds = 0
            while (true) { kotlinx.coroutines.delay(1_000); if (videoState is VideoSessionState.Recording) videoSeconds++ }
        } else videoSeconds = 0
    }

    // Auth invalidation: teardown follows the pinned host contract.
    LaunchedEffect(videoAuth) {
        for (ignored in videoInvalidationSignal) {
            videoStopPlayback?.invoke(); videoStopPlayback = null
            videoSession?.close(); videoSession = null
            // CAM-R2-047: explain security teardown before the capture surface disappears.
            // A screen-local notice would be lost immediately on dismiss.
            android.widget.Toast.makeText(context.applicationContext,
                context.getString(com.example.chat.R.string.camera_session_expired_reopen), android.widget.Toast.LENGTH_LONG).show()
            onDismiss()
            break // This capture entry is invalid; do not emit duplicate expiry messages.
        }
    }

    // Composer hold-to-record requests (front-video entry) drive the inline session.
    val videoHoldPhase = videoHoldRequest?.phase?.collectAsState()?.value
    LaunchedEffect(videoHoldRequest, videoHoldPhase, videoState, videoSession) {
        val video = videoSession ?: return@LaunchedEffect
        val port = videoAuthPort ?: return@LaunchedEffect
        val ready = videoState as? VideoSessionState.Ready
        when (videoHoldRequest?.next(ready != null && port.isSignedIn(port.snapshot()), ready?.boundFront == true)) {
            VideoHoldRequest.Command.Start -> if (videoAuth?.isCurrent() == true) video.start(requestAudio = true)
            VideoHoldRequest.Command.Stop -> video.stop()
            VideoHoldRequest.Command.Discard -> video.discard()
            null -> Unit
        }
    }

    LaunchedEffect(Unit) { requestPermission(ChatyPermissions.camera) }
    var lastPhotoBindingOptions by remember(session) { mutableStateOf<List<Boolean>?>(null) }
    LaunchedEffect(previewView, cameraPermissionGranted, capturedPath, documentReviewPath, scannerBusy, videoOpen, options.stabilization, options.night, captureViewMode, hdrOn) {
        if (!cameraPermissionGranted) {
            lastPhotoBindingOptions = null
            session.unbind()
            videoSession?.close(); videoSession = null
            phase = CameraPhase.permissionDenied
        } else if (videoOpen) {
            lastPhotoBindingOptions = null
            // Inline video owns the shared PreviewView: release the image session first.
            session.unbind()
            if (capturedPath == null && documentReviewPath == null && !scannerBusy) bindVideo()
        } else if (capturedPath == null && documentReviewPath == null && !scannerBusy) {
            videoSession?.close(); videoSession = null; videoState = null
            val desiredBinding = listOf(options.stabilization,
                options.night || captureViewMode == CaptureViewMode.night,
                captureViewMode == CaptureViewMode.portrait,
                hdrOn && captureViewMode in listOf(CaptureViewMode.photo, CaptureViewMode.lenses))
            if (lastPhotoBindingOptions != null && lastPhotoBindingOptions != desiredBinding) {
                // CAM-R2-008: only option-driven native changes settle; first open remains immediate.
                bindJob?.cancel()
                phase = CameraPhase.initializing // Do not capture with options the camera has not bound.
                kotlinx.coroutines.delay(300L)
            }
            lastPhotoBindingOptions = desiredBinding
            session.stabilization = options.stabilization
            session.night = options.night || captureViewMode == CaptureViewMode.night
            session.portrait = captureViewMode == CaptureViewMode.portrait
            // Original unscoped flag: session.hdr = hdrOn
            session.hdr = hdrOn && captureViewMode in listOf(CaptureViewMode.photo, CaptureViewMode.lenses) &&
                !session.night && !session.portrait
            bindCamera()
        }
        else { lastPhotoBindingOptions = null; session.unbind() }
    }
    // Re-check after returning from Android app settings. A granted permission
    // must not leave this composition stranded in its old denied state.
    DisposableEffect(lifecycleOwner, session, capturedPath, documentReviewPath, scannerBusy, videoOpen) {
        // Only a real pause->resume cycle re-binds: registering this observer on an
        // already-RESUMED lifecycle replays ON_CREATE/ON_START/ON_RESUME, and that
        // replay must not re-bind a session the LaunchedEffect just created.
        var wasPaused = false
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                wasPaused = true
                timerJob?.cancel()
                countdown = 0
            }
            if (event == Lifecycle.Event.ON_RESUME && wasPaused) {
                wasPaused = false
                val granted = ChatyPermissions.isGranted(context, ChatyPermissions.camera)
                cameraPermissionGranted = granted
                if (granted) {
                    if (videoOpen) bindVideo() else bindCamera()
                } else {
                    session.unbind()
                    videoSession?.close(); videoSession = null
                    phase = CameraPhase.permissionDenied
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(phase, previewView, cameraPermissionGranted) {
        if (phase == CameraPhase.initializing && previewView != null && cameraPermissionGranted) {
            kotlinx.coroutines.delay(15_000)
            if (phase == CameraPhase.initializing) {
                errorMessage = "The camera did not open. Close another app using it, then tap Retry. You can also pick from your gallery."
                phase = CameraPhase.noCamera
                session.unbind()
            }
        }
    }

    var flashRequestSequence by remember { mutableStateOf(0L) }
    fun toggleFlash() {
        // Original guard checked flash support alone, even after its camera was unbound.
        // if (!flashSupported) return
        if (!flashSupported || phase != CameraPhase.ready || videoOpen || capturing || !ownedFiles.isOpen()) return
        val camera = session.camera ?: return
        val requestedGeneration = faceFrameGeneration
        val requestedFacing = session.lensFacing
        val request = ++flashRequestSequence
        // GB three-state cycle: off -> on -> auto -> off. "on" also lights the
        // torch while composing; "auto" leaves the LED to the capture itself.
        val next = if (flashOn) "auto" else if (flashAuto) "off" else "on"
        val torchNext = next == "on"
        // Original enableTorch call was outside the error guard.
        // val future = camera.cameraControl.enableTorch(torchNext)
        val future = try { camera.cameraControl.enableTorch(torchNext) }
        catch (_: Exception) {
            transientNotice = "Flash could not change on this camera."
            return
        }
        future.addListener(flashResult@{
            // Old completions must never rewrite the new camera's flash mode or notice.
            if (request != flashRequestSequence || !ownedFiles.isOpen() ||
                requestedGeneration != faceFrameGeneration || requestedFacing != session.lensFacing ||
                session.camera !== camera || phase != CameraPhase.ready || videoOpen || !cameraPermissionGranted
            ) return@flashResult
            try {
                future.get()
                flashOn = torchNext
                flashAuto = next == "auto"
                session.torchOn = torchNext
                session.applyCaptureFlashMode(
                    when (next) {
                        "on" -> ImageCapture.FLASH_MODE_ON
                        "auto" -> ImageCapture.FLASH_MODE_AUTO
                        else -> ImageCapture.FLASH_MODE_OFF
                    }
                )
            } catch (_: Exception) {
                flashOn = false
                flashAuto = false
                session.torchOn = false
                session.applyCaptureFlashMode(ImageCapture.FLASH_MODE_OFF)
                transientNotice = "Flash could not change on this camera."
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun flipCamera() {
        if (phase == CameraPhase.initializing || capturing || countdown > 0 || scannerBusy || videoOpen) return
        ChatyHapticsPort.selection(view)
        session.lensFacing = if (session.lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        savedLensFacing = session.lensFacing
        if (session.lensFacing == CameraSelector.LENS_FACING_FRONT) {
            flashOn = false
            flashAuto = false
            session.torchOn = false
            session.applyCaptureFlashMode(ImageCapture.FLASH_MODE_OFF)
        }
        workingMaxZoom = Float.MAX_VALUE
        faceFrameGeneration++
        faceSolver.reset()
        lastFaceSeenElapsedMs = null
        facePoses = emptyList()
        // Prior peer marker was set before bindCamera early-return checks.
        bindCamera(forFlip = true)
    }

    fun stagePickedOrCaptured(path: String) {
        if (!ownedFiles.isOpen()) return
        capturedPath = path
        effectsOpen = false
    }

    // Shared by the photo picker and the GB recent-photos strip: copy the
    // picked image into app-owned cache, then stage it like any capture.
    fun stageRecentPhoto(uri: android.net.Uri) {
        if (!callsAllowed) {
            transientNotice = "Contact acceptance required - both users must accept before media can be shared."
            return
        }
        scope.launch {
            try {
                val dest = withContext(Dispatchers.IO) {
                    val mime = context.contentResolver.getType(uri)
                    require(mime == null || mime.startsWith("image/")) { "Choose an image, not another file type." }
                    File.createTempFile("chaty_picked_", ".jpg", context.cacheDir).also {
                        check(ownedFiles.own(it)) { "Camera session ended." }
                    }
                }
                try { withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        dest.outputStream().use { output ->
                            com.example.chat.ui.media.copyMediaBounded(input, output, com.example.chat.ui.media.FileSanitizationService.MAX_SAFE_FILE_SIZE.toLong())
                        }
                    } ?: error("Image source is unavailable")
                } } catch (failure: Throwable) { dest.delete(); throw failure }
                finally { if(!ownedFiles.isOpen() || !kotlinx.coroutines.currentCoroutineContext().isActive) dest.delete() }
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                withContext(Dispatchers.IO) { android.graphics.BitmapFactory.decodeFile(dest.path, bounds) }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) { dest.delete(); error("Selected file is not a readable image") }
                val selectedPath = dest.absolutePath
                if (captureViewMode == CaptureViewMode.document) documentReviewPath = selectedPath
                else stagePickedOrCaptured(selectedPath)
            } catch(cancelled:CancellationException) { throw cancelled
            } catch (_: Exception) {
                transientNotice = "Could not import this photo. Try another photo or try again."
            }
        }
    }

    // Dart FilePicker(type: image) -> Android Photo Picker (the Dart repo's
    // MainActivity used PickVisualMedia). Picked content is copied into the
    // cache so downstream code always has a file path, then document mode
    // bakes the scan matrix like the Dart.
    val galleryLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri: android.net.Uri? ->
        if (uri != null) stageRecentPhoto(uri)
    }


    fun pickFromGallery() {
        if (!callsAllowed) {
            // Contact not mutually accepted: block attachment and show auth dialog.
            // Messaging still works, but media/calls require both users to accept.
            transientNotice = "Contact acceptance required — both users must accept before media can be shared."
            return
        }
        ChatyHapticsPort.light(view)
        galleryLauncher.launch(
            androidx.activity.result.PickVisualMediaRequest(
                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
            ),
        )
    }

    fun captureNow(captureOptions: CameraCaptureOptions) {
        if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) || scannerBusy) return
        val capture = session.imageCapture ?: return
        if (phase != CameraPhase.ready || capturing) return
        capturing = true
        captureFlashSeq += 1
        val documentCapture = captureViewMode == CaptureViewMode.document
        val frontCapture = session.lensFacing == CameraSelector.LENS_FACING_FRONT
        val captureLens = lensManager.selected
        val capturePoses = if (isCameraFaceSolveFresh(android.os.SystemClock.elapsedRealtime(), lastFaceSeenElapsedMs) &&
            faceSolver.health != TrackingHealth.lost
        ) facePoses.toList() else emptyList()
        val captureViewport = viewportSize
        ChatyHapticsPort.light(view)
        val stamp = System.currentTimeMillis()
        // Original unguarded createTempFile could leave capturing stuck after storage failure.
        val out = try { File.createTempFile("chaty_capture_", ".jpg", context.cacheDir) }
        catch (_: Exception) {
            capturing = false
            transientNotice = "Could not create a photo copy. Check device storage and try again."
            return
        }
        if(!ownedFiles.own(out)) { capturing=false; return }
        val awaitingCallback = java.util.concurrent.atomic.AtomicBoolean(true)
        val callbackWatchdog = scope.launch {
            delay(30_000L)
            if (awaitingCallback.compareAndSet(true, false)) {
                // Do not accept a late callback into a new capture. Recovery requires rebind.
                runCatching { session.unbind() }
                capturing = false
                phase = CameraPhase.noCamera
                errorMessage = "The camera did not finish the photo. Tap Retry or choose a gallery photo."
                withContext(Dispatchers.IO) { runCatching { out.delete() } }
            }
        }
        try {
        val opts = ImageCapture.OutputFileOptions.Builder(out).build()
        capture.takePicture(opts, session.captureExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                if (!awaitingCallback.compareAndSet(true, false)) { out.delete(); return }
                if(!ownedFiles.isOpen()) { out.delete(); return }
                val work = scope.launch {
                    callbackWatchdog.cancel()
                    var path = out.absolutePath
                    if (!documentCapture && !session.nightActive && !session.portraitActive) {
                        // Original callback read the later mutable lens/poses with only a health gate.
                        val lens = captureLens
                        val poses = capturePoses
                        if (!lens.isNoLens && poses.isNotEmpty()) {
                            try {
                                // Original whole-byte-array + PNG stage + second decode/JPEG path retained:
                                // val bytes = composeLensIntoImage(
                                // sourceBytes = File(path).readBytes(),
                                // previewSize = if (captureViewport.width > 0 && captureViewport.height > 0) captureViewport
                                // else LensSize(1080f, 1920f),
                                // lens = lens,
                                // poses = poses,
                                // )
                                // val lensStamp = System.currentTimeMillis()
                                // val staged = File.createTempFile("chaty_lens_", ".png", context.cacheDir)
                                // if(!ownedFiles.own(staged)) return@launch
                                // staged.writeBytes(bytes)
                                // if(!ownedFiles.own(staged)) return@launch
                                // val jpg = File.createTempFile("chaty_lens_", ".jpg", context.cacheDir)
                                // if(!ownedFiles.own(jpg)) return@launch
                                // val ok = try {
                                // jpg.outputStream().use { os ->
                                // android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                // ?.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, os) == true
                                // }
                                // } catch (_: Exception) { false }
                                // if(!ownedFiles.own(jpg)) return@launch
                                // if (ok) {
                                // path = jpg.absolutePath
                                // try { staged.delete() } catch (_: Exception) {}
                                // } else {
                                // path = staged.absolutePath
                                // }
                                val baked = bakeCameraLensFile(File(path), context.cacheDir,
                                    if (captureViewport.width > 0 && captureViewport.height > 0) captureViewport else LensSize(1080f, 1920f),
                                    lens, poses, mirroredPreview = frontCapture, onCreated = { check(ownedFiles.own(it)) { "Camera session ended." } })
                                if (!ownedFiles.own(baked)) return@launch
                                path = baked.path
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: OutOfMemoryError) {
                                transientNotice = "Not enough memory for the lens copy. Original photo kept."
                            } catch (_: Exception) {
                                transientNotice = "The lens could not be saved. Original photo kept."
                            }
                        }
                    }
                    if (documentCapture) {
                        capturing = false
                        documentReviewPath = path
                        return@launch
                    }
                    var derived: String? = null
                    try {
                        path = withContext(Dispatchers.IO) {
                            CameraPhotoOutput.process(context, path, captureOptions,
                                if (viewportSize.height > 0) viewportSize.width / viewportSize.height else 9f / 16f,
                                frontCapture = frontCapture).also { derived=it; if(it != path) ownedFiles.own(File(it)) }
                        }
                    } catch(cancelled:CancellationException) { derived?.takeIf { it != out.absolutePath }?.let { File(it).delete() }; throw cancelled
                    } catch (_: Exception) {
                        transientNotice = "Could not apply capture options. Original photo kept; nothing was sent."
                    }
                    capturing = false
                    stagePickedOrCaptured(path)
                }
                work.invokeOnCompletion { if(!ownedFiles.isOpen()) out.delete() }
            }

            override fun onError(exception: ImageCaptureException) {
                if (!awaitingCallback.compareAndSet(true, false)) { out.delete(); return }
                out.delete()
                scope.launch {
                    callbackWatchdog.cancel()
                    capturing = false
                    // Native exception text can include local paths; keep the user notice generic.
                    // errorMessage = exception.message ?: "The photo could not be captured."
                    errorMessage = "The photo could not be captured. Tap Retry or pick from your gallery."
                    phase = CameraPhase.noCamera
                }
            }
        })
        } catch (_: Exception) {
            awaitingCallback.set(false)
            callbackWatchdog.cancel()
            out.delete()
            capturing = false
            transientNotice = "The photo could not start. Try again or reopen the camera."
        }
    }

    fun shutter() {
        if (countdown > 0) {
            timerJob?.cancel()
            timerJob = null
            countdown = 0
            return
        }
        if (scannerBusy || capturing) return
        if (videoOpen) {
            // Inline video: the shutter toggles recording; review follows on stop.
            val vs = videoSession ?: return
            when (videoState) {
                is VideoSessionState.Recording, is VideoSessionState.Paused -> vs.stop()
                is VideoSessionState.Ready -> if (videoAuth?.isCurrent() == true) vs.start(requestAudio = true)
                else -> Unit
            }
            return
        }

        val captureOptions = options
        timerJob = scope.launch {
            // Original inline loop: for (seconds in captureOptions.timerSeconds downTo 1)
            // { countdown = seconds; delay(1000) }; countdown = 0; captureNow(captureOptions)
            // Extracted without changing the wait/capture order so zero/one/cancellation are testable.
            runCameraCountdown(captureOptions.timerSeconds, onTick = { countdown = it }) {
                captureNow(captureOptions)
            }
        }
    }

    fun discard() {
        if (sendingPhoto) return
        ChatyHapticsPort.light(view)
        capturedPath = null
        ownedFiles.discard()
    }

    fun confirmAndSend() {
        val raw = capturedPath ?: return
        if (sendingPhoto || sentPhotoPath == raw) return
        sendingPhoto = true
        val sendCaption = caption.trim()
        val engineEffect = engine.activeEffect
        var path = raw
        var effectId = if (captureViewMode in listOf(CaptureViewMode.document,CaptureViewMode.night,CaptureViewMode.portrait)) "none" else engineEffect.id
        var intensity = if (captureViewMode in listOf(CaptureViewMode.document,CaptureViewMode.night,CaptureViewMode.portrait)) 1.0 else engine.intensity
        scope.launch {
            try {
            // Stories carry no metadata channel, so the look must live in the pixels.
            // Shader effects (pixelate, segmented backgrounds) have no receiver-side
            // renderer, so they bake into the pixels in EVERY photo mode and the
            // metadata tag is cleared - never a double-apply, never a claimed but
            // unrendered filter (fail-open bakes report applied=false). The exact
            // contract lives in ImageFxBaker.resolveCaptureEffectPlan (unit-tested).
            val effectPlan = ImageFxBaker.resolveCaptureEffectPlan(
                storyMode = mode == ChatyCaptureMode.story,
                effectId = effectId,
                hasRenderPath = engineEffect.colorMatrix != null || engineEffect.shaderKey != null,
                isShaderEffect = engineEffect.shaderKey != null,
                intensity = intensity,
            )
            if (captureViewMode != CaptureViewMode.document && effectPlan.bake) {
                try {
                    val baked = ImageFxBaker.bakeEffectIntoFile(context, path, engineEffect, intensity, onCreated={ ownedFiles.own(it) })
                    path = baked.path
                    val (tag, tagIntensity) = ImageFxBaker.tagAfterBake(effectPlan, baked.applied, effectId, intensity)
                    effectId = tag
                    intensity = tagIntensity
                } catch(cancelled:CancellationException) { throw cancelled
                } catch (_: Exception) {
                    transientNotice = "Could not apply this effect at full resolution. Original kept; nothing sent."
                    return@launch
                } catch (_: OutOfMemoryError) {
                    transientNotice = "Not enough memory for the full-resolution effect. Original kept; nothing sent."
                    return@launch
                }
            }
            if(!ownedFiles.isOpen()) return@launch
            val transferred = ownedFiles.transfer(path) ?: return@launch
            try { onResult(
                ChatyCaptureResult(
                    path = path,
                    effectId = effectId,
                    effectIntensity = intensity,
                    caption = sendCaption,
                    ownedResult = transferred,
                ),
            )
                sentPhotoPath = raw
                ChatyHapticsPort.success(view)
            } catch(error:Throwable) { transferred.close(); throw error }
            } finally { sendingPhoto = false }
        }
    }

    // Back dispatch contract: modal sheets handle their own Back while mounted. These host
    // handlers discard a staged photo/document, or ask before leaving an unsaved video.
    // A new overlay must dismiss itself before either host handler runs. Never bypass the
    // unsaved-video prompt or discard a photo while its synchronous send handoff is busy.
    // PopScope: back with a staged capture discards instead of leaving.
    BackHandler(enabled = !videoOpen && (capturedPath != null || documentReviewPath != null)) {
        if (documentReviewPath != null) { documentReviewPath = null; ownedFiles.discard() } else discard()
    }

    // Inline video: back leaves video mode; an unsaved draft confirms first
    // (pinned host prompt contract preserved).
    BackHandler(enabled = videoOpen) {
        if (videoSession?.hasDraft() == true) videoExitPrompt = true
        else { videoOpen = false; captureViewMode = CaptureViewMode.photo }
    }

    // The last enabled BackHandler wins: overlays dismiss before staged media or video.
    BackHandler(enabled = lensBrowserOpen || optionsOpen || effectsOpen) {
        when {
            lensBrowserOpen -> lensBrowserOpen = false
            optionsOpen -> optionsOpen = false
            effectsOpen -> effectsOpen = false
        }
    }

    DisposableEffect(session) {
        onDispose {
            // Stop the segment producer BEFORE dropping frames. A render still
            // in flight hands its frame to the disposed slot afterwards, which
            // recycles it because it can never be shown. The last visible
            // frame is dereferenced without recycle - teardown does not prove
            // the RenderThread released it.
            runCatching { session.analyzeSegment = false }
            runCatching { segmentFrames.dispose() }
            runCatching { segmentPreviewFrame = null }
            runCatching { zoomRequests.close() }
            runCatching { videoHoldRequest?.close() }
            runCatching { videoStopPlayback?.invoke() }
            runCatching { videoSession?.close() }
            runCatching { videoAuth?.close() }
            runCatching { videoInvalidationSignal.close() }
            runCatching { ownedFiles.close() }
            runCatching { timerJob?.cancel() }
            runCatching { bindJob?.cancel() }
            runCatching { session.dispose() }
            runCatching { faceDetection.dispose() }
            runCatching { lensManager.dispose() }
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (documentReviewPath != null) {
            InlineDocumentReview(documentReviewPath!!,
                onRetake = { documentReviewPath = null; ownedFiles.discard() },
                onCropped = { path -> if(ownedFiles.own(File(path))) { documentReviewPath = null; stagePickedOrCaptured(path) } },
            )
        } else if (capturedPath == null) {
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
            CameraCaptureViewfinder(
                videoLensRecorded = videoLensEffectActive,
                phase = phase,
                // CAM-R1-028: flip-only gate - options/video/retry binds stay bare.
                flipOverlay = flipOverlayPending && phase == CameraPhase.initializing,
                onRetryCamera = {
                    // This action only appears in noCamera, never during a live recording.
                    // Preserve camera, mode and options while recreating the failed session.
                    if (phase == CameraPhase.noCamera &&
                        videoState !is VideoSessionState.Recording && videoState !is VideoSessionState.Paused
                    ) {
                        if (videoOpen) {
                            videoSession?.close()
                            videoSession = null
                            bindVideo()
                        } else bindCamera()
                    }
                },
                errorMessage = errorMessage,
                captureViewMode = captureViewMode,
                effect = if (captureViewMode in listOf(CaptureViewMode.document,CaptureViewMode.night,CaptureViewMode.portrait)) EffectRegistry.allEffects.first { it.id == "none" } else engine.activeEffect,
                effectIntensity = engine.intensity,
                segmentPreview = segmentPreviewFrame,
                onPreviewView = { pv ->
                    previewView = pv
                    var touchDownX = 0f
                    var touchDownY = 0f
                    // GB live pinch-to-zoom: two-finger scale on the preview
                    // drives the same clamped zoom path as the presets/slider.
                    var pinchZoomed = false
                    val pinchDetector = android.view.ScaleGestureDetector(context,
                        object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
                            private var startSpan = 0f
                            private var startRatio = 1f
                            override fun onScaleBegin(detector: android.view.ScaleGestureDetector): Boolean {
                                startSpan = detector.currentSpan
                                startRatio = zoomRatio
                                return phase == CameraPhase.ready && startSpan > 0f
                            }
                            override fun onScale(detector: android.view.ScaleGestureDetector): Boolean {
                                val control = if (videoOpen) videoSession?.boundCamera()?.cameraControl
                                    else session.camera?.cameraControl
                                if (control == null || startSpan <= 0f) return false
                                val target = clampCameraZoom(startRatio * detector.currentSpan / startSpan, minZoom, effectiveZoomMax(maxZoom, workingMaxZoom))
                                // Previous per-pixel native request: control.setZoomRatio(target); zoomRatio = target
                                requestCameraZoom(target)
                                pinchZoomed = true
                                return true
                            }
                        })
                    pv.setOnTouchListener { _, event ->
                        pinchDetector.onTouchEvent(event)
                        when (event.action) {
                            android.view.MotionEvent.ACTION_DOWN -> {
                                touchDownX = event.x
                                touchDownY = event.y
                                pinchZoomed = false
                            }
                            // CAM-R2-041: no preview swipe/tap action behind overlaid controls.
                            // Previous: android.view.MotionEvent.ACTION_UP -> if (phase == CameraPhase.ready && !pinchZoomed) {
                            android.view.MotionEvent.ACTION_UP -> if (phase == CameraPhase.ready && !pinchZoomed &&
                                !effectsOpen && !lensBrowserOpen && !optionsOpen) {
                                val dx = event.x - touchDownX
                                val dy = event.y - touchDownY
                                val swipeThreshold = 56 * pv.resources.displayMetrics.density
                                // Lenses tab only: a decisive horizontal swipe on the
                                // live preview cycles lenses (left = next, right =
                                // previous); an ordinary tap still focuses.
                                val lensMode = captureViewMode == CaptureViewMode.lenses
                                if (lensMode && kotlin.math.abs(dx) > swipeThreshold &&
                                    kotlin.math.abs(dx) > 1.6f * kotlin.math.abs(dy)) {
                                    val catalog = kBuiltInLenses
                                    if (catalog.isNotEmpty()) {
                                        val current = catalog.indexOfFirst { it.id == lensManager.selected.id }
                                            .let { if (it >= 0) it else 0 }
                                        val next = if (dx < 0f) (current + 1) % catalog.size
                                            else (current - 1 + catalog.size) % catalog.size
                                        lensManager.select(catalog[next].id)
                                    }
                                } else {
                                    val point = pv.meteringPointFactory.createPoint(event.x, event.y)
                                    val action = androidx.camera.core.FocusMeteringAction.Builder(point)
                                        .setAutoCancelDuration(5, java.util.concurrent.TimeUnit.SECONDS).build()
                                    val focus = if (videoOpen) videoSession?.startFocusAndMetering(action)
                                    else session.camera?.cameraControl?.startFocusAndMetering(action)
                                    focus?.addListener({
                                        runCatching { focus.get() }.getOrNull()?.takeIf { it.isFocusSuccessful }?.let {
                                            view.announceForAccessibility("Focus locked")
                                        }
                                    }, ContextCompat.getMainExecutor(context))
                                    focusTapPoint = Offset(event.x, event.y)
                                    focusTapSeq += 1
                                }
                                pv.performClick()
                            }
                        }
                        true
                    }
                },
                onViewport = { viewportSize = it },
                lens = lensManager.selected,
                facePoses = facePoses,
                faceHealth = faceHealth,
                flashOn = flashOn,
                flashAuto = flashAuto,
                flashSupported = flashSupported,
                previewAspect = options.aspect.portraitRatio,
                effectsOpen = effectsOpen,
                onClose = onDismiss,
                onToggleOptions = { optionsOpen = !optionsOpen; if(optionsOpen)effectsOpen=false },
                onToggleFlash = ::toggleFlash,
                hdrOn = hdrOn,
                hdrSupported = hdrSupported && captureViewMode in listOf(CaptureViewMode.photo, CaptureViewMode.lenses) && !nightActive && !portraitActive,
                onToggleHdr = { hdrOn = !hdrOn },
                onToggleEffects = {
                    ChatyHapticsPort.selection(view)
                    effectsOpen = !effectsOpen
                    if(effectsOpen)optionsOpen=false
                },
                onOpenSettings = { ChatyPermissions.openAppSettings(context) },
                onPickFromGallery = ::pickFromGallery,
                focusTap = focusTapPoint,
                captureFlashAlpha = captureFlashAlpha.value,
            )
            if (options.grid || options.level) CameraGridAndLevel(options.grid, options.level,
                Modifier.align(Alignment.Center).then(options.aspect.portraitRatio?.let { Modifier.fillMaxWidth().aspectRatio(it) } ?: Modifier.fillMaxSize()))

                // Full-bleed preview remains mounted beneath bounded, safe chrome.
                androidx.compose.foundation.layout.Column(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(top = 64.dp, start = 12.dp, end = 12.dp)
                        .heightIn(max = (maxHeight - 288.dp).coerceIn(48.dp, 350.dp)),
                ) {
                    if (optionsOpen) CameraOptionsPanel(options, stabilizationSupported, nightSupported, { next -> options = next; if(next.night)captureViewMode=CaptureViewMode.night else if(captureViewMode==CaptureViewMode.night)captureViewMode=CaptureViewMode.photo }, { optionsOpen = false }, showPhotoTimer = captureViewMode != CaptureViewMode.video,
                        frontCamera = session.lensFacing == CameraSelector.LENS_FACING_FRONT)
                    // Original white-on-preview notice retained for review:
                    // transientNotice?.let { message -> TextButton(onClick = { transientNotice = null }) { Text(message + "  Dismiss", color = Color.White) } }
                    if (!lensManager.selected.isNoLens && captureViewMode in listOf(CaptureViewMode.photo, CaptureViewMode.lenses)) {
                        // CAM-R4-004 prior: androidx.compose.material3.Text("Saved lens overlays use a copy up to 2048 pixels on the longest side.",
                        androidx.compose.material3.Text(stringResource(com.example.chat.R.string.camera_lens_copy_size_note),
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth().background(androidx.compose.material3.MaterialTheme.colorScheme.surface).padding(8.dp))
                    }
                    transientNotice?.let { message ->
                        androidx.compose.material3.TextButton(
                            onClick = { transientNotice = null },
                            modifier = Modifier.cameraActionFocusRing().fillMaxWidth().semantics {
                                liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite
                            },
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
                                contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                            ),
                        ) // CAM-R4-004 prior: { androidx.compose.material3.Text(message + "  Dismiss") }
                        { androidx.compose.material3.Text(stringResource(com.example.chat.R.string.camera_notice_dismiss, message)) }
                    }
                }
                // CAM-R4-004 prior: if (countdown > 0) androidx.compose.material3.Text("$countdown\nTap shutter to cancel",
                if (countdown > 0) androidx.compose.material3.Text(stringResource(com.example.chat.R.string.camera_countdown_cancel, countdown), modifier = Modifier.align(Alignment.Center)
                    .background(androidx.compose.material3.MaterialTheme.colorScheme.surface, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .padding(16.dp).semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, fontSize = 24.sp)
                // Reference-grounded (Snap/IG recording frames): elapsed time sits
                // top-center while a video records.
                // Previous timer displayed elapsed time only.
                if (videoTimerActive) CameraCaptureTimerTop(videoSeconds,
                    Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(top = 8.dp),
                    remainingSeconds = cameraVideoRemainingSeconds(videoSeconds, cameraVideoDurationLimitMillis(mode == ChatyCaptureMode.story)),
                    muted = when (val current = videoState) {
                        is VideoSessionState.Recording -> !current.hasAudio
                        is VideoSessionState.Paused -> !current.hasAudio
                        else -> false
                    })
                androidx.compose.foundation.layout.Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .heightIn(max = maxHeight)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .verticalScroll(rememberScrollState())
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = .35f)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                if (lensesDisabled && captureViewMode == CaptureViewMode.lenses) androidx.compose.material3.Text(
                    "Lenses unavailable this session", color = androidx.compose.material3.MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.background(androidx.compose.material3.MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 12.dp, vertical = 8.dp).semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite })
                // CAM-R4-004 prior: if (portraitActive) androidx.compose.material3.Text("Portrait · device background blur",color=androidx.compose.ui.graphics.Color.White)
                if (portraitActive) androidx.compose.material3.Text(stringResource(com.example.chat.R.string.camera_portrait_active),color=androidx.compose.ui.graphics.Color.White)
                // CAM-R4-004 prior: if (nightActive) androidx.compose.material3.Text("Night · hold still while capturing",color=androidx.compose.ui.graphics.Color.White)
                if (nightActive) androidx.compose.material3.Text(stringResource(com.example.chat.R.string.camera_night_active),color=androidx.compose.ui.graphics.Color.White)
                val videoZoomReady = videoState is VideoSessionState.Ready || videoTimerActive
                CameraZoomControls(zoomRatio,minZoom,effectiveZoomMax(maxZoom,workingMaxZoom),enabled = phase == CameraPhase.ready && !capturing && (!videoOpen || videoZoomReady),forceSlider = videoTimerActive) { requested ->
                    // Previous unthrottled slider/preset handler retained inactive:
                    // val control = if (videoOpen) videoSession?.boundCamera()?.cameraControl else session.camera?.cameraControl
                    // if (com.example.chat.BuildConfig.DEBUG) Log.i("ZOOM-DIAG", "request=" + requested + " clamped=" + clampCameraZoom(requested,minZoom,maxZoom) + " current=" + zoomRatio + " range=[" + minZoom + "," + maxZoom + "] workingMax=" + workingMaxZoom + " controlNull=" + (control == null))
                    // if (control != null) {
                    // val result = control.setZoomRatio(clampCameraZoom(requested,minZoom,maxZoom))
                    // result.addListener({ try { result.get(); zoomRatio = clampCameraZoom(requested,minZoom,maxZoom)
                    // if (com.example.chat.BuildConfig.DEBUG) Log.i("ZOOM-DIAG", "accepted ratio=" + zoomRatio) }
                    // catch (e: Exception) {
                    // // A superseded drag request is cancelled by CameraX, not
                    // // a camera capability failure: collapsing workingMax here
                    // // hid the slider mid-drag (run 122 ZOOM-DIAG evidence:
                    // // OperationCanceledException for 1.17x while 1.36x was
                    // // accepted 0.3s later on the same camera).
                    // val cancelled = generateSequence(e as Throwable?) { it.cause }.any { it is androidx.camera.core.CameraControl.OperationCanceledException }
                    // if (!cancelled) {
                    // // Clamp the offered range to what the camera accepted,
                    // // so the failed preset disappears instead of failing again.
                    // workingMaxZoom = maxOf(minZoom, zoomRatio)
                    // transientNotice = "Zoom is unavailable on this camera."
                    // }
                    // // A rejection here is a failed REQUEST, not proof of
                    // // missing camera capability - log the exact error.
                    // // Original diagnostic appended e.message, which can contain provider details/paths.
                    // // Log.i("ZOOM-DIAG", "... error=" + e.javaClass.simpleName + ":" + e.message + "...")
                    // if (com.example.chat.BuildConfig.DEBUG) Log.i("ZOOM-DIAG", "rejected requested=" + requested + " error=" + e.javaClass.simpleName + " cancelled=" + cancelled + (if (cancelled) "" else " workingMax->" + workingMaxZoom))
                    // }
                    // }, ContextCompat.getMainExecutor(context))
                    // }
                    requestCameraZoom(requested)
                }
            if (lensBrowserOpen) {
                // Lens explorer: shares this screen's LensManager so selection,
                // recents and favorites stay in sync with the carousel.
                LensBrowserHost(
                    manager = lensManager,
                    onDismiss = { lensBrowserOpen = false },
                    deviceTier = deviceTier,
                )
            }
            CameraCaptureBottomControls(
                paneHeight = maxHeight,
                // CAM-R1-037: UI label only; selector/camera-switch behavior unchanged.
                frontCamera = session.lensFacing == CameraSelector.LENS_FACING_FRONT,
                phase = phase,
                captureViewMode = captureViewMode,
                effectsOpen = effectsOpen,
                capturing = capturing,
                videoActive = videoOpen,
                videoRecording = videoTimerActive,
                videoSeconds = videoSeconds,
                videoPaused = videoState is VideoSessionState.Paused,
                videoSnapshotAvailable = videoSnapshotAvailable,
                onSnapshot = { videoSession?.takeSnapshot { ok -> if (!ok) transientNotice = "Snapshot is unavailable while recording on this camera." } },
                nightSupported = nightSupported,
                portraitSupported = portraitSupported,
                videoSupported = videoAuthPort != null,
                engine = engine,
                lensManager = lensManager,
                faceHealth = faceHealth,
                deviceTier = deviceTier,
                lensIdForEffect = ::lensIdForEffect,
                onBrowseLenses = { lensBrowserOpen = true },
                onSelectEffect = { effect ->
                    ChatyHapticsPort.selection(view)
                    if (effect.isFaceTrackingRequired) {
                        lensIdForEffect(effect.id)?.let { lensManager.select(it) }
                        effectsOpen = false
                    } else {
                        engine.selectEffect(effect)
                    }
                },
                onSetViewMode = { m ->
                    if(!capturing && countdown==0) {
                        optionsOpen=false
                        options=options.copy(night=false)
                        if(m==CaptureViewMode.video && videoAuthPort!=null) {
                            timerJob?.cancel();countdown=0;bindJob?.cancel()
                            ChatyHapticsPort.selection(view)
                            effectsOpen=false;captureViewMode=m;videoOpen=true
                        } else if((m!=CaptureViewMode.night||nightSupported)&&(m!=CaptureViewMode.portrait||portraitSupported)) {
                            if (videoOpen && videoSession?.hasDraft() == true) {
                                // A draft exists: the pinned prompt owns the leave decision.
                                videoExitPrompt = true
                            } else {
                                ChatyHapticsPort.selection(view);captureViewMode=m;videoOpen=false
                                // Color filters open only from the filters button - the
                                // lenses tab shows the lens carousel, never the tray.
                                effectsOpen=false
                                faceFrameGeneration++; faceSolver.reset();lastFaceSeenElapsedMs=null;facePoses=emptyList()
                            }
                        }
                    }
                },
                onShutter = ::shutter,
                onTogglePause = {
                    val vs = videoSession
                    if (vs != null) when (videoState) {
                        is VideoSessionState.Recording -> vs.pauseRecording()
                        is VideoSessionState.Paused -> vs.resumeRecording()
                        else -> Unit
                    }
                },
                onFlip = ::flipCamera,
                onPickFromGallery = ::pickFromGallery,
                recentStripVisible = recentStripAvailable && !videoTimerActive && !effectsOpen,
                recentStripOpen = recentStripOpen,
                recentPhotos = recentPhotos,
                onToggleRecentStrip = { recentStripOpen = it },
                onPickRecent = { uri ->
                    if (ChatyPermissions.isGranted(context, ChatyPermissions.photos)) {
                        recentStripOpen = false
                        stageRecentPhoto(uri)
                    } else {
                        recentPhotos = emptyList()
                        recentStripOpen = false
                    }
                },
            )
                }
            }
        } else {
            CameraCaptureConfirmPane(
                path = capturedPath!!,
                mode = mode,
                contactName = contactName,
                effect = if (captureViewMode in listOf(CaptureViewMode.document,CaptureViewMode.night,CaptureViewMode.portrait)) EffectRegistry.allEffects.first { it.id == "none" } else engine.activeEffect,
                effectIntensity = if (captureViewMode in listOf(CaptureViewMode.document,CaptureViewMode.night,CaptureViewMode.portrait)) 1.0 else engine.intensity,
                caption = caption,
                onCaptionChange = { caption = it },
                onDiscard = ::discard,
                onConfirm = ::confirmAndSend,
                sending = sendingPhoto || sentPhotoPath == capturedPath,
            )
        }

        // ---- Inline video review + draft-loss prompts (pinned host contracts,
        // ported onto the shared preview; the separate host screen is gone). ----
        if (videoOpen) {
            val lease = videoAuth
            val review = videoState as? VideoSessionState.Review
            if (review != null && lease != null) {
                Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
                    VideoCaptureReview(review.video, lease,
                        registerStop = { videoStopPlayback = it },
                        onConfirm = if (onVideoResult != null) ({
                            // No asynchronous handoff is authorized by this short guard.
                            // A supplied callback must synchronously accept ownership.
                            if (lease.isCurrent()) videoSession?.takeReviewedVideo(review.video)?.let {
                                onVideoResult?.invoke(it)
                                videoOpen = false
                                captureViewMode = CaptureViewMode.photo
                            }
                        }) else null,
                        onRetake = { videoRetakePrompt = true },
                    )
                }
            }
            if (videoExitPrompt || videoRetakePrompt) androidx.compose.material3.AlertDialog(
                onDismissRequest = { videoExitPrompt = false; videoRetakePrompt = false },
                title = { androidx.compose.material3.Text("Discard this unsaved video?") },
                text = { androidx.compose.material3.Text("This is a temporary draft. It is not sent or saved. Closing the app can lose it.") },
                confirmButton = { androidx.compose.material3.Button(modifier = Modifier.cameraActionFocusRing(), shape = com.example.chat.ui.kit.ChatyActionShape, onClick = {
                    val leave = videoExitPrompt
                    videoStopPlayback?.invoke(); videoStopPlayback = null
                    videoSession?.discard()
                    videoExitPrompt = false; videoRetakePrompt = false
                    if (leave) { videoOpen = false; captureViewMode = CaptureViewMode.photo }
                }) { androidx.compose.material3.Text("Discard video") } },
                dismissButton = { androidx.compose.material3.Button(modifier = Modifier.cameraActionFocusRing(), shape = com.example.chat.ui.kit.ChatyActionShape, onClick = { videoExitPrompt = false; videoRetakePrompt = false }) { androidx.compose.material3.Text("Keep reviewing") } },
            )
        }
    }
}

// CAM-R4-034 prior UI modifier lines retained inactive:
//             androidx.compose.material3.TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { androidx.compose.material3.Text("Go back") }
//                             modifier = Modifier.fillMaxWidth().semantics {
//                 confirmButton = { androidx.compose.material3.Button(shape = com.example.chat.ui.kit.ChatyActionShape, onClick = {
//                 dismissButton = { androidx.compose.material3.Button(shape = com.example.chat.ui.kit.ChatyActionShape, onClick = { videoExitPrompt = false; videoRetakePrompt = false }) { androidx.compose.material3.Text("Keep reviewing") } },

/* Previous net234 fragments:
        camera?.cameraInfo?.cameraState?.removeObservers(lifecycleOwner)
        try {
            preview?.let { provider?.unbind(it) }
            imageCapture?.let { provider?.unbind(it) }
            analysis?.let { provider?.unbind(it) }
        } catch (_: Exception) {}
        unbind()
        analysisExecutor.shutdown()
        captureExecutor.shutdown()
                    onFrame = { frame, rotation -> processFaceFrame(frame, rotation); processSegmentFrame(frame, rotation) },
                val mime = context.contentResolver.getType(uri)
                require(mime == null || mime.startsWith("image/")) { "Choose an image, not another file type." }
                val dest = File.createTempFile("chaty_picked_", ".jpg", context.cacheDir)
                if(!ownedFiles.own(dest)) return@launch
            session.analyzeSegment = false
            segmentFrames.dispose()
            segmentPreviewFrame = null
            zoomRequests.close()
            videoHoldRequest?.close(); videoStopPlayback?.invoke(); videoSession?.close(); videoAuth?.close(); videoInvalidationSignal.close(); ownedFiles.close(); timerJob?.cancel(); bindJob?.cancel(); session.dispose(); faceDetection.dispose(); lensManager.dispose()
                                    val focus = session.camera?.cameraControl?.startFocusAndMetering(action)
*/
