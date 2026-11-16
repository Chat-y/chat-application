package com.example.chat.ui.settings.security

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Source-only additions. These cases have not been executed in the overnight checkpoint. */
class LockSetupOwnershipTest {
    private class DelayedSetup : LockSetupService {
        var read: suspend () -> Boolean = { false }
        var verify: suspend () -> Boolean = { true }
        var saves = 0
        override suspend fun hasCredential(method: String) = read()
        override suspend fun verifyCredential(method: String, secret: String) = verify()
        override suspend fun setCredential(method: String, secret: String, pinLength: Int?) { saves++ }
        override suspend fun authenticateBiometric(reason: String) = verify()
        override suspend fun authenticateDeviceCredential(reason: String) = verify()
    }

    @Test fun noActionsBeforeSuccessfulInitialization() = runBlocking {
        val service = DelayedSetup()
        val flow = LockCredentialSetupFlow("PIN", 4, service)
        assertEquals(SetupOutcome.none, flow.handleContinue("1357"))
        assertFalse(flow.initialized)
        assertEquals(0, service.saves)
        flow.init()
        assertTrue(flow.initialized)
        assertEquals(SetupOutcome.clearPin, flow.handleContinue("1357"))
    }

    @Test fun initializationFailureDoesNotOfferNewCredential() = runBlocking {
        val service = DelayedSetup().apply { read = { error("private storage failure") } }
        val flow = LockCredentialSetupFlow("PIN", 4, service)
        flow.init()
        assertFalse(flow.initialized)
        assertFalse(flow.busy)
        assertEquals(SetupOutcome.none, flow.handleContinue("1357"))
        assertEquals(0, service.saves)
        assertFalse(flow.error.contains("private storage"))
    }

    @Test fun clearedInitializationCannotReviveSetup() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val result = CompletableDeferred<Boolean>()
        val service = DelayedSetup().apply { read = { entered.complete(Unit); result.await() } }
        val flow = LockCredentialSetupFlow("PIN", 4, service)
        val pending = async { flow.init() }
        entered.await()
        flow.clearSensitiveState()
        result.complete(true)
        pending.await()
        assertFalse(flow.initialized)
        assertFalse(flow.busy)
        assertEquals(SetupOutcome.none, flow.handleContinue("1357"))
    }

    @Test fun clearedVerificationCannotAdvanceToEnrollment() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val result = CompletableDeferred<Boolean>()
        val service = DelayedSetup().apply {
            read = { true }
            verify = { entered.complete(Unit); result.await() }
        }
        val flow = LockCredentialSetupFlow("PIN", 4, service)
        flow.init()
        val pending = async { flow.handleContinue("1357") }
        entered.await()
        flow.clearSensitiveState()
        result.complete(true)
        assertEquals(SetupOutcome.none, pending.await())
        assertEquals(SetupStep.enterCurrent, flow.step)
        assertFalse(flow.initialized)
        assertEquals(0, service.saves)
    }
}
