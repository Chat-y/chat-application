package com.example.chat.data.services

import org.junit.Assert.*
import org.junit.Test

class CallIceRefreshRequestGateTest {
// Previous implementation retained:     @Test fun requestFloodCannotBypassThirtySecondWindow() {
    @Test fun retryFitsReconnectWindowWithoutAllowingFlood() {
        val gate=CallIceRefreshRequestGate()
// Previous implementation retained:         assertTrue(gate.tryAcquire(1000))
// Previous implementation retained:         repeat(100) { assertFalse(gate.tryAcquire(1000L+it)) }
// Previous implementation retained:         assertFalse(gate.tryAcquire(30999));assertTrue(gate.tryAcquire(31000))
        assertTrue(gate.tryAcquire(0))
        repeat(100) { assertFalse(gate.tryAcquire(it.toLong())) }
        assertTrue(gate.tryAcquire(5000));assertTrue(gate.tryAcquire(10000))
        assertFalse(gate.tryAcquire(15000));assertTrue(gate.tryAcquire(30000))
    }
    @Test fun resetIsCallScopedAndClockRollbackIsRejected() {
        val gate=CallIceRefreshRequestGate()
        assertTrue(gate.tryAcquire(50000));assertFalse(gate.tryAcquire(0))
        gate.reset();assertTrue(gate.tryAcquire(0))
    }
}
