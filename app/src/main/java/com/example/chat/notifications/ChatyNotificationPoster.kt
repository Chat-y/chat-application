package com.example.chat.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.example.chat.R

/**
 * Builds and posts system notifications. Channels are created once with the importance's system defaults;
 * sound, vibration and DND stay under the user's control in Android settings (never overridden here).
 */
object ChatyNotificationPoster {
    private const val CH_MISSED = "chaty_missed_calls_live_v1"
    private const val MISSED_ID = 7422

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(ChatyNotificationPolicy.CH_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "New chat messages"; enableVibration(true); setShowBadge(true)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_MISSED, "Missed calls", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Missed voice and video calls"
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(ChatyNotificationPolicy.CH_INCOMING_CALL, "Incoming calls", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Incoming voice and video calls"; enableVibration(true); setShowBadge(false)
                setSound(
                    android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE),
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                )
            },
        )
    }

    private fun canPost(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun smallIcon(style: ChatyNotificationStyle, call: Boolean = false): Int =
        if (call) R.drawable.ic_notif_call
        else if (style.iconVariant == ChatyNotificationStyle.ICON_BELL) R.drawable.ic_notif_bell else R.drawable.ic_notif_chat

    private fun broadcast(context: Context, action: String, nonce: String, mutable: Boolean = false): PendingIntent {
        val i = Intent(action).setClass(context, ChatyNotificationActionReceiver::class.java)
            // Previous identity used nonce.hashCode() alone; extras do not distinguish PendingIntents.
            // Full opaque nonce in data prevents hash collisions from retargeting an existing action.
            .setData(android.net.Uri.Builder().scheme("chaty").authority("notification-action").appendPath(nonce).build())
            .putExtra(ChatyNotificationPolicy.EXTRA_NONCE, nonce)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or when {
            !mutable -> PendingIntent.FLAG_IMMUTABLE
            Build.VERSION.SDK_INT >= 31 -> PendingIntent.FLAG_MUTABLE
            else -> 0 // Before Android12, omitting IMMUTABLE allows RemoteInput fill-in.
        }
        return PendingIntent.getBroadcast(context, nonce.hashCode(), i, flags)
    }

    /** Opens the existing launch activity (normal auth, app lock and incoming-call UI apply there). */
    fun openApp(context: Context, req: Int): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        } ?: return null
        return PendingIntent.getActivity(context, req, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** replyNonce/readNonce null means that action is not offered. [tag] carries the lane's canonical
     *  m:<generation>:<conversationId> tag. R6: internal - the Coordinator's sink implementation and the
     *  fixture are the only callers; no other call site may post or cancel. */
    internal fun postMessage(
        context: Context, style: ChatyNotificationStyle, tag: String, title: String, body: String,
        timestampMillis: Long, replyNonce: String?, readNonce: String?, messageCount: Int = 1,
    ) {
        if (!canPost(context)) return
        ensureChannels(context)
        val nid = ChatyNotificationPolicy.messageNotificationId(tag)
        val sender = Person.Builder().setName(title).build()
        val msgStyle = NotificationCompat.MessagingStyle(Person.Builder().setName("You").build())
            .addMessage(body, timestampMillis, sender)
        val b = NotificationCompat.Builder(context, ChatyNotificationPolicy.CH_MESSAGES)
            .setSmallIcon(smallIcon(style)).setColor(style.accentArgb)
            .setStyle(msgStyle).setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setNumber(messageCount.coerceAtLeast(1))
            .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH).setGroup("chaty_messages")
            .setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(openApp(context, nid))
        if (replyNonce != null) {
            b.addAction(
                NotificationCompat.Action.Builder(R.drawable.ic_notif_reply, "Reply", broadcast(context, ChatyNotificationPolicy.ACTION_REPLY, replyNonce, mutable = true))
                    .addRemoteInput(RemoteInput.Builder(ChatyNotificationPolicy.KEY_REPLY_TEXT).setLabel("Reply").build())
                    .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                    .setShowsUserInterface(false).setAuthenticationRequired(true).build(),
            )
        }
        if (readNonce != null) {
            b.addAction(
                NotificationCompat.Action.Builder(R.drawable.ic_notif_dismiss, "Dismiss", broadcast(context, ChatyNotificationPolicy.ACTION_DISMISS, readNonce))
                    .setShowsUserInterface(false).setAuthenticationRequired(true).build(),
            )
        }
        try { NotificationManagerCompat.from(context).notify(tag, nid, b.build()) } catch (_: SecurityException) {}
    }

    internal fun cancelMessage(context: Context, tag: String) {
        NotificationManagerCompat.from(context).cancel(tag, ChatyNotificationPolicy.messageNotificationId(tag))
    }

    /** Incoming call. The only action OPENS the app (accept happens in-app after auth/lock/permissions).
     *  [tag] is the lane's canonical c:<generation>:<callId> tag (null for the fixture/direct calls). */
    internal fun postIncomingCall(context: Context, style: ChatyNotificationStyle, callerName: String, isVideo: Boolean, declineNonce: String?, tag: String? = null, quiet: Boolean = false) {
        if (!canPost(context)) return
        ensureChannels(context)
        val open = openApp(context, ChatyNotificationPolicy.INCOMING_CALL_ID)
        val b = NotificationCompat.Builder(context, ChatyNotificationPolicy.CH_INCOMING_CALL)
            .setSmallIcon(smallIcon(style, call = true)).setColor(style.accentArgb)
            .setContentTitle(callerName)
            .setContentText(if (isVideo) "Incoming video call" else "Incoming voice call")
            .setCategory(NotificationCompat.CATEGORY_CALL).setPriority(NotificationCompat.PRIORITY_HIGH)
            // Previous implementation retained: .setOngoing(true).setOnlyAlertOnce(true).setTimeoutAfter(60_000)
            .setOngoing(true).setOnlyAlertOnce(true)
            .setTimeoutAfter(com.example.chat.data.services.CallRingPolicy.TIMEOUT_MILLIS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(open)
            .setSilent(quiet)
        // The live Coordinator path must opt in too; legacy ChatyNotifications
        // is not this poster. Keep its existing normal launch/auth/lock route.
        if (!quiet && open != null && (Build.VERSION.SDK_INT < 34 ||
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).canUseFullScreenIntent())) {
            b.setFullScreenIntent(open, true)
        }
        if (declineNonce != null) b.addAction(R.drawable.ic_notif_hangup, "Decline", broadcast(context, ChatyNotificationPolicy.ACTION_DECLINE, declineNonce))
        if (open != null) b.addAction(R.drawable.ic_notif_call, "Open to answer", open)
        try { NotificationManagerCompat.from(context).notify(tag, ChatyNotificationPolicy.INCOMING_CALL_ID, b.build()) } catch (_: SecurityException) {}
    }

    /** Generic copy never exposes a caller identity on locked/shared devices. */
    internal fun postMissedCall(context: Context, style: ChatyNotificationStyle, tag: String, summaryTag: String, group: String, hideOnLockScreen: Boolean = true) {
        if (!canPost(context)) return
        ensureChannels(context)
        val open = openApp(context, MISSED_ID)
        fun builder() = NotificationCompat.Builder(context, CH_MISSED)
            .setSmallIcon(smallIcon(style, call = true)).setColor(style.accentArgb)
            .setContentTitle("Missed Chaty call").setContentText("Open Chaty to review your calls")
            // Previous implementation retained: .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setVisibility(if (hideOnLockScreen) NotificationCompat.VISIBILITY_SECRET else NotificationCompat.VISIBILITY_PRIVATE)
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setContentIntent(open).setAutoCancel(true).setGroup(group).setOnlyAlertOnce(true)
        val manager = NotificationManagerCompat.from(context)
        manager.notify(tag, MISSED_ID, builder().build())
        manager.notify(summaryTag, MISSED_ID, builder().setContentTitle("Missed Chaty calls")
            .setGroupSummary(true).setSilent(true).build())
    }

    private const val REMINDER_CHANNEL = "chaty_call_callback_reminders_v1"
    private const val REMINDER_ID = 7423
    internal fun postCallbackReminder(context: Context, style: ChatyNotificationStyle, tag: String) {
        if (!canPost(context)) return
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(NotificationChannel(REMINDER_CHANNEL, "Call-back reminders", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { lockscreenVisibility = android.app.Notification.VISIBILITY_SECRET })
        }
        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL)
            .setSmallIcon(smallIcon(style, call = true)).setColor(style.accentArgb)
            .setContentTitle("Call-back reminder").setContentText("Open Chaty to review your declined calls.")
            .setContentIntent(openApp(context, REMINDER_ID)).setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET).build()
        NotificationManagerCompat.from(context).notify(tag, REMINDER_ID, notification)
    }
    internal fun cancelCallbackReminder(context: Context, tag: String) {
        NotificationManagerCompat.from(context).cancel(tag, REMINDER_ID)
    }

    internal fun cancelMissedCall(context: Context, tag: String) {
        NotificationManagerCompat.from(context).cancel(tag, MISSED_ID)
    }

    internal fun cancelIncomingCall(context: Context, tag: String? = null) {
        NotificationManagerCompat.from(context).cancel(tag, ChatyNotificationPolicy.INCOMING_CALL_ID)
    }

    @androidx.annotation.RequiresApi(26)
    internal fun pipAction(context: Context, action: String, nonce: String, title: String, icon: Int): android.app.RemoteAction =
        android.app.RemoteAction(android.graphics.drawable.Icon.createWithResource(context, icon), title, title,
            broadcast(context, action, nonce))

    /** Mute/Unmute and Hang up on the quiet ongoing-call notification. Speaker/Earpiece toggles the route. */
    fun addOngoingCallActions(context: Context, builder: NotificationCompat.Builder, bridge: ChatyNotificationBridge?) {
        val mute = bridge?.registerCallAction(ChatyNotificationPolicy.ACTION_MUTE) ?: return
        val hang = bridge.registerCallAction(ChatyNotificationPolicy.ACTION_HANGUP) ?: return
        builder.addAction(
            if (mute.second.muted) R.drawable.ic_notif_mic_off else R.drawable.ic_notif_mic,
            if (mute.second.muted) "Unmute" else "Mute",
            broadcast(context, ChatyNotificationPolicy.ACTION_MUTE, mute.first),
        )
        val spk = bridge.registerCallAction(ChatyNotificationPolicy.ACTION_SPEAKER) ?: return
        builder.addAction(R.drawable.ic_notif_speaker, if (spk.second.onSpeaker) "Earpiece" else "Speaker", broadcast(context, ChatyNotificationPolicy.ACTION_SPEAKER, spk.first))
        builder.addAction(R.drawable.ic_notif_hangup, "Hang up", broadcast(context, ChatyNotificationPolicy.ACTION_HANGUP, hang.first))
        builder.setCategory(NotificationCompat.CATEGORY_CALL)
    }
}
