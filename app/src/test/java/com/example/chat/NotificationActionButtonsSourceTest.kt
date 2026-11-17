package com.example.chat

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Source fixtures only; Android acceptance, lock and permission UI require device tests. */
class NotificationActionButtonsSourceTest {
    private val root get() = if (File("app/src/main").exists()) File("app/src/main") else File("src/main")
    @Test fun acceptLaunchesActivityAndReusesPermissionOwnedInAppFlow() {
        val poster = File(root, "java/com/example/chat/notifications/ChatyNotificationPoster.kt").readText()
        assertTrue(poster.contains("action = ChatyNotificationPolicy.ACTION_ACCEPT"))
        assertTrue(poster.contains("PendingIntent.getActivity(context, nonce.hashCode()"))
        val coordinator = File(root, "java/com/example/chat/notifications/ChatyNotificationCoordinator.kt").readText()
        val accept = coordinator.substringAfter("fun requestNotificationAccept(").substringBefore("private fun callOk")
        assertTrue(accept.contains("api.authSnapshot() !== entry.scope"))
        assertTrue(accept.contains("appLock.lockRequired.value || prefs.security.isAppLockEnabled"))
        assertTrue(accept.contains("PendingCallAction(id, answer = true, owner = entry.scope)"))
        assertFalse(accept.contains("acceptCall()"))
    }
    @Test fun replyActionsAreSingleFlightAndRetiredOnlyAfterQueueAcceptance() {
        val coordinator = File(root, "java/com/example/chat/notifications/ChatyNotificationCoordinator.kt").readText()
        assertTrue(coordinator.contains("if (!repliesInFlight.add(replyNonce)) return"))
        assertTrue(coordinator.contains("registry.dropNonce(replyNonce)"))
        assertTrue(coordinator.contains("finally { repliesInFlight.remove(replyNonce) }"))
        val receiver = File(root, "java/com/example/chat/notifications/ChatyNotificationActionReceiver.kt").readText()
        assertTrue(receiver.contains("finally { pending.finish() }"))
        assertTrue(receiver.contains("SupervisorJob() + Dispatchers.IO"))
    }
}
