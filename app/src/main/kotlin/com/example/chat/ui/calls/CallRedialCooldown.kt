package com.example.chat.ui.calls

/** In-memory UX guard, not server enforcement. No persistence, logs or contact display data. */
internal class CallRedialCooldown {
    private var lastGlobal: Long? = null
    private val recentTargets = mutableMapOf<String, Long>()

    /** 0 accepts this attempt; otherwise returns a positive wait without extending the cooldown. */
    @Synchronized fun acquire(target: String, nowMillis: Long): Long {
        val global = lastGlobal
        if (global != null && nowMillis < global) {
            // A new monotonic epoch cannot reuse old in-memory deadlines.
            lastGlobal = null
            recentTargets.clear()
        }
        recentTargets.entries.removeAll { nowMillis - it.value >= 5000L }
        val globalWait = lastGlobal?.let { (1000L - (nowMillis - it)).coerceAtLeast(0) } ?: 0L
        val targetWait = recentTargets[target]?.let { (5000L - (nowMillis - it)).coerceAtLeast(0) } ?: 0L
        val wait = maxOf(globalWait, targetWait)
        if (wait == 0L) {
            lastGlobal = nowMillis
            recentTargets[target] = nowMillis
        }
        return wait
    }
}
