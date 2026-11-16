package com.example.chat.core.audio

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One process instance via ServiceLocator. Reservations contain only reference identities.
 * Lock order: client local mutex -> arbiter mutex. Call callbacks run WITHOUT arbiter mutex.
 * Native operations must be bounded and must never call this arbiter recursively. */
class DefaultCommunicationMediaArbiter : CommunicationMediaArbiter {
    private val mutex = Mutex()
    private var call: CallLease? = null
    private val media = mutableSetOf<MediaLease>()

    override suspend fun acquireCall(owner: CommunicationMediaOwner): CommunicationCallReservation =
        withContext(NonCancellable) {
            val lease = CallLease(owner)
            val affected = mutex.withLock {
                check(call == null) { "Another call owns audio. Finish its cleanup before trying again." }
                call = lease // Existing runNative operations have drained before this assignment.
                media.toList()
            }
            // Previous inline callback loop retained:
            // var suspended = true
            // for (entry in affected) {
            // try { entry.suspendForCall() } catch (_: Exception) { suspended = false }
            // }
            // lease.mediaSuspended = suspended
            lease.suspendEntries(affected)
            // Always hand back the matching recovery handle, even when suspension failed.
            lease
        }

    override suspend fun acquireVoiceNoteCapture(
        owner: CommunicationMediaOwner,
        suspendForCall: suspend () -> Unit,
    ): CommunicationMediaReservation? = acquire(owner, suspendForCall)

    override suspend fun acquirePlayback(
        owner: CommunicationMediaOwner,
        suspendForCall: suspend () -> Unit,
    ): CommunicationMediaReservation? = acquire(owner, suspendForCall)

    private suspend fun acquire(
        owner: CommunicationMediaOwner,
        suspension: suspend () -> Unit,
    ): CommunicationMediaReservation? = mutex.withLock {
        if (call != null) null else MediaLease(owner, suspension).also { media += it }
    }

    private inner class MediaLease(
        @Suppress("unused") val owner: CommunicationMediaOwner,
        val suspendForCall: suspend () -> Unit,
    ) : CommunicationMediaReservation {
        override suspend fun runNative(operation: suspend () -> Unit): Boolean = mutex.withLock {
            if (call != null || this !in media) return@withLock false
            operation()
            true
        }
        override suspend fun release() { mutex.withLock { media.remove(this) } }
    }

    private inner class CallLease(
        @Suppress("unused") val owner: CommunicationMediaOwner,
    ) : CommunicationCallReservation {
        override var mediaSuspended: Boolean = false
            internal set
        private val recoveryMutex = Mutex()
        suspend fun suspendEntries(entries: List<MediaLease>) {
            var succeeded = true
            for (entry in entries) {
                try { entry.suspendForCall() } catch (_: Exception) { succeeded = false }
            }
            mutex.withLock {
                if (call === this@CallLease) mediaSuspended = succeeded
            }
        }
        override suspend fun retryMediaSuspension(): Boolean = withContext(NonCancellable) {
            recoveryMutex.withLock {
                val entries = mutex.withLock {
                    if (call !== this@CallLease) return@withContext false
                    mediaSuspended = false
                    media.toList()
                }
                suspendEntries(entries)
                mediaSuspended
            }
        }
        override suspend fun release(cleanupSucceeded: Boolean) {
            withContext(NonCancellable) {
                mutex.withLock {
                    // Previous: if (cleanupSucceeded && call === this@CallLease) call = null
                    if (cleanupSucceeded && mediaSuspended && call === this@CallLease) call = null
                }
            }
        }
    }
}
