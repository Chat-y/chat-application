package com.example.chat.data.services

// Previous implementation retained: /** Guarded per-call by CallSignalingService. Remote requests are untrusted input. */
/** Per-call bounded peer requests: a retry can fit the 25s reconnect window. */
internal class CallIceRefreshRequestGate {
    private var windowStarted: Long? = null
    private var lastAccepted: Long? = null
    private var accepted = 0
    fun tryAcquire(nowMillis: Long): Boolean {
        val last = lastAccepted
        if (last != null && (nowMillis < last || nowMillis - last < MIN_INTERVAL_MILLIS)) return false
// Previous implementation retained:         lastAccepted = nowMillis
        val window = windowStarted
        if (window == null || nowMillis - window >= WINDOW_MILLIS) { windowStarted = nowMillis; accepted = 0 }
        if (accepted >= MAX_PER_WINDOW) return false
        accepted++; lastAccepted = nowMillis
        return true
    }
// Previous implementation retained:     fun reset() { lastAccepted = null }
// Previous implementation retained:     companion object { const val MIN_INTERVAL_MILLIS = 30_000L }
    fun reset() { windowStarted = null; lastAccepted = null; accepted = 0 }
    companion object {
        const val MIN_INTERVAL_MILLIS = 5_000L
        const val WINDOW_MILLIS = 30_000L
        const val MAX_PER_WINDOW = 3
    }
}
