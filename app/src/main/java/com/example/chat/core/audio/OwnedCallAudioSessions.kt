package com.example.chat.core.audio

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One native call-audio owner across transport replacements. Never calls signaling. */
class OwnedCallAudioSessions(private val controller: CallAudioSessionDriver) {
    private val mutex = Mutex()
    class Owner internal constructor(internal val generation: Long)
    private val nextGeneration = java.util.concurrent.atomic.AtomicLong()
    private var latestStartedGeneration = 0L
    private var activeOwner: Owner? = null

    fun newOwner(): Owner = Owner(nextGeneration.incrementAndGet())
    fun isLatestOwner(owner: Owner): Boolean = owner.generation == nextGeneration.get()

    suspend fun start(owner: Owner, isVideo: Boolean): Boolean = mutex.withLock {
        if (owner.generation < latestStartedGeneration) return@withLock false
        if (activeOwner === owner) return@withLock true
        latestStartedGeneration = owner.generation
        if (activeOwner != null) {
            try { withContext(NonCancellable) { controller.terminateCallAudio() } }
            finally { activeOwner = null }
        }
        // Provisional ownership lets cancellation/failure release partially acquired native audio.
        activeOwner = owner
        try {
            val started = controller.initializeCallAudio(isVideo)
            if (!started) releaseOwned(owner)
            started
        } catch (failure: Throwable) {
            try { releaseOwned(owner) } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
            throw failure
        }
    }

    suspend fun switchRoute(owner: Owner, route: AudioOutputRoute): Boolean = mutex.withLock {
        if (activeOwner !== owner) return@withLock false
        controller.switchMediaRoute(route) && controller.activeRoute == route
    }

    suspend fun end(owner: Owner) = mutex.withLock { releaseOwned(owner) }

    private suspend fun releaseOwned(owner: Owner) {
        if (activeOwner !== owner) return
        try { withContext(NonCancellable) { controller.terminateCallAudio() } }
        finally { activeOwner = null }
    }
}
