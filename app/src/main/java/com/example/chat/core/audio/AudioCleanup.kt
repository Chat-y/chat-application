package com.example.chat.core.audio

/** Best-effort independent restoration. Preserve the first failure without skipping later resets. */
internal fun attemptAudioCleanup(steps: List<() -> Unit>) {
    var first: Exception? = null
    for (step in steps) {
        try { step() } catch (failure: Exception) {
            if (first == null) first = failure else if (first !== failure) first.addSuppressed(failure)
        }
    }
    first?.let { throw it }
}
