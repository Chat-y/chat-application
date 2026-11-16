package com.example.chat.data.services

import org.junit.Assert.*
import org.junit.Test

/** NOT RUN. Pure lease evidence, not Android service destruction/native shutdown proof. */
class CallForegroundLossGapTest {
    @Test fun lossNotifiesOnlyOnceAndLateObserverStillSeesLoss() {
        var first = 0
        var late = 0
        val lease = CallMediaForegroundLease {}
        lease.onLoss { first++ }
        assertTrue(lease.isActive)
        lease.signalLoss()
        lease.signalLoss()
        lease.onLoss { late++ }
        assertFalse(lease.isActive)
        assertEquals(1, first)
        assertEquals(1, late)
    }
    @Test fun intentionalCloseRemovesObserverAndReleasesOnlyOnce() {
        var releases = 0
        var losses = 0
        val lease = CallMediaForegroundLease { releases++ }
        lease.onLoss { losses++ }
        lease.close()
        lease.close()
        lease.signalLoss()
        lease.onLoss { losses++ }
        assertFalse(lease.isActive)
        assertEquals(1, releases)
        assertEquals(0, losses)
    }
    @Test fun oldLeaseLossDoesNotInvalidateReplacementLease() {
        var losses = 0
        val old = CallMediaForegroundLease {}
        val replacement = CallMediaForegroundLease {}
        replacement.onLoss { losses++ }
        old.signalLoss()
        assertTrue(replacement.isActive)
        assertEquals(0, losses)
    }
}
