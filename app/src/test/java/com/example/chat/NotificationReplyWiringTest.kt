package com.example.chat

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class NotificationReplyWiringTest {
    private val root get() = if (File("app/src/main").exists()) File("app/src/main") else File("src/main")
    @Test fun explicitActionReceiverIsRegisteredAndNotExported() {
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(File(root,"AndroidManifest.xml"))
        val nodes = doc.getElementsByTagName("receiver"); var found = false
        for (i in 0 until nodes.length) {
            val n = nodes.item(i) as org.w3c.dom.Element
            if (n.getAttributeNS("http://schemas.android.com/apk/res/android", "name") == "com.example.chat.notifications.ChatyNotificationActionReceiver") {
                assertEquals("false", n.getAttributeNS("http://schemas.android.com/apk/res/android", "exported")); found = true
            }
        }
        assertTrue(found)
    }
    @Test fun replyIsMutableOnOlderAndroidAndKeepsRemoteInputKey() {
        val s = File(root,"java/com/example/chat/notifications/ChatyNotificationPoster.kt").readText()
        assertTrue(s.contains("!mutable -> PendingIntent.FLAG_IMMUTABLE"))
        assertTrue(s.contains("Build.VERSION.SDK_INT >= 31 -> PendingIntent.FLAG_MUTABLE"))
        assertTrue(s.contains("else -> 0 // Before Android12"))
        assertTrue(s.contains("RemoteInput.Builder(ChatyNotificationPolicy.KEY_REPLY_TEXT)"))
    }
    @Test fun repliesAreDurablyQueuedUnderOriginalOwnerAndCancelOnlyThatOwner() {
        val s = File(root,"kotlin/com/example/chat/data/api/ApiBackendService.kt").readText()
        val reply = s.substringAfter("suspend fun sendMessageForScope(").substringBefore("private suspend fun sendMessageForScopeLocked")
        assertTrue(reply.contains("sendMessage(conversationId, text, expectedScope = expected, onDurablyQueued = { queuedId = it })"))
        assertTrue(reply.contains("it.tempId == queuedId"))
        assertFalse(reply.contains("it.message.text == text"))
        val c = File(root,"java/com/example/chat/notifications/ChatyNotificationCoordinator.kt").readText()
        // Prior source assertion checked the single-line version before nonce single-flight protection.
        assertTrue(c.contains("if (api.sendMessageForScope(account, id, text)) {"))
        assertTrue(c.contains("lane.cancelMessageFor(account, id)"))
    }
    @Test fun newSendsDoNotWaitForSchedulerOrOtherConversations() {
        val s = File(root,"kotlin/com/example/chat/data/api/ApiBackendService.kt").readText()
        assertTrue(s.contains("chatScope.launch { scheduleOutboxRecovery(expected) }"))
        assertTrue(s.contains("flushConversationOutbox(expected, conversationId)"))
        assertTrue(s.contains("kotlinx.coroutines.sync.Semaphore(4)"))
        assertTrue(s.contains("messageCapabilities.supports(expectedScope"))
    }
}
