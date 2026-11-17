package com.example.chat.data.services

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CallOperationTimedOut : IllegalStateException("Call media setup timed out.")
internal class CallMicrophoneUnavailable : IllegalStateException("Call microphone is unavailable.")

internal object CallOperationDeadline {
    const val PREPARE_MILLIS = 30_000L
    const val SDP_MILLIS = 12_000L
}

/** Wrapper box preserves nullable successful values while detecting only this deadline's timeout. */
internal suspend fun <T> callOperationDeadline(timeoutMillis: Long, block: suspend () -> T): T {
    class Completed<T>(val value: T)
    val completed = withTimeoutOrNull(timeoutMillis) { Completed(block()) } ?: throw CallOperationTimedOut()
    return completed.value
}

/** Late/duplicate native callbacks do not resume a cancelled or already-completed operation. */
internal suspend fun <T> awaitCallNativeCallback(register: ((Result<T>) -> Unit) -> Unit): T =
    callOperationDeadline(CallOperationDeadline.SDP_MILLIS) {
        suspendCancellableCoroutine { continuation ->
            val completed = AtomicBoolean(false)
            continuation.invokeOnCancellation { completed.set(true) }
            val complete: (Result<T>) -> Unit = { result ->
                if (completed.compareAndSet(false, true)) result.fold(
                    onSuccess = { continuation.resume(it) },
                    onFailure = { continuation.resumeWithException(it) })
            }
            try { register(complete) } catch (failure: Throwable) { complete(Result.failure(failure)) }
        }
    }
