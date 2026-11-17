package com.example.chat.ui.settings

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import kotlinx.coroutines.flow.collect
import androidx.lifecycle.repeatOnLifecycle

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
// Previous implementation retained: import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.rounded.Cancel
// Previous implementation retained: import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.rounded.CheckCircleOutline
// Previous implementation retained: import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.rounded.HelpOutline
// Previous implementation retained: import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DevicesOther
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.SpeakerGroup
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.VolumeUp
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.example.chat.ui.kit.ChatyAppBar
import com.example.chat.ui.kit.ChatyConfirmationDialog
import com.example.chat.ui.kit.ChatyHapticsPort
import com.example.chat.ui.kit.ChatyModalShape
import com.example.chat.ui.kit.SpecularGlassCard
import com.example.chat.ui.kit.springInteractiveScale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.composed
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.audio.AudioCapabilityService
import com.example.chat.core.audio.AudioOutputRoute
import com.example.chat.core.audio.AudioPolicyCoordinator
import com.example.chat.core.audio.AudioPreferenceMode
import com.example.chat.core.audio.AudioSettingsController
import com.example.chat.core.audio.AudioUseCase
import com.example.chat.core.audio.DualOutputStatus
import com.example.chat.ui.theme.LocalAppColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// AU1-28 / AU3-29: hoisted constants instead of per-recomposition allocations.
// Previous implementation retained: private val AudioCardShape = RoundedCornerShape(16.dp)
private val AudioCardShape = RoundedCornerShape(12.dp)
private val AudioBadgeShape = RoundedCornerShape(12.dp)

/** AU1-02 / AU3-03: ONE friendly-name mapping used by the hero line and the
 *  routes list (Android-style wording per AU2-15 / AU4-18).
 *  was: activeRoute.name rendered raw in the hero, separate names in the list. */
internal fun audioRouteDisplayName(route: AudioOutputRoute): String = when (route) {
    AudioOutputRoute.receiver -> "Phone earpiece"
    AudioOutputRoute.speaker -> "Phone speaker"
    AudioOutputRoute.bluetooth -> "Bluetooth"
    AudioOutputRoute.wiredHeadset -> "Wired headset"
    AudioOutputRoute.usb -> "USB audio"
    AudioOutputRoute.hearingAid -> "Hearing aid"
    AudioOutputRoute.systemDefault -> "System default"
}

internal fun audioRouteIcon(route: AudioOutputRoute): ImageVector = when (route) {
// Previous implementation retained:     AudioOutputRoute.receiver -> com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.earpiece)
    AudioOutputRoute.receiver -> Icons.Rounded.Hearing
    AudioOutputRoute.speaker -> Icons.Rounded.VolumeUp
    AudioOutputRoute.bluetooth -> Icons.Rounded.BluetoothAudio
    AudioOutputRoute.wiredHeadset -> Icons.Rounded.Headphones
    AudioOutputRoute.usb -> Icons.Rounded.Usb
    AudioOutputRoute.hearingAid -> Icons.Rounded.Hearing
    AudioOutputRoute.systemDefault -> Icons.Rounded.DevicesOther
}

/** AU1-15: one immutable snapshot per service notification, copied route list avoids aliasing. */
private data class AudioSettingsUiState(
    val dualStatus: DualOutputStatus,
    val activeRoute: AudioOutputRoute,
    val routes: List<AudioOutputRoute>,
    val mode: AudioPreferenceMode,
    val callInProgress: Boolean,
)

/**
 * Port of lib/core/audio/audio_settings_screen.dart ("Audio & Speakers").
 * Binds the real core/audio controller, capability service and coordinator
 * (listeners merged like Listenable.merge). Device-test rows come verbatim
 * from AudioSettingsController.verifiedDeviceRecords; the status labels are
 * shown as recorded and nothing here claims hardware verification.
 * Hero is the shared SpecularGlassCard (the blur parameter is ignored by the
 * kit, so AU1-29 / AU3-28 carry no GPU cost today), header is the shared
 * ChatyAppBar (AU1-37), and rows use theme colour tokens only (AU1-10).
 * [scope] launches the suspend setPreferredRoute call.
 */
@Composable
fun AudioSettingsScreen(
    settings: AudioSettingsController,
// Previous implementation retained:     capability: AudioCapabilityService,
    capability: com.example.chat.core.audio.AudioCapabilitySource,
    coordinator: AudioPolicyCoordinator,
    scope: CoroutineScope,
    onBack: () -> Unit,
    liveCalls: com.example.chat.data.services.CallSignalingService? = null,
) {
    val colors = LocalAppColors.current
    val screenScope = androidx.compose.runtime.rememberCoroutineScope()
    val reduceAudioMotion = com.example.chat.ui.kit.rememberChatyReduceMotion()
    var predictiveBackProgress by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var predictiveBackDirection by remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    val currentAudioBack by androidx.compose.runtime.rememberUpdatedState(onBack)
    androidx.activity.compose.PredictiveBackHandler { events ->
        completeAudioSettingsBack(events, onProgress = { predictiveBackProgress = it },
            onEdge = { predictiveBackDirection = if (it == androidx.activity.BackEventCompat.EDGE_RIGHT) -1f else 1f },
            onCommit = { currentAudioBack() })
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    fun hasBluetoothPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        androidx.core.content.ContextCompat.checkSelfPermission(context,
            android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
    var bluetoothPermissionGranted by remember(context) { mutableStateOf(hasBluetoothPermission()) }
    var bluetoothPermissionDenied by rememberSaveable { mutableStateOf(false) }
    var showBluetoothRationale by rememberSaveable { mutableStateOf(false) }
    val bluetoothPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        bluetoothPermissionGranted = granted
        bluetoothPermissionDenied = !granted
        // Granting permission only refreshes the route list, never changes output.
    }
    val audioLifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(audioLifecycleOwner, context) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                bluetoothPermissionGranted = hasBluetoothPermission()
            }
        }
        audioLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { audioLifecycleOwner.lifecycle.removeObserver(observer) }
    }
// Previous implementation retained:     var version by remember { mutableIntStateOf(0) }
// Previous implementation retained:     // AU1-14: listeners may fire on a non-main thread; hop to main before
// Previous implementation retained:     // touching snapshot state.
// Previous implementation retained:     val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
// Previous implementation retained:     DisposableEffect(settings, capability, coordinator) {
// Previous implementation retained:         val l: () -> Unit = { mainHandler.post { version += 1 } }
// Previous implementation retained:         settings.addListener(l); capability.addListener(l); coordinator.addListener(l)
// Previous implementation retained:         onDispose { settings.removeListener(l); capability.removeListener(l); coordinator.removeListener(l) }
    val capabilityState by capability.communicationCapabilities.collectAudioStateWithLifecycle()
    val modeState by settings.preferenceModeState.collectAudioStateWithLifecycle()
    val modeSaveFailed by settings.saveError.collectAudioStateWithLifecycle()
    val deviceRecordState by settings.deviceRecords.collectAudioStateWithLifecycle()
    val deviceRecordsFailed by settings.deviceRecordsFailed.collectAudioStateWithLifecycle()
    val policyState by coordinator.policyState.collectAudioStateWithLifecycle()
// Previous implementation retained:     val uiState = remember(capabilityState, modeState, policyState) {
    var liveCallActive by remember(liveCalls) { mutableStateOf(liveCalls?.hasActiveSession == true) }
    DisposableEffect(liveCalls) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        var attached = true
        val listener: () -> Unit = {
            handler.post { if (attached) liveCallActive = liveCalls?.hasActiveSession == true }
            Unit
        }
        liveCalls?.addStateListener(listener)
        listener()
        onDispose { attached = false; liveCalls?.removeStateListener(listener) }
    }
    val uiState = remember(capabilityState, modeState, policyState, liveCallActive) {
        AudioSettingsUiState(capabilityState?.dualOutputStatus ?: DualOutputStatus.notVerified,
            capabilityState?.activeRoute ?: AudioOutputRoute.systemDefault,
            capabilityState?.availableRoutes?.toList() ?: emptyList(), modeState,
// Previous implementation retained:             policyState.activeOwner == AudioUseCase.communication)
            liveCallActive || policyState.activeOwner == AudioUseCase.communication)
    }
// Previous implementation retained:     @Suppress("UNUSED_VARIABLE") val rebuild = version
// Previous implementation retained
// Previous implementation retained: // Previous implementation retained:     val dualStatus = capability.communicationDualStatus
// Previous implementation retained: // Previous implementation retained:     val activeRoute = capability.activeCommunicationRoute
// Previous implementation retained: // Previous implementation retained:     val availableRoutes = capability.availableCommunicationRoutes
// Previous implementation retained: // Previous implementation retained:     val mode = settings.preferenceMode
// Previous implementation retained: // Previous implementation retained:     // AU1-48: the coordinator is now actually read (AU1-23 speaker guard).
// Previous implementation retained: // Previous implementation retained:     val callInProgress = coordinator.activeOwner == AudioUseCase.communication
// Previous implementation retained:     val uiState = remember(settings, capability, coordinator, version) {
// Previous implementation retained:         AudioSettingsUiState(capability.communicationDualStatus, capability.activeCommunicationRoute,
// Previous implementation retained:             capability.availableCommunicationRoutes.toList(), settings.preferenceMode,
// Previous implementation retained:             coordinator.activeOwner == AudioUseCase.communication)
// Previous implementation retained:     androidx.compose.runtime.LaunchedEffect(capability) {
// Previous implementation retained:         capability.getCapabilities(AudioUseCase.communication)
    var loadingError by remember(capability) { mutableStateOf(false) }
    var capabilityAttempt by remember(capability) { androidx.compose.runtime.mutableIntStateOf(0) }
// Previous implementation retained:     androidx.compose.runtime.LaunchedEffect(capability, capabilityAttempt) {
    androidx.compose.runtime.LaunchedEffect(capability, capabilityAttempt, bluetoothPermissionGranted) {
        loadingError = false
        try {
            // capability.getCapabilities(AudioUseCase.communication) // may reuse stale pre-permission cache
            capability.refreshCapabilities(AudioUseCase.communication)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            loadingError = true
        }
    }
    val screenState: AudioUiLoadState = when {
        loadingError || capabilityState?.failureKind != null -> AudioUiLoadState.Failed
        capabilityState != null -> AudioUiLoadState.Ready(uiState)
// Previous implementation retained:         loadingError -> AudioUiLoadState.Failed
        else -> AudioUiLoadState.Loading
    }
    val dualStatus = uiState.dualStatus
    val activeRoute = uiState.activeRoute
// Previous implementation retained:     val availableRoutes = uiState.routes
    val availableRoutes = uiState.routes.filter { route ->
        bluetoothPermissionGranted || (route != AudioOutputRoute.bluetooth && route != AudioOutputRoute.hearingAid)
    }
    val mode = uiState.mode
    val callInProgress = uiState.callInProgress

    // was: hard-coded iOS system colours (AU1-10):
    // val green = Color(0xFF34C759); val orange = Color(0xFFFF9500)
    // val red = Color(0xFFFF3B30); val blue = Color(0xFF007AFF); val gray = Color(0xFF8E8E93)
    // Now theme tokens: colors.success / colors.warning / colors.error / colors.accent / colors.foregroundSecondary.

    val view = LocalView.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showEngineInfo by rememberSaveable { mutableStateOf(false) }
    var showRoutingHelp by rememberSaveable { mutableStateOf(false) }
    var showDeviceDetails by rememberSaveable { mutableStateOf(false) }
    var showAllDevices by rememberSaveable { mutableStateOf(false) }
    var showSpeakerConfirm by rememberSaveable { mutableStateOf(false) }
    var showExperimentalConfirm by rememberSaveable { mutableStateOf(false) }

    fun requestRoute(route: AudioOutputRoute) {
// Previous implementation retained:         scope.launch {
        screenScope.launch {
            // Re-read on action and Undo, not only at composition time. Settings never bypass live signaling.
            if (settingsRouteBlocked(liveCalls?.hasActiveSession == true, coordinator.activeOwner)) {
                snackbarHostState.showSnackbar("Use the call screen to change audio during a call")
                return@launch
            }
            val previous = activeRoute
            // AU1-06: success/failure feedback (was: fire-and-forget).
            // Previous implementation retained: val result = runCatching { capability.setPreferredRoute(AudioUseCase.communication, route) }
            val result = runCatching { coordinator.setSettingsRoute(route) { liveCalls?.hasActiveSession == true } }
// Previous implementation retained:             if (result.isSuccess) {
            val response = result.getOrNull()
// Previous implementation retained:             if (route == AudioOutputRoute.systemDefault && response != null) {
            if (response?.failureKind != null) {
                snackbarHostState.showSnackbar(response.failureReason ?: "Audio output is unavailable")
            } else if (route == AudioOutputRoute.systemDefault && response != null) {
                snackbarHostState.showSnackbar("System route requested. Current route: ${audioRouteDisplayName(response.activeRoute)}")
            } else if (response != null && response.activeRoute == route) {
                ChatyHapticsPort.selection(view) // AU1-22 / AU4-24: haptic only on success
                if (previous != route) {
                    // AU4-23: reversible via Undo.
                    val r = snackbarHostState.showSnackbar(
                        "Call audio: ${audioRouteDisplayName(route)}",
                        actionLabel = "Undo",
                    )
// Previous implementation retained:                     if (r == SnackbarResult.ActionPerformed) {
                    if (r == SnackbarResult.ActionPerformed &&
                        !settingsRouteBlocked(liveCalls?.hasActiveSession == true, coordinator.activeOwner)) {
// Previous implementation retained:                         runCatching { capability.setPreferredRoute(AudioUseCase.communication, previous) }
                        runCatching { coordinator.setSettingsRoute(previous) { liveCalls?.hasActiveSession == true } }
                    }
                }
            } else {
// Previous implementation retained:                 snackbarHostState.showSnackbar("Couldn't switch audio route")
                val actual = response?.activeRoute
                snackbarHostState.showSnackbar(if (actual == null) "Couldn't switch audio route"
                    else "Route hasn't switched to ${audioRouteDisplayName(route)}. Current route: ${audioRouteDisplayName(actual)}")
            }
        }
    }

    if (showBluetoothRationale) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showBluetoothRationale = false },
            title = { Text("Bluetooth call audio") },
            text = { Text("Allow Nearby devices to choose Bluetooth headphones or hearing aids for calls. This does not start a call or change the current audio output.") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = {
                showBluetoothRationale = false
                bluetoothPermissionLauncher.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
            }) { Text("Continue") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { showBluetoothRationale = false }) { Text("Cancel") } },
            containerColor = colors.surface,
            titleContentColor = colors.foreground,
            textContentColor = colors.foregroundSecondary,
        )
    }

// Previous implementation retained:     Box(Modifier.fillMaxSize().background(colors.background)) {
    Box(Modifier.fillMaxSize().background(colors.background).then(
        Modifier.graphicsLayer {
            translationX = if (reduceAudioMotion) 0f else size.width * predictiveBackProgress * predictiveBackDirection
        })) {
    Column(Modifier.fillMaxSize()) {
        // AU1-37: shared page header (was: custom 19*fontScale title Row).
        ChatyAppBar(title = "Audio & Speakers", onBack = onBack)
        CallAudioRecoverySection()
        if (!bluetoothPermissionGranted) {
            Text("Nearby devices permission is needed to choose Bluetooth call audio.",
                color = colors.foregroundSecondary, modifier = Modifier.padding(horizontal = 16.dp))
            androidx.compose.material3.TextButton(onClick = { showBluetoothRationale = true },
                modifier = Modifier.heightIn(min = 48.dp).padding(horizontal = 8.dp)) {
                Text("Allow Bluetooth audio")
            }
            if (bluetoothPermissionDenied) {
                Text("If the permission prompt no longer appears, allow Nearby devices in app settings.",
                    color = colors.foregroundSecondary, modifier = Modifier.padding(horizontal = 16.dp))
                androidx.compose.material3.TextButton(onClick = {
                    runCatching { context.startActivity(android.content.Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.fromParts("package", context.packageName, null))) }
                        .onFailure { screenScope.launch { snackbarHostState.showSnackbar("App settings could not be opened") } }
                }, modifier = Modifier.heightIn(min = 48.dp).padding(horizontal = 8.dp)) { Text("Open app settings") }
            }
        }
        if (screenState !is AudioUiLoadState.Ready) {
            AudioCapabilitiesPlaceholder(failed = screenState is AudioUiLoadState.Failed,
                onRetry = { capabilityAttempt++ })
            return@Column
        }
        // AU4-27: cap content width on large screens; AU1-51 / AU4-28: horizontal cutout insets.
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 600.dp).fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            // 1. Status hero
            val (badgeText, badgeFg, badgeIcon) = when (dualStatus) {
// Previous implementation retained: // Previous implementation retained:                 DualOutputStatus.active -> Triple("Active & verified", colors.success, Icons.Outlined.CheckCircleOutline)
// Previous implementation retained:                 DualOutputStatus.active -> Triple(context.getString(com.example.chat.R.string.audio_dual_active), colors.success, Icons.Outlined.CheckCircleOutline)
// Previous implementation retained:                 DualOutputStatus.active -> Triple("Active & verified", colors.success, Icons.Rounded.CheckCircleOutline)
                DualOutputStatus.active -> Triple(context.getString(dualStatusLabelResource(DualOutputStatus.active)), colors.success, Icons.Rounded.CheckCircleOutline)
// Previous implementation retained: // Previous implementation retained:                 DualOutputStatus.notVerified -> Triple("Available (standard route)", colors.accent, Icons.Outlined.Info)
// Previous implementation retained:                 DualOutputStatus.notVerified -> Triple(context.getString(com.example.chat.R.string.audio_dual_not_verified), colors.accent, Icons.Outlined.Info)
// Previous implementation retained:                 DualOutputStatus.notVerified -> Triple("Available (standard route)", colors.accent, Icons.Rounded.Info)
                DualOutputStatus.notVerified -> Triple(context.getString(dualStatusLabelResource(DualOutputStatus.notVerified)), colors.accent, Icons.Rounded.Info)
// Previous implementation retained: // Previous implementation retained:                 DualOutputStatus.unavailable -> Triple("Temporarily unavailable", colors.warning, Icons.Outlined.HelpOutline)
// Previous implementation retained:                 DualOutputStatus.unavailable -> Triple(context.getString(com.example.chat.R.string.audio_dual_unavailable), colors.warning, Icons.Outlined.HelpOutline)
// Previous implementation retained:                 DualOutputStatus.unavailable -> Triple("Temporarily unavailable", colors.warning, Icons.Rounded.HelpOutline)
                DualOutputStatus.unavailable -> Triple(context.getString(dualStatusLabelResource(DualOutputStatus.unavailable)), colors.warning, Icons.Rounded.HelpOutline)
// Previous implementation retained: // Previous implementation retained:                 DualOutputStatus.unsupported -> Triple("Unsupported by OS/hardware", colors.foregroundSecondary, Icons.Outlined.Cancel)
// Previous implementation retained:                 DualOutputStatus.unsupported -> Triple(context.getString(com.example.chat.R.string.audio_dual_unsupported), colors.foregroundSecondary, Icons.Outlined.Cancel)
// Previous implementation retained:                 DualOutputStatus.unsupported -> Triple("Unsupported by OS/hardware", colors.foregroundSecondary, Icons.Rounded.Cancel)
                DualOutputStatus.unsupported -> Triple(context.getString(dualStatusLabelResource(DualOutputStatus.unsupported)), colors.foregroundSecondary, Icons.Rounded.Cancel)
// Previous implementation retained: // Previous implementation retained:                 DualOutputStatus.failed -> Triple("Activation failed", colors.error, Icons.Outlined.Cancel)
// Previous implementation retained:                 DualOutputStatus.failed -> Triple(context.getString(com.example.chat.R.string.audio_dual_failed), colors.error, Icons.Outlined.Cancel)
// Previous implementation retained:                 DualOutputStatus.failed -> Triple("Activation failed", colors.error, Icons.Rounded.Cancel)
                DualOutputStatus.failed -> Triple(context.getString(dualStatusLabelResource(DualOutputStatus.failed)), colors.error, Icons.Rounded.Cancel)
            }
// Previous implementation retained:             SpecularGlassCard(Modifier.fillMaxWidth(), blur = 12f, borderRadius = 20.dp, padding = PaddingValues(18.dp)) {
            SpecularGlassCard(Modifier.fillMaxWidth(), blur = 12f, borderRadius = 12.dp, padding = PaddingValues(18.dp)) {
              Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {

//Icon(Icons.Rounded.SpeakerGroup, null, Modifier.size(26.dp), tint = colors.accent)
                    com.example.chat.ui.icons.ChatyPurposeIcon(com.example.chat.ui.icons.ChatyIconId.speakerGroup, Icons.Rounded.SpeakerGroup, null, Modifier.size(26.dp), tint = colors.accent)
                    Spacer(Modifier.width(10.dp))
                    // AU4-19: plain words (was: "Dual-Speaker Engine").
                    Text("Dual-speaker mode", color = colors.foreground, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
// Previous implementation retained:                     // AU1-38 / AU4-16: the long description sits behind an info button.
// Previous implementation retained:                     IconButton(onClick = { showEngineInfo = !showEngineInfo }) {
// Previous implementation retained: // Previous implementation retained:                         Icon(Icons.Outlined.Info, "About dual-speaker mode", Modifier.size(20.dp), tint = colors.foregroundSecondary)
// Previous implementation retained:                         Icon(Icons.Rounded.Info, "About dual-speaker mode", Modifier.size(20.dp), tint = colors.foregroundSecondary)
// Previous implementation retained:                     }
// Previous implementation retained: // Previous implementation retained:                     // AU4-07: icon + text badge, so colour is not the only cue.
// Previous implementation retained: // Previous implementation retained:                     Row(
// Previous implementation retained: // Previous implementation retained:                         verticalAlignment = Alignment.CenterVertically,
// Previous implementation retained: // Previous implementation retained:                         modifier = Modifier.clip(AudioBadgeShape).background(badgeFg.copy(alpha = 0.15f))
// Previous implementation retained: // Previous implementation retained:                             .border(BorderStroke(1.dp, badgeFg.copy(alpha = 0.3f)), AudioBadgeShape)
// Previous implementation retained: // Previous implementation retained:                             .padding(horizontal = 10.dp, vertical = 4.dp),
// Previous implementation retained: // Previous implementation retained:                     ) {
// Previous implementation retained: // Previous implementation retained:                         Icon(badgeIcon, null, Modifier.size(13.dp), tint = badgeFg)
// Previous implementation retained: // Previous implementation retained:                         Spacer(Modifier.width(5.dp))
// Previous implementation retained: // Previous implementation retained:                         Text(badgeText, color = badgeFg, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
// Previous implementation retained: // Previous implementation retained:                     }

                }
                AudioStatusChip(badgeText, badgeIcon, badgeFg)

// Previous implementation retained:                 if (showEngineInfo) {
// Previous implementation retained:                     Spacer(Modifier.height(10.dp))
// Previous implementation retained:                     // AU1-39: plain wording (was: "Smart audio policy coordinates native earpiece ...").
// Previous implementation retained:                     Text(
// Previous implementation retained:                         "Plays calls through the earpiece and speaker together when possible, without breaking Bluetooth or the microphone.",
// Previous implementation retained:                         color = colors.foregroundSecondary, fontSize = 13.sp, lineHeight = 18.sp,
// Previous implementation retained:                     )
// Previous implementation retained:                 }
// Previous implementation retained:                 Spacer(Modifier.height(12.dp))
// Previous implementation retained:                 // AU4-03: the selected mode answers first.
// Previous implementation retained:                 Text(
// Previous implementation retained:                     "Mode: " + when (mode) {
// Previous implementation retained:                         AudioPreferenceMode.automatic -> "Automatic"
// Previous implementation retained:                         AudioPreferenceMode.preferStereo -> "Standard stereo"
// Previous implementation retained:                         AudioPreferenceMode.experimentalDualOutput -> "Dual-speaker (experimental)"
// Previous implementation retained:                     },
// Previous implementation retained:                     color = colors.foreground, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
// Previous implementation retained:                 )
// Previous implementation retained:                 Spacer(Modifier.height(6.dp))
                // AU1-16: "Current route" instead of "Active Output" (nothing is active outside a call);
                // AU1-57: one annotated string; AU1-54 / AU4-33: announced on change.
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = colors.foregroundSecondary)) { append("Current route: ") }
                        withStyle(SpanStyle(color = colors.foreground, fontWeight = FontWeight.SemiBold)) { append(audioRouteDisplayName(activeRoute)) }
                    },
                    fontSize = 13.sp,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
              }
            }
            androidx.compose.material3.TextButton(onClick = { showEngineInfo = !showEngineInfo },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
                    stateDescription = if (showEngineInfo) "Expanded" else "Collapsed"
                }) { Text("About dual-speaker mode") }
            if (showEngineInfo) Text(
                "Plays calls through the earpiece and speaker together when possible, without breaking Bluetooth or the microphone.",
                color = colors.foregroundSecondary, fontSize = 13.sp, lineHeight = 18.sp)
            androidx.compose.material3.TextButton(onClick = { showRoutingHelp = !showRoutingHelp },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
                    stateDescription = if (showRoutingHelp) "Expanded" else "Collapsed"
                }) { Text("My audio comes from the wrong place") }
            if (showRoutingHelp) Column(Modifier.fillMaxWidth().padding(12.dp)) {
                audioRoutingHelpSteps.forEach { step ->
                    Text(step, color = colors.foregroundSecondary, fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 4.dp))
                }
            }
            Spacer(Modifier.height(20.dp))

            // 2. Routes first (AU1-41: the most-used action moves up; was below the preference card).
            // AU1-07: honest title (was: "ACTIVE & AVAILABLE ROUTES").
// Previous implementation retained:             AudioSectionHeader("Call audio routes")
            AudioSectionHeader("Routes")
            if (callInProgress) Text("Change audio from the call screen while a call is active.",
                color = colors.foregroundSecondary, fontSize = 13.sp)
            AudioGroupedCard {
                if (availableRoutes.isEmpty()) {
                    // AU1-17 / AU4-26: empty state (was: blank card).
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Text("No audio outputs found", color = colors.foregroundSecondary, fontSize = 13.sp)
                    }
                }
                availableRoutes.forEachIndexed { i, route ->
                    if (i > 0) AudioDivider()
                    val name = audioRouteDisplayName(route)
                    val isActive = route == activeRoute
                    var routeHasFocus by remember(route) { mutableStateOf(false) }
// Previous implementation retained:                     Row(
// Previous implementation retained:                         Modifier.fillMaxWidth().springInteractiveScale(0.98f)
                    androidx.compose.material3.ListItem(
// Previous implementation retained:                         modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).springInteractiveScale(0.98f)
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
// Previous implementation retained:                             .then(if (!reduceAudioMotion) Modifier.springInteractiveScale(0.98f) else Modifier)
                            .then(if (!reduceAudioMotion) Modifier.audioRoutePressScale() else Modifier)
                            .onFocusChanged { routeHasFocus = it.isFocused }
                            .then(if (routeHasFocus) Modifier.border(2.dp, colors.accent, AudioCardShape) else Modifier)
                            // AU4-32: one merged TalkBack item; AU4-34: "Active" in the state description.
                            .semantics(mergeDescendants = true) { stateDescription = if (isActive) "Active" else "Not active" }
                            // AU1-34 / AU4-48: named action ("Use earpiece").
// Previous implementation retained:                             .clickable(onClickLabel = "Use $name") {
                            .clickable(enabled = !callInProgress, onClickLabel = "Use $name") {
                                // AU1-23: switching to the speaker mid-call asks first.
                                if (route == AudioOutputRoute.speaker && callInProgress && !isActive) {
                                    showSpeakerConfirm = true
                                } else {
                                    requestRoute(route)
                                }
                            }
// Previous implementation retained:                             .padding(horizontal = 16.dp, vertical = 12.dp),
// Previous implementation retained:                         verticalAlignment = Alignment.CenterVertically,
// Previous implementation retained:                     ) {
// Previous implementation retained:                         // AU4-12: 40dp container, 24dp icon (was: 36 / 20).
// Previous implementation retained:                         Box(
                            .padding(horizontal = 0.dp),
                        headlineContent = { Text(name, color = colors.foreground, fontWeight = FontWeight.SemiBold,
                            fontSize = 14.5.sp) },
                        leadingContent = { Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(colors.accent.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center,
// Previous implementation retained:                         ) { Icon(audioRouteIcon(route), null, Modifier.size(24.dp), tint = colors.accent) }
// Previous implementation retained:                         Spacer(Modifier.width(14.dp))
// Previous implementation retained:                         Text(name, color = colors.foreground, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, modifier = Modifier.weight(1f))
// Previous implementation retained:                         if (isActive) {
// Previous implementation retained:                             // AU1-08 / AU4-10: same check-circle pattern as the radios;
// Previous implementation retained:                             // AU1-09: replaces the low-contrast 11sp green "ACTIVE" chip.
// Previous implementation retained:                             Icon(Icons.Rounded.CheckCircle, null, Modifier.size(22.dp), tint = colors.accent)
// Previous implementation retained:                         }
// Previous implementation retained:                     }
                        ) { Icon(audioRouteIcon(route), null, Modifier.size(24.dp), tint = colors.accent) } },
                        trailingContent = {
// Previous implementation retained:                             if (isActive) Icon(Icons.Rounded.CheckCircle, null, Modifier.size(24.dp), tint = colors.accent)
                            if (isActive) AudioStatusChip("Active", Icons.Rounded.CheckCircle, colors.accent)
                        },
                        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = colors.surface),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))

            // 3. Routing preference
// Previous implementation retained:             AudioSectionHeader("Routing preference")
            AudioSectionHeader("Call audio")
// Previous implementation retained:             Text("Modes are saved preferences on this phone. They do not activate dual-speaker playback; that support is not verified.",
            Text("Only the selected mode is saved on this phone, not Bluetooth names or addresses. This preference is not sent to the server. It does not activate dual-speaker playback; that support is not verified.",
                color = colors.foregroundSecondary, fontSize = 13.sp)
            if (modeSaveFailed) {
                Text("This audio preference has not been saved on this phone.", color = colors.error)
                androidx.compose.material3.TextButton(onClick = { settings.retryPreferenceSave() },
                    modifier = Modifier.heightIn(min = 48.dp)) { Text("Retry save") }
            }
            AudioGroupedCard {
                // AU1-03: a real radio group for TalkBack (was: plain clickable rows).
                Column(Modifier.selectableGroup()) {
                    AudioRadioRow("Automatic (recommended)", "Best for most people - uses your phone's normal stereo and call routing",
                        mode == AudioPreferenceMode.automatic, true) { settings.setPreferenceMode(AudioPreferenceMode.automatic) }
                    AudioDivider()
                    AudioRadioRow("Standard stereo playback", "Keeps the phone's standard media and call routes",
                        mode == AudioPreferenceMode.preferStereo, true) { settings.setPreferenceMode(AudioPreferenceMode.preferStereo) }
                    AudioDivider()
                    AudioRadioRow(
                        // AU1-05 / AU4-21: the experimental risk is visible in the title.
                        "Dual-speaker (experimental)",
                        // AU1-04 / AU1-45: the reason is stated for every disabled state;
                        // AU1-52: the privacy side effect is spelled out.
                        when (dualStatus) {
                            DualOutputStatus.unsupported -> "Unsupported on this device hardware/OS"
                            DualOutputStatus.unavailable -> "Temporarily unavailable right now"
                            DualOutputStatus.failed -> "Activation failed on this device"
                            else -> "Plays call audio on the earpiece and speaker together - people nearby may hear the call"
                        },
                        mode == AudioPreferenceMode.experimentalDualOutput,
                        // AU1-04: also disabled when unavailable or failed (was: only unsupported).
// Previous implementation retained:                         dualStatus != DualOutputStatus.unsupported && dualStatus != DualOutputStatus.unavailable && dualStatus != DualOutputStatus.failed,
// Previous implementation retained:                     ) { settings.setPreferenceMode(AudioPreferenceMode.experimentalDualOutput) }
                        audioModeSelectionEnabled(AudioPreferenceMode.experimentalDualOutput, dualStatus),
// Previous implementation retained:                     ) { selectAudioModeIfEnabled(settings, AudioPreferenceMode.experimentalDualOutput, dualStatus) }
                    ) { if (audioModeSelectionEnabled(AudioPreferenceMode.experimentalDualOutput, dualStatus)) showExperimentalConfirm = true }
                }
            }
            Spacer(Modifier.height(24.dp))

            // 4. Evidence dataset (verbatim records; status shown as recorded)
            // AU1-01: honest framing (was: "VERIFIED DEVICE TEST DATASET").
            androidx.compose.material3.TextButton(
                onClick = { showDeviceDetails = !showDeviceDetails },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics {
                    stateDescription = if (showDeviceDetails) "Expanded" else "Collapsed"
                },
            ) { Text(if (showDeviceDetails) "Hide device compatibility details" else "Device compatibility details") }
            if (showDeviceDetails) {
            AudioSectionHeader("Tested by the Chaty team")
            Text(
                "Lab results on specific builds, with their test date - not the status of this phone.",
                color = colors.foregroundSecondary, fontSize = 12.sp,
                modifier = Modifier.padding(start = 14.dp, bottom = 10.dp),
            )
            AudioGroupedCard {
                // AU3-08: records for this device model first.
// Previous implementation retained:                 val records = remember {
// Previous implementation retained: // Previous implementation retained:                     AudioSettingsController.verifiedDeviceRecords.sortedByDescending { it["deviceModel"] == Build.MODEL }
// Previous implementation retained:                     AudioSettingsController.deviceVerifications.sortedByDescending { it.deviceModel == Build.MODEL }
// Previous implementation retained:                 val allRecords = AudioSettingsController.deviceVerifications
                val allRecords = deviceRecordState
// Previous implementation retained:                 val records = remember(allRecords, showAllDevices) {
// Previous implementation retained:                     audioDeviceRecordsForDisplay(allRecords, Build.MODEL, showAllDevices)
// Previous implementation retained:                 }
                val matchingRecords = remember(allRecords, showAllDevices) {
                    audioDeviceRecordsForDisplay(allRecords, Build.MODEL, showAllDevices)
                }
                var recordPage by remember(allRecords, showAllDevices) { mutableIntStateOf(0) }
                val pageCount = ((matchingRecords.size + 19) / 20).coerceAtLeast(1)
                val safePage = recordPage.coerceIn(0, pageCount - 1)
                val records = matchingRecords.drop(safePage * 20).take(20)
                if (pageCount > 1) {
                    Text("Device records page ${safePage + 1} of $pageCount",
                        color = colors.foregroundSecondary, modifier = Modifier.padding(horizontal = 14.dp))
                    Column(Modifier.fillMaxWidth()) {
                        androidx.compose.material3.TextButton(enabled = safePage > 0,
                            onClick = { recordPage = safePage - 1 }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Previous records") }
                        androidx.compose.material3.TextButton(enabled = safePage < pageCount - 1,
                            onClick = { recordPage = safePage + 1 }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Next records") }
                    }
                }
                androidx.compose.material3.TextButton(onClick = { showAllDevices = !showAllDevices },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(if (showAllDevices) "Show this device only" else "Show all recorded devices")
                }
// Previous implementation retained:                 if (records.isEmpty()) Text("No recorded lab results match this device model.",
                if (deviceRecordsFailed) {
                    Text("Recorded device details could not be loaded.", color = colors.error,
                        modifier = Modifier.padding(16.dp))
                    androidx.compose.material3.TextButton(onClick = { settings.retryDeviceRecords() },
                        modifier = Modifier.heightIn(min = 48.dp)) { Text("Retry device details") }
                }
                if (!deviceRecordsFailed && records.isEmpty()) Text("No recorded lab results match this device model.",
                    color = colors.foregroundSecondary, modifier = Modifier.padding(16.dp))
                records.forEachIndexed { idx, rec ->
                    if (idx > 0) AudioDivider()
                    // AU1-19: safe casts (was: `as String`, crashes on a missing key).
// Previous implementation retained:                     val status = rec["testStatus"] as? String ?: "Unknown"
// Previous implementation retained:                     val deviceModel = rec["deviceModel"] as? String ?: "Unknown device"
// Previous implementation retained:                     val osVersion = rec["osVersion"] as? String ?: ""
// Previous implementation retained:                     val method = rec["verificationMethod"] as? String ?: ""
// Previous implementation retained:                     val testDate = rec["testDate"] as? String
                    val status = rec.status
                    val deviceModel = rec.deviceModel
                    val osVersion = rec.osVersion
                    val method = rec.method
                    val testDate = rec.recordedDate
                    // AU1-10: theme tokens (was: hard-coded iOS colours).
                    val (color, statusIcon) = when (status) {
// Previous implementation retained: // Previous implementation retained:                         "Verified Dual Output" -> colors.success to Icons.Outlined.CheckCircleOutline
// Previous implementation retained:                         com.example.chat.core.audio.DeviceVerificationStatus.verifiedDualOutput -> colors.success to Icons.Outlined.CheckCircleOutline
// Previous implementation retained:                         "Verified Dual Output" -> colors.success to Icons.Rounded.CheckCircleOutline
                        com.example.chat.core.audio.DeviceVerificationStatus.verifiedDualOutput -> colors.success to Icons.Rounded.CheckCircleOutline
// Previous implementation retained:                         "Native Stereo" -> colors.accent to Icons.Rounded.MusicNote
// Previous implementation retained:                         "Standard Single Route" -> colors.foregroundSecondary to com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.radioOn)
// Previous implementation retained: // Previous implementation retained:                         "Unsupported or Failed" -> colors.error to Icons.Outlined.Cancel
                        com.example.chat.core.audio.DeviceVerificationStatus.nativeStereo -> colors.accent to Icons.Rounded.MusicNote
                        com.example.chat.core.audio.DeviceVerificationStatus.standardSingleRoute -> colors.foregroundSecondary to com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.radioOn)
// Previous implementation retained:                         com.example.chat.core.audio.DeviceVerificationStatus.unsupportedOrFailed -> colors.error to Icons.Outlined.Cancel
// Previous implementation retained:                         "Unsupported or Failed" -> colors.error to Icons.Rounded.Cancel
                        com.example.chat.core.audio.DeviceVerificationStatus.unsupportedOrFailed -> colors.error to Icons.Rounded.Cancel
// Previous implementation retained:                         else -> colors.warning to Icons.Outlined.HelpOutline
                        else -> colors.warning to Icons.Rounded.HelpOutline
                    }
                    // AU1-33: merged TalkBack item.
                    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(statusIcon, null, Modifier.size(20.dp), tint = color)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
// Previous implementation retained:                             Row(verticalAlignment = Alignment.CenterVertically) {
// Previous implementation retained:                                 // AU1-32: minimum 12sp (was: 13.5 / 11.5 / 11.5 / 10.5).
// Previous implementation retained:                                 Text(deviceModel, color = colors.foreground, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
// Previous implementation retained:                                 Spacer(Modifier.width(6.dp))
// Previous implementation retained:                                 Text("($osVersion)", color = colors.foregroundSecondary, fontSize = 12.sp)
// Previous implementation retained:                             }
                            Text(deviceModel, color = colors.foreground, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                            Text(osVersion, color = colors.foregroundSecondary, fontSize = 12.sp)
                            if (rec.platform.equals("Android", ignoreCase = true)) Text(
                                context.getString(com.example.chat.R.string.audio_record_security_patch,
                                    rec.securityPatchLevel?.toString()
                                        ?: context.getString(com.example.chat.R.string.audio_record_not_recorded)),
                                color = colors.foregroundSecondary, fontSize = 12.sp)

                            Spacer(Modifier.height(2.dp))
                            Text(method, color = colors.foregroundSecondary, fontSize = 12.sp)
                            if (testDate != null) {
                                Spacer(Modifier.height(2.dp))
                                Text("Tested $testDate", color = colors.foregroundSecondary, fontSize = 12.sp)
                            }
                        }
// Previous implementation retained:                         Text(
// Previous implementation retained:                             status, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
// Previous implementation retained:                             modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 2.dp),
// Previous implementation retained:                         )
// Previous implementation retained:                         AudioStatusChip(status, statusIcon, color, Modifier.widthIn(max = 160.dp))
                        AudioStatusChip(status.label, statusIcon, color, Modifier.widthIn(max = 160.dp))
                    }
                }
            }
            }
            androidx.compose.material3.TextButton(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                onClick = {
                    val context = view.context
                    val opened = runCatching {
                        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_CAPTIONING_SETTINGS))
                    }.isSuccess
// Previous implementation retained:                     if (!opened) scope.launch { snackbarHostState.showSnackbar("Caption settings aren't available on this device") }
                    if (!opened) screenScope.launch { snackbarHostState.showSnackbar("Caption settings aren't available on this device") }
                },
            ) { Text("System caption settings") }
            Text("Android caption options vary by device. Opening settings doesn't enable captions.",
                color = colors.foregroundSecondary, fontSize = 12.sp)
            AudioDiagnosticShare(coordinator)
            Spacer(Modifier.height(36.dp))
        }
        }
    }
    SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (showExperimentalConfirm) {
        ChatyConfirmationDialog(
            shape = ChatyModalShape,
            onDismissRequest = { showExperimentalConfirm = false },
            title = { Text("Save experimental audio preference?", color = colors.foreground) },
            text = { Text("Dual-speaker support is not verified. If supported in a future call, people nearby may hear its audio. Saving this preference does not activate dual-speaker playback.", color = colors.foregroundSecondary) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = {
                showExperimentalConfirm = false
                // Re-check the live capability, not the status captured when the dialog opened.
                val latestStatus = capability.communicationCapabilities.value?.dualOutputStatus ?: DualOutputStatus.unavailable
                selectAudioModeIfEnabled(settings, AudioPreferenceMode.experimentalDualOutput, latestStatus)
            }) { Text("Save preference") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { showExperimentalConfirm = false }) { Text("Cancel") } },
        )
    }

    if (showSpeakerConfirm) {
        // AU1-23: speaker confirmation during an active call.
        ChatyConfirmationDialog(
            shape = ChatyModalShape,
            onDismissRequest = { showSpeakerConfirm = false },
            title = { Text("Play call on speaker?", color = colors.foreground) },
            text = { Text("People nearby may hear the call.", color = colors.foregroundSecondary) },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { showSpeakerConfirm = false }) { Text("Cancel", color = colors.primary) } },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showSpeakerConfirm = false
                    requestRoute(AudioOutputRoute.speaker)
                }) { Text("Use speaker", color = colors.primary) }
            },
        )
    }
}

@Composable
private fun AudioSectionHeader(title: String) {
    val colors = LocalAppColors.current
    Text(
        title, color = colors.foregroundSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp,
        // AU1-35 / AU4-31: heading semantics for TalkBack navigation.
        modifier = Modifier.semantics { heading() }.padding(start = 14.dp, bottom = 10.dp),
    )
}

@Composable
private fun AudioGroupedCard(content: @Composable () -> Unit) {
    val colors = LocalAppColors.current
    // AU1-11 / AU4-15: theme surface in both themes (was: Color(0xFF1C1C1E) in dark);
    // AU4-14: border + tonal surface only (was: custom 5dp shadow, withShadow flag).
    Column(
        Modifier.fillMaxWidth()
            .clip(AudioCardShape)
            .background(colors.surface, AudioCardShape)
            .border(BorderStroke(0.8.dp, colors.borderSubtle), AudioCardShape),
    ) { content() }
}

@Composable
private fun AudioDivider() {
    val colors = LocalAppColors.current
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(0.8.dp).background(colors.borderSubtle.copy(alpha = 0.6f)))
}

@Composable
// Previous implementation retained: private fun AudioRadioRow(title: String, subtitle: String, selected: Boolean, enabled: Boolean, onTap: () -> Unit) {
internal fun AudioRadioRow(title: String, subtitle: String, selected: Boolean, enabled: Boolean, onTap: () -> Unit) {
    val colors = LocalAppColors.current
    val view = LocalView.current
    val reduceMotion = com.example.chat.ui.kit.rememberChatyReduceMotion()
    var hasKeyboardFocus by remember { mutableStateOf(false) }
    Row(
        // AU1-21: no press animation on a disabled row (was: always springInteractiveScale).
// Previous implementation retained:         Modifier.fillMaxWidth()
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .onFocusChanged { hasKeyboardFocus = it.isFocused }
            .then(if (hasKeyboardFocus) Modifier.border(2.dp, colors.accent, AudioCardShape) else Modifier)
// Previous implementation retained:             .then(if (enabled) Modifier.springInteractiveScale(0.98f) else Modifier)
            .then(if (enabled && !reduceMotion) Modifier.springInteractiveScale(0.98f) else Modifier)
            // AU1-03: proper radio semantics (was: clickable without a role).
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton) {
                ChatyHapticsPort.selection(view)
                onTap()
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                color = if (enabled) colors.foreground else colors.foregroundSecondary.copy(alpha = 0.5f),
            )
            Spacer(Modifier.height(3.dp))
            // AU1-58: the reason subtitle keeps full contrast when disabled.
            Text(subtitle, color = colors.foregroundSecondary, fontSize = 12.sp)
        }
// Previous implementation retained:         Icon(
// Previous implementation retained: // Previous implementation retained:             if (selected) Icons.Rounded.CheckCircle else com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.radioOff), null, Modifier.size(22.dp),
// Previous implementation retained:             if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, Modifier.size(22.dp),
// Previous implementation retained:             tint = if (selected) colors.accent else colors.foregroundSecondary.copy(alpha = 0.4f),
// Previous implementation retained:         )
        if (!enabled) {

//Icon(Icons.Rounded.Info, null, Modifier.size(24.dp), tint = colors.foregroundSecondary)
            com.example.chat.ui.icons.ChatyPurposeIcon(com.example.chat.ui.icons.ChatyIconId.info, Icons.Rounded.Info, null, Modifier.size(24.dp), tint = colors.foregroundSecondary)
            Spacer(Modifier.width(8.dp))
        }
        RadioButton(selected = selected, onClick = null, enabled = enabled)
    }
}

/** One wrapping status chip for current-route, capability and device-result badges. */
@Composable
internal fun AudioStatusChip(label: String, icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Row(modifier.clip(AudioBadgeShape).background(tint.copy(alpha = 0.15f))
        .border(BorderStroke(1.dp, tint.copy(alpha = 0.3f)), AudioBadgeShape)
        .padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(16.dp), tint = tint)
        Spacer(Modifier.width(8.dp))
        Text(label, color = tint, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
            modifier = Modifier.weight(1f, fill = false))
    }
}


/** One exhaustive status-to-resource mapping, kept separate for source-only unit fixtures. */
internal fun dualStatusLabelResource(status: DualOutputStatus): Int = when (status) {
    DualOutputStatus.active -> com.example.chat.R.string.audio_dual_active
    DualOutputStatus.notVerified -> com.example.chat.R.string.audio_dual_not_verified
    DualOutputStatus.unavailable -> com.example.chat.R.string.audio_dual_unavailable
    DualOutputStatus.unsupported -> com.example.chat.R.string.audio_dual_unsupported
    DualOutputStatus.failed -> com.example.chat.R.string.audio_dual_failed
}


/** Lifecycle-aware collection using existing runtime dependency; no Gradle change. */
@Composable
private fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectAudioStateWithLifecycle(): androidx.compose.runtime.State<T> {
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    return androidx.compose.runtime.produceState(value, this, lifecycle) {
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            this@collectAudioStateWithLifecycle.collect { value = it }
        }
    }
}


/** Exact model matching only. Do not claim a near-match lab result belongs to this phone. */
internal fun audioDeviceRecordsForDisplay(
    records: List<com.example.chat.core.audio.DeviceVerification>, model: String, showAll: Boolean,
): List<com.example.chat.core.audio.DeviceVerification> =
    if (showAll) records.sortedByDescending { it.deviceModel.equals(model, ignoreCase = true) }
    else records.filter { it.deviceModel.equals(model, ignoreCase = true) }


/** Readiness never masquerades as an empty hardware route list. */
private sealed interface AudioUiLoadState {
    data object Loading : AudioUiLoadState
    data object Failed : AudioUiLoadState
    data class Ready(val snapshot: AudioSettingsUiState) : AudioUiLoadState
}

@Composable
internal fun AudioCapabilitiesPlaceholder(failed: Boolean, onRetry: () -> Unit) {
    val colors = LocalAppColors.current
    val label = androidx.compose.ui.res.stringResource(if (failed) com.example.chat.R.string.audio_capabilities_failed
        else com.example.chat.R.string.audio_capabilities_loading)
    Column(Modifier.fillMaxWidth().padding(16.dp).semantics {
        liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite
    }, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
        Text(label, color = colors.foreground, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
        if (failed) {
            androidx.compose.material3.TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.audio_capabilities_retry))
            }
        } else repeat(3) {
            // Static skeleton: no flash/shimmer, including when reduced motion is enabled.
            Box(Modifier.fillMaxWidth().height(56.dp).background(colors.surface, AudioCardShape))
        }
    }
}


/** Settings are not a second live-call routing authority. */
internal fun settingsRouteBlocked(hasLiveCall: Boolean, owner: AudioUseCase?): Boolean =
    hasLiveCall || owner == AudioUseCase.communication

// Baseline source retained for reversibility:
// Previous implementation retained:             val result = runCatching { capability.setPreferredRoute(AudioUseCase.communication, route) }


/** One selection gate for rendered disabled state and the selection callback. */
internal fun audioModeSelectionEnabled(mode: AudioPreferenceMode, status: DualOutputStatus): Boolean =
    mode != AudioPreferenceMode.experimentalDualOutput || when (status) {
        DualOutputStatus.unsupported, DualOutputStatus.unavailable, DualOutputStatus.failed -> false
        else -> true
    }

internal fun selectAudioModeIfEnabled(
    settings: AudioSettingsController, mode: AudioPreferenceMode, status: DualOutputStatus,
): Boolean {
    if (!audioModeSelectionEnabled(mode, status)) return false
    settings.setPreferenceMode(mode)
    return true
}


/** A canceled predictive gesture resets the preview and never exits the page. */
internal suspend fun completeAudioSettingsBack(
    events: kotlinx.coroutines.flow.Flow<androidx.activity.BackEventCompat>,
    onProgress: (Float) -> Unit,
    onEdge: (Int) -> Unit,
    onCommit: () -> Unit,
) {
    completeAudioBackProgress(events, { onEdge(it.swipeEdge); it.progress }, onProgress, onCommit)
}

/** Generic progress seam keeps commit/cancel tests independent of Android event construction. */
internal suspend fun <T> completeAudioBackProgress(
    events: kotlinx.coroutines.flow.Flow<T>, progressOf: (T) -> Float,
    onProgress: (Float) -> Unit, onCommit: () -> Unit,
) {
    try {
        events.collect {
            val progress = progressOf(it)
            onProgress(if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f)
        }
        onCommit()
    } finally { onProgress(0f) }
}


internal val audioRoutingHelpSteps = listOf(
    "During a call, use the Speaker control on the call screen. Audio settings cannot change a live call.",
    "For a Bluetooth device or headset, check Android's system audio controls and confirm the device is connected.",
    "If the requested output does not match the current output shown here, the switch has not been confirmed. Try another available output.",
    "If a call loses audio after another app or phone call interrupts it, end the call and start it again when safe. No test sound plays automatically.",
)


/** AU3-30: animation state is read in the graphics layer, not composition. */
private fun Modifier.audioRoutePressScale(): Modifier = composed {
    val scale = remember { androidx.compose.animation.core.Animatable(1f) }
    val animationScope = androidx.compose.runtime.rememberCoroutineScope()
    val feedbackView = LocalView.current
    this.then(Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value })
        .pointerInputForAudio(scale, animationScope, feedbackView)
}

private fun Modifier.pointerInputForAudio(
    scale: androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    animationScope: kotlinx.coroutines.CoroutineScope,
    feedbackView: android.view.View,
): Modifier = pointerInput(scale, feedbackView) {
    var animation: kotlinx.coroutines.Job? = null
    try {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            ChatyHapticsPort.selection(feedbackView)
            animation?.cancel()
            animation = animationScope.launch {
                scale.animateTo(0.98f, androidx.compose.animation.core.spring(dampingRatio = 0.82f, stiffness = 380f))
            }
            waitForUpOrCancellation(pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            animation?.cancel()
            animation = animationScope.launch {
                scale.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.82f, stiffness = 380f))
            }
        }
    } finally {
        animation?.cancel()
        animationScope.launch { scale.snapTo(1f) }
    }
}
