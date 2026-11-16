package com.example.chat

import com.example.chat.data.e2ee.SecureKeyValueStore
import com.example.chat.data.services.LocalLockService
import com.example.chat.ui.settings.security.*
import com.example.chat.ui.vault.HiddenVaultService
import com.example.chat.platform.LockBiometricPolicy
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PrivacyOpenGapRegressionTest {
    private class Store : SecureKeyValueStore {
        val values = mutableMapOf<String, String>()
        val reads = mutableListOf<String>()
        var failMigration = false
        override suspend fun read(key: String): String? { reads += key; return values[key] }
        override suspend fun write(key: String, value: String) {
            if (failMigration && key.endsWith("decoy_pin.hash")) error("write failed")
            values[key] = value
        }
        override suspend fun delete(key: String) { values.remove(key) }
    }
//     @Test fun phraseLengthSkipsHashReadAndLegacyStillWorks() = runBlocking {
//         val store = Store(); val service = LocalLockService(store)
//         service.setSecretPhrase("a private phrase")
//         store.reads.clear()
//         assertFalse(service.verifySecretPhrase("short"))
//         assertFalse(store.reads.any { it.endsWith("secret_phrase.hash") })
//         store.values.keys.filter { it.endsWith("secret_phrase.length") }.toList().forEach { store.values.remove(it) }
//         assertTrue(service.verifySecretPhrase("a private phrase"))
//         service.clearSecretPhrase()
//         assertFalse(store.values.keys.any { it.contains("secret_phrase") })
//     }
    @Test fun phraseUnlockWorksWithoutLengthMetadataAndIgnoresStaleHints() = runBlocking {
        val store = Store(); val service = LocalLockService(store)
        service.setSecretPhrase("a private phrase")
        assertFalse(store.values.keys.any { it.endsWith("secret_phrase.length") })
        store.reads.clear()
        assertFalse(service.verifySecretPhrase("short"))
        assertTrue(store.reads.any { it.endsWith("secret_phrase.hash") })
        assertTrue(service.verifySecretPhrase("a private phrase"))
        val hashKey = store.values.keys.single { it.endsWith("secret_phrase.hash") }
        store.values[hashKey.removeSuffix("hash") + "length"] = "1"
        store.reads.clear()
        assertTrue(service.verifySecretPhrase("a private phrase"))
        assertFalse(store.reads.any { it.endsWith("secret_phrase.length") })
        service.clearSecretPhrase()
        assertFalse(service.verifySecretPhrase("a private phrase"))
    }
    @Test fun legacyDecoyMigratesOnlyAfterProof() = runBlocking {
        val store = Store(); val lock = LocalLockService(store)
        lock.setCredential("pin", "1357", 4)
        store.values["chaty.hidden_vault.decoy_pin"] = "8642"
        val vault = HiddenVaultService(lock, store)
        assertFalse(vault.verifyPin("7531"))
        assertEquals("8642", store.values["chaty.hidden_vault.decoy_pin"])
        assertTrue(vault.verifyPin("8642"))
        assertTrue(vault.isDecoyMode)
        assertNull(store.values["chaty.hidden_vault.decoy_pin"])
        assertTrue(store.values["chaty.hidden_vault.decoy_pin.hash"]!!.startsWith("v1:"))
        vault.lockVault()
        assertTrue(vault.verifyPin("8642"))
    }
    @Test fun failedMigrationDoesNotDeleteOldCredential() = runBlocking {
        val store = Store(); val lock = LocalLockService(store)
        lock.setCredential("pin", "1357", 4)
        store.values["chaty.hidden_vault.decoy_pin"] = "8642"
        store.failMigration = true
        val vault = HiddenVaultService(lock, store)
        assertTrue(runCatching { vault.verifyPin("8642") }.isFailure)
        assertFalse(vault.isUnlocked)
        assertEquals("8642", store.values["chaty.hidden_vault.decoy_pin"])
    }
    @Test fun protectedToastBodyFailsClosedForUnknownChatContext() {
        assertTrue(ProtectedToastPolicy.conceal(false, true, true))
        assertTrue(ProtectedToastPolicy.conceal(true, false, false))
        assertFalse(ProtectedToastPolicy.conceal(false, false, true))
        assertFalse(ProtectedToastPolicy.conceal(false, true, false))
    }
    @Test fun classThreeLockMaskExcludesWeakBiometric() {
        assertEquals(BIOMETRIC_STRONG, LockBiometricPolicy.lockAuthenticators(true, true))
        assertEquals(BIOMETRIC_STRONG or DEVICE_CREDENTIAL, LockBiometricPolicy.lockAuthenticators(false, true))
        assertEquals(0, LockBiometricPolicy.lockAuthenticators(true, false) and (BIOMETRIC_WEAK xor BIOMETRIC_STRONG))
    }
    @Test fun setupClearsWrongPasswordAndNewPatternRequiresFiveNodes() = runBlocking {
        val setup = object : LockSetupService {
            override suspend fun hasCredential(method: String) = true
            override suspend fun verifyCredential(method: String, secret: String) = false
            override suspend fun setCredential(method: String, secret: String, pinLength: Int?) {}
            override suspend fun authenticateBiometric(reason: String) = false
            override suspend fun authenticateDeviceCredential(reason: String) = false
        }
        val password = LockCredentialSetupFlow("Password", 4, setup)
        password.init()
        assertEquals(SetupOutcome.clearText, password.handleContinue("wrong password"))
        val pattern = LockCredentialSetupFlow("Pattern", 4, setup, replaceExisting = true)
        pattern.init(); pattern.onPatternComplete("0-1-2-5")
        assertFalse(pattern.canContinuePattern)
        pattern.onPatternComplete("0-1-2-5-8")
        assertTrue(pattern.canContinuePattern)
        val legacyCurrent = LockCredentialSetupFlow("Pattern", 4, setup)
        legacyCurrent.init(); legacyCurrent.onPatternComplete("0-1-2-5")
        assertTrue(legacyCurrent.canContinuePattern)
    }
}
