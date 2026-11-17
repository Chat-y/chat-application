package com.example.chat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LooksOne
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.ServiceLocator
import com.example.chat.ui.chats.detail.VoiceLevelMeter
import com.example.chat.ui.chats.detail.VoiceLevelMeterBar
import com.example.chat.ui.chats.detail.VoiceNoteController
import com.example.chat.ui.chats.detail.VoiceUiState
import com.example.chat.ui.chats.detail.decodeVoiceWaveform
import com.example.chat.ui.icons.ChatyIcon
import com.example.chat.ui.icons.ChatyIconId
import com.example.chat.ui.kit.ChatyActionButton
import com.example.chat.ui.kit.ChatyMotion
import com.example.chat.ui.kit.ChatyMotionTokens
import com.example.chat.ui.kit.GbCardContainer
import com.example.chat.ui.navigation.ReferenceLayoutMetrics
import com.example.chat.ui.theme.LocalAppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Row 7 premium voice recording UI, matched to the reference recording Bandi
 *  sent on 2026-11-17 (WhatsApp voice-note flow). Presentation-only drop-in
 *  replacements for the private VoiceRecordingCard / VoiceReviewCard in
 *  ChatVoiceBinding.kt: identical parameter lists and callback semantics, so
 *  the swap is one line per call site (that file is owned by the E2E worker).
 *  All colors come from LocalAppColors tokens; the recording session, gesture
 *  policy, lock/pause behaviour and send ordering are untouched.
 *  Light circles (send, mic, play) use the inverse token pair
 *  foreground/surface: near-white circle with dark glyph on dark themes,
 *  adapting on light themes. The trash control is a translucent error circle
 *  with an error-color glyph, matching the reference dark-red style. */

@Composable
fun VoiceRecordingCardPremium(
    state: VoiceUiState,
    levels: List<Double>,
    onDelete: () -> Unit,
    onPause: () -> Unit,
    // Return whether the existing session accepted the tap, avoiding a stuck label on a refused send.
    onSend: () -> Boolean,
    viewOnce: Boolean = false,
    onToggleViewOnce: () -> Unit = {},
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val reduced = com.example.chat.ui.kit.rememberChatyReduceMotion()
    // Presentation only: capture stop/upload/send ordering remains owned by the session.
    var sendSnapshot by remember { mutableStateOf<Pair<Int, List<Double>>?>(null) }
    val sendRequested = sendSnapshot != null
    LaunchedEffect(state.busy) { if (!state.busy) sendSnapshot = null }
    val controlsEnabled = !state.busy && !sendRequested
    val status = when {
        sendRequested -> "Sending recording…"
        state.busy -> "Processing recording…"
        state.interruptedForCall -> "Paused for call"
        state.paused -> "Paused"
        else -> "Recording"
    }
    // The reference bar shows a status line only while processing/sending or when
    // a call paused the capture; plain recording shows timer + waveform only.
    val showStatus = sendRequested || state.busy || state.interruptedForCall || state.paused
    // Freeze only the display on Send; never keep suggesting capture during finalization/upload.
    val shownSeconds = sendSnapshot?.first ?: state.seconds
    val shownLevels = sendSnapshot?.second ?: levels
    val elapsed = "%d:%02d".format(shownSeconds / 60, shownSeconds % 60)
    // Shared card owns shape, border, surface and LocalAppColors.shadow.
    GbCardContainer(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        margin = PaddingValues(ReferenceLayoutMetrics.recorderCardMarginDp.dp),
        padding = PaddingValues(horizontal = ReferenceLayoutMetrics.recorderCardHorizontalPaddingDp.dp,
            vertical = ReferenceLayoutMetrics.recorderCardVerticalPaddingDp.dp),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(ReferenceLayoutMetrics.recorderContentGapDp.dp)) {
        if (state.locked) {
            // Reference locked bar, top row: elapsed time left, live microphone
            // waveform right. Kept as the existing real sampled meter.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ReferenceLayoutMetrics.recorderControlGapDp.dp)) {
                ChatyIcon(ChatyIconId.microphone, size = 18.dp,
                    color = if (sendRequested || state.paused) colors.foregroundSecondary else colors.error)
                Text(elapsed, style = androidx.compose.material3.MaterialTheme.typography.labelLarge, color = colors.foreground)
                // Keep the existing real microphone samples. No decorative/random waveform.
                VoiceLevelMeterBar(shownLevels, colors.accent, Modifier.weight(1f))
            }
            if (showStatus) Text(status, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = colors.foregroundSecondary)
            // Reference locked bar, controls row: trash (dark red) / Pause pill /
            // view-once "1" / send (light circle). View-once sits between Pause
            // and Send (Bandi, 2026-11-17).
            // P2 width-aware fallback (OpenCode): the controls row needs about
            // 340dp including card padding; below that (320dp screens, large
            // text, split screen) it scrolls horizontally instead of clipping,
            // keeping the order trash / Pause / view-once "1" / send. The wide
            // layout keeps the reference spacing with the trash pinned left.
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val compactControls = maxWidth < 340.dp
                val controls: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
                    IconButton(onClick = onDelete, enabled = controlsEnabled,
                        modifier = Modifier.size(52.dp).background(colors.error.copy(alpha = 0.16f), CircleShape)) {
                        ChatyIcon(ChatyIconId.delete, semanticLabel = "Delete recording", color = colors.error)
                    }
                    if (!compactControls) Spacer(Modifier.weight(1f))
                    ChatyActionButton(onClick = onPause, enabled = controlsEnabled,
                        modifier = Modifier.widthIn(min = ReferenceLayoutMetrics.recorderPauseMinWidthDp.dp),
                        containerColor = colors.surfaceElevated, contentColor = colors.foreground,
                        icon = if (state.paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        label = if (state.paused) "Resume" else "Pause")
                    IconButton(onClick = onToggleViewOnce, enabled = controlsEnabled,
                        modifier = Modifier.size(52.dp).background(if (viewOnce) colors.accent else colors.surfaceElevated, CircleShape)) {
//                         ChatyIcon(ChatyIconId.numberOne,
//                             semanticLabel = if (viewOnce) "View once on. The voice message plays once." else "Send as view once voice message",
//                             color = if (viewOnce) colors.onAccent else colors.foreground)
                        com.example.chat.ui.icons.ChatyPurposeIcon(ChatyIconId.numberOne, Icons.Rounded.LooksOne,
                            contentDescription = if (viewOnce) "View once on. The voice message plays once." else "Send as view once voice message",
                            tint = if (viewOnce) colors.onAccent else colors.foreground)
                    }
                    IconButton(onClick = {
                        // finishVoice reserves busy synchronously, before its suspended finalize/upload work.
                        if (sendSnapshot == null) {
                            val snapshot = state.seconds to levels.toList()
                            if (onSend()) sendSnapshot = snapshot
                        }
                    }, enabled = controlsEnabled && !state.interruptedForCall,
                        modifier = Modifier.size(56.dp).background(
                            colors.foreground.copy(alpha = if (controlsEnabled && !state.interruptedForCall) 1f else 0.4f), CircleShape)) {
                        ChatyIcon(ChatyIconId.send,
                            semanticLabel = if (sendRequested) "Sending recording" else "Send recording",
                            color = colors.surface)
                    }
                }
                if (compactControls) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(ReferenceLayoutMetrics.recorderControlGapDp.dp),
                        verticalAlignment = Alignment.CenterVertically, content = controls)
                } else {
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ReferenceLayoutMetrics.recorderControlGapDp.dp),
                        verticalAlignment = Alignment.CenterVertically, content = controls)
                }
            }
        } else {
            // Reference hold bar: red mic glyph + elapsed left, "< Slide to cancel"
            // hint, light mic circle right with the lock pill sliding up above it.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ReferenceLayoutMetrics.recorderContentGapDp.dp)) {
                ChatyIcon(ChatyIconId.microphone, size = 18.dp, color = colors.error)
                Text(elapsed, style = androidx.compose.material3.MaterialTheme.typography.labelLarge, color = colors.foreground)
                Spacer(Modifier.weight(1f))
                Text(if (state.busy) status else "‹ Slide to cancel", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = colors.foregroundSecondary)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val slideMs = ChatyMotion.durationMs(context, ChatyMotion.baseMs)
                    // Lock pill slides up over the mic circle on entry; reduced
                    // motion makes the duration 0 via ChatyMotion.
                    AnimatedVisibility(
                        visible = true,
                        enter = slideInVertically(initialOffsetY = { it / 2 },
                            animationSpec = tween(slideMs, easing = ChatyMotion.curve(context, ChatyMotion.enter))) + fadeIn(tween(slideMs)),
                        exit = slideOutVertically(targetOffsetY = { it / 2 },
                            animationSpec = tween(slideMs, easing = ChatyMotion.curve(context, ChatyMotion.exit))) + fadeOut(tween(slideMs)),
                    ) {
                        Box(Modifier.padding(bottom = 4.dp).background(colors.surfaceElevated, CircleShape).padding(horizontal = 10.dp, vertical = 6.dp)) {
                            ChatyIcon(ChatyIconId.lock, size = 16.dp, semanticLabel = "Slide up to lock recording", color = colors.foregroundSecondary)
                        }
                    }
                    val scale = if (reduced) 1f else ChatyMotionTokens.RECORDER_MIC_BASE_SCALE + (levels.lastOrNull() ?: 0.0).toFloat().coerceIn(0f, 1f) * ChatyMotionTokens.RECORDER_MIC_LEVEL_SCALE
                    Box(Modifier.size(ReferenceLayoutMetrics.recorderMicrophoneSizeDp.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                        .background(colors.foreground, CircleShape), contentAlignment = Alignment.Center) {
                        ChatyIcon(ChatyIconId.microphone, semanticLabel = "Recording audio", color = colors.surface)
                    }
                }
            }
        }
      }
    }
}

/** After-recording review (also the reference paused-state preview): play circle,
 *  scrub waveform with position/duration, then trash / Resume / view-once / send.
 *  Player, audio-focus, account-scope and join logic are copied verbatim from the
 *  private VoiceReviewCard; only the layout and color sources changed.
 *  Chaty icons and theme colors only. Reduced motion removes the scrub and press animation. */
@Composable
fun VoiceReviewCardPremium(
    clip: VoiceNoteController.Clip,
    recordingAccountGeneration: Long?,
    busy: Boolean,
    viewOnce: Boolean,
    onToggleViewOnce: () -> Unit,
    onDelete: () -> Unit,
    onSend: () -> Unit,
    onResume: () -> Unit,
    onRetryJoin: () -> Unit,
) {
    // Previous palette source: ServiceLocator.themeController.globalTheme. Unified
    // with the recording card's scoped palette; both cards share one overlay slot.
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val reduced = ChatyMotion.durationMs(context, ChatyMotion.instantMs) == 0
    val fadeMs = if (reduced) 0 else 150
    val enter = remember(clip.path) { androidx.compose.animation.core.Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(clip.path) { enter.animateTo(1f, androidx.compose.animation.core.tween(fadeMs)) }
    val playbackScope = rememberCoroutineScope()
    val player = remember(clip.path, recordingAccountGeneration) { android.media.MediaPlayer() }
    var ready by remember(clip.path) { mutableStateOf(false) }
    var playing by remember(clip.path) { mutableStateOf(false) }
    var positionMs by remember(clip.path) { mutableStateOf(0) }
    var totalMs by remember(clip.path) { mutableStateOf(clip.durationSeconds * 1000) }
    var speed by remember(clip.path) { mutableStateOf(1f) }
    var error by remember(clip.path) { mutableStateOf<String?>(null) }
    var peaks by remember(clip.path) { mutableStateOf<List<Float>?>(null) }
    LaunchedEffect(clip.path) {
        peaks = runCatching { decodeVoiceWaveform(java.io.File(clip.path)) }.getOrNull()
    }
    // Audio focus: take it for playback, give it back on pause, end and dispose.
    val audioManager = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager }
    val focusRequest = remember(player) {
        android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener { change ->
                if (change == android.media.AudioManager.AUDIOFOCUS_LOSS ||
                    change == android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    runCatching { if (player.isPlaying) player.pause() }; playing = false
                }
            }.build()
    }
    val playbackGuard = remember(clip.path, recordingAccountGeneration) {
        com.example.chat.core.audio.CommunicationPlaybackGuard(
            arbiter = ServiceLocator.communicationMediaArbiter,
            pauseNative = {
                withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                    if (ready && player.isPlaying) player.pause()
                    playing = false
                    audioManager.abandonAudioFocusRequest(focusRequest)
                }
            },
            disposeNative = {
                withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                    audioManager.abandonAudioFocusRequest(focusRequest)
                    player.release()
                    playing = false
                }
            },
            isOwnerCurrent = { recordingAccountGeneration != null &&
                ServiceLocator.apiBackend.authSnapshot().generation == recordingAccountGeneration },
            onCallSuspended = {
                withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                    error = "Playback paused for a call. Press Listen after the call."
                }
            },
        )
    }
    DisposableEffect(playbackGuard) {
        onDispose {
            playbackGuard.markClosing()
            playbackScope.launch(kotlinx.coroutines.NonCancellable) {
                runCatching { playbackGuard.dispose() }
                // Retained arbiter reservation fails closed if native teardown remains uncertain.
            }
        }
    }
    LaunchedEffect(playing) {
        while (playing) { positionMs = runCatching { player.currentPosition }.getOrDefault(positionMs); delay(100L) }
    }
    fun togglePlay() {
        playbackScope.launch {
            error = null
            try {
                if (playing) { playbackGuard.pause(); return@launch }
                val allowed = playbackGuard.runNative {
                    if (!ready) {
                        player.setDataSource(clip.path); player.prepare(); ready = true
                        totalMs = player.duration.coerceAtLeast(1)
                        player.setOnCompletionListener {
                            playing = false; positionMs = 0
                            audioManager.abandonAudioFocusRequest(focusRequest)
                        }
                    }
                    check(audioManager.requestAudioFocus(focusRequest) == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
                    if (positionMs >= totalMs - 50) { player.seekTo(0); positionMs = 0 }
                    runCatching { player.playbackParams = player.playbackParams.setSpeed(speed) }
                    player.start(); playing = true
                }
                if (!allowed) error = "Playback cannot start during a call or after the account changed."
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
            } catch (_: Exception) {
                error = "Audio is unavailable. Try Listen again."
            }
        }
    }
    fun cycleSpeed() {
        speed = when (speed) { 1f -> 1.5f; 1.5f -> 2f; else -> 1f }
        if (playing) playbackScope.launch {
            // MediaPlayer playbackParams may start playback; it needs the same atomic guard.
            runCatching {
                if (!playbackGuard.runNative { player.playbackParams = player.playbackParams.setSpeed(speed) }) {
                    error = "Finish the call before changing playback."
                }
            }.onFailure { error = "Playback speed could not change." }
        }
    }
    fun seekTo(fraction: Float) {
        val target = (fraction.coerceIn(0f, 1f) * totalMs).toInt()
        positionMs = target
        if (ready) runCatching { player.seekTo(target) }
    }
    fun fmt(ms: Int) = "%d:%02d".format(ms / 60000, (ms / 1000) % 60)
    val bars = peaks ?: List(VoiceLevelMeter.BAR_COUNT * 2) { 0.35f }
    val progress = if (totalMs > 0) positionMs.toFloat() / totalMs else 0f
    GbCardContainer(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).graphicsLayer { alpha = enter.value },
        margin = PaddingValues(ReferenceLayoutMetrics.recorderCardMarginDp.dp),
        padding = PaddingValues(horizontal = ReferenceLayoutMetrics.recorderCardHorizontalPaddingDp.dp,
            vertical = ReferenceLayoutMetrics.recorderCardVerticalPaddingDp.dp),
    ) {
        // P2: one constraint scope for both rows. Below 300dp the waveform's
        // weight absorbs the preview row and the position text drops; the speed
        // toggle moves into the scrollable controls row so playback speed stays
        // reachable (OpenCode: never remove it entirely, drop only the position
        // text). compactPreview implies compactControls (300 < 340), so the
        // toggle only ever lands in a scrolling row.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compactPreview = maxWidth < 300.dp
            val compactControls = maxWidth < 340.dp
            val speedToggle: @Composable () -> Unit = {
                TextButton(onClick = ::cycleSpeed, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (speed == 1f) "1x" else "${speed}x", color = colors.foreground)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(ReferenceLayoutMetrics.recorderContentGapDp.dp)) {
                // Reference preview row: light play/pause circle, position, scrub
                // waveform (tap or drag to seek), total duration, speed toggle.
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ReferenceLayoutMetrics.recorderControlGapDp.dp)) {
                    IconButton(onClick = ::togglePlay, enabled = !busy,
                        modifier = Modifier.size(44.dp).background(colors.foreground.copy(alpha = if (!busy) 1f else 0.4f), CircleShape)) {
                        androidx.compose.animation.Crossfade(targetState = playing, animationSpec = tween(fadeMs), label = "voice-play-pause") { isPlaying ->
                            ChatyIcon(
                                if (isPlaying) ChatyIconId.pause else ChatyIconId.playArrow,
                                semanticLabel = if (isPlaying) "Pause playback" else "Play recording", color = colors.surface, size = 20.dp,
                            )
                        }
                    }
                    if (!compactPreview) Text(fmt(positionMs), style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = colors.foreground)
                    androidx.compose.foundation.Canvas(
                        Modifier.weight(1f).heightIn(min = 48.dp)
                            .pointerInput(clip.path) {
                                detectTapGestures { o -> seekTo(o.x / size.width) }
                            }
                            .pointerInput(clip.path) {
                                detectHorizontalDragGestures { change, _ -> seekTo(change.position.x / size.width) }
                            },
                    ) {
                        val n = bars.size
                        val step = size.width / n
                        val barW = (step * 0.55f).coerceAtLeast(2f)
                        for (i in 0 until n) {
                            val h = (bars[i].coerceIn(0.08f, 1f)) * size.height
                            val x = i * step + step / 2
                            drawLine(
                                color = if (i.toFloat() / n <= progress) colors.foreground else colors.foreground.copy(alpha = 0.4f),
                                start = androidx.compose.ui.geometry.Offset(x, (size.height - h) / 2),
                                end = androidx.compose.ui.geometry.Offset(x, (size.height + h) / 2),
                                strokeWidth = barW,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                            )
                        }
                        drawCircle(colors.foreground, radius = 6.dp.toPx(), center = androidx.compose.ui.geometry.Offset(progress * size.width, size.height / 2))
                    }
                    Text(fmt(totalMs), style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = colors.foregroundSecondary)
                    if (!compactPreview) speedToggle()
                }
                error?.let { Text(it, color = colors.foreground, fontSize = 12.sp) }
                if (clip.joinFailed) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ReferenceLayoutMetrics.recorderControlGapDp.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Parts could not be joined. Send is off until they are.", color = colors.foreground, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = onRetryJoin, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                        ChatyIcon(ChatyIconId.refresh, semanticLabel = "Retry joining recording parts", color = colors.foreground)
                        Text("Retry", color = colors.foreground)
                    }
                }
                // Controls row: trash (dark red) / Resume pill / view-once "1" /
                // send (light circle); compact widths scroll and then also host
                // the moved speed toggle after send.
                val controls: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
                    IconButton(onClick = {
                        playbackScope.launch {
                            runCatching { playbackGuard.dispose(); onDelete() }
                                .onFailure { error = "Playback cleanup failed. Tap Delete to retry." }
                        }
                    }, enabled = !busy, modifier = Modifier.size(52.dp).background(colors.error.copy(alpha = 0.16f), CircleShape)) {
                        ChatyIcon(ChatyIconId.delete, semanticLabel = "Delete recording", color = colors.error)
                    }
                    if (!compactControls) Spacer(Modifier.weight(1f))
                    ChatyActionButton(onClick = {
                        playbackScope.launch {
                            runCatching { playbackGuard.pause(); onResume() }
                                .onFailure { error = "Playback could not stop. Try again before recording." }
                        }
                    }, enabled = !busy,
                        modifier = Modifier.widthIn(min = ReferenceLayoutMetrics.recorderPauseMinWidthDp.dp),
                        containerColor = colors.surfaceElevated, contentColor = colors.foreground,
                        icon = Icons.Default.Mic, label = "Resume")
                    IconButton(onClick = onToggleViewOnce, enabled = !busy,
                        modifier = Modifier.size(52.dp).background(if (viewOnce) colors.accent else colors.surfaceElevated, CircleShape)) {
//                         ChatyIcon(ChatyIconId.numberOne,
//                             semanticLabel = if (viewOnce) "View once on. The voice message plays once." else "Send as view once voice message",
//                             color = if (viewOnce) colors.onAccent else colors.foreground)
                        com.example.chat.ui.icons.ChatyPurposeIcon(ChatyIconId.numberOne, Icons.Rounded.LooksOne,
                            contentDescription = if (viewOnce) "View once on. The voice message plays once." else "Send as view once voice message",
                            tint = if (viewOnce) colors.onAccent else colors.foreground)
                    }
                    IconButton(onClick = {
                        playbackScope.launch {
                            runCatching { playbackGuard.pause(); onSend() }
                                .onFailure { error = "Playback could not stop. Try Send again." }
                        }
                    }, enabled = !busy && !clip.joinFailed,
                        modifier = Modifier.size(56.dp).background(colors.foreground.copy(alpha = if (!busy && !clip.joinFailed) 1f else 0.4f), CircleShape)) {
                        ChatyIcon(ChatyIconId.send, semanticLabel = "Send recording", color = colors.surface)
                    }
                    if (compactPreview) speedToggle()
                }
                if (compactControls) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(ReferenceLayoutMetrics.recorderControlGapDp.dp),
                        verticalAlignment = Alignment.CenterVertically, content = controls)
                } else {
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ReferenceLayoutMetrics.recorderControlGapDp.dp),
                        verticalAlignment = Alignment.CenterVertically, content = controls)
                }
            }
        }
    }
}
