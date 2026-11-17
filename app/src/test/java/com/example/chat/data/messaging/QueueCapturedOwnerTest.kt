package com.example.chat.data.messaging

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** UNRUN mock ownership fixtures; no native MLS/network/device proof. */
class QueueCapturedOwnerTest {
    private class Cache : SnapshotCacheStore {
        val data = ConcurrentHashMap<String, Any>()
        @Volatile var afterRead: (() -> Unit)? = null
        override suspend fun readJson(userId: String, scope: String): Any? {
            val value = data[userId]; afterRead?.invoke(); return value
        }
        override suspend fun writeJson(userId: String, scope: String, value: Any?) {
            if (value == null) data.remove(userId) else data[userId] = value
        }
    }
    @Test fun switchAfterFirstDeliveryDoesNotRetargetSecondOrRemoveOriginalQueue() = runBlocking {
        val cache = Cache()
        val store = PendingSecureSendStore(cache)
        val firstOwner = Any()
        val owner = AtomicReference(firstOwner)
        val user = AtomicReference("a")
        store.put("a", PendingSecureSend("one", "room", "text", "first", emptyMap(), Instant.EPOCH))
        store.put("a", PendingSecureSend("two", "room", "text", "second", emptyMap(), Instant.EPOCH.plusSeconds(1)))
        val delivered = mutableListOf<String>()
        val queue = OutgoingMessageQueueEngine(
            store, null, deliver = { error("Ambient delivery must never run") },
            currentUserId = { user.get() }, ownerSnapshot = { owner.get() },
            ownerIsCurrent = { it === owner.get() },
            deliverForOwner = { send, captured ->
                assertSame(firstOwner, captured)
                delivered.add(send.clientMessageId)
                owner.set(Any()); user.set("b")
            },
        )
        queue.processQueue()
        assertEquals(listOf("one"), delivered)
        assertEquals(listOf("one", "two"), store.read("a").map { it.clientMessageId })
        assertTrue(store.read("b").isEmpty())
    }

    @Test fun staleOwnerCountIsBestEffortAndDoesNotNotify() = runBlocking {
        val a = Any(); val b = Any(); val notifications = java.util.concurrent.atomic.AtomicInteger()
        val queue = OutgoingMessageQueueEngine(
            PendingSecureSendStore(Cache()), null, deliver = { error("No send") }, currentUserId = { "a" },
            notifyChanged = { notifications.incrementAndGet() }, ownerSnapshot = { a }, ownerIsCurrent = { it === b },
        )
        queue.updateQueuedCount()
        assertEquals(0, notifications.get())
    }
    @Test fun staleOwnerManualRetryReturnsFalseInsteadOfThrowing() = runBlocking {
        val queue = OutgoingMessageQueueEngine(
            PendingSecureSendStore(Cache()), null, deliver = { error("No send") }, currentUserId = { "a" },
            ownerSnapshot = { Any() }, ownerIsCurrent = { false },
        )
        assertFalse(queue.retryMessage("one"))
    }
    @Test fun switchDuringReadCannotDeliverUnderNewGenerationWithSameUid() = runBlocking {
        val cache = Cache(); val store = PendingSecureSendStore(cache)
        store.put("same-uid", PendingSecureSend("one", "room", "text", "private", emptyMap(), Instant.EPOCH))
        val owner = AtomicReference(Any())
        val deliveries = java.util.concurrent.atomic.AtomicInteger()
        val caller = Thread.currentThread()
        val queue = OutgoingMessageQueueEngine(
            store, null, deliver = { error("No ambient send") }, currentUserId = { "same-uid" },
            ownerSnapshot = { owner.get() }, ownerIsCurrent = { it === owner.get() },
            deliverForOwner = { _, _ -> deliveries.incrementAndGet(); Unit },
        )
        // The init count uses an IO thread; only this synchronous test retry switches here.
        cache.afterRead = { if (Thread.currentThread() === caller) owner.set(Any()) }
        assertFalse(queue.retryMessage("one"))
        cache.afterRead = null
        assertEquals(0, deliveries.get())
        assertEquals(listOf("one"), store.read("same-uid").map { it.clientMessageId })
    }
    @Test fun staleSameUidGenerationDoesNotDrainQueue() = runBlocking {
        val cache = Cache(); val store = PendingSecureSendStore(cache)
        store.put("same-uid", PendingSecureSend("one", "room", "text", "private", emptyMap(), Instant.EPOCH))
        val oldGeneration = Any(); val currentGeneration = Any()
        val queue = OutgoingMessageQueueEngine(
            store, null, deliver = { error("No send") }, currentUserId = { "same-uid" },
            ownerSnapshot = { oldGeneration }, ownerIsCurrent = { it === currentGeneration },
        )
        queue.processQueue()
        assertFalse(queue.retryMessage("one"))
        assertEquals(1, store.read("same-uid").size)
    }
}
