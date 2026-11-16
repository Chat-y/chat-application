package com.example.chat.ui.chats.detail

/** MR-R5-025: crossing detection is separate from the monotonic1/s speech budget. */
internal class TrimBoundaryAnnouncementGate(startMs: Long, endMs: Long) {
    private var observed = seconds(startMs, endMs)
    private var lastSpokenAt: Long? = null

    fun shouldAnnounce(startMs: Long, endMs: Long, elapsedMs: Long): Boolean {
        val next = seconds(startMs, endMs)
        if (next == observed) return false
        observed = next
        val previous = lastSpokenAt
        // A reset clock starts a new budget. Production uses elapsedRealtime, not wall time.
        if (previous != null && elapsedMs >= previous && elapsedMs - previous < 1_000L) return false
        lastSpokenAt = elapsedMs
        return true
    }

    private fun seconds(startMs: Long, endMs: Long): Pair<Long, Long> =
        startMs.coerceAtLeast(0L) / 1_000L to endMs.coerceAtLeast(0L) / 1_000L
}
