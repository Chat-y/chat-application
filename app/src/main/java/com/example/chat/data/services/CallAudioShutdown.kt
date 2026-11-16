package com.example.chat.data.services

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Complete every cleanup stage; cancellation and other failures remain the original failure. */
internal suspend fun finishCallAudioCleanup(
    stages: List<suspend () -> Unit>,
    completion: suspend (Boolean) -> Unit,
) = withContext(NonCancellable) {
    var first: Throwable? = null
    suspend fun attempt(stage: suspend () -> Unit) {
        try { stage() } catch (failure: Throwable) {
            val prior = first
            if (prior == null) first = failure else if (prior !== failure) prior.addSuppressed(failure)
        }
    }
    stages.forEach { attempt(it) }
    attempt { completion(first == null) }
    first?.let { throw it }
}
