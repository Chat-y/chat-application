package com.example.chat.data.messaging

import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** UNRUN namespace/race fixtures, not device or crypto proof. */
class PendingQueueCapturedNamespaceTest {
    private class Cache : SnapshotCacheStore {
        val values = mutableMapOf<String, Any?>()
        var afterRead: (() -> Unit)? = null
        override suspend fun readJson(userId: String, scope: String): Any? = values[userId]
        override suspend fun writeJson(userId: String, scope: String, value: Any?) { values[userId] = value }
        override suspend fun readJsonStrict(userId: String, scope: String): Any? {
            val value = values[userId]; afterRead?.invoke(); return value
        }
        override suspend fun writeJsonStrict(userId: String, scope: String, value: Any?) { values[userId] = value }
    }
    private fun item() = PendingSecureSend("id", "room", "text", "private", emptyMap(), Instant.EPOCH)
    @Test fun namespaceCapturedBeforeSuspensionSurvivesSameUidBackendSwitch() = runBlocking {
        val cache = Cache(); val store = PendingSecureSendStore(cache, true)
        var ambient = "backend-a"
        val captured = ambient
        cache.afterRead = { ambient = "backend-b" }
        store.put("same-uid", item(), captured)
        assertEquals("backend-b", ambient)
        assertTrue(cache.values.containsKey("backend-a")); assertFalse(cache.values.containsKey("backend-b"))
        cache.afterRead = null
        store.put("same-uid", item().copy(text = "other"), "backend-b")
        store.remove("same-uid", "id", captured)
        assertEquals("other", store.read("same-uid", "backend-b").single().text)
    }
    @Test fun unknownLegacyOwnerBlocksWithoutChangingEitherQueue() = runBlocking {
        val cache = Cache(); val legacy = listOf(item().toJson()); cache.values["uid"] = legacy
        val store = PendingSecureSendStore(cache, true)
        var failed = false
        try { store.put("uid", item(), "namespace") } catch (_: IllegalStateException) { failed = true }
        assertTrue(failed); assertEquals(legacy, cache.values["uid"])
        assertFalse(cache.values.containsKey("namespace"))
    }
    @Test fun offModeIgnoresNamespaceAndKeepsOriginalStorage() = runBlocking {
        val cache = Cache(); val store = PendingSecureSendStore(cache, false)
        store.put("uid", item(), "ignored")
        assertTrue(cache.values.containsKey("uid")); assertFalse(cache.values.containsKey("ignored"))
        assertEquals(1, store.read("uid", "ignored").size)
    }
}
