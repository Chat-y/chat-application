package com.example.chat

import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.parseApiUnreadCount
import org.junit.Assert.*
import org.junit.Test

/** Unrun source regression fixtures; no Android/runtime proof. */
class RestUnreadCountRegressionTest {
    @Test fun roomDirectoryCarriesUnreadIntoConversation() {
        assertEquals(7, Conversation.fromApi(mapOf("id" to "room", "unread_count" to 7)).unreadCount)
        assertEquals(4, Conversation.fromApi(mapOf("id" to "room", "unread_count" to "4")).unreadCount)
    }
    @Test fun unreadRejectsMalformedAndClampsOverflow() {
        listOf(null, false, "oops", -3, 1.5, Double.NaN).forEach { assertEquals(0, parseApiUnreadCount(it)) }
        assertEquals(Int.MAX_VALUE, parseApiUnreadCount(Long.MAX_VALUE))
        assertEquals(0, parseApiUnreadCount(Long.MIN_VALUE))
    }
}
