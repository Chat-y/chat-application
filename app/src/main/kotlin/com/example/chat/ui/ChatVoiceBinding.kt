package com.example.chat.ui

import android.Manifest
import androidx.compose.foundation.layout.PaddingValues
import com.example.chat.ui.kit.ChatyActionButton
import com.example.chat.ui.kit.GbCardContainer
import com.example.chat.ui.kit.ChatyMotionTokens
import com.example.chat.ui.navigation.ReferenceLayoutMetrics
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.rounded.LooksOne
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.core.content.ContextCompat
import com.example.chat.ServiceLocator
import com.example.chat.data.services.AndroidPcmVoiceRecorderPort
import com.example.chat.data.services.AndroidVoiceStagingPort
import com.example.chat.domain.models.MessageType
import com.example.chat.domain.models.MessageAttachment
import com.example.chat.data.services.ChatMediaService
import com.example.chat.data.network.TransferDirection
import com.example.chat.data.network.TransferKind
import com.example.chat.data.network.TransferStatus
import com.example.chat.ui.chats.detail.VoiceLevelMeter
import com.example.chat.ui.chats.detail.VoiceLevelMeterBar
import com.example.chat.ui.chats.detail.VoiceLevelSampler
import com.example.chat.ui.chats.detail.VoiceNoteController
import com.example.chat.ui.chats.detail.VoiceRecordConfig
import com.example.chat.ui.chats.detail.VoiceSessionController
import com.example.chat.ui.chats.detail.VoiceUiState
import com.example.chat.ui.kit.ChatyMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Binding of VoiceNoteController/VoiceSessionController (Dart voice_note_service.dart and the
 *  screen's _beginVoice/_finishVoice/_cancelVoice) for ChatDetailHost. Bound: MediaRecorder capture,
 *  RECORD_AUDIO runtime prompt, record/lock/cancel/send, streamed upload via the custom
 *  ApiBackendService REST /chat/upload endpoint and send with the Dart JSON envelope. Capture runs the record_android 2.1.2
 *  default engine port (AndroidPcmVoiceRecorderPort: PCM AudioRecord + AGC/AEC/NS on the record
 *  session + MediaCodec AAC-LC + MediaMuxer .m4a), and the overlay renders the Dart
 *  ChatyVoiceLevelMeter port fed by VoiceLevelSampler at the Dart 90ms poll.
 *  Deviations: the recording status strip is a host overlay (ChatDetailScreen has no recording
 *  composer parameters); recording indicator goes through richRealtime
 *  (Supabase RPC, tolerated on failure like Dart); the first press that triggers the permission
 *  prompt does not start recording, the user presses again after granting. */
class ChatVoiceBindings(
    val onVoiceTap: () -> Unit,
    val onVoiceHoldStart: () -> Unit,
    val onVoiceMove: (Offset) -> Unit,
    val onVoiceHoldEnd: () -> Unit,
    /** Invalidates any in-progress or staged recording (e.g. when encryption
     *  is turned on mid-recording): the session fails its next validation and
     *  is discarded, never uploaded. */
    val cancelActiveRecording: () -> Unit = {},
    val overlay: @Composable () -> Unit,
)

@Composable
fun rememberChatVoice(conversationId: String, afterSend: () -> Unit, clearTyping: () -> Unit, secureChannel: () -> Boolean = { false }): ChatVoiceBindings {
    val secureChannelNow by rememberUpdatedState(secureChannel)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ui by remember(conversationId) { mutableStateOf(VoiceUiState()) }
    // GB/stock parity: view-once toggle for voice notes, chosen in the locked
    // recording UI before send; lives outside VoiceUiState because the session
    // overwrites that state on every tick.
    var voiceViewOnce by remember(conversationId) { mutableStateOf(false) }
    // Scope captured when a recording starts; upload and send are refused if the account changed.
    val recordScope = remember(conversationId) { java.util.concurrent.atomic.AtomicReference<com.example.chat.data.api.ApiAccountScope?>(null) }
    val note = remember(conversationId) {
        VoiceNoteController(
            conversationId = conversationId,
            communicationArbiter = ServiceLocator.communicationMediaArbiter,
            joinSegments = { parts, out -> withContext(Dispatchers.IO) { com.example.chat.ui.chats.detail.AndroidAacJoin.join(parts, out) } },
            validateSession = {
                // Turning encryption on mid-recording invalidates the session:
                // the note is discarded, never uploaded plaintext.
                check(!secureChannelNow() || com.example.chat.ReleaseFeatures.EXPERIMENTAL_MLS_CHAT_ROUTING) { "Secure voice routing is unavailable." }
                val started = recordScope.get()
                if (started != null) check(ServiceLocator.apiBackend.authSnapshot().generation == started.generation) {
                    "Account changed while recording."
                }
            },
            recorder = AndroidPcmVoiceRecorderPort(context.applicationContext),
            staging = AndroidVoiceStagingPort(context.applicationContext),
            uploadAudio = { path, name, seconds ->
                // Guard BEFORE any byte leaves the device: a secure chat never
                // uploads plaintext audio to REST /chat/upload. The AUTHORITATIVE
                // check is inside tracker.begin: it re-reads the durable flag
                // (persisted-first repo state, not this possibly-stale UI lambda)
                // after its dispatch-lock suspension, so a toggle that completed
                // just before now still refuses the upload, and a toggle that
                // comes after is blocked while the upload is registered.
                // The expected identity is the scope captured when RECORDING
                // started - not a fresh snapshot: an account switch between
                // requireActive() and here must not upload the old recording
                // under the new account. Checked before AND after begin's
                // suspension (generation is the scope-identity discriminator,
                // same as validateSession and the transition listener).
                val scopeNow = recordScope.get()
                    ?: throw IllegalStateException("No recording session; the voice note was not uploaded.")
                check(ServiceLocator.apiBackend.authSnapshot().generation == scopeNow.generation) {
                    "Account changed after recording; the voice note was discarded."
                }
                if (secureChannelNow()) {
                    check(com.example.chat.ReleaseFeatures.EXPERIMENTAL_MLS_CHAT_ROUTING &&
                        ServiceLocator.conversationsRepository.isSecureChannel(conversationId)) { "Secure routing is unresolved." }
                    check(!voiceViewOnce) { "Encrypted view-once voice notes need authenticated consume support." }
                    ServiceLocator.secureAttachmentFiles.upload(scopeNow, conversationId, java.io.File(path), "audio", name,
                        "audio/mp4", seconds)
                } else {
                val endUpload = com.example.chat.data.messaging.AttachmentUploadTracker.begin(
                    com.example.chat.data.messaging.ConversationDispatchLocks.accountKey(
                        scopeNow.userId, scopeNow.tenantId, scopeNow.baseUrl),
                    conversationId,
                )
                // Everything after begin() stays inside the counter cleanup:
                // a throw from file setup, the transfer ledger or the scope
                // revalidation still unregisters the upload.
                try {
                    check(ServiceLocator.apiBackend.authSnapshot().generation == scopeNow.generation) {
                        "Account changed while the upload was starting; nothing was uploaded."
                    }
                    val file = java.io.File(path)
                    val transferId = ServiceLocator.transferMonitor.begin(
                        TransferDirection.upload,
                        TransferKind.audio,
                        file.length(),
                        name,
                    )
                    try {
                        val uploaded = ServiceLocator.apiBackend.uploadMediaForScope(scopeNow, file) { sent, total ->
                            ServiceLocator.transferMonitor.update(transferId, sent, total)
                        }
                        val mediaUrl = uploaded["url"]?.toString()?.takeIf { it.isNotBlank() }
                            ?: uploaded["path"]?.toString()?.takeIf { it.isNotBlank() }
                            ?: throw IllegalStateException("Custom media endpoint returned no media URL.")
                        ServiceLocator.transferMonitor.update(transferId, file.length(), file.length())
                        ServiceLocator.transferMonitor.finish(transferId)
                        MessageAttachment(
                            id = java.util.UUID.randomUUID().toString(),
                            type = "audio",
                            name = name,
                            size = ChatMediaService.formatBytes(file.length()),
                            url = mediaUrl,
                            durationSeconds = seconds,
                        )
                    } catch (error: Throwable) {
                        ServiceLocator.transferMonitor.finish(transferId, TransferStatus.failed)
                        throw error
                    }
                } finally {
                    endUpload()
                }
                }
            },
            sendAudioMessage = { attachment ->
                // A secure chat never sends unencrypted voice notes; encrypted
                // media is Phase 2 and not wired yet. (The recording itself is
                // blocked earlier, at the hold-to-record entry point.)

                // Bind the send to the recording's account: sendMessage's own
                // default would capture a FRESH scope after requireActive(),
                // which is exactly the switch window this guard exists for.
                val started = recordScope.get()
                    ?: throw IllegalStateException("No recording session; the voice note was not sent.")
                check(ServiceLocator.apiBackend.authSnapshot().generation == started.generation) {
                    "Account changed after recording; the voice note was discarded."
                }
                if (secureChannelNow()) {
                    check(attachment.encryptedFile != null && !voiceViewOnce) { "Verified encrypted voice descriptor is required." }
                    ServiceLocator.conversationsRepository.sendMessage(conversationId, "", MessageType.audio, attachment,
                        expectedScope = started)
                } else {
                ServiceLocator.apiBackend.sendMessage(
                    conversationId,
                    "",
                    MessageType.audio,
                    attachment,
                    extraMetadata = if (voiceViewOnce) mapOf("view_once" to true) else null,
                    expectedScope = started,
                )
                }
            },
            recordConfig = {
                val uplink = ServiceLocator.networkMonitor.state.value.upstreamKbps
                val voiceBitrate = when {
                    uplink in 1..128 -> 32_000
                    uplink in 129..400 -> 48_000
                    uplink in 401..900 -> 64_000
                    else -> 96_000
                }
                VoiceRecordConfig(bitRate = voiceBitrate)
            },
        )
    }
    // Dart _ComposerState._startLevelPolling: reset on record start, then poll every
    // VoiceLevelMeter.POLL_INTERVAL_MS; bars are real microphone samples only.
    val sampler = remember(conversationId) { VoiceLevelSampler { note.currentLevelDbfs() } }
    var levels by remember(conversationId) { mutableStateOf(List(VoiceLevelMeter.BAR_COUNT) { 0.0 }) }
    LaunchedEffect(ui.recording) {
        if (ui.recording) {
            sampler.reset()
            levels = sampler.levels()
            while (isActive) {
                sampler.pollOnce()
                levels = sampler.levels()
                delay(VoiceLevelMeter.POLL_INTERVAL_MS)
            }
        }
    }
    val session = remember(conversationId) {
        VoiceSessionController(
            voice = note,
            scope = scope,
            setRecordingIndicator = { v -> ServiceLocator.apiBackend.sendRecording(conversationId, v) },
            clearTyping = clearTyping,
            onSent = {
                voiceViewOnce = false
                afterSend()
            },
            showError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
            onState = { ui = it },
        )
    }
    DisposableEffect(session) {
        onDispose {
            session.dispose() // Synchronously invalidates start/resume/send, independent of scope cancellation.
            ServiceLocator.appScope.launch(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                // The leaving composition's scope cannot own microphone teardown.
                runCatching { session.disposeAndAwait() }
                    .onFailure {
                        withContext(Dispatchers.Main.immediate) {
                            Toast.makeText(context.applicationContext,
                                "Recording cleanup is incomplete. Audio remains blocked until cleanup can be retried.",
                                Toast.LENGTH_LONG).show()
                        }
                    }
                // A retained failed owner can retry full teardown via the arbiter's call recovery.
            }
        }
    }
    // Synchronous account-transition guard: the voice controller stops accepting upload/send.
    DisposableEffect(note) {
        val l: (com.example.chat.data.api.ApiAccountScope) -> Unit = { next ->
            val started = recordScope.get()
            if (started != null && next.generation != started.generation) note.invalidate()
        }
        ServiceLocator.apiBackend.addAuthScopeListener(l)
        onDispose { ServiceLocator.apiBackend.removeAuthScopeListener(l) }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(context, "Microphone permission is required.", Toast.LENGTH_SHORT).show()
    }
    fun begin(locked: Boolean) {
        if (secureChannelNow() && !com.example.chat.ReleaseFeatures.EXPERIMENTAL_MLS_CHAT_ROUTING) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            note.rearm()
            recordScope.set(ServiceLocator.apiBackend.authSnapshot())
            session.beginVoice(locked)
        } else {
            permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    var origin by remember { mutableStateOf(Offset.Zero) }
    return ChatVoiceBindings(
        onVoiceTap = { if (ui.recording && ui.locked) session.finishVoice() else begin(true) },
        onVoiceHoldStart = { origin = Offset.Zero; begin(false) },
        onVoiceMove = { o -> session.onDrag(o.x, o.y) },
        onVoiceHoldEnd = { session.onRelease() },
        cancelActiveRecording = { note.invalidate() },
        overlay = {
            if (ui.review != null) {
                Box(
                    Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    VoiceReviewCardPremium(
                        clip = ui.review!!,
                        recordingAccountGeneration = recordScope.get()?.generation,
                        busy = ui.busy,
                        viewOnce = voiceViewOnce,
                        onToggleViewOnce = { voiceViewOnce = !voiceViewOnce },
                        onDelete = { voiceViewOnce = false; session.deleteReview() },
                        onSend = session::sendReview,
                        onResume = session::resumeRecording,
                        onRetryJoin = session::retryJoin,
                    )
                }
            } else if (ui.recording) {
                // Previous card UI restored, docked over the composer and lifted
                // clear of the system gesture bar (was a floating pill at a fixed
                // 76.dp offset that overlapped the composer and message list).
                Box(
                    Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    VoiceRecordingCardPremium(
                        state = ui,
                        levels = levels,
                        onDelete = {
                            voiceViewOnce = false
                            session.cancelVoice()
                        },
                        // Pause pill opens the review step: stop, keep the file, Listen / Send.
                        // Call interruption retains the live paused draft, including <1s.
                        onPause = { if (ui.interruptedForCall) session.togglePause() else session.beginReview() },
                        // Previous binding: onSend = session::finishVoice
                        // Reject a queued duplicate before interpreting busy as acceptance of THIS tap.
                        // Previous callback: onSend = { session.finishVoice(); session.state.busy },
                        onSend = {
                            val current = session.state
                            if (!current.recording || current.busy || current.interruptedForCall) false
                            else { session.finishVoice(); session.state.busy }
                        },
                        viewOnce = voiceViewOnce,
                        onToggleViewOnce = { voiceViewOnce = !voiceViewOnce },
                    )
                }
            }
        },
    )
}


/** Restored recording card (the pre-pill UI): real sampled levels and elapsed
 *  time, delete/pause/send controls when locked. No decorative waveform. */
// @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
// @Composable
// private fun VoiceRecordingCard(
//     state: VoiceUiState,
//     levels: List<Double>,
//     onDelete: () -> Unit,
//     onPause: () -> Unit,
//     // Return whether the existing session accepted the tap, avoiding a stuck label on a refused send.
//     onSend: () -> Boolean,
//     viewOnce: Boolean = false,
//     onToggleViewOnce: () -> Unit = {},
// ) {
//     // Follow the visible chat's scoped palette rather than the global fallback.
//     val theme = com.example.chat.ui.theme.LocalThemeConfig.current
//     val accent = Color(theme.accentColor)
//     val foreground = Color(theme.primaryTextColor)
//     val secondary = Color(theme.secondaryTextColor)
//     val onAccent = com.example.chat.ui.kit.themeGlyphColor(accent, theme)
//     val reduced = com.example.chat.ui.kit.rememberChatyReduceMotion()
//     // Presentation only: capture stop/upload/send ordering remains owned by the session.
//     var sendSnapshot by remember { mutableStateOf<Pair<Int, List<Double>>?>(null) }
//     val sendRequested = sendSnapshot != null
//     LaunchedEffect(state.busy) { if (!state.busy) sendSnapshot = null }
//     val controlsEnabled = !state.busy && !sendRequested
//     val status = when {
//         sendRequested -> "Sending recording…"
//         state.busy -> "Processing recording…"
//         state.interruptedForCall -> "Paused for call"
//         state.paused -> "Paused"
//         else -> "Recording"
//     }
//     // Freeze only the display on Send; never keep suggesting capture during finalization/upload.
//     val shownSeconds = sendSnapshot?.first ?: state.seconds
//     val shownLevels = sendSnapshot?.second ?: levels
//     val elapsed = "%d:%02d".format(shownSeconds / 60, shownSeconds % 60)
//     // Shared card owns shape, border, surface and LocalAppColors.shadow.
//     GbCardContainer(
//         modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
//         margin = PaddingValues(ReferenceLayoutMetrics.recorderCardMarginDp.dp),
//         padding = PaddingValues(horizontal = ReferenceLayoutMetrics.recorderCardHorizontalPaddingDp.dp,
//             vertical = ReferenceLayoutMetrics.recorderCardVerticalPaddingDp.dp),
//     ) {
//       Column(verticalArrangement = Arrangement.spacedBy(ReferenceLayoutMetrics.recorderContentGapDp.dp)) {
//         if (state.locked) {
//             Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
//                 horizontalArrangement = Arrangement.spacedBy(ReferenceLayoutMetrics.recorderControlGapDp.dp)) {
//                 Text("●", color = if (sendRequested || state.paused) secondary else Color(theme.dangerColor), style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
//                 Text(status, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = foreground, modifier = Modifier.weight(1f))
//                 Text(elapsed, style = androidx.compose.material3.MaterialTheme.typography.labelLarge, color = secondary)
//             }
//             // Keep the existing real microphone samples. No decorative/random waveform.
//             VoiceLevelMeterBar(shownLevels, accent, Modifier.fillMaxWidth())
//             Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
//                 horizontalArrangement = Arrangement.spacedBy(ReferenceLayoutMetrics.recorderControlGapDp.dp), verticalAlignment = Alignment.CenterVertically) {
//                 ChatyActionButton(onClick = onDelete, enabled = controlsEnabled,
//                     containerColor = Color(theme.cardColor), contentColor = foreground) {
//                     com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.delete,
//                         // Previous explicit tint: color = foreground. Inherit enabled/disabled button color.
//                         semanticLabel = "Delete recording")
//                 }
//                 ChatyActionButton(onClick = onPause, enabled = controlsEnabled,
//                     modifier = Modifier.widthIn(min = ReferenceLayoutMetrics.recorderPauseMinWidthDp.dp),
//                     containerColor = Color(theme.cardColor), contentColor = foreground,
//                     icon = if (state.paused) Icons.Default.PlayArrow else Icons.Default.Pause,
//                     label = if (state.paused) "Resume" else "Pause")
//                 ChatyActionButton(onClick = onToggleViewOnce, enabled = controlsEnabled,
//                     containerColor = if (viewOnce) accent else Color(theme.cardColor),
//                     contentColor = if (viewOnce) onAccent else foreground) {
//                     Icon(Icons.Rounded.LooksOne,
//                         // Previous tint = if (viewOnce) onAccent else foreground; inherit disabledForeground too.
//                         if (viewOnce) "View once on. The voice message plays once." else "Send as view once voice message")
//                 }
//                 ChatyActionButton(onClick = {
//                     // finishVoice reserves busy synchronously, before its suspended finalize/upload work.
//                     if (sendSnapshot == null) {
//                         val snapshot = state.seconds to levels.toList()
//                         if (onSend()) sendSnapshot = snapshot
//                     }
//                 }, enabled = controlsEnabled && !state.interruptedForCall,
//                     containerColor = accent, contentColor = onAccent) {
//                     com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.send,
//                         // Previous explicit tint: color = onAccent. Inherit disabledForeground during Send.
//                         semanticLabel = if (sendRequested) "Sending recording" else "Send recording")
//                 }
//             }
//         } else {
//             // A single measured row cannot FlowRow-wrap the held mic over the composer.
//             Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
//                 horizontalArrangement = Arrangement.spacedBy(ReferenceLayoutMetrics.recorderContentGapDp.dp)) {
//                 Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ReferenceLayoutMetrics.recorderControlGapDp.dp)) {
//                     Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ReferenceLayoutMetrics.recorderControlGapDp.dp)) {
//                         Text("●", color = Color(theme.dangerColor), style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
//                         Text(elapsed, style = androidx.compose.material3.MaterialTheme.typography.labelLarge, color = foreground)
//                     }
//                     Text(if (state.busy) status else "‹ Slide to cancel", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = secondary)
//                     VoiceLevelMeterBar(shownLevels, accent, Modifier.fillMaxWidth())
//                 }
//                 Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ReferenceLayoutMetrics.recorderControlGapDp.dp)) {
//                     com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.lock,
//                         semanticLabel = "Slide up to lock recording", color = secondary)
//                     val scale = if (reduced) 1f else ChatyMotionTokens.RECORDER_MIC_BASE_SCALE + (levels.lastOrNull() ?: 0.0).toFloat().coerceIn(0f, 1f) * ChatyMotionTokens.RECORDER_MIC_LEVEL_SCALE
//                     Box(Modifier.size(ReferenceLayoutMetrics.recorderMicrophoneSizeDp.dp).graphicsLayer { scaleX = scale; scaleY = scale }
//                         .background(accent, CircleShape), contentAlignment = Alignment.Center) {
//                         com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.microphone,
//                             semanticLabel = "Recording audio", color = onAccent)
//                     }
//                 }
//             }
//         }
//     }
//   }
// }

// Previous recording-card body retained for review (inactive):
//     val theme = ServiceLocator.themeController.globalTheme
//     val accent = Color(theme.accentColor)
//     val foreground = Color(theme.primaryTextColor)
//     val context = LocalContext.current
//     val reduced = ChatyMotion.durationMs(context, ChatyMotion.instantMs) == 0
//     Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).background(Color(theme.surfaceColor), RoundedCornerShape(28.dp)).padding(12.dp),
//         verticalArrangement = Arrangement.spacedBy(8.dp)) {
//         if (state.locked) {
//             Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
//                 Text((if (state.interruptedForCall) "Paused for call · " else if (state.paused) "Paused · " else "Recording · ") + "%d:%02d".format(state.seconds / 60, state.seconds % 60), color = foreground)
//                 VoiceLevelMeterBar(levels, accent, Modifier.fillMaxWidth())
//             }
//             FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
//                 IconButton(onClick = onDelete, enabled = !state.busy, modifier = Modifier.size(52.dp).background(Color(theme.cardColor), CircleShape)) {
//                     com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.delete, semanticLabel = "Delete recording", color = foreground)
//                 }
//                 Button(onClick = onPause, enabled = !state.busy, modifier = Modifier.widthIn(min = 112.dp).heightIn(min = 52.dp),
//                     colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color(theme.onAccentColor)),
//                     shape = RoundedCornerShape(26.dp)) {
//                     Icon(if (state.paused) Icons.Default.PlayArrow else Icons.Default.Pause, if (state.paused) "Resume recording" else "Pause recording")
//                     Text(if (state.paused) "Resume" else "Pause")
//                 }
//                 // GB/stock view-once toggle for the voice note about to be sent.
//                 IconButton(
//                     onClick = onToggleViewOnce,
//                     enabled = !state.busy,
//                     modifier = Modifier.size(52.dp).background(
//                         if (viewOnce) accent else Color(theme.cardColor),
//                         CircleShape,
//                     ),
//                 ) {
//                     Icon(
//                         Icons.Rounded.LooksOne,
//                         if (viewOnce) "View once on. The voice message plays once." else "Send as view once voice message",
//                         tint = if (viewOnce) Color(theme.onAccentColor) else foreground,
//                     )
//                 }
//                 IconButton(onClick = onSend, enabled = !state.busy && !state.interruptedForCall, modifier = Modifier.size(52.dp).background(accent, CircleShape)) {
//                     com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.send, semanticLabel = "Send recording", color = Color(theme.onAccentColor))
//                 }
//             }
//         } else {
//             FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
//                 Text("●", color = Color(theme.dangerColor))
//                 Text("%d:%02d".format(state.seconds / 60, state.seconds % 60), color = foreground)
//                 Text("‹ Slide to cancel", color = foreground, modifier = Modifier.widthIn(min = 112.dp))
//                 Column(horizontalAlignment = Alignment.CenterHorizontally) {
//                     com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.lock, semanticLabel = "Slide up to lock recording", color = foreground)
//                     // The scale follows real microphone samples; reduced motion removes scaling.
//                     val scale = if (reduced) 1f else 1.08f + (levels.lastOrNull() ?: 0.0).toFloat() * .14f
//                     Box(Modifier.size(58.dp).graphicsLayer { scaleX = scale; scaleY = scale }.background(accent, CircleShape), contentAlignment = Alignment.Center) {
//                         com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.microphone, semanticLabel = "Recording audio", color = Color(theme.onAccentColor))
//                     }
//                 }
//             }
//         }
//     }
// }


/** After-recording review: same sheet and Delete / pill / Send row as the recording card.
 *  Chaty icons and theme colors only. Reduced motion removes the scrub and press animation. */
// @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
// @Composable
// private fun VoiceReviewCard(
//     clip: VoiceNoteController.Clip,
//     recordingAccountGeneration: Long?,
//     busy: Boolean,
//     viewOnce: Boolean,
//     onToggleViewOnce: () -> Unit,
//     onDelete: () -> Unit,
//     onSend: () -> Unit,
//     onResume: () -> Unit,
//     onRetryJoin: () -> Unit,
// ) {
//     val theme = ServiceLocator.themeController.globalTheme
//     val accent = Color(theme.accentColor)
//     val foreground = Color(theme.primaryTextColor)
//     val onAccent = Color(theme.onAccentColor)
//     val context = LocalContext.current
//     val reduced = ChatyMotion.durationMs(context, ChatyMotion.instantMs) == 0
//     val fadeMs = if (reduced) 0 else 150
//     val enter = remember(clip.path) { androidx.compose.animation.core.Animatable(if (reduced) 1f else 0f) }
//     LaunchedEffect(clip.path) { enter.animateTo(1f, androidx.compose.animation.core.tween(fadeMs)) }
//     val playbackScope = rememberCoroutineScope()
//     val player = remember(clip.path, recordingAccountGeneration) { android.media.MediaPlayer() }
//     var ready by remember(clip.path) { mutableStateOf(false) }
//     var playing by remember(clip.path) { mutableStateOf(false) }
//     var positionMs by remember(clip.path) { mutableStateOf(0) }
//     var totalMs by remember(clip.path) { mutableStateOf(clip.durationSeconds * 1000) }
//     var speed by remember(clip.path) { mutableStateOf(1f) }
//     var error by remember(clip.path) { mutableStateOf<String?>(null) }
//     var peaks by remember(clip.path) { mutableStateOf<List<Float>?>(null) }
//     LaunchedEffect(clip.path) {
//         peaks = runCatching { com.example.chat.ui.chats.detail.decodeVoiceWaveform(java.io.File(clip.path)) }.getOrNull()
//     }
//     // Audio focus: take it for playback, give it back on pause, end and dispose.
//     val audioManager = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager }
//     val focusRequest = remember(player) {
//         android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
//             .setAudioAttributes(android.media.AudioAttributes.Builder()
//                 .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
//                 .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
//             .setOnAudioFocusChangeListener { change ->
//                 if (change == android.media.AudioManager.AUDIOFOCUS_LOSS ||
//                     change == android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
//                     runCatching { if (player.isPlaying) player.pause() }; playing = false
//                 }
//             }.build()
//     }
//     val playbackGuard = remember(clip.path, recordingAccountGeneration) {
//         com.example.chat.core.audio.CommunicationPlaybackGuard(
//             arbiter = ServiceLocator.communicationMediaArbiter,
//             pauseNative = {
//                 withContext(Dispatchers.Main.immediate) {
//                     if (ready && player.isPlaying) player.pause()
//                     playing = false
//                     audioManager.abandonAudioFocusRequest(focusRequest)
//                 }
//             },
//             disposeNative = {
//                 withContext(Dispatchers.Main.immediate) {
//                     audioManager.abandonAudioFocusRequest(focusRequest)
//                     player.release()
//                     playing = false
//                 }
//             },
//             isOwnerCurrent = { recordingAccountGeneration != null &&
//                 ServiceLocator.apiBackend.authSnapshot().generation == recordingAccountGeneration },
//             onCallSuspended = {
//                 withContext(Dispatchers.Main.immediate) {
//                     error = "Playback paused for a call. Press Listen after the call."
//                 }
//             },
//         )
//     }
//     DisposableEffect(playbackGuard) {
//         onDispose {
//             playbackGuard.markClosing()
//             playbackScope.launch(kotlinx.coroutines.NonCancellable) {
//                 runCatching { playbackGuard.dispose() }
//                 // Retained arbiter reservation fails closed if native teardown remains uncertain.
//             }
//         }
//     }
//     LaunchedEffect(playing) {
//         while (playing) { positionMs = runCatching { player.currentPosition }.getOrDefault(positionMs); delay(100L) }
//     }
//     fun togglePlay() {
//         playbackScope.launch {
//             error = null
//             try {
//                 if (playing) { playbackGuard.pause(); return@launch }
//                 val allowed = playbackGuard.runNative {
//                     if (!ready) {
//                         player.setDataSource(clip.path); player.prepare(); ready = true
//                         totalMs = player.duration.coerceAtLeast(1)
//                         player.setOnCompletionListener {
//                             playing = false; positionMs = 0
//                             audioManager.abandonAudioFocusRequest(focusRequest)
//                         }
//                     }
//                     check(audioManager.requestAudioFocus(focusRequest) == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
//                     if (positionMs >= totalMs - 50) { player.seekTo(0); positionMs = 0 }
//                     runCatching { player.playbackParams = player.playbackParams.setSpeed(speed) }
//                     player.start(); playing = true
//                 }
//                 if (!allowed) error = "Playback cannot start during a call or after the account changed."
//             } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
//             } catch (_: Exception) {
//                 error = "Audio is unavailable. Try Listen again."
//             }
//         }
//     }
//     fun cycleSpeed() {
//         speed = when (speed) { 1f -> 1.5f; 1.5f -> 2f; else -> 1f }
//         if (playing) playbackScope.launch {
//             // MediaPlayer playbackParams may start playback; it needs the same atomic guard.
//             runCatching {
//                 if (!playbackGuard.runNative { player.playbackParams = player.playbackParams.setSpeed(speed) }) {
//                     error = "Finish the call before changing playback."
//                 }
//             }.onFailure { error = "Playback speed could not change." }
//         }
//     }
//     fun seekTo(fraction: Float) {
//         val target = (fraction.coerceIn(0f, 1f) * totalMs).toInt()
//         positionMs = target
//         if (ready) runCatching { player.seekTo(target) }
//     }
//     fun fmt(ms: Int) = "%d:%02d".format(ms / 60000, (ms / 1000) % 60)
//     val bars = peaks ?: List(VoiceLevelMeter.BAR_COUNT * 2) { 0.35f }
//     val progress = if (totalMs > 0) positionMs.toFloat() / totalMs else 0f
//     Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).graphicsLayer { alpha = enter.value }.background(Color(theme.surfaceColor), RoundedCornerShape(28.dp)).padding(12.dp),
//         verticalArrangement = Arrangement.spacedBy(8.dp)) {
//         FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
//             Text(fmt(positionMs), color = foreground)
//             // Scrub waveform: tap or drag to seek. Played part uses the foreground color.
//             androidx.compose.foundation.Canvas(
//                 Modifier.fillMaxWidth().heightIn(min = 48.dp)
//                     .pointerInput(clip.path) {
//                         detectTapGestures { o -> seekTo(o.x / size.width) }
//                     }
//                     .pointerInput(clip.path) {
//                         detectHorizontalDragGestures { change, _ -> seekTo(change.position.x / size.width) }
//                     },
//             ) {
//                 val n = bars.size
//                 val step = size.width / n
//                 val barW = (step * 0.55f).coerceAtLeast(2f)
//                 for (i in 0 until n) {
//                     val h = (bars[i].coerceIn(0.08f, 1f)) * size.height
//                     val x = i * step + step / 2
//                     drawLine(
//                         color = if (i.toFloat() / n <= progress) foreground else foreground.copy(alpha = 0.4f),
//                         start = androidx.compose.ui.geometry.Offset(x, (size.height - h) / 2),
//                         end = androidx.compose.ui.geometry.Offset(x, (size.height + h) / 2),
//                         strokeWidth = barW,
//                         cap = androidx.compose.ui.graphics.StrokeCap.Round,
//                     )
//                 }
//                 drawCircle(foreground, radius = 6.dp.toPx(), center = androidx.compose.ui.geometry.Offset(progress * size.width, size.height / 2))
//             }
//             Text(fmt(totalMs), color = foreground.copy(alpha = 0.6f))
//             TextButton(onClick = ::cycleSpeed, modifier = Modifier.heightIn(min = 48.dp)) {
//                 Text(if (speed == 1f) "1x" else "${speed}x", color = foreground)
//             }
//         }
//         error?.let { Text(it, color = foreground, fontSize = 12.sp) }
//         if (clip.joinFailed) FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
//             Text("Parts could not be joined. Send is off until they are.", color = foreground, fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
//             TextButton(onClick = onRetryJoin, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
//                 com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.refresh, semanticLabel = "Retry joining recording parts", color = foreground)
//                 Text("Retry", color = foreground)
//             }
//         }
//         FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
//             IconButton(onClick = {
//                 playbackScope.launch {
//                     runCatching { playbackGuard.dispose(); onDelete() }
//                         .onFailure { error = "Playback cleanup failed. Tap Delete to retry." }
//                 }
//             }, enabled = !busy, modifier = Modifier.size(52.dp).background(Color(theme.cardColor), CircleShape)) {
//                 com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.delete, semanticLabel = "Delete recording", color = foreground)
//             }
//                 Button(onClick = ::togglePlay, enabled = !busy, modifier = Modifier.widthIn(min = 112.dp).heightIn(min = 52.dp),
//                     colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = onAccent),
//                     shape = RoundedCornerShape(26.dp)) {
//                     androidx.compose.animation.Crossfade(targetState = playing, animationSpec = androidx.compose.animation.core.tween(fadeMs), label = "voice-play-pause") { isPlaying ->
//                         com.example.chat.ui.icons.ChatyIcon(
//                             if (isPlaying) com.example.chat.ui.icons.ChatyIconId.pause else com.example.chat.ui.icons.ChatyIconId.playArrow,
//                             semanticLabel = if (isPlaying) "Pause playback" else "Play recording", color = onAccent,
//                         )
//                     }
//                     Text(if (playing) "Pause" else "Listen")
//                 }
//                 Button(onClick = {
//                     playbackScope.launch {
//                         runCatching { playbackGuard.pause(); onResume() }
//                             .onFailure { error = "Playback could not stop. Try again before recording." }
//                     }
//                 }, enabled = !busy, modifier = Modifier.widthIn(min = 112.dp).heightIn(min = 52.dp),
//                     colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = onAccent),
//                     shape = RoundedCornerShape(26.dp)) {
//                     com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.microphone, semanticLabel = "Resume recording", color = onAccent)
//                     Text("Resume")
//                 }
//             IconButton(
//                 onClick = onToggleViewOnce, enabled = !busy,
//                 modifier = Modifier.size(52.dp).background(if (viewOnce) accent else Color(theme.cardColor), CircleShape),
//             ) {
//                 com.example.chat.ui.icons.ChatyIcon(
//                     com.example.chat.ui.icons.ChatyIconId.numberOne,
//                     semanticLabel = if (viewOnce) "View once on. The voice message plays once." else "Send as view once voice message",
//                     color = if (viewOnce) onAccent else foreground,
//                 )
//             }
//             IconButton(onClick = {
//                 playbackScope.launch {
//                     runCatching { playbackGuard.pause(); onSend() }
//                         .onFailure { error = "Playback could not stop. Try Send again." }
//                 }
//             }, enabled = !busy && !clip.joinFailed, modifier = Modifier.size(52.dp).background(accent.copy(alpha = if (clip.joinFailed) 0.4f else 1f), CircleShape)) {
//                 com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.send, semanticLabel = "Send recording", color = onAccent)
//             }
//         }
//     }
// }

/* Previous implementation fragments retained (net212):
                        onPause = session::beginReview,
                Text((if (state.paused) "Paused · " else "Recording · ") + "%d:%02d".format(state.seconds / 60, state.seconds % 60), color = foreground)
                IconButton(onClick = onSend, enabled = !state.busy, modifier = Modifier.size(52.dp).background(accent, CircleShape)) {
*/

/* Previous implementation fragments retained (net213):
    val player = remember(clip.path) { android.media.MediaPlayer() }
    val focusRequest = remember {
    DisposableEffect(clip.path) {
            runCatching { audioManager.abandonAudioFocusRequest(focusRequest) }
            runCatching { player.release() }
        error = null
        try {
            if (!ready) {
                player.setDataSource(clip.path); player.prepare(); ready = true
                totalMs = player.duration.coerceAtLeast(1)
                player.setOnCompletionListener {
                    playing = false; positionMs = 0
                    runCatching { audioManager.abandonAudioFocusRequest(focusRequest) }
                }
            }
            if (player.isPlaying) {
                player.pause(); playing = false
                audioManager.abandonAudioFocusRequest(focusRequest)
            } else {
                if (audioManager.requestAudioFocus(focusRequest) != android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    error = "Audio is in use by another app."; return
                if (positionMs >= totalMs - 50) { player.seekTo(0); positionMs = 0 }
                runCatching { player.playbackParams = player.playbackParams.setSpeed(speed) }
                player.start(); playing = true
        } catch (e: Exception) {
            playing = false; error = e.message ?: "Audio unavailable"
        if (playing) runCatching { player.playbackParams = player.playbackParams.setSpeed(speed) }
            IconButton(onClick = onDelete, enabled = !busy, modifier = Modifier.size(52.dp).background(Color(theme.cardColor), CircleShape)) {
                    runCatching { if (player.isPlaying) player.pause() }; playing = false
                    runCatching { audioManager.abandonAudioFocusRequest(focusRequest) }
                    onResume()
            IconButton(onClick = onSend, enabled = !busy && !clip.joinFailed, modifier = Modifier.size(52.dp).background(accent.copy(alpha = if (clip.joinFailed) 0.4f else 1f), CircleShape)) {
*/

/* Previous implementation fragments retained (net217):
    DisposableEffect(session) { onDispose { session.dispose() } }
*/

/* Previous implementation fragments retained (net218):
    Column(Modifier.fillMaxWidth().background(Color(theme.surfaceColor), RoundedCornerShape(28.dp)).padding(12.dp),
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                VoiceLevelMeterBar(levels, accent, Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onPause, enabled = !state.busy, modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = RoundedCornerShape(26.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("●", color = Color(0xFFE65B64))
                Text("‹ Slide to cancel", color = foreground, modifier = Modifier.weight(1f))
                        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.microphone, semanticLabel = "Recording audio", color = foreground)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Modifier.weight(1f).heightIn(min = 48.dp).fillMaxWidth()
        if (clip.joinFailed) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Parts could not be joined. Send is off until they are.", color = foreground, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = ::togglePlay, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = RoundedCornerShape(26.dp)) {
                }, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = RoundedCornerShape(26.dp)) {
            }
*/

/* Previous implementation fragments retained (net220):
    Column(Modifier.fillMaxWidth().graphicsLayer { alpha = enter.value }.background(Color(theme.surfaceColor), RoundedCornerShape(28.dp)).padding(12.dp),
*/

// Previous reviewed recording card before shared-component cleanup (inactive):
//     // Follow the visible chat's scoped palette rather than the global fallback.
//     val theme = com.example.chat.ui.theme.LocalThemeConfig.current
//     val accent = Color(theme.accentColor)
//     val foreground = Color(theme.primaryTextColor)
//     val secondary = Color(theme.secondaryTextColor)
//     val onAccent = com.example.chat.ui.kit.themeGlyphColor(accent, theme)
//     val reduced = com.example.chat.ui.kit.rememberChatyReduceMotion()
//     // Presentation only: capture stop/upload/send ordering remains owned by the session.
//     var sendSnapshot by remember { mutableStateOf<Pair<Int, List<Double>>?>(null) }
//     val sendRequested = sendSnapshot != null
//     LaunchedEffect(state.busy) { if (!state.busy) sendSnapshot = null }
//     val controlsEnabled = !state.busy && !sendRequested
//     val shape = RoundedCornerShape(28.dp)
//     val status = when {
//         sendRequested -> "Sending recording…"
//         state.busy -> "Processing recording…"
//         state.interruptedForCall -> "Paused for call"
//         state.paused -> "Paused"
//         else -> "Recording"
//     }
//     // Freeze only the display on Send; never keep suggesting capture during finalization/upload.
//     val shownSeconds = sendSnapshot?.first ?: state.seconds
//     val shownLevels = sendSnapshot?.second ?: levels
//     val elapsed = "%d:%02d".format(shownSeconds / 60, shownSeconds % 60)
//     Column(
//         Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
//             .background(Color(theme.surfaceColor), shape)
//             .border(1.dp, Color(theme.cardColor), shape)
//             .padding(horizontal = 16.dp, vertical = 12.dp),
//         verticalArrangement = Arrangement.spacedBy(12.dp),
//     ) {
//         if (state.locked) {
//             Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
//                 horizontalArrangement = Arrangement.spacedBy(8.dp)) {
//                 Text("●", color = if (sendRequested || state.paused) secondary else Color(theme.dangerColor), fontSize = 12.sp)
//                 Text(status, color = foreground, modifier = Modifier.weight(1f))
//                 Text(elapsed, color = secondary)
//             }
//             // Keep the existing real microphone samples. No decorative/random waveform.
//             VoiceLevelMeterBar(shownLevels, accent, Modifier.fillMaxWidth())
//             Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
//                 horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
//                 IconButton(onClick = onDelete, enabled = controlsEnabled,
//                     modifier = Modifier.size(48.dp).background(Color(theme.cardColor), CircleShape)) {
//                     com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.delete,
//                         semanticLabel = "Delete recording", color = foreground)
//                 }
//                 Button(onClick = onPause, enabled = controlsEnabled,
//                     modifier = Modifier.widthIn(min = 104.dp).heightIn(min = 48.dp),
//                     colors = ButtonDefaults.buttonColors(containerColor = Color(theme.cardColor), contentColor = foreground),
//                     shape = RoundedCornerShape(24.dp)) {
//                     Icon(if (state.paused) Icons.Default.PlayArrow else Icons.Default.Pause,
//                         if (state.paused) "Resume recording" else "Pause recording")
//                     Spacer(Modifier.width(6.dp))
//                     Text(if (state.paused) "Resume" else "Pause")
//                 }
//                 IconButton(onClick = onToggleViewOnce, enabled = controlsEnabled,
//                     modifier = Modifier.size(48.dp).background(if (viewOnce) accent else Color(theme.cardColor), CircleShape)) {
//                     Icon(Icons.Rounded.LooksOne,
//                         if (viewOnce) "View once on. The voice message plays once." else "Send as view once voice message",
//                         tint = if (viewOnce) onAccent else foreground)
//                 }
//                 IconButton(onClick = {
//                     // finishVoice reserves busy synchronously, before its suspended finalize/upload work.
//                     if (sendSnapshot == null) {
//                         val snapshot = state.seconds to levels.toList()
//                         if (onSend()) sendSnapshot = snapshot
//                     }
//                 }, enabled = controlsEnabled && !state.interruptedForCall,
//                     modifier = Modifier.size(48.dp).background(accent, CircleShape)) {
//                     com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.send,
//                         semanticLabel = if (sendRequested) "Sending recording" else "Send recording", color = onAccent)
//                 }
//             }
//         } else {
//             // A single measured row cannot FlowRow-wrap the held mic over the composer.
//             Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
//                 horizontalArrangement = Arrangement.spacedBy(12.dp)) {
//                 Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
//                     Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
//                         Text("●", color = Color(theme.dangerColor), fontSize = 12.sp)
//                         Text(elapsed, color = foreground)
//                     }
//                     Text(if (state.busy) status else "‹ Slide to cancel", color = secondary)
//                     VoiceLevelMeterBar(shownLevels, accent, Modifier.fillMaxWidth())
//                 }
//                 Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
//                     com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.lock,
//                         semanticLabel = "Slide up to lock recording", color = secondary)
//                     val scale = if (reduced) 1f else 1.04f + (levels.lastOrNull() ?: 0.0).toFloat().coerceIn(0f, 1f) * .08f
//                     Box(Modifier.size(56.dp).graphicsLayer { scaleX = scale; scaleY = scale }
//                         .background(accent, CircleShape), contentAlignment = Alignment.Center) {
//                         com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.microphone,
//                             semanticLabel = "Recording audio", color = onAccent)
//                     }
//                 }
//             }
//         }
//     }
// }
//

// Previous E2EE media wiring retained inactive:
//                 check(!secureChannelNow()) { "Encryption was turned on while recording; the voice note was discarded." }
//                 if (secureChannelNow()) throw IllegalStateException("Voice notes are not sent in an encrypted chat yet; encrypted media is not wired.")
//         if (secureChannelNow()) {
//             Toast.makeText(context, "Voice notes are not sent in an encrypted chat yet; encrypted media is not wired.", Toast.LENGTH_SHORT).show()
//             return
//         }
