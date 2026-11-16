package com.example.chat

import com.example.chat.data.services.ChatyNotificationService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatyNotificationServiceTest {
    @Test
    fun activityFeedIsNewestFirstBoundedAndClearable() {
        val service = ChatyNotificationService()
        repeat(105) { index ->
            service.triggerEventNotification("Event $index", "Body $index")
        }

        val entries = service.notifications
        assertEquals(100, entries.size)
        assertEquals("Event 104", entries.first().title)
        assertEquals("Event 5", entries.last().title)
        assertEquals(100, entries.map { it.id }.distinct().size)

        service.clearAll()
        assertTrue(service.notifications.isEmpty())
        assertEquals(null, service.latest)
    }

    @Test
    fun listenersReceiveChangesAndCanBeRemoved() {
        val service = ChatyNotificationService()
        var changes = 0
        val listener: () -> Unit = { changes++ }
        service.addListener(listener)
        service.triggerEventNotification("One", "First")
        assertEquals(1, changes)

        service.removeListener(listener)
        service.triggerEventNotification("Two", "Second")
        assertEquals(1, changes)
    }

    @Test
    fun eachEventGetsAUniqueIdentity() {
        val service = ChatyNotificationService()
        service.triggerEventNotification("One", "Same body")
        service.triggerEventNotification("One", "Same body")
        val ids = service.notifications.map { it.id }
        assertEquals(2, ids.distinct().size)
        assertNotEquals(ids[0], ids[1])
    }

    @Test
    fun clearReviewedEntriesPreservesNewArrivals() {
        val service = ChatyNotificationService()
        service.triggerEventNotification("Reviewed", "Before confirmation")
        val reviewedIds = service.notifications.map { it.id }.toSet()
        service.triggerEventNotification("New", "After confirmation opened")
        service.clear(reviewedIds)
        assertEquals(listOf("New"), service.notifications.map { it.title })
    }

    @Test
    fun clearUnknownOrEmptyIdsDoesNotNotify() {
        val service = ChatyNotificationService()
        var changes = 0
        service.addListener { changes++ }
        service.triggerEventNotification("Keep", "Body")
        service.clear(emptySet())
        service.clear(setOf("unknown"))
        assertEquals(1, changes)
        assertEquals(1, service.notifications.size)
    }

    @Test
    fun clearReviewedIdsIsSafeWithConcurrentArrival() {
        val service = ChatyNotificationService()
        repeat(20) { service.triggerEventNotification("Old $it", "Body") }
        val reviewedIds = service.notifications.map { it.id }.toSet()
        val writer = Thread { repeat(20) { service.triggerEventNotification("New $it", "Body") } }
        writer.start()
        service.clear(reviewedIds)
        writer.join()
        assertEquals(20, service.notifications.size)
        assertTrue(service.notifications.none { it.id in reviewedIds })
    }
}
