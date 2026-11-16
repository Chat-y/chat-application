package com.example.chat.ui.media

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Process-local protection hook. A cleanup owner must query this AND prove its own file ownership.
 * No method here deletes, renames or claims ownership of a user's file. */
object MediaPlaybackFileLeases {
    private val lock = Any()
    private val counts = mutableMapOf<String, Int>()

    fun acquire(paths: Collection<String>): AutoCloseable {
        val keys = paths.map { File(it).canonicalPath }.toSet()
        synchronized(lock) { keys.forEach { counts[it] = (counts[it] ?: 0) + 1 } }
        val closed = AtomicBoolean(false)
        return AutoCloseable {
            if (closed.compareAndSet(false, true)) synchronized(lock) {
                keys.forEach { key ->
                    val remaining = (counts[key] ?: 1) - 1
                    if (remaining <= 0) counts.remove(key) else counts[key] = remaining
                }
            }
        }
    }
    /** Unresolvable path is protected, not an excuse to delete it. */
    fun isProtected(file: File): Boolean {
        val key = try { file.canonicalPath } catch (_: Exception) { return true }
        return synchronized(lock) { (counts[key] ?: 0) > 0 }
    }
}
