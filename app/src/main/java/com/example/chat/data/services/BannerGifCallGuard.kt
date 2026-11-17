package com.example.chat.data.services

import com.example.chat.core.audio.CommunicationMediaArbiter
import com.example.chat.core.audio.CommunicationPlaybackGuard

/** A call permanently retires this mounted GIF session. No automatic call-end resume. */
internal class BannerGifCallGuard(arbiter: CommunicationMediaArbiter,
    private val stop: suspend () -> Unit, private val allowed: () -> Boolean) {
    @Volatile private var retired = false
    private val guard = CommunicationPlaybackGuard(arbiter,
        pauseNative = { retired = true; stop() },
        disposeNative = { retired = true; stop() },
        isOwnerCurrent = { !retired && allowed() })
    suspend fun start(operation: suspend () -> Unit): Boolean = guard.runNative(operation)
    fun close() { retired = true; guard.markClosing() }
    suspend fun dispose() = guard.dispose()
}
