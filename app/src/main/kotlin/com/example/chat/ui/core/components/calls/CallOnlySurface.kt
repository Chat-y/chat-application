package com.example.chat.ui.core.components.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.chat.data.services.AudioRouteType
import com.example.chat.data.services.ChatyCallSession
import com.example.chat.data.services.CallVideoSurfaceProvider
import com.example.chat.ui.calls.callPipStatus
import org.webrtc.SurfaceViewRenderer

/** Call-only presentation for a separate opaque Activity. Never accepts chat or account content. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CallOnlySurface(
    session: ChatyCallSession?, durationSeconds: Int, video: CallVideoSurfaceProvider?, compact: Boolean,
    pipUnavailable: String?, onPip: () -> Unit, onPipSettings: () -> Unit, onOpenApp: () -> Unit,
    onMute: () -> Unit, onSpeaker: () -> Unit, onHangup: () -> Unit, onReady: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        if (session?.isVideo == true && video?.hasRemoteVideo == true) {
            key(session.callId, video) {
                AndroidView(factory = { context -> SurfaceViewRenderer(context).apply { video.attachRemoteRenderer(this) } },
                    modifier = Modifier.fillMaxSize(), onRelease = { video.detachRenderer(it) })
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(12.dp)) {
                Text(if (session == null) "Call unavailable" else "Chaty call", color = Color.White, fontSize = 16.sp)
                if (session != null) Text(callPipStatus(session.state, durationSeconds), color = Color.LightGray, fontSize = 12.sp)
            }
        }
        if (!compact && session != null) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.85f))
                .windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                pipUnavailable?.let { Text(it, color = Color.White) }
                FlowRow(horizontalArrangement = Arrangement.Center) {
                    CallControlButton(if (session.isMuted) "Unmute" else "Mute", session.isMuted, Color(0xFF00BFA5), true, onMute) { tint ->
                        com.example.chat.ui.icons.ChatyIcon(if (session.isMuted) com.example.chat.ui.icons.ChatyIconId.microphoneOff else com.example.chat.ui.icons.ChatyIconId.microphone, color = tint)
                    }
                    Spacer(Modifier.width(12.dp))
                    CallControlButton(if (session.audioRoute == AudioRouteType.speaker) "Earpiece" else "Speaker", session.audioRoute == AudioRouteType.speaker, Color(0xFF00BFA5), true, onSpeaker) { tint ->
                        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.speaker, color = tint)
                    }
                    Spacer(Modifier.width(12.dp))
                    CallControlButton("Hang up", true, Color(0xFFFF5252), true, onHangup) { tint ->
                        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.callEnd, color = tint)
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = onPip) { Text("Float call") }
                    if (pipUnavailable != null) TextButton(onClick = onPipSettings) { Text("Allow picture-in-picture") }
                    TextButton(onClick = onOpenApp) { Text("Return to app") }
                }
            }
        }
    }
    LaunchedEffect(session?.callId) {
        withFrameNanos { }; withFrameNanos { }
        onReady()
    }
}
