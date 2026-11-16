package com.example.chat.core.audio

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Call-side handle. Permission is granted before acquire; native setup follows its true guard. */
internal class CallCommunicationOwnership(
    private val arbiter: CommunicationMediaArbiter,
    private val recovery: CallAudioRecoveryStore = CallAudioRecovery.store,
) {
    private val owner = CommunicationMediaOwner()
    private var reservation: CommunicationCallReservation? = null
    private var uncertainCleanup = false

    suspend fun acquire() {
        check(!uncertainCleanup && reservation == null) { "Previous call audio cleanup needs attention." }
        // Retain the handle before cancellation can interrupt handoff to the caller.
        withContext(NonCancellable) { reservation = arbiter.acquireCall(owner) }
        currentCoroutineContext().ensureActive()
        check(reservation?.mediaSuspended == true) { "Previous media could not be suspended. Retry audio cleanup." }
    }

    fun markCleanupUncertain() { uncertainCleanup = true }

    suspend fun finishCleanup(cleanupSucceeded: Boolean) = withContext(NonCancellable) {
        if (!cleanupSucceeded) uncertainCleanup = true
        val lease = reservation ?: return@withContext
        val nativeClean = !uncertainCleanup
        val canRelease = nativeClean && lease.mediaSuspended
        try {
            lease.release(canRelease)
            if (canRelease) {
                recovery.forget(lease)
                reservation = null
            } else recovery.retain(lease, nativeClean)
        } catch (failure: Throwable) {
            recovery.retain(lease, nativeClean)
            throw failure
        }
    }
}

data class CallAudioRecoveryStatus(val pending: Int = 0, val nativeCleanupUncertain: Boolean = false)

/** Retained handles survive a failed media factory being detached. No identities, files or audio. */
class CallAudioRecoveryStore {
    private val lock = Any()
    private val retryMutex = Mutex()
    private val retained = java.util.IdentityHashMap<CommunicationCallReservation, Boolean>()
    private val mutableStatus = MutableStateFlow(CallAudioRecoveryStatus())
    val status = mutableStatus.asStateFlow()
    private fun publish() {
        mutableStatus.value = CallAudioRecoveryStatus(retained.size, retained.values.any { !it })
    }
    internal fun retain(lease: CommunicationCallReservation, nativeClean: Boolean) = synchronized(lock) {
        // Once uncertain, a later empty dispose cannot upgrade this handle to clean.
        retained[lease] = (retained[lease] ?: true) && nativeClean
        publish()
    }
    internal fun forget(lease: CommunicationCallReservation) = synchronized(lock) {
        retained.remove(lease)
        publish()
    }

    /** Explicit user recovery only. Never starts a call or resumes playback/recording. */
    suspend fun retry(): Boolean = retryMutex.withLock {
        val snapshot = synchronized(lock) { retained.entries.map { it.key to it.value } }
        for ((lease, nativeClean) in snapshot) {
            if (!nativeClean) continue
            try {
                withContext(NonCancellable) {
                    if (lease.retryMediaSuspension() && lease.mediaSuspended) {
                        lease.release(true)
                        forget(lease)
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch (_: Exception) { /* Keep the original matching handle for another explicit retry. */ }
        }
        synchronized(lock) { retained.isEmpty() }
    }
}

object CallAudioRecovery {
    val store = CallAudioRecoveryStore()
}
