package com.example.chat.ui.settings

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
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DevicesOther
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.SpeakerGroup
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.VolumeUp
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
private val AudioCardShape = RoundedCornerShape(16.dp)
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
    AudioOutputRoute.systemDefault -> "System default"
}

internal fun audioRouteIcon(route: AudioOutputRoute): ImageVector = when (route) {
    AudioOutputRoute.receiver -> com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.earpiece)
    AudioOutputRoute.speaker -> Icons.Rounded.VolumeUp
    AudioOutputRoute.bluetooth -> Icons.Rounded.BluetoothAudio
    AudioOutputRoute.wiredHeadset -> Icons.Rounded.Headphones
    AudioOutputRoute.usb -> Icons.Rounded.Usb
    AudioOutputRoute.systemDefault -> Icons.Rounded.DevicesOther
}

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
    capability: AudioCapabilityService,
    coordinator: AudioPolicyCoordinator,
    scope: CoroutineScope,
    onBack: () -> Unit,
) {
    val colors = LocalAppColors.current
    var version by remember { mutableIntStateOf(0) }
    // AU1-14: listeners may fire on a non-main thread; hop to main before
    // touching snapshot state.
    val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    DisposableEffect(settings, capability, coordinator) {
        val l: () -> Unit = { mainHandler.post { version += 1 } }
        settings.addListener(l); capability.addListener(l); coordinator.addListener(l)
        onDispose { settings.removeListener(l); capability.removeListener(l); coordinator.removeListener(l) }
    }
    @Suppress("UNUSED_VARIABLE") val rebuild = version

    val dualStatus = capability.communicationDualStatus
    val activeRoute = capability.activeCommunicationRoute
    val availableRoutes = capability.availableCommunicationRoutes
    val mode = settings.preferenceMode
    // AU1-48: the coordinator is now actually read (AU1-23 speaker guard).
    val callInProgress = coordinator.activeOwner == AudioUseCase.communication

    // was: hard-coded iOS system colours (AU1-10):
    // val green = Color(0xFF34C759); val orange = Color(0xFFFF9500)
    // val red = Color(0xFFFF3B30); val blue = Color(0xFF007AFF); val gray = Color(0xFF8E8E93)
    // Now theme tokens: colors.success / colors.warning / colors.error / colors.accent / colors.foregroundSecondary.

    val view = LocalView.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showEngineInfo by rememberSaveable { mutableStateOf(false) }
    var showSpeakerConfirm by rememberSaveable { mutableStateOf(false) }

    fun requestRoute(route: AudioOutputRoute) {
        scope.launch {
            val previous = activeRoute
            // AU1-06: success/failure feedback (was: fire-and-forget).
            val result = runCatching { capability.setPreferredRoute(AudioUseCase.communication, route) }
            if (result.isSuccess) {
                ChatyHapticsPort.selection(view) // AU1-22 / AU4-24: haptic only on success
                if (previous != route) {
                    // AU4-23: reversible via Undo.
                    val r = snackbarHostState.showSnackbar(
                        "Call audio: ${audioRouteDisplayName(route)}",
                        actionLabel = "Undo",
                    )
                    if (r == SnackbarResult.ActionPerformed) {
                        runCatching { capability.setPreferredRoute(AudioUseCase.communication, previous) }
                    }
                }
            } else {
                snackbarHostState.showSnackbar("Couldn't switch audio route")
            }
        }
    }

    Box(Modifier.fillMaxSize().background(colors.background)) {
    Column(Modifier.fillMaxSize()) {
        // AU1-37: shared page header (was: custom 19*fontScale title Row).
        ChatyAppBar(title = "Audio & Speakers", onBack = onBack)
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
                DualOutputStatus.active -> Triple("Active & verified", colors.success, Icons.Outlined.CheckCircleOutline)
                DualOutputStatus.notVerified -> Triple("Available (standard route)", colors.accent, Icons.Outlined.Info)
                DualOutputStatus.unavailable -> Triple("Temporarily unavailable", colors.warning, Icons.Outlined.HelpOutline)
                DualOutputStatus.unsupported -> Triple("Unsupported by OS/hardware", colors.foregroundSecondary, Icons.Outlined.Cancel)
                DualOutputStatus.failed -> Triple("Activation failed", colors.error, Icons.Outlined.Cancel)
            }
            SpecularGlassCard(Modifier.fillMaxWidth(), blur = 12f, borderRadius = 20.dp, padding = PaddingValues(18.dp)) {
              Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.SpeakerGroup, null, Modifier.size(26.dp), tint = colors.accent)
                    Spacer(Modifier.width(10.dp))
                    // AU4-19: plain words (was: "Dual-Speaker Engine").
                    Text("Dual-speaker mode", color = colors.foreground, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    // AU1-38 / AU4-16: the long description sits behind an info button.
                    IconButton(onClick = { showEngineInfo = !showEngineInfo }) {
                        Icon(Icons.Outlined.Info, "About dual-speaker mode", Modifier.size(20.dp), tint = colors.foregroundSecondary)
                    }
                    // AU4-07: icon + text badge, so colour is not the only cue.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clip(AudioBadgeShape).background(badgeFg.copy(alpha = 0.15f))
                            .border(BorderStroke(1.dp, badgeFg.copy(alpha = 0.3f)), AudioBadgeShape)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Icon(badgeIcon, null, Modifier.size(13.dp), tint = badgeFg)
                        Spacer(Modifier.width(5.dp))
                        Text(badgeText, color = badgeFg, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                }
                if (showEngineInfo) {
                    Spacer(Modifier.height(10.dp))
                    // AU1-39: plain wording (was: "Smart audio policy coordinates native earpiece ...").
                    Text(
                        "Plays calls through the earpiece and speaker together when possible, without breaking Bluetooth or the microphone.",
                        color = colors.foregroundSecondary, fontSize = 13.sp, lineHeight = 18.sp,
                    )
                }
                Spacer(Modifier.height(12.dp))
                // AU4-03: the selected mode answers first.
                Text(
                    "Mode: " + when (mode) {
                        AudioPreferenceMode.automatic -> "Automatic"
                        AudioPreferenceMode.preferStereo -> "Standard stereo"
                        AudioPreferenceMode.experimentalDualOutput -> "Dual-speaker (experimental)"
                    },
                    color = colors.foreground, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                )
                Spacer(Modifier.height(6.dp))
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
            Spacer(Modifier.height(20.dp))

            // 2. Routes first (AU1-41: the most-used action moves up; was below the preference card).
            // AU1-07: honest title (was: "ACTIVE & AVAILABLE ROUTES").
            AudioSectionHeader("Call audio routes")
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
                    Row(
                        Modifier.fillMaxWidth().springInteractiveScale(0.98f)
                            // AU4-32: one merged TalkBack item; AU4-34: "Active" in the state description.
                            .semantics(mergeDescendants = true) { stateDescription = if (isActive) "Active" else "Not active" }
                            // AU1-34 / AU4-48: named action ("Use earpiece").
                            .clickable(onClickLabel = "Use $name") {
                                // AU1-23: switching to the speaker mid-call asks first.
                                if (route == AudioOutputRoute.speaker && callInProgress && !isActive) {
                                    showSpeakerConfirm = true
                                } else {
                                    requestRoute(route)
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // AU4-12: 40dp container, 24dp icon (was: 36 / 20).
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(colors.accent.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center,
                        ) { Icon(audioRouteIcon(route), null, Modifier.size(24.dp), tint = colors.accent) }
                        Spacer(Modifier.width(14.dp))
                        Text(name, color = colors.foreground, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, modifier = Modifier.weight(1f))
                        if (isActive) {
                            // AU1-08 / AU4-10: same check-circle pattern as the radios;
                            // AU1-09: replaces the low-contrast 11sp green "ACTIVE" chip.
                            Icon(Icons.Rounded.CheckCircle, null, Modifier.size(22.dp), tint = colors.accent)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))

            // 3. Routing preference
            AudioSectionHeader("Routing preference")
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
                        dualStatus != DualOutputStatus.unsupported && dualStatus != DualOutputStatus.unavailable && dualStatus != DualOutputStatus.failed,
                    ) { settings.setPreferenceMode(AudioPreferenceMode.experimentalDualOutput) }
                }
            }
            Spacer(Modifier.height(24.dp))

            // 4. Evidence dataset (verbatim records; status shown as recorded)
            // AU1-01: honest framing (was: "VERIFIED DEVICE TEST DATASET").
            AudioSectionHeader("Tested by the Chaty team")
            Text(
                "Lab results on specific builds, with their test date - not the status of this phone.",
                color = colors.foregroundSecondary, fontSize = 12.sp,
                modifier = Modifier.padding(start = 14.dp, bottom = 10.dp),
            )
            AudioGroupedCard {
                // AU3-08: records for this device model first.
                val records = remember {
                    AudioSettingsController.verifiedDeviceRecords.sortedByDescending { it["deviceModel"] == Build.MODEL }
                }
                records.forEachIndexed { idx, rec ->
                    if (idx > 0) AudioDivider()
                    // AU1-19: safe casts (was: `as String`, crashes on a missing key).
                    val status = rec["testStatus"] as? String ?: "Unknown"
                    val deviceModel = rec["deviceModel"] as? String ?: "Unknown device"
                    val osVersion = rec["osVersion"] as? String ?: ""
                    val method = rec["verificationMethod"] as? String ?: ""
                    val testDate = rec["testDate"] as? String
                    // AU1-10: theme tokens (was: hard-coded iOS colours).
                    val (color, statusIcon) = when (status) {
                        "Verified Dual Output" -> colors.success to Icons.Outlined.CheckCircleOutline
                        "Native Stereo" -> colors.accent to Icons.Rounded.MusicNote
                        "Standard Single Route" -> colors.foregroundSecondary to com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.radioOn)
                        "Unsupported or Failed" -> colors.error to Icons.Outlined.Cancel
                        else -> colors.warning to Icons.Outlined.HelpOutline
                    }
                    // AU1-33: merged TalkBack item.
                    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(statusIcon, null, Modifier.size(20.dp), tint = color)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // AU1-32: minimum 12sp (was: 13.5 / 11.5 / 11.5 / 10.5).
                                Text(deviceModel, color = colors.foreground, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                                Spacer(Modifier.width(6.dp))
                                Text("($osVersion)", color = colors.foregroundSecondary, fontSize = 12.sp)
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(method, color = colors.foregroundSecondary, fontSize = 12.sp)
                            if (testDate != null) {
                                Spacer(Modifier.height(2.dp))
                                Text("Tested $testDate", color = colors.foregroundSecondary, fontSize = 12.sp)
                            }
                        }
                        Text(
                            status, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(36.dp))
        }
        }
    }
    SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
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
private fun AudioRadioRow(title: String, subtitle: String, selected: Boolean, enabled: Boolean, onTap: () -> Unit) {
    val colors = LocalAppColors.current
    val view = LocalView.current
    Row(
        // AU1-21: no press animation on a disabled row (was: always springInteractiveScale).
        Modifier.fillMaxWidth()
            .then(if (enabled) Modifier.springInteractiveScale(0.98f) else Modifier)
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
        Icon(
            if (selected) Icons.Rounded.CheckCircle else com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.radioOff), null, Modifier.size(22.dp),
            tint = if (selected) colors.accent else colors.foregroundSecondary.copy(alpha = 0.4f),
        )
    }
}
