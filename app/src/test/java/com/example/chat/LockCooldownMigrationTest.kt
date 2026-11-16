package com.example.chat.data.services

import com.example.chat.data.e2ee.SecureKeyValueStore
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class LockCooldownMigrationTest {
    private class Store : SecureKeyValueStore {
        val data = mutableMapOf<String, String>()
        var failRead = false
        var failWrite = false
        override suspend fun read(key: String): String? { check(!failRead); return data[key] }
        override suspend fun write(key: String, value: String) { check(!failWrite); data[key] = value }
        override suspend fun delete(key: String) { data.remove(key) }
    }
    private class Clock : LockCooldownClock {
        var elapsed = 10_000L
        var boot = 4L
        var wall = 1_000_000L
        var unavailable = false
        override fun read(): LockCooldownInstant { check(!unavailable); return LockCooldownInstant(elapsed, boot) }
    }
    private fun service(s: Store, c: Clock) = LocalLockService(s, cooldownClock = c, now = { c.wall })
    private val prefix = "chaty.local_lock.v2"
    private suspend fun fiveFailures(l: LocalLockService) { repeat(5) { l.recordFailedAttempt() } }

    @Test fun wallClockJumpsCannotChangeMonotonicCountdown() = runBlocking {
        val s = Store(); val c = Clock(); val l = service(s, c); fiveFailures(l)
        c.wall = Long.MAX_VALUE; assertEquals(30, l.getRemainingCooldownSeconds())
        c.wall = 1; c.elapsed += 1_001; assertEquals(29, l.getRemainingCooldownSeconds())
        c.elapsed += 29_000; assertEquals(0, l.getRemainingCooldownSeconds())
    }
    @Test fun restartSameBootPreservesOnlyRemainingTime() = runBlocking {
        val s = Store(); val c = Clock(); fiveFailures(service(s, c)); c.elapsed += 12_000
        assertEquals(18, service(s, c).getRemainingCooldownSeconds())
    }
    @Test fun rebootRestartsBoundedTierOnlyOnce() = runBlocking {
        val s = Store(); val c = Clock(); fiveFailures(service(s, c)); c.boot++; c.elapsed = 3
        assertEquals(30, service(s, c).getRemainingCooldownSeconds())
        c.elapsed += 7_000; assertEquals(23, service(s, c).getRemainingCooldownSeconds())
    }
    @Test fun legacyDeadlineRestartsTierEvenIfCivilDeadlineAppearsExpired() = runBlocking {
        val s = Store(); val c = Clock(); s.data["$prefix.failed_attempts"] = "10"; s.data["$prefix.lockout_until"] = "1"
        assertEquals(60, service(s, c).getRemainingCooldownSeconds())
        assertTrue(s.data["$prefix.lockout_until"]!!.startsWith("v3:4:"))
        c.elapsed += 60_000; assertEquals(0, service(s, c).getRemainingCooldownSeconds())
    }
    @Test fun phraseMigrationAndResetRemainIndependent() = runBlocking {
        val s = Store(); val c = Clock(); val l = service(s, c)
        s.data["$prefix.secret_phrase.failed_attempts"] = "15"; s.data["$prefix.secret_phrase.lockout_until"] = "900000"
        assertEquals(300, l.getSecretPhraseRemainingCooldownSeconds()); assertEquals(0, l.getRemainingCooldownSeconds())
        l.clearSecretPhrase(); assertEquals(0, l.getSecretPhraseRemainingCooldownSeconds())
    }
    @Test fun corruptedOrUnavailableMetadataNeverMeansUnlocked() = runBlocking {
        val s = Store(); val c = Clock(); val l = service(s, c)
        c.unavailable = true; assertEquals(300, l.getRemainingCooldownSeconds()); c.unavailable = false
        s.data["$prefix.lockout_until"] = "v3:4:999999999:999999"; assertEquals(300, l.getRemainingCooldownSeconds())
        s.failRead = true; assertEquals(300, l.getRemainingCooldownSeconds())
    }
    @Test fun failedMigrationKeepsLegacyAndFailsClosed() = runBlocking {
        val s = Store(); val c = Clock(); val l = service(s, c)
        s.data["$prefix.lockout_until"] = "1"; s.data["$prefix.failed_attempts"] = "5"; s.failWrite = true
        assertEquals(300, l.getRemainingCooldownSeconds()); assertEquals("1", s.data["$prefix.lockout_until"])
        s.failWrite = false; assertEquals(30, l.getRemainingCooldownSeconds())
    }
    @Test fun monotonicRollbackRestartsOneBoundedTier() = runBlocking {
        val s = Store(); val c = Clock(); val l = service(s, c); fiveFailures(l)
        c.elapsed = 0; assertEquals(30, l.getRemainingCooldownSeconds())
        c.elapsed = 30_000; assertEquals(0, l.getRemainingCooldownSeconds())
    }
    @Test fun accountAndResetIsolation() = runBlocking {
        val s = Store(); val c = Clock(); val l = service(s, c); l.setAccountNamespace("first"); fiveFailures(l)
        l.setAccountNamespace("second"); assertEquals(0, l.getRemainingCooldownSeconds())
        l.setAccountNamespace("first"); assertEquals(30, l.getRemainingCooldownSeconds()); l.resetFailedAttempts()
        assertEquals(0, l.getRemainingCooldownSeconds()); assertFalse(s.data.keys.any { it.endsWith("lockout_until") })
    }
    @Test fun queuedCooldownMutationCannotMoveIntoAnotherAccount() = runBlocking {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val values = mutableMapOf<String, String>()
        val store = object : SecureKeyValueStore {
            override suspend fun read(key: String): String? {
                if (key.endsWith(".first.lockout_until")) { entered.complete(Unit); release.await() }
                return values[key]
            }
            override suspend fun write(key: String, value: String) { values[key] = value }
            override suspend fun delete(key: String) { values.remove(key) }
        }
        val lock = LocalLockService(store, cooldownClock = Clock())
        lock.setAccountNamespace("first")
        val reader = async(start = CoroutineStart.UNDISPATCHED) { lock.getRemainingCooldownSeconds() }
        entered.await()
        val writer = async(start = CoroutineStart.UNDISPATCHED) { runCatching { lock.recordFailedAttempt() } }
        lock.setAccountNamespace("second"); release.complete(Unit)
        assertEquals(300, reader.await()); assertTrue(writer.await().isFailure)
        assertTrue(values.isEmpty())
    }

}
