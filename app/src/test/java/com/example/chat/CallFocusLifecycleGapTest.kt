package com.example.chat

import android.media.AudioManager
import com.example.chat.data.services.CallFocusInterruptionPolicy
import com.example.chat.data.services.CallMediaForegroundLease
import com.example.chat.data.services.CallForegroundLeasePolicy
import org.junit.Assert.*
import org.junit.Test

/** NOT RUN. Pure focus policy/foreground lease seams, not Android focus/FGS proof. */
class CallFocusLifecycleGapTest {
    @Test fun transientLossPausesAndGainResumesSameCall() {
        val policy = CallFocusInterruptionPolicy()
        assertFalse(policy.update(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT))
        assertTrue(policy.update(AudioManager.AUDIOFOCUS_GAIN))
        assertFalse(policy.update(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK))
        assertTrue(policy.update(AudioManager.AUDIOFOCUS_GAIN))
    }
    @Test fun permanentLossNeverResumesFromLaterGainCallback() {
        val policy = CallFocusInterruptionPolicy()
        assertFalse(policy.update(AudioManager.AUDIOFOCUS_LOSS))
        assertFalse(policy.update(AudioManager.AUDIOFOCUS_GAIN))
        assertFalse(policy.update(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT))
        assertFalse(policy.update(AudioManager.AUDIOFOCUS_GAIN))
    }
    @Test fun provisionalForegroundReleaseRunsExactlyOnce() {
        var releases = 0
        val lease = CallMediaForegroundLease { releases++ }
        lease.close(); lease.close()
        assertEquals(1, releases)
    }
    @Test fun staleProvisionalReleaseCannotStopReplacementOrLifecycleTakeover() {
        val policy = CallForegroundLeasePolicy()
        policy.begin("old")
        policy.begin("new")
        assertFalse(policy.releaseIfCurrent("old"))
        assertTrue(policy.releaseIfCurrent("new"))
        assertFalse(policy.releaseIfCurrent("new"))
        policy.begin("another")
        policy.begin(null) // Ordinary lifecycle sync has taken ownership.
        assertFalse(policy.releaseIfCurrent("another"))
    }

}
