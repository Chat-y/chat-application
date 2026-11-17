package com.example.chat.data.messaging

import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** UNRUN policy fixtures, not Keystore/crypto/disk/device proof. */
class PendingStoreStrictDurabilityTest {
    private class Cache : SnapshotCacheStore {
        var value: Any? = null
        var failRead = false
        var failWrite = false
        var lenientReads = 0
        var lenientWrites = 0
        override suspend fun readJson(userId: String, scope: String): Any? {
            lenientReads++
            return value
        }
        override suspend fun writeJson(userId: String, scope: String, value: Any?) {
            lenientWrites++
            this.value = value
        }
        override suspend fun readJsonStrict(userId: String, scope: String): Any? {
            check(!failRead) { "decrypt failed" }
            return value
        }
        override suspend fun writeJsonStrict(userId: String, scope: String, value: Any?) {
            check(!failWrite) { "disk full" }
            this.value = value
        }
    }
    private fun message(id: String) = PendingSecureSend(
        id, "conversation", "text", "private", emptyMap(), Instant.EPOCH,
    )
    private suspend fun mustFail(action: suspend () -> Unit) {
        var failed = false
        try { action() } catch (_: IllegalStateException) { failed = true }
        assertTrue("Must surface failure, never assume empty or queued success", failed)
    }
    @Test fun strictWriteFailurePreservesEarlierQueueAndNeverFallsBack() = runBlocking {
        val cache = Cache()
        val store = PendingSecureSendStore(cache, strictStorage = true)
        store.put("owner", message("first"))
        val prior = cache.value
        cache.failWrite = true
        mustFail { store.put("owner", message("second")) }
        assertEquals(prior, cache.value)
        assertEquals(listOf("first"), store.read("owner").map { it.clientMessageId })
        assertEquals(0, cache.lenientReads)
        assertEquals(0, cache.lenientWrites)
    }
    @Test fun strictDecryptFailureCannotBecomeAnEmptyQueue() = runBlocking {
        val cache = Cache().apply { failRead = true }
        val store = PendingSecureSendStore(cache, strictStorage = true)
        mustFail { store.read("owner"); Unit }
        mustFail { store.put("owner", message("new")) }
        assertEquals(0, cache.lenientReads)
        assertEquals(0, cache.lenientWrites)
    }
    @Test fun strictMalformedRowsCannotBeSilentlyDiscardedByPut() = runBlocking {
        for (bad in listOf<Any>(mapOf("wrong" to "shape"), listOf("bad row"), listOf(emptyMap<String, Any>()))) {
            val cache = Cache().apply { value = bad }
            val store = PendingSecureSendStore(cache, strictStorage = true)
            mustFail { store.put("owner", message("new")) }
            assertEquals(bad, cache.value)
            assertEquals(0, cache.lenientWrites)
        }
    }
    @Test fun strictMissingQueueIsEmptyAndRemovalUsesStrictWrite() = runBlocking {
        val cache = Cache()
        val store = PendingSecureSendStore(cache, strictStorage = true)
        assertTrue(store.read("owner").isEmpty())
        store.put("owner", message("first"))
        cache.failWrite = true
        mustFail { store.remove("owner", "first") }
        assertEquals(listOf("first"), store.read("owner").map { it.clientMessageId })
        assertEquals(0, cache.lenientWrites)
    }
    @Test fun defaultModeKeepsExistingLenientContract() = runBlocking {
        val cache = Cache()
        val store = PendingSecureSendStore(cache)
        store.put("owner", message("first"))
        assertEquals(listOf("first"), store.read("owner").map { it.clientMessageId })
        assertTrue(cache.lenientReads > 0)
        assertTrue(cache.lenientWrites > 0)
    }
}
