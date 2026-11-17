package com.example.chat.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Not exported. Carries only an opaque nonce; all identity checks are in the bridge. Unknown/stale = no-op. */
class ChatyNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in setOf(ChatyNotificationPolicy.ACTION_REPLY, ChatyNotificationPolicy.ACTION_DISMISS,
                ChatyNotificationPolicy.ACTION_DECLINE, ChatyNotificationPolicy.ACTION_MUTE,
                ChatyNotificationPolicy.ACTION_HANGUP, ChatyNotificationPolicy.ACTION_SPEAKER)) return
        val nonce = intent.getStringExtra(ChatyNotificationPolicy.EXTRA_NONCE) ?: return
        val reply = if (action == ChatyNotificationPolicy.ACTION_REPLY)
            ChatyNotificationPolicy.cleanReply(RemoteInput.getResultsFromIntent(intent)
                ?.getCharSequence(ChatyNotificationPolicy.KEY_REPLY_TEXT)) ?: return else null
        val pending = goAsync()
        scope.launch {
            try {
                com.example.chat.ServiceLocator.ensurePushReady(context.applicationContext)
                com.example.chat.ServiceLocator.apiBackend.initialize()
                ChatyNotificationBridgeHolder.bridge?.dispatch(nonce, action, reply)
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { /* Keep notification available; never log reply content. */ }
            finally { pending.finish() }
        }
    }
// Prior receiver source:         com.example.chat.ServiceLocator.ensurePushReady(context.applicationContext)
// Prior receiver source:         val bridge = ChatyNotificationBridgeHolder.bridge ?: return
// Prior receiver source:         val action = intent.action ?: return
// Prior receiver source:         val nonce = intent.getStringExtra(ChatyNotificationPolicy.EXTRA_NONCE)
// Prior receiver source:         val reply = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(ChatyNotificationPolicy.KEY_REPLY_TEXT)
// Prior receiver source:         val pending = goAsync()
// Prior receiver source:         scope.launch {
// Prior receiver source:             // Cold process: restore the backend session before the bridge reads
// Prior receiver source:             // apiBackend state. Runs on Dispatchers.Default via goAsync - never a
// Prior receiver source:             // bare initialize() on the main-thread onReceive.
// Prior receiver source:             runCatching { com.example.chat.ServiceLocator.apiBackend.initialize() }
// Prior receiver source:             try { bridge.dispatch(nonce, action, reply) } catch (_: Exception) { } finally { pending.finish() }
// Prior receiver source:         }
// Prior receiver source:     }

    companion object { private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO) }
}
