package com.example.chat

import com.example.chat.data.e2ee.SecureKeyValueStore
import com.example.chat.data.services.LocalLockService
import com.example.chat.ui.vault.HiddenVaultService
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class VaultOpenGapVerificationTest {
    private class Store : SecureKeyValueStore {
        val data = mutableMapOf<String, String>()
        var failRegistryRead = false
        var failRegistryWrite = false
        override suspend fun read(key: String): String? {
            if (failRegistryRead && key.contains("locked_conversations")) error("private storage detail")
            return data[key]
        }
        override suspend fun write(key: String, value: String) {
            if (failRegistryWrite && key.contains("locked_conversations")) error("private storage detail")
            data[key] = value
        }
        override suspend fun delete(key: String) { data.remove(key) }
    }
    @Test fun missingCredentialAndImplicitDefaultFailClosed() = runBlocking {
        val store = Store(); val lock = LocalLockService(store); val vault = HiddenVaultService(lock, store)
        assertFalse(vault.verifyPin("1357")); assertFalse(vault.isUnlocked)
        lock.setCredential("pin", "1357", 4)
        assertFalse(vault.verifyPin("0000")); assertFalse(vault.isUnlocked)
    }
    @Test fun wrongLengthDoesNotSubmitAndThrottleSurvivesNewService() = runBlocking {
        val store = Store(); val lock = LocalLockService(store, now = { 1_000_000 })
        lock.setCredential("pin", "135790", 6)
        val vault = HiddenVaultService(lock, store)
        assertEquals(6, vault.getPinLength())
        assertFalse(vault.verifyPin("1357")); assertEquals(0, lock.getFailedAttempts())
        repeat(5) { assertFalse(vault.verifyPin("000000")) }
        assertEquals(30, LocalLockService(store, now = { 1_000_000 }).getRemainingCooldownSeconds())
        assertFalse(vault.verifyPin("135790")); assertFalse(vault.isUnlocked)
    }
    @Test fun registryWriteFailurePreservesMembershipAndReadFailureIsNotEmptySuccess() = runBlocking {
        val store = Store(); val lock = LocalLockService(store); lock.setCredential("pin", "1357", 4)
        val vault = HiddenVaultService(lock, store); assertTrue(vault.verifyPin("1357"))
        vault.toggleConversationLock("first")
        store.failRegistryWrite = true
        assertTrue(runCatching { vault.toggleConversationLock("second") }.isFailure)
        assertEquals(setOf("first"), vault.lockedConversationIds)
        assertNotNull(vault.registryError)
        store.failRegistryRead = true; vault.loadVaultRegistry()
        assertEquals(setOf("first"), vault.lockedConversationIds)
        assertNotNull(vault.registryError); assertFalse(vault.registryLoading)
    }
    @Test fun biometricExceptionFailsClosedAndDoesNotBlockNextAttempt() = runBlocking {
        val store = Store(); val lock = LocalLockService(store); var attempts = 0
        val vault = HiddenVaultService(lock, store, authenticateBiometric = { attempts++; if (attempts == 1) error("private failure"); true })
        assertFalse(vault.unlockWithBiometrics()); assertFalse(vault.isUnlocked)
        assertTrue(vault.unlockWithBiometrics()); assertTrue(vault.isUnlocked)
    }
    @Test fun supersededBiometricResultCannotUnlockDifferentAccount() = runBlocking {
        val store = Store(); val lock = LocalLockService(store)
        val entered = CompletableDeferred<Unit>(); val result = CompletableDeferred<Boolean>()
        val vault = HiddenVaultService(lock, store, authenticateBiometric = { entered.complete(Unit); result.await() })
        val pending = async { vault.unlockWithBiometrics() }; entered.await()
        vault.resetForAccountSwitchSync("other-account"); result.complete(true)
        assertFalse(pending.await()); assertFalse(vault.isUnlocked)
    }
}
