package com.example.chat.data.api

import com.example.chat.data.local.SnapshotCodec
import com.example.chat.data.messaging.SnapshotCacheStore
import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.MessageType
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Regression sources only. No test/build was run for the patch-only audit. */
class ApiSnapshotCacheBoundaryTest {
    private class Store : SnapshotCacheStore {
        var value: Any? = null
        var failure: Exception? = null
        override suspend fun readJson(userId: String, scope: String): Any? {
            failure?.let { throw it }
            return value
        }
        override suspend fun writeJson(userId: String, scope: String, value: Any?) {
            failure?.let { throw it }
            this.value = value
        }
    }

    private fun message(second: Int) = ChatMessage(
        id = "m$second", conversationId = "room", senderId = "peer",
        type = MessageType.text, text = "message",
        createdAt = Instant.ofEpochSecond(second.toLong()),
    )

    @Test fun `descending inputs retain chronologically newest 300 messages`() = runBlocking {
        val store = Store()
        val cache = ApiSnapshotCacheCoordinator(store)
        cache.persistMessages("account", "room", (400 downTo 1).map(::message))
        val messages = cache.readMessages("account", "room")!!
        assertEquals(300, messages.size)
        assertEquals("m101", messages.first().id)
        assertEquals("m400", messages.last().id)
    }

    @Test fun `legacy reads remain bounded to newest 300 messages`() = runBlocking {
        val store = Store()
        store.value = (400 downTo 1).map { SnapshotCodec.chatMessageToJson(message(it)) }
        val messages = ApiSnapshotCacheCoordinator(store).readMessages("account", "room")!!
        assertEquals(300, messages.size)
        assertEquals("m101", messages.first().id)
        assertEquals("m400", messages.last().id)
    }

    @Test fun `all read and write entry points propagate store cancellation`() = runBlocking {
        val store = Store().apply { failure = CancellationException("cancelled") }
        val cache = ApiSnapshotCacheCoordinator(store)
        suspend fun expectCancellation(action: suspend () -> Unit) {
            var caught = false
            try { action() } catch (_: CancellationException) { caught = true }
            assertTrue(caught)
        }
        expectCancellation { cache.readConversations("account") }
        expectCancellation { cache.readUsers("account") }
        expectCancellation { cache.readMessages("account", "room") }
        expectCancellation { cache.persistConversations("account", emptyList()) }
        expectCancellation { cache.persistUsers("account", emptyList()) }
        expectCancellation { cache.persistMessages("account", "room", emptyList()) }
    }

    @Test fun `ordinary store errors remain cache misses and best effort writes`() = runBlocking {
        val store = Store().apply { failure = IllegalStateException("disk unavailable") }
        val cache = ApiSnapshotCacheCoordinator(store)
        assertNull(cache.readConversations("account"))
        assertNull(cache.readUsers("account"))
        assertNull(cache.readMessages("account", "room"))
        cache.persistConversations("account", emptyList())
        cache.persistUsers("account", emptyList())
        cache.persistMessages("account", "room", emptyList())
        assertNull(store.value)
    }
}
