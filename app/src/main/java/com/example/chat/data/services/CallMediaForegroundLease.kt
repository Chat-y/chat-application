package com.example.chat.data.services

// Previous implementation retained: /** Provisional foreground execution transfers to ordinary lifecycle sync when it arrives. */
/** Provisional foreground execution transfers to lifecycle sync; loss never becomes restart. */
class CallMediaForegroundLease(private val release: () -> Unit) : AutoCloseable {
// Previous implementation retained:     private val closed = java.util.concurrent.atomic.AtomicBoolean()
// Previous implementation retained:     override fun close() { if (closed.compareAndSet(false, true)) release() }
    private val lock = Any()
    private var closed = false
    private var lost = false
    private var onLost: (() -> Unit)? = null
    val isActive: Boolean get() = synchronized(lock) { !closed && !lost }

    /** A late subscriber observes an already-lost service too. Callback runs outside the lock. */
    fun onLoss(listener: () -> Unit) {
        val notify = synchronized(lock) {
            if (closed) false else if (lost) true else { onLost = listener; false }
        }
        if (notify) listener()
    }
    internal fun signalLoss() {
        val callback = synchronized(lock) {
            if (closed || lost) return
            lost = true
            onLost.also { onLost = null }
        }
        callback?.invoke()
    }
    override fun close() {
        val shouldRelease = synchronized(lock) {
            if (closed) false else { closed = true; onLost = null; true }
        }
        if (shouldRelease) release()
    }
}
