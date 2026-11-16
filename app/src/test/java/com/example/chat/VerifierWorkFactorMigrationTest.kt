package com.example.chat

import com.example.chat.data.e2ee.SecureKeyValueStore
import com.example.chat.data.services.LocalLockService
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class VerifierWorkFactorMigrationTest {
    private class Store : SecureKeyValueStore {
        val values = mutableMapOf<String,String>()
        var failUpgrade = false
        var entered: CompletableDeferred<Unit>? = null
        var release: CompletableDeferred<Unit>? = null
        override suspend fun read(key: String) = values[key]
        override suspend fun write(key: String, value: String) {
            if (key.endsWith(".hash") && value.startsWith("v2:600000:") && values[key]?.contains(':') == false) {
                if (failUpgrade) error("unavailable")
                entered?.complete(Unit); release?.await()
            }
            values[key] = value
        }
        override suspend fun delete(key: String) { values.remove(key) }
    }
    private fun legacy(store: Store, factor: String, secret: String): String {
        val prefix = "chaty.local_lock.v2.$factor"
        val salt = ByteArray(16) { it.toByte() }
        val spec = PBEKeySpec(secret.toCharArray(), salt, 120_000, 256)
        val hash = try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
        store.values["$prefix.salt"] = Base64.getEncoder().encodeToString(salt)
        store.values["$prefix.hash"] = Base64.getEncoder().encodeToString(hash)
        hash.fill(0); salt.fill(0)
        return "$prefix.hash"
    }
    @Test fun newCredentialsAndPhrasesCarryTheirWorkFactor() = runBlocking {
        val store = Store(); val service = LocalLockService(store)
        service.setCredential("password", "  private test  ")
        service.setSecretPhrase("a private phrase")
        assertTrue(store.values.filterKeys { it.endsWith(".hash") }.values.all { it.startsWith("v2:600000:") })
        assertTrue(service.hasCredentialForSetup("password"))
        assertTrue(service.verifyCredential("password", "  private test  "))
        assertFalse(service.verifyCredential("password", "private test"))
        assertTrue(service.verifySecretPhrase("a private phrase"))
    }
    @Test fun legacyProofUpgradesOneHashFieldWithoutChangingSalt() = runBlocking {
        val store = Store(); val key = legacy(store, "pin", "1357"); val before = store.values.toMap()
        val service = LocalLockService(store)
        assertTrue(service.hasCredentialForSetup("pin"))
        assertFalse(service.verifyCredential("pin", "2468")); assertEquals(before[key], store.values[key])
        assertTrue(service.verifyCredential("pin", "1357"))
        assertTrue(store.values[key]!!.startsWith("v2:600000:"))
        assertEquals(before[key.removeSuffix("hash") + "salt"], store.values[key.removeSuffix("hash") + "salt"])
        assertTrue(service.verifyCredential("pin", "1357"))
    }
    @Test fun upgradeWriteFailureKeepsLegacyCredentialUsableAndRetriesLater() = runBlocking {
        val store = Store(); val key = legacy(store, "password", "legacy password"); val original = store.values[key]
        val service = LocalLockService(store); store.failUpgrade = true
        assertTrue(service.verifyCredential("password", "legacy password")); assertEquals(original, store.values[key])
        assertEquals(0, service.getFailedAttempts())
        store.failUpgrade = false
        assertTrue(service.verifyCredential("password", "legacy password")); assertTrue(store.values[key]!!.startsWith("v2:600000:"))
    }
    @Test fun phraseLegacyCompatibilityNeedsNoLengthHintAndUsesIndependentThrottle() = runBlocking {
        val store = Store(); val key = legacy(store, "secret_phrase", "a private phrase")
        val service = LocalLockService(store)
        assertTrue(service.verifySecretPhrase("a private phrase")); assertTrue(store.values[key]!!.startsWith("v2:600000:"))
        repeat(5) { assertFalse(service.verifySecretPhrase("ordinary search")) }
        assertTrue(service.getSecretPhraseRemainingCooldownSeconds() > 0); assertEquals(0, service.getRemainingCooldownSeconds())
        assertFalse(store.values.keys.any { it.endsWith("secret_phrase.length") })
    }
    @Test fun malformedOrUnboundedFormatsFailClosedWithoutReplacingStoredData() = runBlocking {
        val store = Store(); val key = legacy(store, "pin", "1357"); val service = LocalLockService(store)
        for (invalid in listOf("v2:2147483647:AAAA", "v3:600000:AAAA", "v2:0:AAAA", "v2:600000:bad!")) {
            store.values[key] = invalid
            assertFalse(service.verifyCredential("pin", "1357"))
            assertTrue(runCatching { service.hasCredentialForSetup("pin") }.isFailure)
            assertEquals(invalid, store.values[key])
        }
    }
    @Test fun upgradeAndNewEnrollmentSerializeSoOldProofCannotOverwriteNewSecret() = runBlocking {
        val store = Store(); legacy(store, "pin", "1357")
        store.entered = CompletableDeferred(); store.release = CompletableDeferred()
        val service = LocalLockService(store)
        val proof = async { service.verifyCredential("pin", "1357") }; store.entered!!.await()
        val replacement = async { service.setCredential("pin", "2468", 4) }; yield()
        assertFalse(replacement.isCompleted)
        store.release!!.complete(Unit)
        assertTrue(proof.await()); replacement.await()
        assertTrue(service.verifyCredential("pin", "2468")); assertFalse(service.verifyCredential("pin", "1357"))
    }
    @Test fun queuedEnrollmentCannotAdoptAnAccountThatChangedWhileWaiting() = runBlocking {
        val store = Store(); legacy(store, "pin", "1357")
        store.entered = CompletableDeferred(); store.release = CompletableDeferred()
        val service = LocalLockService(store)
        val proof = async { service.verifyCredential("pin", "1357") }; store.entered!!.await()
        val replacement = async { runCatching { service.setCredential("pin", "2468", 4) } }; yield()
        service.setAccountNamespace("new-owner")
        store.release!!.complete(Unit)
        assertFalse(proof.await()); assertTrue(replacement.await().isFailure)
        assertFalse(service.hasCredential("pin"))
    }

}
