package com.example.chat.data.services

/** Independent native cleanup attempts. Preserve the first ordinary failure and attach the rest. */
internal class CallMediaCleanup {
    private var first: Exception? = null
    private var interrupted = false
    fun attempt(action: () -> Unit) {
        try { action() } catch (failure: Exception) {
            if (failure is InterruptedException) interrupted = true
            val prior = first
            if (prior == null) first = failure else if (prior !== failure) prior.addSuppressed(failure)
        }
    }
    fun finish() {
        if (interrupted) Thread.currentThread().interrupt()
        first?.let { throw it }
    }
}
