package com.example.chat.ui.camera

import com.example.chat.R
import androidx.compose.ui.res.stringResource
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
// Duplicate import retained disabled after source audit: import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import java.io.File
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.max
import kotlin.math.roundToInt

/** UI halves of camera_capture_screen.dart (viewfinder, top bar, bottom
 *  controls, mode selector, shutter, confirm pane, no-camera pane). All
 *  layout constants transliterated from the Dart; tight breakpoint:
 *  shortestSide < 340 || height < 680. */

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CameraCaptureViewfinder(
    phase: CameraPhase,
    errorMessage: String?,
    captureViewMode: CaptureViewMode,
    videoLensRecorded: Boolean = true,
    effect: ChatyCameraEffect,
    effectIntensity: Double,
    segmentPreview: android.graphics.Bitmap? = null,
    onPreviewView: (PreviewView) -> Unit,
    onViewport: (LensSize) -> Unit,
    lens: LensDefinition,
    facePoses: List<FacePose>,
    faceHealth: TrackingHealth,
    flashOn: Boolean,
    flashAuto: Boolean = false,
    flashSupported: Boolean = true,
    previewAspect: Float? = null,
    effectsOpen: Boolean,
    onClose: () -> Unit,
    onToggleOptions: () -> Unit = {},
    onToggleFlash: () -> Unit,
    hdrOn: Boolean = false,
    hdrSupported: Boolean = false,
    onToggleHdr: () -> Unit = {},
    onToggleEffects: () -> Unit,
    onOpenSettings: () -> Unit,
    onPickFromGallery: () -> Unit,
    focusTap: Offset? = null,
    captureFlashAlpha: Float = 0f,
    flipOverlay: Boolean = false,
    onRetryCamera: (() -> Unit)? = null,
) {
    val previewLabel = stringResource(R.string.camera_preview_surface)
    val focusingLabel = stringResource(R.string.camera_focusing_here)
    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            // Previous value: Color.Black
            .background(rememberCameraOverlayColors().backdrop)
            .onGloballyPositioned {
                onViewport(LensSize(it.size.width.toFloat(), it.size.height.toFloat()))
            },
    ) {
        // Mount the surface before binding. Waiting for ready here deadlocks:
        // ready itself requires a PreviewView surface to bind the camera.
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    // CAM-R4-004 prior: contentDescription = "Camera preview surface"
                    contentDescription = previewLabel
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    onPreviewView(this)
                }
            },
            modifier = Modifier.align(Alignment.Center).clipToBounds()
                .then(if (previewAspect != null) Modifier.fillMaxWidth().aspectRatio(previewAspect) else Modifier.fillMaxSize())
                // Video records only color-matrix effects; other filters stay
                // off the preview in video mode instead of faking a recorded
                // effect that is not there.
                .then(if (captureViewMode != CaptureViewMode.video || effect.id == "none" || effect.colorMatrix != null) effectRenderModifier(effect, effectIntensity) else Modifier),
        )
        // Live segmented background preview (bg_*): the composited analysis
        // frame covers the raw preview surface while a segment effect is
        // active. Best effort - when no composited frame exists yet (or the
        // segmenter fails open) the unfiltered preview shows through. Not shown
        // in video mode: segment effects are not recorded into video.
        if (segmentPreview != null && effect.shaderKey?.startsWith("segment_") == true && captureViewMode != CaptureViewMode.video) {
            Image(
                bitmap = segmentPreview.asImageBitmap(),
                // CAM-R4-004 prior: contentDescription = "Live background filter preview",
                contentDescription = stringResource(R.string.camera_live_filter_preview),
                contentScale = ContentScale.Crop,
                modifier = Modifier.align(Alignment.Center)
                    .then(if (previewAspect != null) Modifier.fillMaxWidth().aspectRatio(previewAspect) else Modifier.fillMaxSize()),
            )
        }
        // Tap-to-focus feedback ring (reference camera UIs, incl. the GB
        // AutofocusOverlay, show a ring where focus locked; a focus tap with no
        // visible response reads as dead). Drawn in the PreviewView's own
        // coordinate space: same centered box, same aspect constraint.
        val focusRing = focusTap
        if (focusRing != null) {
            BoxWithConstraints(
                Modifier
                    .align(Alignment.Center)
                    .then(if (previewAspect != null) Modifier.fillMaxWidth().aspectRatio(previewAspect) else Modifier.fillMaxSize()),
            ) {
                Box(
                    Modifier
                        .offset { IntOffset((focusRing.x - 34.dp.toPx()).coerceIn(0f, (maxWidth.toPx() - 68.dp.toPx()).coerceAtLeast(0f)).roundToInt(), (focusRing.y - 34.dp.toPx()).coerceIn(0f, (maxHeight.toPx() - 68.dp.toPx()).coerceAtLeast(0f)).roundToInt()) }
                        .size(68.dp)
                        // Previous value: Color.White.copy(alpha = 0.9f)
                        .border(2.dp, rememberCameraOverlayColors().content.copy(alpha = 0.9f), CircleShape)
                        // CAM-R4-004 prior: .semantics { contentDescription = "Focusing here" },
                        .semantics { contentDescription = focusingLabel },
                )
            }
        }
        when (phase) {
            // CAM-R2-016 prior initializing presentation:
            // CameraPhase.initializing -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // CircularProgressIndicator(color = Color(0xB3FFFFFF))
            // }
            CameraPhase.initializing -> CameraInitializingFeedback()
            CameraPhase.permissionDenied -> CameraCaptureNoCameraPane(
                // CAM-R4-004 prior: title = "Camera permission needed",
                title = stringResource(R.string.camera_permission_title),
                // CAM-R4-004 prior: message = "Allow Chaty to use the camera to take photos and share them instantly.",
                message = stringResource(R.string.camera_permission_message),
                // CAM-R4-004 prior: actionLabel = "Open settings",
                actionLabel = stringResource(R.string.camera_open_settings),
                onAction = onOpenSettings,
            )
            CameraPhase.noCamera -> CameraCaptureNoCameraPane(
                // CAM-R4-004 prior: title = "Camera unavailable",
                title = stringResource(R.string.camera_unavailable_title),
                message = errorMessage
                    // CAM-R4-004 prior: ?: "No usable camera was found on this device. You can still share from your gallery.",
                    ?: stringResource(R.string.camera_unavailable_message),
                // Original gallery-only action retained:
                // actionLabel = "Pick from gallery", onAction = onPickFromGallery
                // CAM-R4-004 prior: actionLabel = if (onRetryCamera != null) "Retry" else "Pick from gallery",
                actionLabel = stringResource(if (onRetryCamera != null) R.string.camera_retry else R.string.camera_pick_gallery),
                onAction = onRetryCamera ?: onPickFromGallery,
                // CAM-R4-004 prior: secondaryActionLabel = if (onRetryCamera != null) "Pick from gallery" else null,
                secondaryActionLabel = if (onRetryCamera != null) stringResource(R.string.camera_pick_gallery) else null,
                onSecondaryAction = onPickFromGallery,
            )
            CameraPhase.ready -> Unit
        }
        // Lenses preview in photo, lenses AND video modes: the recorded video
        // composites the same overlay, so preview and output stay identical.
        // Video preview draws the lens only when the recording pipeline actually
        // composites it (OverlayEffect bound); otherwise the choice is disabled
        // below instead of faking a recorded effect that is not there.
        val lensVisible = captureViewMode == CaptureViewMode.photo || captureViewMode == CaptureViewMode.lenses ||
            (captureViewMode == CaptureViewMode.video && videoLensRecorded)
        if (phase == CameraPhase.ready && lensVisible) {
            LensOverlay(lens = lens, poses = facePoses, health = faceHealth)
        }
        if (phase == CameraPhase.ready && captureViewMode == CaptureViewMode.video && effect.id != "none" && effect.colorMatrix == null) {
            Text(
                // CAM-R4-004 prior: "Filter can't be recorded; video captures the unfiltered camera",
                stringResource(R.string.camera_filter_not_recorded),
                // Previous value: Color.White
                color = rememberCameraOverlayColors().content,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    // Centering/stack fix (measured): both video notices can be visible at
                    // once (face lens + null-matrix effect). The lens notice at top=96 with
                    // 12.sp text + 4dp vertical padding is ~26dp tall, so 118 left a ~6dp
                    // overlap. 132 = 96 + 26 chip + 10 gap (4dp grid). Looks-only.
                    // Previous: .padding(top = 118.dp)
                    .padding(top = 132.dp)
                    // Previous value: Color.Black.copy(alpha = 0.55f)
                    .background(rememberCameraOverlayColors().scrimStrong, RoundedCornerShape(10.dp))
                    // CAM-R4-030 previous spacing: .padding(horizontal = 10.dp, vertical = 4.dp),
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        if (phase == CameraPhase.ready && captureViewMode == CaptureViewMode.video && !videoLensRecorded && !lens.isNoLens) {
            Text(
                // CAM-R4-004 prior: "Lens can't be recorded on this device",
                stringResource(R.string.camera_lens_not_recorded),
                // Previous value: Color.White
                color = rememberCameraOverlayColors().content,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 96.dp)
                    // Previous value: Color.Black.copy(alpha = 0.55f)
                    .background(rememberCameraOverlayColors().scrimStrong, RoundedCornerShape(10.dp))
                    // CAM-R4-030 previous spacing: .padding(horizontal = 10.dp, vertical = 4.dp),
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        if (phase == CameraPhase.ready && captureViewMode == CaptureViewMode.document) {
            // Paper detection/corner review belongs to the real scanner flow,
            // not a decorative viewfinder frame pretending detection.
        }
        // Top bar: top = statusBar + 8, horizontal 8.
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(
                    androidx.compose.material3.MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f),
                    androidx.compose.ui.graphics.Color.Transparent)))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(top = 8.dp, start = 8.dp, end = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // CAM-R4-004 prior: CameraCaptureRoundAction(label = "Close camera", kind = "close", onClick = onClose)
            //143 prior reference: CameraCaptureRoundAction(label = stringResource(R.string.camera_close), kind = "close", onClick = onClose)
            CameraCaptureRoundAction(label = stringResource(R.string.camera_close_viewfinder), kind = "close", onClick = onClose)
            // User direction (device screenshot 2026-10-30): the action group is
            // centered as a unit so every icon stays visible and balanced.
            FlowRow(
                modifier = Modifier,
                horizontalArrangement = Arrangement.Center,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
            if (phase == CameraPhase.ready) {
                if (flashSupported) {
                    CameraCaptureRoundAction(
                        label = when {
                            // CAM-R4-004 prior: flashAuto -> "Turn flash off"
                            flashAuto -> stringResource(R.string.camera_flash_turn_off)
                            // CAM-R4-004 prior: flashOn -> "Switch flash to auto"
                            flashOn -> stringResource(R.string.camera_flash_auto_action)
                            // CAM-R4-004 prior: else -> "Turn flash on"
                            else -> stringResource(R.string.camera_flash_turn_on)
                        },
                        kind = if (flashAuto) "flash_auto" else "flash",
                        // CAM-R4-004 prior: actionState = when { flashAuto -> "Flash auto"; flashOn -> "Flash on"; else -> "Flash off" },
                        actionState = when { flashAuto -> stringResource(R.string.camera_flash_auto); flashOn -> stringResource(R.string.camera_flash_on); else -> stringResource(R.string.camera_flash_off) },
                        onClick = onToggleFlash,
                    )
                    // CAM-R4-030 previous spacing: Spacer(Modifier.width(10.dp))
                    Spacer(Modifier.width(12.dp))
                }
                if (hdrSupported) {
                    CameraCaptureRoundAction(
                        // CAM-R4-004 prior: label = if (hdrOn) "Turn HDR off" else "Turn HDR on",
                        label = if (hdrOn) stringResource(R.string.camera_hdr_turn_off) else stringResource(R.string.camera_hdr_turn_on),
                        kind = "hdr",
                        highlighted = hdrOn,
                        // CAM-R4-004 prior: actionState = if (hdrOn) "HDR on" else "HDR off",
                        actionState = if (hdrOn) stringResource(R.string.camera_hdr_on) else stringResource(R.string.camera_hdr_off),
                        onClick = onToggleHdr,
                    )
                    // CAM-R4-030 previous spacing: Spacer(Modifier.width(10.dp))
                    Spacer(Modifier.width(12.dp))
                }
                if ((captureViewMode == CaptureViewMode.photo || captureViewMode == CaptureViewMode.lenses)) {
                    CameraCaptureRoundAction(
                        // CAM-R4-004 prior: label = "Color filters",
                        label = stringResource(R.string.camera_color_filters),
                        // CAM-R4-004 prior: actionState = if(effectsOpen) "Expanded" else "Collapsed",
                        actionState = if(effectsOpen) stringResource(R.string.camera_expanded) else stringResource(R.string.camera_collapsed),
                        kind = "filters",
                        highlighted = effectsOpen || effectActive(effect),
                        onClick = onToggleEffects,
                    )
                    // CAM-R4-030 previous spacing: Spacer(Modifier.width(10.dp))
                    Spacer(Modifier.width(12.dp))
                }
            }
            // CAM-R4-004 prior: CameraCaptureRoundAction(label = "Camera settings", kind = "settings", onClick = onToggleOptions)
            CameraCaptureRoundAction(label = stringResource(R.string.camera_settings), kind = "settings", onClick = onToggleOptions)
            }
        }
        // ShutterOverlay parity: brief white flash on photo capture.
        if (captureFlashAlpha > 0f) {
            // Previous value: Color.White.copy(alpha = captureFlashAlpha)
            Box(Modifier.fillMaxSize().background(rememberCameraOverlayColors().captureFlash.copy(alpha = captureFlashAlpha)))
        }
        // CAM-R1-028: 200 ms crossfade scrim over the frozen preview while a
        // flip-triggered rebind is in flight; static (no fade) under
        // reduce-motion. Drawn after the initializing feedback so the two
        // never shimmer together on slow binds.
        val reduceFlipMotion = com.example.chat.ui.kit.rememberChatyReduceMotion()
        val flipOverlayAlpha by animateFloatAsState(
            targetValue = if (flipOverlay) 1f else 0f,
            animationSpec = tween(if (reduceFlipMotion) 0 else 200),
            label = "flipOverlay",
        )
        if (flipOverlayAlpha > 0f) {
            // Peer delta prior: Box(Modifier.fillMaxSize().clearAndSetSemantics { }.background(Color.Black.copy(alpha = flipOverlayAlpha)))
            // Existing viewfinder scrim token, matching the camera's other overlay.
            Box(Modifier.fillMaxSize().clearAndSetSemantics { }.background(androidx.compose.material3.MaterialTheme.colorScheme.scrim.copy(alpha = flipOverlayAlpha)))
        }
    }
}

private fun effectActive(effect: ChatyCameraEffect) = effect.id != "none"

/** _RoundIconAction: 42dp circle, white28 highlighted / white24, glyph 21. */
@Composable
// CAM-R4-031 prior visibility: private fun CameraCaptureRoundAction (body unchanged; isolated component tests).
internal fun CameraCaptureRoundAction(
    label: String,
    kind: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
    actionState: String? = null,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            // Previous value: Color.White.copy(alpha = 0.28f) / Color(0x99000000)
            .background(if (highlighted) rememberCameraOverlayColors().content.copy(alpha = 0.28f) else rememberCameraOverlayColors().scrim)
            // CAM-R4-034 previous: .clickable(onClick = onClick)
            .cameraActionFocusRing().clickable(onClick = onClick)
            .semantics { contentDescription = label; actionState?.let { stateDescription=it } },
        contentAlignment = Alignment.Center,
    ) {
        if (kind == "hdr") {
            androidx.compose.material3.Text(
                "HDR",
                // Previous value: Color.White
                color = rememberCameraOverlayColors().content,
                // CAM-R4-028 prior small text: fontSize = 11.sp,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            )
        } else {
            // Previous value: Color.White
            CameraGlyph(kind = kind, color = rememberCameraOverlayColors().content, size = 21.dp)
        }
    }
}

@Composable
// CAM-R4-031 prior visibility: private fun CameraCaptureNoCameraPane (body unchanged).
internal fun CameraCaptureNoCameraPane(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    secondaryActionLabel: String? = null,
    onSecondaryAction: () -> Unit = {},
) {
    val actionFocus = androidx.compose.runtime.remember { FocusRequester() }
    LaunchedEffect(title) { actionFocus.requestFocus() }
    Box(Modifier.fillMaxSize().semantics { liveRegion = LiveRegionMode.Polite }, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Previous value: Color(0x8AFFFFFF)
            CameraGlyph(kind = "camera", color = rememberCameraOverlayColors().contentSecondary, size = 46.dp)
            // CAM-R4-030 previous spacing: Spacer(Modifier.height(14.dp))
            Spacer(Modifier.height(16.dp))
            Text(
                title,
                // Previous value: Color.White
                color = rememberCameraOverlayColors().content,
                fontSize = 17.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.W800,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                // Previous value: Color(0x99FFFFFF)
                color = rememberCameraOverlayColors().contentSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 19.5.sp,
            )
            Spacer(Modifier.height(20.dp))
            OutlinedButton(modifier = Modifier.cameraActionFocusRing().heightIn(min = 48.dp).focusRequester(actionFocus), shape = com.example.chat.ui.kit.ChatyActionShape, onClick = onAction) {
                // Previous value: Color.White
                CameraGlyph(kind = "filters", color = rememberCameraOverlayColors().content, size = 18.dp)
                // CAM-R4-030 previous spacing: Spacer(Modifier.width(6.dp))
                Spacer(Modifier.width(8.dp))
                // Previous value: Color.White
                Text(actionLabel, color = rememberCameraOverlayColors().content)
            }
            if (secondaryActionLabel != null) {
                androidx.compose.material3.FilledTonalButton(
                    onClick = onSecondaryAction,
                    modifier = Modifier.cameraActionFocusRing().heightIn(min = 48.dp),
                    shape = com.example.chat.ui.kit.ChatyActionShape,
                ) { Text(secondaryActionLabel) }
            }
        }
    }
}

/** Recording accent: the user's design red (stop ring/glyph). Centralized so
 *  recording semantics stay red regardless of the configurable theme accent. */
internal val CameraRecordingRed = Color.Red

/** Active mode-strip text color: the reference camera (user video 2026-10-30)
 *  highlights the active mode in yellow; matches the user's zoom-preset yellow
 *  (CameraZoomControls 0xFFFFD54F) so there is one active-highlight token. */
internal val CameraModeActiveYellow = Color(0xFFFFD54F)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CameraCaptureBottomControls(
    paneHeight: androidx.compose.ui.unit.Dp,
    phase: CameraPhase,
    captureViewMode: CaptureViewMode,
    effectsOpen: Boolean,
    capturing: Boolean,
    nightSupported: Boolean = false,
    portraitSupported: Boolean = false,
    videoSupported: Boolean = false,
    videoActive: Boolean = false,
    videoRecording: Boolean = false,
    videoSeconds: Int = 0,
    videoPaused: Boolean = false,
    videoSnapshotAvailable: Boolean = false,
    engine: EffectEngine,
    lensManager: LensManager,
    faceHealth: TrackingHealth,
    deviceTier: DeviceCapabilityTier,
    lensIdForEffect: (String) -> String?,
    onSelectEffect: (ChatyCameraEffect) -> Unit,
    onSetViewMode: (CaptureViewMode) -> Unit,
    onShutter: () -> Unit,
    onTogglePause: () -> Unit = {},
    onSnapshot: () -> Unit = {},
    onFlip: () -> Unit,
    onPickFromGallery: () -> Unit,
    frontCamera: Boolean = false,
    onBrowseLenses: () -> Unit = {},
    recentStripVisible: Boolean = false,
    recentStripOpen: Boolean = false,
    recentPhotos: List<android.net.Uri> = emptyList(),
    onToggleRecentStrip: (Boolean) -> Unit = {},
    onPickRecent: (android.net.Uri) -> Unit = {},
) {
    val modeStripLabel = stringResource(R.string.camera_modes)
    val effectsTrayLabel = stringResource(R.string.camera_effects_tray)
    val unavailableEffectLabel = stringResource(R.string.camera_effect_unavailable)
    val galleryPickLabel = stringResource(R.string.camera_pick_image_gallery)
    val browseLensesLabel = stringResource(R.string.camera_browse_lenses)
    val snapshotLabel = stringResource(R.string.camera_snapshot_recording)
    val pauseLabel = stringResource(if (videoPaused) R.string.camera_resume_recording else R.string.camera_pause_recording)
    val ready = phase == CameraPhase.ready
    val reduceControlMotion = com.example.chat.ui.kit.rememberChatyReduceMotion()
    val allEffects = EffectRegistry.allEffects
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        val paneWidthDp = maxWidth.value.toInt()
        val paneHeightDp = paneHeight.value.toInt().coerceAtLeast(1)
        val tight = cameraControlsTight(paneWidthDp, paneHeightDp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // net233: root scrolling belongs to the bounded Screen parent, not this unbounded Column child.
                .background(
                    Brush.verticalGradient(
                        // Previous value: Color.Transparent / Color(0xDE000000) / Color.Black
                        listOf(rememberCameraOverlayColors().backdrop.copy(alpha = 0f), rememberCameraOverlayColors().backdrop.copy(alpha = 0.87f), rememberCameraOverlayColors().backdrop),
                    ),
                )
                // CAM-R4-030 conditional-spacing correction: .padding(bottom = (if (tight) 6 else 12).dp, top = (if (tight) 8 else 14).dp),
                .padding(bottom = (if (tight) 8 else 12).dp, top = (if (tight) 8 else 16).dp),
        ) {
            Column(Modifier.fillMaxWidth().heightIn(max = (paneHeightDp * .22f).dp)
                // CAM-R4-004 prior: .semantics { if(ready && effectsOpen && captureViewMode in listOf(CaptureViewMode.photo,CaptureViewMode.lenses)) contentDescription="Camera effects tray" }
                .semantics { if(ready && effectsOpen && captureViewMode in listOf(CaptureViewMode.photo,CaptureViewMode.lenses)) contentDescription=effectsTrayLabel }
                .verticalScroll(rememberScrollState())) {
            if (ready && captureViewMode == CaptureViewMode.document) {
                Text(
                    // CAM-R4-004 prior: "DOCUMENT · Capture, then adjust paper corners",
                    stringResource(R.string.camera_document_instruction),
                    // Previous value: Color.White
                    color = rememberCameraOverlayColors().content,
                    // CAM-R4-028 prior small text: fontSize = 11.sp,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    letterSpacing = 1.0.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.W700,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // CAM-R4-030 previous spacing: modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 7.dp),
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                )
            }
            if (ready && effectsOpen && (captureViewMode == CaptureViewMode.photo || captureViewMode == CaptureViewMode.lenses)) {
                // Effects tray: horizontal, items centered at rest, 66/74 tall.
                val listState = rememberLazyListState()
                LazyRow(
                    state = listState,
                    contentPadding = PaddingValues(
                        horizontal = max(8.0, (paneWidthDp - 52) / 2.0).dp,
                    ),
                    modifier = Modifier.height((if (tight) 66 else 74).dp),
                ) {
                    items(allEffects.size) { index ->
                        val effect = allEffects[index]
                        // Honest availability: effects with no working renderer are
                        // disabled, never shown as working. Segmented background
                        // effects preview live (best effort) and bake at capture.
                        val unavailable = effect.colorMatrix == null && effect.shaderKey == null &&
                            !effect.isFaceTrackingRequired && effect.id != "none"
                        val lensId = lensIdForEffect(effect.id)
                        val isSelected = if (effect.isFaceTrackingRequired) {
                            lensId != null && lensManager.selected.id == lensId
                        } else {
                            engine.activeEffect.id == effect.id
                        }
                        val scale by animateFloatAsState(
                            targetValue = if (isSelected) 1.06f else 0.88f,
                            // Original fixed animationSpec = tween(180, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)),
                            animationSpec = tween(if (reduceControlMotion) 0 else 180, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)),
                            label = "effect-scale",
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                // CAM-R4-030 previous spacing: .padding(horizontal = 5.dp)
                                .padding(horizontal = 8.dp)
                                .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (unavailable) 0.45f else 1f }
                                // CAM-R4-004 prior: .semantics { if (unavailable) stateDescription = "Not available yet" }
                                .semantics { if (unavailable) stateDescription = unavailableEffectLabel }
                                // CAM-R4-034 previous: .clickable(enabled = !unavailable) { onSelectEffect(effect) },
                                .cameraActionFocusRing().clickable(enabled = !unavailable) { onSelectEffect(effect) },
                        ) {
                            Box(
                                modifier = Modifier
                                    .size((if (tight) 42 else 48).dp)
                                    .clip(CircleShape)
                                    .background(
                                        effect.previewColor.copy(alpha = if (isSelected) 0.95f else 0.4f),
                                    )
                                    .border(
                                        width = (if (isSelected) 2.5 else 1.2).dp,
                                        // Previous value: Color.White / Color(0x4DFFFFFF)
                                        color = if (isSelected) rememberCameraOverlayColors().content else rememberCameraOverlayColors().contentDisabled,
                                        shape = CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                EffectGlyph(
                                    id = effect.id,
                                    category = effect.category,
                                    // Previous value: Color.Black / Color.White
                                    color = if (isSelected) rememberCameraOverlayColors().contentInverse else rememberCameraOverlayColors().content,
                                    size = (if (tight) 20 else 22).dp,
                                )
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(
                                effect.name,
                                // Previous value: Color.White / Color(0x99FFFFFF)
                                color = if (isSelected) rememberCameraOverlayColors().content else rememberCameraOverlayColors().contentSecondary,
                                // CAM-R4-028 prior small text: fontSize = 10.sp,
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                fontWeight = if (isSelected) {
                                    androidx.compose.ui.text.font.FontWeight.W700
                                } else {
                                    androidx.compose.ui.text.font.FontWeight.W500
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width((if (tight) 56 else 64).dp),
                            )
                        }
                    }
                }
                // CAM-R4-030 conditional-spacing correction: Spacer(Modifier.height((if (tight) 4 else 9).dp))
                Spacer(Modifier.height((if (tight) 4 else 12).dp))
            }
            }
            // Reference-grounded (Snap/IG recording frames): idle controls hide
            // while a video records; only the stop control and timer stay.
            if (!videoRecording) {
            // Snap-style text mode strip (product direction 2026-10-30): plain
            // text items, never capsule chips; the active mode is centered and
            // underlined. Tapping selects and centers; horizontal swipes only
            // scroll - selection never derives from scroll position, so the
            // strip can never change or revert a mode by itself. Fixed slot
            // width + symmetric content padding from the real viewport width
            // make animateScrollToItem land the active item exactly centered.
            val stripState = rememberLazyListState()
            // Unsupported modes are hidden entirely (user direction 2026-10-30):
            // never a truncated "unavailable" label consuming a slot. The active
            // mode is always supported, so the centered underline never moves.
            // Previous per-recomposition allocation: listOf(...).filter { ... } on every
            // composition. Pure function of the three support flags, so remember it; the
            // produced list is identical (behavior-preserving, looks-only).
            val stripModes = remember(portraitSupported, nightSupported, videoSupported) {
                listOf(CaptureViewMode.photo,CaptureViewMode.portrait,CaptureViewMode.night,CaptureViewMode.lenses,CaptureViewMode.video,CaptureViewMode.document)
                    .filter { when(it){CaptureViewMode.portrait->portraitSupported;CaptureViewMode.night->nightSupported;CaptureViewMode.video->videoSupported;else->true} }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                // CAM-R1-046 prior fixed slot: val slotWidth = (if (tight) 76 else 88).dp
                val slotWidth = cameraModeSlotWidthDp(tight, androidx.compose.ui.platform.LocalDensity.current.fontScale).dp
                // Original centering always animated and keyed only captureViewMode/maxWidth.
                // CAM-R1-046 previous keys: LaunchedEffect(captureViewMode, maxWidth, reduceControlMotion) {
                LaunchedEffect(captureViewMode, maxWidth, slotWidth, reduceControlMotion) {
                    val idx = stripModes.indexOf(captureViewMode)
                    if (idx >= 0) {
                        // Original: stripState.animateScrollToItem(idx)
                        if (reduceControlMotion) stripState.scrollToItem(idx) else stripState.animateScrollToItem(idx)
                    }
                }
                LazyRow(
                    state = stripState,
                    contentPadding = PaddingValues(horizontal = ((maxWidth - slotWidth) / 2).coerceAtLeast(0.dp)),
                    // CAM-R4-004 prior: modifier = Modifier.fillMaxWidth().semantics{contentDescription="Camera modes"},
                    modifier = Modifier.fillMaxWidth().semantics{contentDescription=modeStripLabel},
                ) {
                    items(stripModes.size) { i ->
                        val mode = stripModes[i]
                        // CAM-R4-004 prior: val name=mode.name.replaceFirstChar{it.uppercase()}
                        val name=stringResource(when(mode) {
                            CaptureViewMode.photo -> R.string.camera_mode_photo
                            CaptureViewMode.portrait -> R.string.camera_mode_portrait
                            CaptureViewMode.night -> R.string.camera_mode_night
                            CaptureViewMode.lenses -> R.string.camera_mode_lenses
                            CaptureViewMode.video -> R.string.camera_mode_video
                            CaptureViewMode.document -> R.string.camera_mode_document
                        })
                        CameraCaptureModeButton(label=name,
                            selected=captureViewMode==mode,tight=tight,enabled=!capturing,onClick={onSetViewMode(mode)},modifier=Modifier.width(slotWidth))
                    }
                }
            }
            Spacer(Modifier.height((if (tight) 4 else 8).dp))
            }
            // The lenses tab activates the Snap-style lens UI; the color-filter
            // tray is a separate surface opened only from the filters button, so
            // lens UI never mixes with the open tray.
            if (ready && captureViewMode == CaptureViewMode.lenses && !effectsOpen) {
                // Lens name + tracking health row.
                Column(Modifier.fillMaxWidth()) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        lensManager.selected.name,
                        // Previous value: Color.White
                        color = rememberCameraOverlayColors().content,
                        fontSize = 13.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.W700,
                        letterSpacing = 0.15.sp,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(
                                if (lensManager.selected.requiresFaceTracking &&
                                    faceHealth == TrackingHealth.tracked
                                // Previous value: Color(0xFF7CFAAE) / Color(0x61FFFFFF)
                                ) rememberCameraOverlayColors().success else rememberCameraOverlayColors().content.copy(alpha = 0.38f),
                            ),
                    )
                    // CAM-R4-030 previous spacing: Spacer(Modifier.width(6.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        // CAM-R4-004 prior: if (!lensManager.selected.requiresFaceTracking) "Swipe to explore"
                        if (!lensManager.selected.requiresFaceTracking) stringResource(R.string.camera_swipe_explore)
                        else when (faceHealth) {
                            // CAM-R4-004 prior: TrackingHealth.tracked -> "Face locked"
                            TrackingHealth.tracked -> stringResource(R.string.camera_face_locked)
                            // CAM-R4-004 prior: TrackingHealth.temporarilyLost -> "Reacquiring…"
                            TrackingHealth.temporarilyLost -> stringResource(R.string.camera_face_reacquiring)
                            // CAM-R4-004 prior: TrackingHealth.lost -> "Find a face"
                            TrackingHealth.lost -> stringResource(R.string.camera_find_face)
                        },
                        // Previous value: Color(0xB3FFFFFF)
                        color = rememberCameraOverlayColors().content.copy(alpha = 0.70f),
                        // CAM-R4-028 prior small text: fontSize = 11.sp,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FlowRow(Modifier.fillMaxWidth().heightIn(min = 48.dp), horizontalArrangement = Arrangement.Center,
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier
                        // CAM-R4-030 previous spacing: .padding(start = 10.dp)
                        .padding(start = 12.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        // CAM-R4-034 previous: .clickable(onClick = onPickFromGallery)
                        .cameraActionFocusRing().clickable(onClick = onPickFromGallery)
                        // CAM-R4-004 prior: .semantics { contentDescription = "Pick image from gallery" },
                        .semantics { contentDescription = galleryPickLabel },
                    contentAlignment = Alignment.Center,
                ) {
                    // CAM-R1-056 previous gallery glyph: CameraGlyph(kind = "gallery", color = Color.White, size = 22.dp)
                    CameraGalleryThumbnail(if (recentStripVisible) recentPhotos.firstOrNull() else null, 22)
                }
                // Lens explorer entry + camera flip at this row's end (Snap-style).
                FlowRow(
                    modifier = Modifier
                        // CAM-R4-030 previous spacing: .padding(end = 10.dp),
                        .padding(end = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            // CAM-R4-034 previous: .clickable(enabled = ready, onClick = onFlip)
                            .cameraActionFocusRing().clickable(enabled = ready, onClick = onFlip)
                            // CAM-R1-037 original: .semantics { contentDescription = "Switch camera" },
                            .semantics { contentDescription = cameraFlipActionLabel(frontCamera) },
                        contentAlignment = Alignment.Center,
                    ) {
                        CameraGlyph(
                            kind = "flip",
                            // Previous value: Color.White / Color(0x4DFFFFFF)
                            color = if (ready) rememberCameraOverlayColors().content else rememberCameraOverlayColors().contentDisabled,
                            size = 22.dp,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            // Previous value: Color(0x33FFFFFF)
                            .background(rememberCameraOverlayColors().content.copy(alpha = 0.20f))
                            // CAM-R4-034 previous: .clickable(onClick = onBrowseLenses)
                            .cameraActionFocusRing().clickable(onClick = onBrowseLenses)
                            // CAM-R4-004 prior: .semantics { contentDescription = "Browse all lenses" },
                            .semantics { contentDescription = browseLensesLabel },
                        contentAlignment = Alignment.Center,
                    ) {
                        // Previous value: Color.White
                        CameraGlyph(kind = "browse_grid", color = rememberCameraOverlayColors().content, size = 20.dp)
                    }
                }
                }
                }

                // CAM-R4-030 conditional-spacing correction: Spacer(Modifier.height((if (tight) 1 else 4).dp))
                Spacer(Modifier.height(4.dp))
            }
            if (recentStripVisible) {
                CameraRecentStrip(
                    open = recentStripOpen,
                    photos = recentPhotos,
                    onToggle = onToggleRecentStrip,
                    onPick = onPickRecent,
                )
                // CAM-R4-030 conditional-spacing correction: Spacer(Modifier.height((if (tight) 2 else 6).dp))
                Spacer(Modifier.height((if (tight) 4 else 8).dp))
            }
            // Snap-style inline row (verified reference): gallery, shutter, then the
            // lens carousel scrolling on the SAME row, flip at the far edge. Gallery
            // and flip keep their existing capabilities and safe-area padding.
            // While recording, the stop control stays centered alone (reference).
            if (videoRecording) {
                // Reference recording row (user video 2026-10-30): the stop control
                // stays centered; pause/resume sits at the right edge.
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CameraCaptureShutter(enabled = true, capturing = false, videoActive = true, recording = true, elapsedSeconds = videoSeconds, paused = videoPaused, onClick = onShutter)
                    // CAM-R4-004 prior: if (videoPaused) Text("Paused", color = androidx.compose.material3.MaterialTheme.colorScheme.onTertiaryContainer,
                    if (videoPaused) Text(stringResource(R.string.camera_paused), color = androidx.compose.material3.MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.align(Alignment.TopCenter).offset(y = (-32).dp)
                            .background(androidx.compose.material3.MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(12.dp))
                            // CAM-R4-030 previous spacing: .padding(horizontal = 12.dp, vertical = 6.dp).semantics { liveRegion = LiveRegionMode.Polite })
                            .padding(horizontal = 12.dp, vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite })
                    if (videoSnapshotAvailable) {
                        // Reference recording row: still snapshot button left of stop.
                        // Drawn only when the session really bound the 3-use-case
                        // combination - never a dead button.
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = (if (tight) 12 else 16).dp)
                                .size(48.dp)
                                .clip(CircleShape)
                                // Previous value: Color.White
                                .border(3.dp, rememberCameraOverlayColors().content, CircleShape)
                                // CAM-R4-034 previous: .clickable(onClick = onSnapshot)
                                .cameraActionFocusRing().clickable(onClick = onSnapshot)
                                // CAM-R4-004 prior: .semantics { contentDescription = "Take snapshot while recording" },
                                .semantics { contentDescription = snapshotLabel },
                        ) {}
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = (if (tight) 12 else 16).dp)
                            .size(48.dp)
                            .clip(CircleShape)
                            // CAM-R4-034 previous: .clickable(onClick = onTogglePause)
                            .cameraActionFocusRing().clickable(onClick = onTogglePause)
                            // CAM-R4-004 prior: .semantics { contentDescription = if (videoPaused) "Resume recording" else "Pause recording" },
                            .semantics { contentDescription = pauseLabel },
                        contentAlignment = Alignment.Center,
                    ) {
                        com.example.chat.ui.icons.ChatyIcon(
                            if (videoPaused) com.example.chat.ui.icons.ChatyIconId.playArrow
                            else com.example.chat.ui.icons.ChatyIconId.pause,
                            // Previous value: Color.White
                            color = rememberCameraOverlayColors().content, size = 18.dp,
                        )
                    }
                }
            } else {
            // User direction (device screenshot 2026-10-30): the shutter sits at
            // true screen center in every idle mode; gallery and flip anchor the
            // edges. In lenses mode the carousel spans the full row and the
            // centered (selected) lens renders inside the hollow shutter ring -
            // clean, no white fill over the lens.
            val lensesRow = captureViewMode == CaptureViewMode.lenses && !effectsOpen
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = (if (tight) 12 else 16).dp),
                contentAlignment = Alignment.Center,
            ) {
                if (lensesRow) {
                    // A bottom-to-top drag here opens the lens browser; horizontal
                    // scrolling stays with the carousel and taps still select, and
                    // preview tap-to-focus / pinch-zoom live on the viewfinder, so
                    // no gesture conflicts.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .pointerInput(lensManager) {
                                var draggedUp = 0f
                                var opened = false
                                detectVerticalDragGestures(
                                    onDragStart = { draggedUp = 0f; opened = false },
                                    onVerticalDrag = { _, dragAmount ->
                                        if (dragAmount < 0f) {
                                            draggedUp += -dragAmount
                                            if (!opened && draggedUp > 48.dp.toPx()) {
                                                opened = true
                                                onBrowseLenses()
                                            }
                                        }
                                    },
                                )
                            },
                    ) {
                        LensCarousel(manager = lensManager, deviceTier = deviceTier)
                    }
                }
                if (!lensesRow) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size((if (tight) 48 else 52).dp)
                        .clip(CircleShape)
                        // CAM-R4-034 previous: .clickable(onClick = onPickFromGallery)
                        .cameraActionFocusRing().clickable(onClick = onPickFromGallery)
                        // CAM-R4-004 prior: .semantics { contentDescription = "Pick image from gallery" },
                        .semantics { contentDescription = galleryPickLabel },
                    contentAlignment = Alignment.Center,
                ) {
                    // CAM-R1-056 previous gallery glyph: CameraGlyph(kind = "gallery", color = Color.White, size = 27.dp)
                    CameraGalleryThumbnail(if (recentStripVisible) recentPhotos.firstOrNull() else null, 27)
                }
                }
                CameraCaptureShutter(enabled = ready, capturing = capturing, videoActive = videoActive, recording = videoRecording, hollow = lensesRow, onClick = onShutter)
                if (!lensesRow) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size((if (tight) 48 else 52).dp)
                        .clip(CircleShape)
                        // CAM-R4-034 previous: .clickable(enabled = ready, onClick = onFlip)
                        .cameraActionFocusRing().clickable(enabled = ready, onClick = onFlip)
                        // CAM-R1-037 original: .semantics { contentDescription = "Switch camera" },
                        .semantics { contentDescription = cameraFlipActionLabel(frontCamera) },
                    contentAlignment = Alignment.Center,
                ) {
                    CameraGlyph(
                        kind = "flip",
                        // Previous value: Color.White / Color(0x4DFFFFFF)
                        color = if (ready) rememberCameraOverlayColors().content else rememberCameraOverlayColors().contentDisabled,
                        size = 27.dp,
                    )
                }
                }
            }
            }
        }
    }
}

@Composable
// CAM-R4-031 prior visibility: private fun CameraCaptureModeButton (body unchanged; isolated component tests).
internal fun CameraCaptureModeButton(
    label: String,
    selected: Boolean,
    tight: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val modeDescription = stringResource(R.string.camera_mode_description, label)
    // Plain text + active-only underline (Snap-style), 48dp touch target,
    // tab semantics preserved for the mode-strip proofs and TalkBack.
    Column(
        modifier = modifier
            // CAM-R1-046 prior fixed height: .height(48.dp)
            .heightIn(min = 48.dp)
            .clearAndSetSemantics {
                // CAM-R4-004 prior: contentDescription = "$label camera mode"
                contentDescription = modeDescription
                this.selected = selected
                role = Role.Tab
                if(!enabled) disabled() else onClick { onClick(); true }
            }
            // CAM-R4-034 previous: .clickable(enabled=enabled,onClick = onClick)
            .cameraActionFocusRing().clickable(enabled=enabled,onClick = onClick)
            // CAM-R4-030 previous spacing: .padding(horizontal = 6.dp),
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            label,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            // Previous value: Color(0x99FFFFFF) / Color(0x4DFFFFFF)
            color = if (selected) CameraModeActiveYellow else if(enabled) rememberCameraOverlayColors().contentSecondary else rememberCameraOverlayColors().contentDisabled,
            fontSize = (if (tight) 12 else 13).sp,
            // CAM-R1-055: reinforce existing underline/weight with a theme-token shadow.
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge.copy(
                shadow = androidx.compose.ui.graphics.Shadow(
                    color = androidx.compose.material3.MaterialTheme.colorScheme.scrim,
                    offset = Offset(0f, 1f), blurRadius = 2f,
                ),
            ),
            fontWeight = if (selected) FontWeight.W700 else FontWeight.W500,
        )
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier
                .width(18.dp)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(if (selected) CameraModeActiveYellow else Color.Transparent),
        )
    }
}

/** _buildShutter: 76dp ring (4dp white border, 5dp gap), spinner while
 *  capturing, filled inner circle otherwise. */
@Composable
// CAM-R4-031 prior visibility: private fun CameraCaptureShutter (body unchanged; isolated component tests).
internal fun CameraCaptureShutter(enabled: Boolean, capturing: Boolean, videoActive: Boolean = false, recording: Boolean = false, hollow: Boolean = false, elapsedSeconds: Int = 0, paused: Boolean = false, onClick: () -> Unit) {
    val shutterLabel = stringResource(if (videoActive) {
        if (recording) R.string.camera_stop_recording else R.string.camera_record_video
    } else R.string.camera_take_photo)
    val shutterState = when {
        recording && paused -> stringResource(R.string.camera_recording_paused_seconds, elapsedSeconds)
        recording -> stringResource(R.string.camera_recording_seconds, elapsedSeconds)
        capturing -> stringResource(R.string.camera_capturing_photo)
        else -> stringResource(R.string.camera_ready)
    }
    // Reference (user video 2026-10-30): white ring in every state; photo fill is
    // white, video idle is a red filled circle, recording is a red rounded square.
    // Size stays 76dp so the row geometry is stable.
    Box(
        modifier = Modifier
            .size(76.dp)
            .clip(CircleShape)
            // Previous value: Color.White
            .border(4.dp, rememberCameraOverlayColors().content, CircleShape)
            // CAM-R4-034 previous: .clickable(enabled = enabled && !capturing, onClick = { if (!capturing) onClick() })
            .cameraActionFocusRing().clickable(enabled = enabled && !capturing, onClick = { if (!capturing) onClick() })
            .padding(5.dp)
            // CAM-R4-004 prior shutter semantic labels/state:
            // .semantics { contentDescription = if (videoActive) { if (recording) "Stop recording" else "Record video" } else "Take photo"; stateDescription = when { recording && paused -> "Recording paused, $elapsedSeconds seconds"; recording -> "Recording, $elapsedSeconds seconds"; capturing -> "Capturing photo"; else -> "Ready" } },
            .semantics { contentDescription = shutterLabel; stateDescription = shutterState },
        contentAlignment = Alignment.Center,
    ) {
        if (capturing) {
            CircularProgressIndicator(
                // Previous value: Color.White
                color = rememberCameraOverlayColors().content,
                strokeWidth = 3.dp,
                modifier = Modifier.padding(16.dp),
            )
        } else if (recording) {
            // Reference-grounded stop affordance: rounded square inside the ring.
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(18.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(CameraRecordingRed),
            )
        } else if (videoActive) {
            // Reference (user video 2026-10-30): video idle shows a red filled
            // shutter inside the white ring.
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    // Previous value: Color(0x61FF0000)
                    .background(if (enabled) CameraRecordingRed else rememberCameraOverlayColors().danger.copy(alpha = 0.38f)),
            )
        } else if (!hollow) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    // Previous value: Color.White / Color(0x61FFFFFF)
                    .background(if (enabled) rememberCameraOverlayColors().content else rememberCameraOverlayColors().content.copy(alpha = 0.38f)),
            )
        }
        // hollow (lenses mode): only the white ring - the centered selected lens
        // shows clean inside it, no white fill over the lens.
    }
}

/** Top-center recording time (user reference video: red dot + white
 *  "HH:MM:SS" text, top-center, no pill). */
@Composable
fun CameraCaptureTimerTop(seconds: Int, modifier: Modifier = Modifier, remainingSeconds: Int? = null, muted: Boolean = false) {
    val timerLabel = stringResource(R.string.camera_recording_time)
    val timerState = if (remainingSeconds == null) stringResource(R.string.camera_recording_seconds, seconds)
        else stringResource(R.string.camera_recording_with_remaining, seconds, remainingSeconds)
    val effectiveTimerState = if (muted) stringResource(R.string.camera_recording_muted_state, timerState) else timerState
    val elapsedLabel = String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60)
    val remainingLabel = remainingSeconds?.let { String.format(java.util.Locale.ROOT, "%02d:%02d", it / 60, it % 60) }
    Row(
        // CAM-R4-004 prior: modifier = modifier.semantics { contentDescription = "Recording time"; stateDescription = "Recording, $seconds seconds" },
        modifier = modifier.semantics { contentDescription = timerLabel; stateDescription = effectiveTimerState },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(CameraRecordingRed))
        // CAM-R4-030 previous spacing: Spacer(Modifier.width(6.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            // Previous text: elapsedLabel only.
            // Previous label showed time only, even when actual recording had no audio.
            (if (remainingLabel == null) elapsedLabel else stringResource(R.string.camera_recording_time_remaining, elapsedLabel, remainingLabel)) +
                (if (muted) " · " + stringResource(R.string.camera_recording_muted_label) else ""),
            // Previous value: Color.White
            color = rememberCameraOverlayColors().content,
            fontSize = 14.sp,
            style = androidx.compose.material3.LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.W600,
        )
    }
}

/** _buildConfirmPane: zoomable preview with the stored effect applied exactly
 *  like receivers render it, caption field, discard + confirm.
 *  Hierarchy: Send/Post is the single filled primary action; Discard stays secondary and
 *  caption is optional. The caption never requests initial focus or opens the keyboard.
 *  Keep quality-priority still capture honest with the in-flight ring; burst is not implemented.
 */
@Composable
fun CameraCaptureConfirmPane(
    path: String,
    mode: ChatyCaptureMode,
    contactName: String?,
    effect: ChatyCameraEffect,
    effectIntensity: Double,
    caption: String,
    onCaptionChange: (String) -> Unit,
    onDiscard: () -> Unit,
    onConfirm: () -> Unit,
    sending: Boolean = false,
) {
    val isChat = mode == ChatyCaptureMode.chat
    val discardLabel = stringResource(R.string.camera_discard_photo)
    val confirmLabel = stringResource(if (isChat) R.string.camera_send_photo else R.string.camera_post_status)
    val preparingLabel = stringResource(R.string.camera_preparing_photo)
    // Previous value: Color.Black
    Column(Modifier.fillMaxSize().background(rememberCameraOverlayColors().backdrop)) {
        // Prior preview Box omitted clipping, so transformed content could cover caption/actions.
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds(), contentAlignment = Alignment.Center) {
            // InteractiveViewer(maxScale: 4)
            var scale by remember { mutableStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            // Dart InteractiveViewer(maxScale: 4, boundaryMargin: zero,
            // constrained: true): the transform is about the child's TOP-LEFT
            // origin (Flutter Transform default), pinch zoom keeps the gesture
            // focal point fixed, and the child AABB is clamped inside the
            // viewport every update. Compose graphicsLayer defaults to a
            // CENTER pivot, so the pivot is pinned to top-left explicitly and
            // the gesture math lives in confirmPaneZoomPan (JVM-tested).
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val step = confirmPaneZoomPan(
                                offset = offset,
                                centroid = centroid,
                                pan = pan,
                                scale = scale,
                                zoom = zoom,
                                viewportWidth = size.width.toFloat(),
                                viewportHeight = size.height.toFloat(),
                            )
                            scale = step.first
                            offset = step.second
                        }
                    }
                    .graphicsLayer {
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
                contentAlignment = Alignment.Center,
            ) {
                ApplyStoredEffect(effectId = effect.id, intensity = effectIntensity) {
                    AsyncImage(
                        model = File(path),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Previous value: Color.Black
                .background(rememberCameraOverlayColors().backdrop)
                .navigationBarsPadding()
                .imePadding()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                // CAM-R4-030 previous spacing: .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 14.dp),
                .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    // CAM-R4-034 previous: .clickable(enabled = !sending, onClick = onDiscard)
                    .cameraActionFocusRing().clickable(enabled = !sending, onClick = onDiscard)
                    // CAM-R4-004 prior: .semantics { contentDescription = "Discard photo" },
                    .semantics { contentDescription = discardLabel },
                contentAlignment = Alignment.Center,
            ) {
                // Previous value: Color.White
                CameraGlyph(kind = "close", color = rememberCameraOverlayColors().content, size = 26.dp)
            }
            // CAM-R4-030 previous spacing: Spacer(Modifier.width(10.dp))
            Spacer(Modifier.width(12.dp))
            TextField(
                value = caption,
                onValueChange = onCaptionChange,
                enabled = !sending,
                modifier = Modifier.weight(1f),
                minLines = 1,
                maxLines = 2,
                placeholder = {
                    Text(
                        // CAM-R4-004 prior: if (isChat && contactName != null) "Message $contactName…" else "Add a caption…",
                        if (isChat && contactName != null) stringResource(R.string.camera_message_contact, contactName) else stringResource(R.string.camera_caption_placeholder),
                        // Previous value: Color(0x8AFFFFFF)
                        color = rememberCameraOverlayColors().contentSecondary,
                    )
                },
                shape = RoundedCornerShape(26.dp),
                colors = TextFieldDefaults.colors(
                    // Previous value: Color(0x1FFFFFFF)
                    focusedContainerColor = rememberCameraOverlayColors().content.copy(alpha = 0.12f),
                    // Previous value: Color(0x1FFFFFFF)
                    unfocusedContainerColor = rememberCameraOverlayColors().content.copy(alpha = 0.12f),
                    // Previous value: Color.White
                    focusedTextColor = rememberCameraOverlayColors().content,
                    // Previous value: Color.White
                    unfocusedTextColor = rememberCameraOverlayColors().content,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
            // CAM-R4-030 previous spacing: Spacer(Modifier.width(10.dp))
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(androidx.compose.material3.MaterialTheme.colorScheme.primary)
                    // CAM-R4-034 previous: .clickable(enabled = !sending, onClick = onConfirm)
                    .cameraActionFocusRing().clickable(enabled = !sending, onClick = onConfirm)
                    .semantics {
                        // CAM-R4-004 prior: contentDescription = if (isChat) "Send photo" else "Post to my status"
                        // if (sending) stateDescription = "Preparing photo; please wait"
                        contentDescription = confirmLabel
                        if (sending) stateDescription = preparingLabel
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (sending) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary)
                else CameraGlyph(
                    kind = "send",
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                    size = 24.dp,
                )
            }
        }
    }
}


/** InteractiveViewer pan clamp with zero boundaryMargin over a
 *  viewport-constrained child (top-left pivot): offset stays in
 *  [viewport * (1-scale), 0] per axis. Pure for JVM tests. */
fun clampConfirmPanePan(
    offset: Offset,
    scale: Float,
    viewportWidth: Float,
    viewportHeight: Float,
): Offset {
    val minX = viewportWidth * (1f - scale)
    val minY = viewportHeight * (1f - scale)
    return Offset(
        offset.x.coerceIn(minX, 0f),
        offset.y.coerceIn(minY, 0f),
    )
}


/** One InteractiveViewer scale/pan update: zoom clamps to [1,4], the gesture
 *  centroid stays fixed on screen (Flutter scales the matrix about the focal
 *  point), pan accumulates, then the result is boundary-clamped with
 *  [clampConfirmPanePan]. Returns (newScale, newOffset). Pure for JVM tests. */
fun confirmPaneZoomPan(
    offset: Offset,
    centroid: Offset,
    pan: Offset,
    scale: Float,
    zoom: Float,
    viewportWidth: Float,
    viewportHeight: Float,
): Pair<Float, Offset> {
    val newScale = (scale * zoom).coerceIn(1f, 4f)
    // Keep the centroid fixed: offset' = c + (offset - c) * (s'/s) + pan.
    val factor = newScale / scale
    val unclamped = Offset(
        centroid.x + (offset.x - centroid.x) * factor + pan.x,
        centroid.y + (offset.y - centroid.y) * factor + pan.y,
    )
    return newScale to clampConfirmPanePan(
        unclamped, newScale, viewportWidth, viewportHeight,
    )
}

/** CAM-R2-016: local waiting feedback, not provider/bind/first-frame progress measurement. */
@Composable
private fun CameraInitializingFeedback() {
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(800L); slow = true }
    val reduceMotion = com.example.chat.ui.kit.rememberChatyReduceMotion()
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var foreground by remember(owner) {
        mutableStateOf(owner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))
    }
    androidx.compose.runtime.DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, _ ->
            foreground = owner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val sweep = remember { androidx.compose.animation.core.Animatable(-1f) }
    LaunchedEffect(slow, reduceMotion, foreground) {
        sweep.snapTo(-1f)
        if (slow && !reduceMotion && foreground) sweep.animateTo(
            1f, androidx.compose.animation.core.infiniteRepeatable(
                tween(1200, easing = androidx.compose.animation.core.LinearEasing),
            ),
        )
    }
    val colors = androidx.compose.material3.MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (slow) androidx.compose.foundation.Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
            if (reduceMotion) drawRect(colors.surface.copy(alpha = 0.2f))
            else {
                val center = size.width * (sweep.value + 1f)
                drawRect(Brush.horizontalGradient(
                    listOf(colors.surface.copy(alpha = 0.08f), colors.onSurface.copy(alpha = 0.16f), colors.surface.copy(alpha = 0.08f)),
                    startX = center - size.width * 0.5f, endX = center + size.width * 0.5f,
                ))
            }
        }
        Column(Modifier.background(colors.surface.copy(alpha = 0.85f), RoundedCornerShape(8.dp)).padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!reduceMotion) CircularProgressIndicator(color = colors.onSurface)
            // CAM-R4-004 prior: if (slow || reduceMotion) Text("Opening camera", color = colors.onSurface,
            if (slow || reduceMotion) Text(stringResource(R.string.camera_opening), color = colors.onSurface,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
}

/** CAM-R1-056: reuse only an already-loaded recent-strip item; no query/permission expansion. */
@Composable
private fun CameraGalleryThumbnail(uri: android.net.Uri?, fallbackSize: Int) {
    val colors = androidx.compose.material3.MaterialTheme.colorScheme
    @Composable fun fallback() {
        CameraGlyph(kind = "gallery", color = colors.onSurface, size = fallbackSize.dp)
    }
    Box(Modifier.size(36.dp).clip(CircleShape).background(colors.surface), contentAlignment = Alignment.Center) {
        if (uri == null) fallback() else coil.compose.SubcomposeAsyncImage(
            model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                .data(uri).size(128).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { fallback() } },
            error = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { fallback() } },
        )
    }
}

// CAM-R4-034 prior UI modifier lines retained inactive:
//             OutlinedButton(modifier = Modifier.heightIn(min = 48.dp).focusRequester(actionFocus), shape = com.example.chat.ui.kit.ChatyActionShape, onClick = onAction) {
//                     modifier = Modifier.heightIn(min = 48.dp),

/* Previous implementation fragments retained (net231):
            modifier = Modifier.align(Alignment.Center)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(32.dp),
    val configuration = LocalConfiguration.current
    // CAM-R1-058 original breakpoint:
    // val tight = minOf(configuration.screenWidthDp, configuration.screenHeightDp) < 340 || configuration.screenHeightDp < 680
    val tight = cameraControlsTight(configuration.screenWidthDp, configuration.screenHeightDp)

    Box(
        Modifier
            .fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter,
    ) {
            Column(Modifier.fillMaxWidth().heightIn(max = (configuration.screenHeightDp * .22f).dp)
                        horizontal = max(8.0, (configuration.screenWidthDp - 52) / 2.0).dp,
                Box(Modifier.fillMaxWidth()) {
                // Gallery + flip live on the lens-name row in lenses mode (run-128
                // evidence): on the shutter row their hit bounds covered the
                // full-width carousel's thumbnails - Switch camera
                // [901,2071][1038,2208] swallowed the Gold Crown thumbnail's center
                // tap point, Puppy Ears sat fully beneath it, and a scrolled No Lens
                // hit the same overlap under gallery. Same size/callbacks/labels,
                // no carousel-lane overlap. Photo/video rows keep the verified
                // reference layout untouched.
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        // CAM-R4-030 previous spacing: .padding(start = 10.dp)
                        .padding(start = 12.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        // CAM-R4-034 previous: .clickable(onClick = onPickFromGallery)
                        .cameraActionFocusRing().clickable(onClick = onPickFromGallery)
                        // CAM-R4-004 prior: .semantics { contentDescription = "Pick image from gallery" },
                        .semantics { contentDescription = galleryPickLabel },
                    contentAlignment = Alignment.Center,
                ) {
                    // CAM-R1-056 previous gallery glyph: CameraGlyph(kind = "gallery", color = Color.White, size = 22.dp)
                    CameraGalleryThumbnail(if (recentStripVisible) recentPhotos.firstOrNull() else null, 22)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                        maxLines = 1,
                        maxLines = 1,
                Row(
                        .align(Alignment.CenterEnd)
                    verticalAlignment = Alignment.CenterVertically,
                                .size(40.dp)
            // CAM-R4-034 previous: .clickable(enabled = enabled, onClick = onClick)
            .cameraActionFocusRing().clickable(enabled = enabled, onClick = onClick)
*/

/* Previous net233 fragments:
        val paneHeightDp = maxHeight.value.toInt().coerceAtLeast(1)
                .verticalScroll(rememberScrollState())
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
*/
