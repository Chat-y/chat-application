package com.example.chat.core.audio

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Reusable guard for a native player not owned by MediaPlayerCoordinator.
 * Use runNative for EVERY operation that could start native playback (including speed setters).
 * Native callbacks should use their required dispatcher and must not reenter this guard/arbiter.
 * No automatic resume. One instance per native player/account generation. */
class CommunicationPlaybackGuard(
    private val arbiter: CommunicationMediaArbiter,
    private val pauseNative: suspend () -> Unit,
    private val disposeNative: suspend () -> Unit,
    private val isOwnerCurrent: () -> Boolean = { true },
    private val onCallSuspended: suspend () -> Unit = {},
    private val nativeOperationTimeoutMillis: Long = 30_000L,
) {
    private val mutex = Mutex()
    private val owner = CommunicationMediaOwner()
    private var reservation: CommunicationMediaReservation? = null
    private var nativeDisposed = false
    private var nativeOperationUncertain = false
    init { require(nativeOperationTimeoutMillis > 0) }
    @Volatile private var closing = false

    /** Disable queued starts synchronously before scheduling composition/lifecycle teardown. */
    fun markClosing() { closing = true }

    /** Bounded cooperative wait, including mutex/arbiter waits. Timeout propagates as cancellation.
     * Blocking/non-cooperative native APIs cannot be force-interrupted. An interrupted operation
     * retains the reservation and refuses new starts until serialized cleanup succeeds. */
    suspend fun runNative(operation: suspend () -> Unit): Boolean = withTimeout(nativeOperationTimeoutMillis) {
        mutex.withLock {
        if (closing || nativeDisposed || nativeOperationUncertain || !isOwnerCurrent()) return@withLock false
        val held = reservation ?: arbiter.acquirePlayback(owner) {
            mutex.withLock {
                if (closing) {
                    // Lifecycle teardown failed earlier: retained call recovery can retry it.
                    if (!nativeDisposed) { disposeNative(); nativeDisposed = true }
                    reservation?.release()
                    reservation = null
                } else if (!nativeDisposed) {
                    check(!nativeOperationUncertain) { "Native playback cleanup is incomplete." }
                    pauseNative()
                }
                onCallSuspended()
            }
        }?.also { reservation = it }
        if (held == null) return@withLock false
        held.runNative {
            check(!closing && isOwnerCurrent()) { "Playback session changed." }
            nativeOperationUncertain = true
            operation()
            nativeOperationUncertain = false
        }
        }
    }

    /** No permission check or reservation acquisition. Cleanup/reset/release ONLY, never start.
     * Call from a coroutine AFTER a native callback returns, never reenter from a guarded operation.
     * Success clears interrupted-operation uncertainty but retains the media reservation. */
    suspend fun serializeNativeCleanup(operation: suspend () -> Unit): Unit = withContext(NonCancellable) {
        withTimeout(nativeOperationTimeoutMillis) {
            mutex.withLock {
                if (!nativeDisposed) {
                    nativeOperationUncertain = true
                    operation()
                    nativeOperationUncertain = false
                }
            }
        }
    }

    suspend fun pause(): Unit = mutex.withLock {
        if (!nativeDisposed) pauseNative()
    }

    /** Serialized, cancellation-safe and retryable. Native failure retains the reservation. */
    suspend fun dispose() = withContext(NonCancellable) {
        mutex.withLock {
            closing = true
            if (!nativeDisposed) {
                disposeNative()
                nativeDisposed = true
            }
            reservation?.release()
            reservation = null
        }
    }
}
