package com.example.chat.ui.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors

/** Explicitly typed output. Never pass to the existing image-only capture callback. */
data class FinalizedVideo(val file: File, val durationMs: Long, val hasAudio: Boolean, val mimeType: String = "video/mp4")
sealed interface VideoSessionState {
    data object Preparing : VideoSessionState
    data class Ready(val stabilizationEnabled: Boolean, val boundFront: Boolean = false, val snapshotAvailable: Boolean = false, val lensEffectActive: Boolean = true) : VideoSessionState
    data class Recording(val hasAudio: Boolean) : VideoSessionState
    data class Paused(val hasAudio: Boolean) : VideoSessionState
    data object Finalizing : VideoSessionState
    data class Review(val video: FinalizedVideo) : VideoSessionState
    data class Error(val message: String) : VideoSessionState
}

/** Instrumentation-only timing/observation hooks. Release builds ignore all setters at use sites. */
internal object CameraVideoNativeProof {
    @Volatile var beforeBind: (suspend () -> Unit)? = null
    @Volatile var observe: ((VideoSessionState) -> Unit)? = null
    /** Instrumentation-only: simulate a device that cannot bind the 3-use-case
     *  combination so tests can prove the no-snapshot fallback path. */
    @Volatile var forceSnapshotUnavailable: Boolean = false
    /** Instrumentation-only: skip both effect bind attempts so tests can isolate
     *  plain Preview+VideoCapture recording from the effect pipeline. */
    @Volatile var forceEffectUnavailable: Boolean = false
    // DEBUG-only: emulator cameras see no real face, so tests inject a synthetic
    // pose to prove the recorded-video compositing path with real output pixels.
    @Volatile var fakeLensPoses: List<FacePose>? = null
    fun reset() { beforeBind=null; observe=null; forceSnapshotUnavailable=false; forceEffectUnavailable=false; fakeLensPoses=null }
}

/** Main-thread API. Caller must release its image/analysis use cases BEFORE binding.
 * Owns only its own preview/video bindings, never unbindAll. No permission request,
 * upload, send, or capture starts automatically. */
class CameraVideoSession(
    context: Context,
    private val owner: LifecycleOwner,
    private val previewView: PreviewView,
    private val selector: CameraSelector,
    private val desiredStabilization: Boolean,
    private val auth: VideoScopeLease,
    private val viewportProvider: () -> LensSize = { LensSize(0f, 0f) },
    private val lensProvider: () -> LensDefinition? = { null },
    private val posesProvider: () -> List<FacePose> = { emptyList() },
    private val matrixProvider: () -> FloatArray? = { null },
    private val maxDurationMillis: Long = 300_000L,
    private val onState: (VideoSessionState) -> Unit,
) : DefaultLifecycleObserver, AutoCloseable {
    private val app = context.applicationContext
    private val main = ContextCompat.getMainExecutor(app)
    private val io = Executors.newSingleThreadExecutor()
    private var provider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var capture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var camera: androidx.camera.core.Camera? = null
    private var overlayProcessorRef: ColorLensSurfaceProcessor? = null
    private var snapshot: ImageCapture? = null
    private var recordingAudio = false
    private var activeFile: File? = null
    private var reviewFile: File? = null
    private var closed = false
    private var busy = false
    private var storagePreflightPending = false
    private var lowBatteryWarningShown = false
    private val recorderTransitions = kotlinx.coroutines.sync.Mutex()
    private var recorderPhase = CameraRecorderPhase.Idle
    private fun recorderTransition(action: () -> Unit) {
        checkMain()
        if (!recorderTransitions.tryLock()) {
            if (com.example.chat.BuildConfig.DEBUG) android.util.Log.d("CameraRecorder", "transition-busy")
            return
        }
        try { action() } finally { recorderTransitions.unlock() }
    }
    private var generation = 0
    private var stabilized = false
    private var binding = false
    private var bindSequence = 0
    private var stopSequence = 0
    // True only while a discard-triggered stop is being scheduled; consumed by stop().
    private var discardPending = false
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var boundFront = false
    // Retain real bind capabilities across discard/stale completion/preflight recovery.
    private var boundSnapshotAvailable = false
    private var boundLensEffectActive = false
    // Previous three recovery sites: publish(VideoSessionState.Ready(stabilized, boundFront))
    private fun retainedReadyState() = VideoSessionState.Ready(
        stabilizationEnabled = stabilized,
        boundFront = boundFront,
        snapshotAvailable = boundSnapshotAvailable,
        lensEffectActive = boundLensEffectActive,
    )

    init { require(maxDurationMillis > 0); owner.lifecycle.addObserver(this) }
    private fun publish(state: VideoSessionState) {
        // The auth barrier authorizes only a short decision. Compose/native work stays outside.
        if (auth.isCurrent()) {
            onState(state)
            if(com.example.chat.BuildConfig.DEBUG) try { CameraVideoNativeProof.observe?.invoke(state) } catch (_:Throwable) { /* Collect-only instrumentation cannot affect capture. */ }
        }
    }
    fun hasDraft(): Boolean = busy || activeFile != null || reviewFile != null
    fun bind() {
        checkMain()
        if (!auth.isCurrent() || closed || capture != null || binding) return
        binding = true
        // Preparing watchdog (photo path parity: the 15s init watchdog in CameraCaptureScreen).
        // A wedged provider future must not leave the session in Preparing forever; the
        // sequence guard keeps an older timer from failing a newer bind.
        val bindTicket = ++bindSequence
        mainHandler.postDelayed({
            if (!closed && binding && capture == null && bindTicket == bindSequence) {
                binding = false
                bindSequence++
                publish(VideoSessionState.Error("The camera did not open. Close another app using it, then try again."))
            }
        }, 15_000L)
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            binding = false
            publish(VideoSessionState.Error("Camera permission is needed to record video.")); return
        }
        publish(VideoSessionState.Preparing)
        val future = ProcessCameraProvider.getInstance(app)
        fun finishBinding() {
            // An old provider completion must not consume a newer bind's busy flag.
            if (bindTicket != bindSequence || !binding) return
            // Recheck after any debug timing suspension, and also on the ordinary path.
            if (!auth.isCurrent() || closed || capture != null || !binding ||
                ContextCompat.checkSelfPermission(app, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                !owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                binding = false
                return
            }
            binding = false
            try {
                val p = future.get()
                val info = p.availableCameraInfos.firstOrNull { selector.filter(listOf(it)).isNotEmpty() }
                    ?: error("Selected camera is unavailable")
                stabilized = desiredStabilization && Recorder.getVideoCapabilities(info).isStabilizationSupported
                val recorder = Recorder.Builder().setQualitySelector(
                    QualitySelector.from(Quality.FHD, FallbackStrategy.lowerQualityOrHigherThan(Quality.FHD)),
                ).build()
                val video = VideoCapture.Builder(recorder).setVideoStabilizationEnabled(stabilized).build()
                val view = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                // Reference camera UI offers a still snapshot while recording.
                // That needs Preview+VideoCapture+ImageCapture together, which not
                // every device supports: try the 3-use-case bind first and fall
                // back to video-only, reporting real availability in Ready so the
                // UI never draws a dead snapshot button.
                // Gallery save uses scoped storage (API 29+); below that a public
                // Pictures write needs a permission this app does not request, so
                // the snapshot combination is only attempted where saving can work.
                val canSaveSnapshot = android.os.Build.VERSION.SDK_INT >= 29
                val wantSnapshot = canSaveSnapshot && !(com.example.chat.BuildConfig.DEBUG && CameraVideoNativeProof.forceSnapshotUnavailable)
                // Selected face lenses composite into BOTH preview and the recorded
                // file, and the active color matrix reaches the recorded file too
                // (single CameraEffect targeting PREVIEW|VIDEO_CAPTURE with a
                // custom SurfaceProcessor) so video output is never a raw feed
                // behind a filtered preview. UseCaseGroup rejects multiple
                // effects sharing a target, so lens and color share this one
                // pipeline; the shader applies the matrix only to VIDEO_CAPTURE
                // output (preview color stays on the Compose modifier) and the
                // lens overlay draws only into VIDEO_CAPTURE output (the Compose
                // LensOverlay remains the single preview owner, gated by
                // videoLensRecorded) so the lens is never drawn twice. Drawing reuses the same
                // LensAnchorPainter as the photo bake path; poses map from
                // preview cover-crop space onto each frame like
                // composeLensIntoImage.
                val processor = ColorLensSurfaceProcessor(
                    lensProvider = { lensProvider() },
                    posesProvider = { (if (com.example.chat.BuildConfig.DEBUG) CameraVideoNativeProof.fakeLensPoses else null) ?: posesProvider() },
                    viewportProvider = { val pv = viewportProvider(); android.util.Size(pv.width.toInt(), pv.height.toInt()) },
                    matrixProvider = matrixProvider,
                    lensTargetMask = androidx.camera.core.CameraEffect.VIDEO_CAPTURE,
                    colorTargetMask = androidx.camera.core.CameraEffect.VIDEO_CAPTURE,
                )
                // Effect failures must surface, not silently record unfiltered:
                // the consumer reports through onState so the UI can show the
                // lens/filter is not applied instead of a dead effect.
                val overlay = object : androidx.camera.core.CameraEffect(
                    androidx.camera.core.CameraEffect.PREVIEW or androidx.camera.core.CameraEffect.VIDEO_CAPTURE,
                    processor.getGlExecutor(),
                    processor,
                    androidx.core.util.Consumer { err ->
                        main.execute {
                            // Previous callback exposed err.message and bypassed current-session checks.
                            // onState(VideoSessionState.Error("Camera effect failed; reopen the camera or disable the lens/filter. (" + (err.message ?: "unknown") + ")"))
                            if (!closed && auth.isCurrent() && overlayProcessorRef === processor) {
                                publish(VideoSessionState.Error(app.getString(com.example.chat.R.string.camera_effect_failed_recovery)))
                            }
                        }
                    },
                ) {}
                overlayProcessorRef = processor
                fun bindWithEffect(vararg extra: androidx.camera.core.UseCase): androidx.camera.core.Camera? = try {
                    val group = androidx.camera.core.UseCaseGroup.Builder()
                        .addUseCase(view).addUseCase(video)
                        .apply { extra.forEach { addUseCase(it) } }
                        .addEffect(overlay)
                        .build()
                    p.bindToLifecycle(owner, selector, group)
                } catch (_: Exception) { null }
                var actual: androidx.camera.core.Camera? = null
                var lensEffectActive = false
                val wantEffect = !(com.example.chat.BuildConfig.DEBUG && CameraVideoNativeProof.forceEffectUnavailable)
                if (wantSnapshot && wantEffect) {
                    val snap = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                    actual = bindWithEffect(snap)
                    if (actual != null) { snapshot = snap; lensEffectActive = true }
                }
                // Plain-recording fallback: devices rejecting the effect bind still
                // record video rather than failing the camera outright, with the UI
                // told the lens will NOT be in the recording (lensEffectActive=false).
                if (actual == null && wantEffect) actual = bindWithEffect().also { if (it != null) lensEffectActive = true }
                if (actual == null) actual = p.bindToLifecycle(owner, selector, view, video)
                boundFront = CameraSelector.DEFAULT_FRONT_CAMERA.filter(listOf(actual.cameraInfo)).isNotEmpty()
                provider = p; preview = view; capture = video; camera = actual
                boundSnapshotAvailable = snapshot != null
                boundLensEffectActive = lensEffectActive
                // Previous: publish(VideoSessionState.Ready(stabilized, boundFront, snapshot != null, lensEffectActive))
                publish(retainedReadyState())
            } catch (e: Exception) { publish(VideoSessionState.Error("Video camera could not start. Try Photo or reopen the camera.")) }
        }
        future.addListener({
            val barrier=if(com.example.chat.BuildConfig.DEBUG) CameraVideoNativeProof.beforeBind else null
            if(barrier == null) finishBinding()
            else owner.lifecycleScope.launch {
                try { barrier(); finishBinding() }
                catch(cancelled:kotlinx.coroutines.CancellationException) { if (bindTicket == bindSequence) binding=false; throw cancelled }
                catch(error:Throwable) { if (bindTicket == bindSequence) binding=false; throw error }
            }
        }, main)
    }

    fun start(requestAudio: Boolean) {
        checkMain()
        if (!auth.isCurrent() || closed || busy || capture == null ||
            !owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        // CAM-R3-039: storage IO precedes allocation/old-draft removal, off the UI thread.
        val ticket = generation
        busy = true
        storagePreflightPending = true
        io.execute {
            val enough = runCatching { hasCameraVideoStorage(android.os.StatFs(app.cacheDir.path).availableBytes) }.getOrDefault(false)
            val battery = runCatching {
                app.getSystemService(android.os.BatteryManager::class.java)
                    ?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: Int.MIN_VALUE
            }.getOrDefault(Int.MIN_VALUE)
            main.execute preflightResult@{
                busy = false
                storagePreflightPending = false
                if (closed) { io.shutdown(); return@preflightResult }
                if (ticket != generation) {
                    if (auth.isCurrent()) publish(retainedReadyState())
                    return@preflightResult
                }
                if (!auth.isCurrent() ||
                    !owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@preflightResult
                if (!enough) {
                    publish(VideoSessionState.Error("Not enough storage to record. Free at least 500 MB and try again."))
                } else {
                    if (!lowBatteryWarningShown && shouldWarnCameraLowBattery(battery)) {
                        lowBatteryWarningShown = true
                        android.widget.Toast.makeText(app, app.getString(com.example.chat.R.string.camera_low_battery_video_warning), android.widget.Toast.LENGTH_LONG).show()
                    }
                    startAfterStorageCheck(requestAudio)
                }
            }
        }
    }

    // Previous start body now runs only after the off-main storage guard above.
    private fun startAfterStorageCheck(requestAudio: Boolean) {
        checkMain()
        if (!auth.isCurrent() || closed || busy || !owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        val video = capture ?: return
        val audio = shouldRecordAudio(requestAudio, ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        reviewFile?.delete(); reviewFile = null
        val file = try {
            File.createTempFile("video-", ".mp4", File(app.cacheDir, "camera-video").apply { mkdirs() })
        } catch (_: Exception) {
            publish(VideoSessionState.Error("Not enough storage to start recording."))
            return
        }
        activeFile = file; busy = true
        val ticket = ++generation
        try {
            // CAM-R2-035 previous output had no native duration limit.
            // var pending = video.output.prepareRecording(app, FileOutputOptions.Builder(file).build())
            var pending = video.output.prepareRecording(app,
                FileOutputOptions.Builder(file).setDurationLimitMillis(maxDurationMillis).build())
            if (audio) pending = pending.withAudioEnabled()
            if (!auth.isCurrent()) { busy = false; activeFile = null; file.delete(); return }
            recorderPhase = CameraRecorderPhase.Starting
            recording = pending.start(main) recordingEvent@{ event ->
                // A timed-out/retired recording must never clear a replacement's handle or phase.
                if (activeFile !== file) {
                    if (event is VideoRecordEvent.Finalize && !io.isShutdown) io.execute { file.delete() }
                    return@recordingEvent
                }
                // Pause/resume UI changes follow native acknowledgement, not merely a request.
                // Previous Start handler published Recording without a command phase.
                if (event is VideoRecordEvent.Start && ticket == generation && recorderPhase == CameraRecorderPhase.Starting && !closed && auth.isCurrent()) {
                    recorderPhase = CameraRecorderPhase.Recording; recordingAudio = audio; publish(VideoSessionState.Recording(audio))
                }
                if (event is VideoRecordEvent.Pause && ticket == generation && recorderPhase == CameraRecorderPhase.Pausing && !closed && auth.isCurrent()) {
                    recorderPhase = CameraRecorderPhase.Paused; publish(VideoSessionState.Paused(recordingAudio))
                }
                if (event is VideoRecordEvent.Resume && ticket == generation && recorderPhase == CameraRecorderPhase.Resuming && !closed && auth.isCurrent()) {
                    recorderPhase = CameraRecorderPhase.Recording; publish(VideoSessionState.Recording(recordingAudio))
                }
                if (event is VideoRecordEvent.Finalize) {
                    recorderPhase = CameraRecorderPhase.Idle
                    recording = null
                    io.execute {
                        // Duration-limit completion saves bytes; validate those like an explicit stop.
                        // Previous: val metadata = if (!event.hasError()) validatedMetadata(file) else null
                        val expectedLimit = event.error == VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED
                        val metadata = if (!event.hasError() || expectedLimit) validatedMetadata(file) else null
                        main.execute finalizeResult@{
                            if (closed) io.shutdown()
                            if (activeFile !== file) { file.delete(); return@finalizeResult }
                            busy = false; activeFile = null
                            if (!auth.isCurrent() || closed || ticket != generation || metadata == null) {
                                file.delete()
                                if (!closed) {
                                    if (ticket != generation) publish(retainedReadyState())
                                    else publish(VideoSessionState.Error("Video was not saved. Please record again."))
                                }
                            } else {
                                reviewFile = file
                                publish(VideoSessionState.Review(FinalizedVideo(file, metadata.first, metadata.second)))
                            }
                        }
                    }
                }
            }
            // Start watchdog: native Start/Finalize must arrive; otherwise recover instead of
            // wedging in Starting, where stop() refuses and the user cannot end the capture.
            // Start timeout follows the file identity, including a discard during Starting.
            mainHandler.postDelayed({
                if (recorderPhase == CameraRecorderPhase.Starting && activeFile === file) {
                    runCatching { recording?.close() }
                    recording = null
                    recorderPhase = CameraRecorderPhase.Idle
                    busy = false
                    activeFile = null
                    generation++
                    if (!io.isShutdown) io.execute { file.delete(); if (closed) io.shutdown() }
                    if (!closed) publish(VideoSessionState.Error("Recording could not start. Please try again."))
                }
            }, 10_000L)
        } catch (e: Exception) {
            recorderPhase = CameraRecorderPhase.Idle
            busy = false; activeFile = null; file.delete()
            publish(VideoSessionState.Error("Recording could not start. Please try again."))
        }
    }
    /** The bound camera for this session's own preview/video binding, for zoom control.
     *  Null before bind completes and after close. */
    fun boundCamera(): androidx.camera.core.Camera? = camera
    /** Still snapshot while recording (reference camera UI). Only possible when the
     *  device bound the 3-use-case combination (Ready.snapshotAvailable). Saves the
     *  JPEG to the device gallery via MediaStore; the callback reports the real
     *  result - no fake success. */
    fun takeSnapshot(onResult: (Boolean) -> Unit) {
        checkMain()
        val snap = snapshot
        if (snap == null || !auth.isCurrent() || closed || recording == null) { onResult(false); return }
        if (android.os.Build.VERSION.SDK_INT < 29) { onResult(false); return }
        val requestGeneration = generation
        val requestCapture = capture
        fun requestCurrent(): Boolean = !closed && auth.isCurrent() && generation == requestGeneration &&
            capture === requestCapture && snapshot === snap
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "chaty-recording-${System.currentTimeMillis()}.jpg")
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Chaty")
        }
        val output = ImageCapture.OutputFileOptions.Builder(app.contentResolver, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values).build()
        snap.takePicture(output, main, object : ImageCapture.OnImageSavedCallback {
            // Previous callbacks unconditionally notified a possibly replaced session.
            // override fun onImageSaved(results: ImageCapture.OutputFileResults) { onResult(true) }
            // override fun onError(exception: ImageCaptureException) { onResult(false) }
            override fun onImageSaved(results: ImageCapture.OutputFileResults) { if (requestCurrent()) onResult(true) }
            override fun onError(exception: ImageCaptureException) { if (requestCurrent()) onResult(false) }
        })
    }
    /** Pause an active recording (reference camera UI: pause control beside stop).
     *  Same auth/main-thread discipline as start/stop; no-op unless recording. */
    // Previous optimistic transitions retained inactive:
    // fun pauseRecording() {
    // checkMain()
    // if (!auth.isCurrent() || closed) return
    // val active = recording ?: return
    // active.pause()
    // publish(VideoSessionState.Paused(recordingAudio))
    // }
    // fun resumeRecording() {
    // checkMain()
    // if (!auth.isCurrent() || closed) return
    // val active = recording ?: return
    // active.resume()
    // publish(VideoSessionState.Recording(recordingAudio))
    // }
    // // Previous stop: main-thread recording stop only, with no pending preflight to cancel.
    // fun stop() {
    // checkMain()
    // if (storagePreflightPending) { generation++; storagePreflightPending = false }
    // if (recording != null) { publish(VideoSessionState.Finalizing); recording?.stop() }
    // }
    fun pauseRecording() = recorderTransition {
        if (!auth.isCurrent() || closed || !canPauseCameraRecorder(recorderPhase)) return@recorderTransition
        val active = recording ?: return@recorderTransition
        recorderPhase = CameraRecorderPhase.Pausing
        try { active.pause() } catch (_: Exception) {
            recorderPhase = CameraRecorderPhase.Recording
            if (com.example.chat.BuildConfig.DEBUG) android.util.Log.d("CameraRecorder", "pause-rejected")
        }
    }
    fun resumeRecording() = recorderTransition {
        if (!auth.isCurrent() || closed || !canResumeCameraRecorder(recorderPhase)) return@recorderTransition
        val active = recording ?: return@recorderTransition
        recorderPhase = CameraRecorderPhase.Resuming
        try { active.resume() } catch (_: Exception) {
            recorderPhase = CameraRecorderPhase.Paused
            if (com.example.chat.BuildConfig.DEBUG) android.util.Log.d("CameraRecorder", "resume-rejected")
        }
    }
    fun stop() = recorderTransition {
        if (storagePreflightPending) { generation++; storagePreflightPending = false }
        val wasDiscard = discardPending
        discardPending = false
        val active = recording ?: return@recorderTransition
        if (!canStopCameraRecorder(recorderPhase)) return@recorderTransition
        recorderPhase = CameraRecorderPhase.Stopping
        if (!closed) publish(VideoSessionState.Finalizing)
        try { active.stop() } catch (_: Exception) {
            // Close is the native cancellation fallback, never claim a saved clip here.
            runCatching { active.close() }
            if (com.example.chat.BuildConfig.DEBUG) android.util.Log.d("CameraRecorder", "stop-rejected")
        }
        // Finalize watchdog: a wedged native finalize must not wedge the session (busy forever,
        // draft never cleanable, active file never reclaimed). Identity is the stop sequence,
        // not the generation, so a discard between stop and fire still recovers; the published
        // state follows whether the user discarded (back to Ready) or stopped (honest failure).
        val stopTicket = ++stopSequence
        val stopGeneration = generation
        mainHandler.postDelayed({
            if (recorderPhase == CameraRecorderPhase.Stopping && stopTicket == stopSequence) {
                runCatching { active.close() }
                recording = null
                recorderPhase = CameraRecorderPhase.Idle
                val stranded = activeFile
                activeFile = null
                busy = false
                if (!closed) {
                    if (wasDiscard || stopGeneration != generation) publish(retainedReadyState())
                    else { generation++; publish(VideoSessionState.Error("Video was not saved. Please record again.")) }
                }
                if (!io.isShutdown) io.execute { stranded?.delete(); if (closed) io.shutdown() }
            }
        }, 20_000L)
    }
    fun discard() {
        checkMain()
        if (closed) return
        generation++
        reviewFile?.delete(); reviewFile = null
        if (busy) { discardPending = true; publish(VideoSessionState.Finalizing); stop() }
        else publish(retainedReadyState())
    }
    /** Only transfers a finalized local video after explicit review confirmation. */
    fun takeReviewedVideo(video: FinalizedVideo): FinalizedVideo? {
        checkMain()
        if (closed || reviewFile != video.file || !video.file.isFile) return null
        var allowed = false
        auth.runIfCurrent { allowed = true }
        if (!allowed) return null
        reviewFile = null
        return video
    }
    // Pause finishes a local reviewable clip; it never confirms/transfers it.
    override fun onPause(owner: LifecycleOwner) { stop() }
    override fun onResume(owner: LifecycleOwner) { if (!closed && capture == null) bind() }
    override fun onDestroy(owner: LifecycleOwner) { close() }
    override fun close() {
        try { overlayProcessorRef?.release() } catch (_: Exception) {}
        checkMain()
        if (closed) return
        closed = true; generation++; owner.lifecycle.removeObserver(this)
        // All explicit recorder stop requests cross the same transition gate.
        stop(); reviewFile?.delete(); reviewFile = null
        // Finalize owns active-file cleanup, so do not delete a file still being encoded.
        if (!busy) activeFile?.delete()
        preview?.let { provider?.unbind(it) }; capture?.let { provider?.unbind(it) }
        snapshot?.let { provider?.unbind(it) }
        preview = null; capture = null; camera = null; snapshot = null
        boundSnapshotAvailable = false; boundLensEffectActive = false
        // Shutdown accepts already queued finalization work; no further starts are possible.
        if (!busy) io.shutdown()
    }
    private fun validatedMetadata(file: File): Pair<Long, Boolean>? = runCatching {
        if (!file.isFile || file.length() == 0L) return@runCatching null
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val frame = if (hasVideo) retriever.getFrameAtTime(0) else null
            val decoded = frame != null
            frame?.recycle()
            if (canReviewVideo(false, file.length(), duration, hasVideo) && decoded) {
                duration!! to (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes")
            } else null
        } finally { retriever.release() }
    }.getOrNull()
    /** Focus only this video session's bound camera, never the photo session's stale handle. */
    fun startFocusAndMetering(action: androidx.camera.core.FocusMeteringAction):
        com.google.common.util.concurrent.ListenableFuture<androidx.camera.core.FocusMeteringResult>? {
        checkMain()
        return if (closed) null else camera?.cameraControl?.startFocusAndMetering(action)
    }
    private fun checkMain() { check(android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) }
}

/* Previous net234 fragments:

*/
