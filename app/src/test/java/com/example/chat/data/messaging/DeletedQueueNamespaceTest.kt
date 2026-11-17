package com.example.chat.data.messaging

import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** UNRUN strict-queue deletion policy fixtures, not native-key shutdown proof. */
class DeletedQueueNamespaceTest {
    private class Cache : SnapshotCacheStore {
        val values = mutableMapOf<String, Any?>()
        override suspend fun readJson(userId: String, scope: String): Any? = values["$userId:$scope"]
        override suspend fun writeJson(userId: String, scope: String, value: Any?) { values["$userId:$scope"] = value }
        override suspend fun readJsonStrict(userId: String, scope: String) = readJson(userId, scope)
        override suspend fun writeJsonStrict(userId: String, scope: String, value: Any?) = writeJson(userId, scope, value)
    }
    private fun item() = PendingSecureSend("id", "room", "text", "private", emptyMap(), Instant.EPOCH)
    @Test fun capturedLatePutCannotRecreateDeletedNamespaceOrTouchOtherBackend() = runBlocking {
        val cache = Cache(); val store = PendingSecureSendStore(cache, true)
        store.put("uid", item(), "a"); store.put("uid", item(), "b")
        val ready = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val late = async {
            val capturedNamespace = "a"; ready.complete(Unit); release.await()
            var failed = false
            try { store.put("uid", item(), capturedNamespace) } catch (_: IllegalStateException) { failed = true }
            failed
        }
        ready.await(); store.retireNamespace("uid", "a"); release.complete(Unit)
        assertTrue(late.await())
        assertEquals(1, store.read("uid", "b").size)
        assertEquals(emptyList<Any?>(), cache.values["a:pending_secure_sends"])
    }
    @Test fun tombstoneSurvivesTargetSnapshotDeletionAndStoreRecreation() = runBlocking {
        val cache = Cache(); val store = PendingSecureSendStore(cache, true)
        store.retireNamespace("uid", "a")
        cache.values.remove("a:pending_secure_sends")
        val restarted = PendingSecureSendStore(cache, true)
        var failed = false
        try { restarted.put("uid", item(), "a") } catch (_: IllegalStateException) { failed = true }
        assertTrue(failed)
        assertFalse(cache.values.containsKey("a:pending_secure_sends"))
    }
    @Test fun retirementWaitsForAdmittedDeliveryThenRejectsAlreadyReadRetry() = runBlocking {
        val cache = Cache(); val store = PendingSecureSendStore(cache, true)
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val delivery = async { store.withActiveNamespace("uid", "a") { entered.complete(Unit); release.await() } }
        entered.await()
        val retirement = async { store.retireNamespace("uid", "a") }
        kotlinx.coroutines.yield()
        assertFalse(retirement.isCompleted)
        release.complete(Unit); delivery.await(); retirement.await()
        var called = false; var rejected = false
        try { store.withActiveNamespace("uid", "a") { called = true } } catch (_: IllegalStateException) { rejected = true }
        assertTrue(rejected); assertFalse(called)
    }

    @Test fun pendingQuarantineDoesNotDiscardUnsentPayload() = runBlocking {
        val cache = Cache(); val store = PendingSecureSendStore(cache, true)
        store.put("uid", item(), "a")
        store.quarantineNamespace("uid", "a")
        val retained = cache.values["a:pending_secure_sends"] as List<*>
        assertEquals(1, retained.size)
        var rejected = false
        try { store.read("uid", "a") } catch (_: IllegalStateException) { rejected = true }
        assertTrue(rejected)
    }

    @Test fun offStorageStillUsesHashDeletionIdentityAndRetainsLegacyPayload() = runBlocking {
        val cache = Cache(); val store = PendingSecureSendStore(cache, strictStorage = false, deletionPolicy = true)
        store.put("uid", item(), "backend-hash")
        assertTrue(cache.values.containsKey("uid:pending_secure_sends"))
        store.quarantineNamespace("uid", "backend-hash")
        var rejected = false
        try { store.read("uid", "backend-hash") } catch (_: IllegalStateException) { rejected = true }
        assertTrue(rejected)
        assertEquals(1, (cache.values["uid:pending_secure_sends"] as List<*>).size)
    }

}
