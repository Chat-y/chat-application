package com.example.chat.data.services

/** Attempt every native cleanup; retain the first error with later failures suppressed. */
internal class VoiceNativeCleanup(initial: Exception? = null) {
    private var failure: Exception? = initial
    fun attempt(action: () -> Unit) {
        try { action() } catch (error: Exception) {
            val first = failure
            if (first == null) failure = error else if (first !== error) first.addSuppressed(error)
        }
    }
    fun throwIfFailed() { failure?.let { throw it } }
}
