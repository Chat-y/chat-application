package com.example.chat

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** UNRUN structural guard, not notification/MLS/queue runtime proof. */
class EncryptedInlineReplyRoutingSourceTest {
    private fun source(path: String): String {
        val local = File(path)
        return (if (local.exists()) local else File("app", path)).readText()
    }
    @Test fun notificationUsesInjectedOwnerAndStableNonceInsteadOfRegularOnlySend() {
        val source = source("src/main/java/com/example/chat/notifications/ChatyNotificationCoordinator.kt")
        assertTrue(source.contains("if (replySender(account, id, text, replyNonce))"))
        assertTrue(source.contains("if (!registry.isCurrent(replyNonce, e)) return"))
    }
    @Test fun secureBranchKeepsExactOwnerAndNonceAndHasNoRestCatchFallback() {
        val source = source("src/main/kotlin/com/example/chat/ServiceLocator.kt")
        val lane = source.substringAfter("replySender = { owner, conversationId, text, nonce ->").substringBefore("val appLock:")
        assertTrue(lane.contains("conversationsRepository.isSecureChannel(conversationId)"))
        assertTrue(lane.contains("java.util.UUID.nameUUIDFromBytes"))
        assertTrue(lane.contains("expectedScope = owner"))
        assertTrue(lane.contains("chaty-notification-reply-v1:"))
        assertTrue(lane.contains("SecureSendPendingException"))
        assertEquals(1, Regex("apiBackend.sendMessageForScope").findAll(lane).count())
        assertTrue(lane.contains("} else {"))
    }
}
