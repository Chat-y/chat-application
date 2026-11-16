package com.example.chat.notifications

import org.junit.Assert.*
import org.junit.Test

/** Source-only ringer policy fixture, not an Android audio/vibration test. */
class ForegroundQuietCallTest {
    private class Player : RingerPlayer {
        var starts = 0
        var stops = 0
        override val isPlaying: Boolean get() = starts > stops
        override fun start() { starts++ }
        override fun stop() { stops++ }
    }
    @Test fun quietSnapshotNeverStartsForegroundRingtone() {
        val player = Player()
        val ringer = ForegroundCallRinger(player)
        ringer.onCallSnapshot(true, true, quiet = true)
        assertEquals(0, player.starts)
        ringer.onCallSnapshot(true, true, quiet = false)
        assertEquals(1, player.starts)
        ringer.onCallSnapshot(true, true, quiet = true)
        assertEquals(1, player.stops)
    }
}
