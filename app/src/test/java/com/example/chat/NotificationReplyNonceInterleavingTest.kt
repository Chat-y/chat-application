package com.example.chat

import com.example.chat.notifications.ChatyActionRegistry
import com.example.chat.notifications.ChatyNotificationPolicy
import java.util.concurrent.ConcurrentHashMap
import org.junit.Assert.*
import org.junit.Test

class NotificationReplyNonceInterleavingTest {
    @Test fun deliveryResolvedBeforeFirstSendCannotReplayAfterSlotIsReleased() {
        val owner = Any()
        val registry = ChatyActionRegistry<Any>()
        val nonce = registry.register(owner, ChatyNotificationPolicy.ACTION_REPLY, conversationId = "room")
        val first = registry.resolve(nonce)!!
        // Second receiver has resolved the same entry, then is paused before acquiring the slot.
        val pausedSecond = registry.resolve(nonce)!!
        val inFlight = ConcurrentHashMap.newKeySet<String>()
        var sends = 0
        assertTrue(inFlight.add(nonce))
        assertTrue(registry.isCurrent(nonce, first))
        sends++
        registry.dropNonce(nonce) // durable queue accepted first reply
        inFlight.remove(nonce)
        // Resume the second receiver after the first has completed and released the slot.
        assertTrue(inFlight.add(nonce))
        if (registry.isCurrent(nonce, pausedSecond)) sends++
        inFlight.remove(nonce)
        assertEquals(1, sends)
        assertNull(registry.resolve(nonce))
    }

    @Test fun failedSendKeepsTheSameNonceRetryableButReplacementDoesNotMatch() {
        val owner = Any()
        val registry = ChatyActionRegistry<Any>()
        val nonce = registry.register(owner, ChatyNotificationPolicy.ACTION_REPLY, conversationId = "room")
        val entry = registry.resolve(nonce)!!
        assertTrue(registry.isCurrent(nonce, entry)) // failed send never retires nonce
        registry.dropNonce(nonce)
        val replacement = registry.register(owner, ChatyNotificationPolicy.ACTION_REPLY, conversationId = "room")
        assertFalse(registry.isCurrent(replacement, entry))
        assertTrue(registry.isCurrent(replacement, registry.resolve(replacement)!!))
    }
}
