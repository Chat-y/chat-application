package com.example.chat.data.services

/** WebRTC MediaStream.dispose owns added tracks, but detached tracks still need disposal. */
internal object StreamTrackCleanup {
    fun dispose(stream: (() -> Unit)?, tracks: List<Pair<Boolean, () -> Unit>>) {
// Previous implementation retained:         stream?.invoke()
// Previous implementation retained:         tracks.forEach { (ownedByStream, disposeTrack) -> if (!ownedByStream) disposeTrack() }
        val cleanup = CallMediaCleanup()
        cleanup.attempt { stream?.invoke(); Unit }
        tracks.forEach { (ownedByStream, disposeTrack) -> if (!ownedByStream) cleanup.attempt(disposeTrack) }
        cleanup.finish()
    }
}
