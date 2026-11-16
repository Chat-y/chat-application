package com.example.chat.ui.chats.detail

import android.content.Context
import android.widget.VideoView
import com.example.chat.core.audio.CommunicationMediaArbiter
import com.example.chat.core.audio.CommunicationPlaybackGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Native MediaController and VideoView key controls dispatch to these virtual methods too.
 * One view per active page/source/account. Preparation never requests autoplay; pause clears the
 * native target state even before preparation completes. All native work stays on Main. */
internal class ArbitratedReviewVideoView(
    context: Context,
    arbiter: CommunicationMediaArbiter,
    isOwnerCurrent: () -> Boolean,
    private val onAudioError: (String) -> Unit,
) : VideoView(context) {
    private val nativeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val guard = CommunicationPlaybackGuard(arbiter,
        pauseNative = { withContext(Dispatchers.Main.immediate) { pauseNativeNow() } },
        disposeNative = { withContext(Dispatchers.Main.immediate) { disposeNativeNow() } },
        isOwnerCurrent = isOwnerCurrent)

    private fun pauseNativeNow() { super.pause() }
    private fun disposeNativeNow() { super.stopPlayback() }

    override fun start() {
        nativeScope.launch {
            runCatching {
                if (!guard.runNative { super.start() }) onAudioError("Finish the call before playing this preview.")
            }.onFailure { onAudioError("Preview playback is unavailable. Choose this item again.") }
        }
    }

    override fun pause() {
        nativeScope.launch {
            runCatching { guard.pause() }.onFailure { onAudioError("Preview could not pause. Try again.") }
        }
    }

    override fun seekTo(msec: Int) {
        nativeScope.launch {
            // Seeking can trigger prepared/target-state behavior on native players.
            runCatching { guard.runNative { super.seekTo(msec) } }
                .onFailure { onAudioError("Preview could not seek. Try again.") }
        }
    }

    /** VideoView.resume can reopen native playback with its old target state. Never bypass start. */
    override fun resume() { start() }

    override fun stopPlayback() { releaseForReview() }

    fun releaseForReview() {
        guard.markClosing() // Synchronous fence for queued MediaController commands.
        nativeScope.launch(NonCancellable) {
            runCatching { guard.dispose() }
                .onFailure { onAudioError("Preview cleanup is incomplete. Audio remains blocked until cleanup succeeds.") }
            // Failed native handles remain registered; call recovery can retry full disposal.
        }
    }
}
