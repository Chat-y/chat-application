package com.example.chat.notifications

import com.example.chat.ServiceLocator
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Framework-created FCM entry point.
 *
 *  Two events only:
 *   - onNewToken: forward to the registrar (dual-backend registration,
 *     exactly the ported Dart lifecycle).
 *   - onMessageReceived: our nudges are content-free data messages
 *     ({type: "sync"}); hand them to the push-agnostic wake handler. Any
 *     message carrying a notification block or unexpected data is ignored -
 *     we never render push-provided content.
 */
class ChatyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        ServiceLocator.ensurePushReady(applicationContext)
        FcmRegistrarHolder.registrar?.onTokenReceived(token)
    }

    override fun onDeletedMessages() {
        ServiceLocator.ensurePushReady(applicationContext)
        ServiceLocator.recoverDeletedPushes(applicationContext)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val binding = message.data["account_binding"]?.takeIf { it.isNotBlank() }
        val type = message.data["type"]
        val callId = if (type == "incoming_call") message.data["call_id"]?.takeIf { it.isNotBlank() } else null

        // Persist continuation before any network work; OS may kill this process after the callback.
        if (binding != null && (type == "sync" || type == "incoming_call")) {
            AccountPushRecoveryWorker.enqueue(applicationContext, binding, callId,
                urgent = message.priority == RemoteMessage.PRIORITY_HIGH)
        }

        // When a visual notification payload is present, Google Play Services already
        // displayed the system notification banner directly.
        if (message.notification != null) return
        if (binding == null) return
        if (type != "sync" && type != "incoming_call") return

        ServiceLocator.ensurePushReady(applicationContext)
        val handler = PushWakeHolder.handler ?: return
        when (type) {
            "sync" -> runBlocking {
                withTimeoutOrNull(PUSH_WAKE_TIMEOUT_MS) {
                    runCatching { withContext(kotlinx.coroutines.Dispatchers.IO) { ServiceLocator.apiBackend.initialize() } }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                    handler.onAccountSyncNudgeAndWait(binding)
                }
            }
            "incoming_call" -> callId?.let { cid ->
                runBlocking {
                    withTimeoutOrNull(PUSH_WAKE_TIMEOUT_MS) {
                        // Same cold-process ensure-init as the sync branch; without it
                        // an incoming-call push is dropped on an uninitialized backend.
                        runCatching { withContext(kotlinx.coroutines.Dispatchers.IO) { ServiceLocator.apiBackend.initialize() } }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                        handler.onAccountIncomingCallNudgeAndWait(binding, cid)
                    }
                }
            }
        }
    }

    private companion object {
        const val PUSH_WAKE_TIMEOUT_MS = 12_000L
    }
}

// Replaced source retained for review (inactive; do not restore without its surrounding implementation).
// Prior source:     override fun onMessageReceived(message: RemoteMessage) {
// Prior source:         val handler = PushWakeHolder.handler ?: return
// Prior source:         when (message.data["type"]) {
// Prior source:             "sync" -> runBlocking {
// Prior source:                 withTimeoutOrNull(PUSH_WAKE_TIMEOUT_MS) {
// Prior source:                     // Cold process: restore the backend session before the handler
// Prior source:                     // reads apiBackend state, else the sync nudge is dropped. Off-main
// Prior source:                     // (Dispatchers.IO); @Synchronized no-op if the bootstrap landed.
// Prior source:                     runCatching { withContext(kotlinx.coroutines.Dispatchers.IO) { ServiceLocator.apiBackend.initialize() } }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
// Prior source:                     handler.onAccountSyncNudgeAndWait(binding)
// Prior source:                 }
// Prior source:             }
