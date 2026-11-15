package com.example.chat

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Source wiring checks only. These do not prove FCM delivery or Android notification display. */
class MessageIngressWiringTest {
    private fun backend(): String {
        val root = if (File("app/src/main").exists()) File("app/src/main") else File("src/main")
        return File(root, "kotlin/com/example/chat/data/api/ApiBackendService.kt").readText()
    }

    @Test fun incomingPushDoesNotWaitForOutgoingQueue() {
        val code = backend().substringAfter("suspend fun syncFromPushNudge(").substringBefore("/** Network regained")
        val receive = code.indexOf("loadConversationsForScope(owner, incoming)")
        val send = code.indexOf("flushOutbox()")
        assertTrue(receive >= 0)
        assertTrue(send > code.indexOf("emitIncomingMessage(owner, msg"))
        assertTrue(code.substringBefore("loadConversationsForScope(owner, incoming)").indexOf("flushOutbox()") < 0)
        assertTrue(code.contains("chatScope.launch { flushOutbox() }"))
    }

    @Test fun restLoadsUseTheSameNotificationAdmissionAsPushAndSocket() {
        val code = backend()
        val rest = code.substringAfter("private fun applyLoadedMessages(").substringBefore("/**")
        assertTrue(rest.contains("findNewIncomingMessagesForPush"))
        assertTrue(rest.contains("emitIncomingMessage(expected, it"))
        assertTrue(code.contains("if (isNewToList) emitIncomingMessage(expected, normalized"))
        assertTrue(code.contains("emitIncomingMessage(owner, msg"))
        val admission = code.substringAfter("private fun emitIncomingMessage(").substringBefore("// Process lifecycle")
        assertTrue(admission.contains("withAuthScope(owner) { admitted = notifiedPushMessageIds.add(message.id) }"))
        assertTrue(admission.contains("isConversationMuted(message.conversationId)"))
        assertTrue(admission.contains("scoped(owner, message"))
    }

    @Test fun socketReconnectIncludesRoomsThatAreNotOpen() {
        val reconnect = backend().substringAfter("val wasDown = socketWasDisconnected").substringBefore("override fun onDisconnected")
        assertTrue(reconnect.contains("syncFromPushNudge(connectedOwner)"))
    }

    @Test fun pushFollowsOnlyTheGuardedRefreshResult() {
        val code = backend()
        assertTrue(code.contains("val owner = ensureFreshToken(expected) ?: return@withLock"))
        val refresh = code.substringAfter("private suspend fun performRefresh(")
        assertTrue(refresh.contains("if (authSnapshot() !== expected) return SessionRefreshOutcome.UNAVAILABLE"))
        assertTrue(refresh.contains("val adopted = synchronized(authScopeLock)"))
        assertTrue(refresh.contains("onRefreshed?.invoke(authSnapshot())"))
    }
}
