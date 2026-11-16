package com.example.chat.ui.camera

import androidx.compose.ui.res.stringResource
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Local-only playback. Confirm returns typed video, not image bytes. */
@Composable
fun VideoCaptureReview(video: FinalizedVideo, auth: VideoScopeLease, registerStop: ((() -> Unit)?) -> Unit, onConfirm: (() -> Unit)?, onRetake: () -> Unit) {
    key(video.file.absolutePath) {
    var prepared by remember(video.file) { mutableStateOf(false) }
    var error by remember(video.file) { mutableStateOf(false) }
    var audioNotice by remember(video.file) { mutableStateOf<String?>(null) }
    // Previous raw player: var view by remember { mutableStateOf<VideoView?>(null) }
    // Review playback crosses the same call-arbitration guard as MediaReviewScreen: while a
    // call owns audio, Play is refused with a notice instead of mixing over the call.
    var view by remember { mutableStateOf<com.example.chat.ui.chats.detail.ArbitratedReviewVideoView?>(null) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) view?.pause() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(video.file) {
        registerStop { view?.stopPlayback(); view = null }
        onDispose { view?.stopPlayback(); view = null; registerStop(null) }
    }
    Column(Modifier.fillMaxSize().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        // CAM-R4-004 prior: Text(if (video.hasAudio) "Video with sound" else "Silent video (microphone audio was not recorded)", color = MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium)
        Text(if (video.hasAudio) stringResource(com.example.chat.R.string.camera_video_with_sound) else stringResource(com.example.chat.R.string.camera_video_silent), color = MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium)
        AndroidView(
            factory = { context -> com.example.chat.ui.chats.detail.ArbitratedReviewVideoView(
                context,
                com.example.chat.ServiceLocator.communicationMediaArbiter,
                // isOwnerCurrent = { auth.isCurrent() },
                isOwnerCurrent = { auth.isCurrent() && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) },
                onAudioError = { audioNotice = it },
            ).also { player ->
                view = player
                // Only the scope-guarded Play button controls playback.
                // Native MediaController buttons bypass that boundary.
                if (auth.isCurrent()) player.setVideoURI(Uri.fromFile(video.file))
                player.setOnPreparedListener { media ->
                    if (auth.isCurrent()) { prepared = true; media.isLooping = false; player.seekTo(1) }
                    else player.stopPlayback()
                }
                player.setOnErrorListener { _, _, _ -> if (auth.isCurrent()) error = true; true }
            } },
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        // CAM-R4-004 prior: if (error) Text("This video could not be played. Record it again.", color = MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodyMedium)
        if (error) Text(stringResource(com.example.chat.R.string.camera_video_play_error), color = MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodyMedium)
        audioNotice?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom=16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(prepared&&!error) Button(shape = com.example.chat.ui.kit.ChatyActionShape, onClick = { audioNotice = null; if (auth.isCurrent()) { view?.start(); if (!auth.isCurrent()) view?.stopPlayback() } },
                // CAM-R4-004 prior: enabled=prepared&&!error,modifier=Modifier.cameraActionFocusRing().fillMaxWidth().heightIn(min=48.dp)) { Text("Play recording") }
                enabled=prepared&&!error,modifier=Modifier.cameraActionFocusRing().fillMaxWidth().heightIn(min=48.dp)) { Text(stringResource(com.example.chat.R.string.camera_video_play_recording)) }
            // CAM-R4-004 prior: OutlinedButton(shape = com.example.chat.ui.kit.ChatyActionShape, onClick=onRetake,modifier=Modifier.cameraActionFocusRing().fillMaxWidth().heightIn(min=48.dp)) { Text("Record again") }
            OutlinedButton(shape = com.example.chat.ui.kit.ChatyActionShape, onClick=onRetake,modifier=Modifier.cameraActionFocusRing().fillMaxWidth().heightIn(min=48.dp)) { Text(stringResource(com.example.chat.R.string.camera_video_record_again)) }
            // CAM-R4-004 prior: if(onConfirm!=null) Button(shape = com.example.chat.ui.kit.ChatyActionShape, onClick=onConfirm,enabled=prepared&&!error,modifier=Modifier.cameraActionFocusRing().fillMaxWidth().heightIn(min=48.dp)) { Text("Use video") }
            if(onConfirm!=null) Button(shape = com.example.chat.ui.kit.ChatyActionShape, onClick=onConfirm,enabled=prepared&&!error,modifier=Modifier.cameraActionFocusRing().fillMaxWidth().heightIn(min=48.dp)) { Text(stringResource(com.example.chat.R.string.camera_video_use)) }
        }
    }
}
}

// CAM-R4-034 prior UI modifier lines retained inactive:
//                 enabled=prepared&&!error,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text("Play recording") }
//             OutlinedButton(shape = com.example.chat.ui.kit.ChatyActionShape, onClick=onRetake,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text("Record again") }
//             if(onConfirm!=null) Button(shape = com.example.chat.ui.kit.ChatyActionShape, onClick=onConfirm,enabled=prepared&&!error,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text("Use video") }
