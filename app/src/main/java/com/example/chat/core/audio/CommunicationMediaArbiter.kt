package com.example.chat.core.audio

/** Opaque reference identity. Create per account/operation generation, never from user IDs. */
class CommunicationMediaOwner

/** Process-scoped microphone/playback arbitration. Calls reserve after permission succeeds.
 * Media must use reservation.runNative for EVERY start/resume, never check-then-start.
 * Call acquisition first blocks new runNative operations atomically, then awaits each registered
 * suspension outside the arbiter lock. Suspension callbacks must confirm native pause/stop and
 * preserve drafts; they must not acquire a new reservation. No automatic resume on call release.
 * If native suspension is uncertain, call setup fails closed until matching recovery release. */
interface CommunicationMediaArbiter {
    suspend fun acquireCall(owner: CommunicationMediaOwner): CommunicationCallReservation
    suspend fun acquireVoiceNoteCapture(
        owner: CommunicationMediaOwner,
        suspendForCall: suspend () -> Unit,
    ): CommunicationMediaReservation?
    suspend fun acquirePlayback(
        owner: CommunicationMediaOwner,
        suspendForCall: suspend () -> Unit,
    ): CommunicationMediaReservation?
}

interface CommunicationMediaReservation {
    /** False means blocked/stale. The native operation runs atomically against call reservation.
     * Do not recursively call this arbiter from operation; retain reservation on uncertain failure. */
    suspend fun runNative(operation: suspend () -> Unit): Boolean
    /** Release only after native media is confirmed paused/stopped/disposed. Token checked. */
    suspend fun release()
}

interface CommunicationCallReservation {
    /** Mandatory guard before call FGS/focus/native setup. False still returns the recovery handle.
     * A false guard must recover via retryMediaSuspension before release(true) can unlock. */
    val mediaSuspended: Boolean
    /** Retry native suspension under the retained block. True confirms all registered native
     * media is suspended/clean; false remains blocked. Stale reservations return false. */
    suspend fun retryMediaSuspension(): Boolean
    /** Idempotent/token checked. False retains the block. Only this reservation's later successful
     * recovery may release it; an older reservation can never unlock a replacement call. */
    suspend fun release(cleanupSucceeded: Boolean)
}
