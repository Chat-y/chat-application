package com.example.chat.notifications

import android.app.NotificationManager
import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.data.api.ApiAccountScope
import com.example.chat.data.api.ApiBackendService
import com.example.chat.data.services.AppLockCoordinator
import com.example.chat.data.services.AudioRouteType
import com.example.chat.data.services.CallSignalingService
import com.example.chat.data.services.NotificationChannelManager
import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.MessageType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Owns system notifications for the custom-API account plane. Every action is bound at build time to the EXACT
 * [ApiAccountScope] reference (never serialized; PendingIntents carry a random nonce only) and revalidated at
 * dispatch. All registry entries are dropped on every auth transition; NotificationManager post/cancel IPC is
 * serialized on ONE process-lived lane thread ([ChatyNotificationLane]) that re-checks admission at execution
 * time, so old-account alerts are cancelled in FIFO order and old-account cleanup can never cancel a new
 * account's alert. Auth-scope listeners only clear the in-memory registry and ENQUEUE a cleanup - no
 * NotificationManager call runs under the auth lock. The Coordinator is the lane's only [ChatyNotificationSink]
 * (R6: Poster notify/cancel are internal with no other caller).
 */
class ChatyNotificationCoordinator(
    private val context: Context,
    private val scope: CoroutineScope,
    private val api: ApiBackendService,
    private val calls: CallSignalingService,
    private val prefs: ChatyPreferencesController,
    private val appLock: AppLockCoordinator,
    private val channelManager: NotificationChannelManager,
    private val isChatVaultLocked: (String) -> Boolean,
    private val callPresentation: com.example.chat.ui.calls.CallPresentationPreferencesStore? = null,
    /** False unless sent or durably queued under the exact notification owner.
     * The nonce is also the stable MLS idempotency key for duplicate action deliveries. */
    private val replySender: suspend (ApiAccountScope, String, String, String) -> Boolean = { _, _, _, _ -> false },
) : ChatyNotificationBridge, ChatyNotificationSink {

    private val registry = ChatyActionRegistry<ApiAccountScope>()
    private val repliesInFlight = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    @Volatile private var lastScope: ApiAccountScope? = null
    @Volatile private var lastIncomingCallId: String? = null
    private val reportedMissed = linkedSetOf<String>()
    private fun markMissedReported(key: String): Boolean = synchronized(reportedMissed) {
        val added = reportedMissed.add(key)
        while (reportedMissed.size > 512) reportedMissed.remove(reportedMissed.first())
        added
    }
    /** Foreground incoming-call ring (Bandi's ChatyNotifications.startRinging intent, coordinator-owned):
     *  audible/vibrating ring ONLY while a live incoming call is present and the app is foreground;
     *  backgrounded calls ring through the system notification instead (never both). */
    private val foregroundRinger = ForegroundCallRinger(AndroidRingtonePlayer(context))
    private data class AlertAdmission(val owner: ApiAccountScope, val callId: String, val quiet: Boolean)
    @Volatile private var alertAdmission: AlertAdmission? = null
    private val alertAdmissionLock = Any()

    private val stylePrefs get() = context.getSharedPreferences("chaty_notification_style", Context.MODE_PRIVATE)

    /** The notification lane. Created ONCE in [start] together with its single daemon thread
     *  (process-lived, never per-transition, never shut down); start() itself is called once from
     *  ServiceLocator init and is NOT idempotent. */
    private lateinit var lane: ChatyNotificationLane

    var style: ChatyNotificationStyle
        get() = ChatyNotificationStyle.sanitized(
            stylePrefs.getInt("accent", ChatyNotificationStyle().accentArgb), stylePrefs.getInt("icon", 0),
        )
        set(v) {
            val c = ChatyNotificationStyle.sanitized(v.accentArgb, v.iconVariant)
            stylePrefs.edit().putInt("accent", c.accentArgb).putInt("icon", c.iconVariant).apply()
        }

    fun start() {
        val laneExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
            Thread(r, "chaty-notification-lane").apply { isDaemon = true }
        }
        lane = ChatyNotificationLane(
            currentScope = { api.authSnapshot() },
            liveCallId = { calls.currentSession?.takeIf { !it.state.isTerminal }?.callId },
            registry = registry,
            sink = this,
            executor = SerialDeferredExecutor { command -> laneExecutor.execute { runCatching { command.run() } } },
        )
        ChatyNotificationPoster.ensureChannels(context)
        ChatyNotificationBridgeHolder.bridge = this
        api.addAuthScopeListener { next ->
            if (lastScope !== next) {
                lastScope = next
                foregroundRinger.stop()
                alertAdmission = null
                synchronized(reportedMissed) { reportedMissed.clear() }
                registry.clear()
                lane.cleanupNonCurrent() // enqueue-only: NO sink IPC under the auth lock
            }
        }
        // Capture the terminal receipt synchronously: a later coroutine may see the next session.
        calls.addStateListener {
            val receipt = snapshotCall()
            // Enqueue terminal receipt now, before a later Calls-screen clear can overtake it.
            if (receipt?.missed == true && receipt.account === api.authSnapshot()) {
                val key = "${receipt.account.generation}:${receipt.callId}"
                if (markMissedReported(key)) lane.postMissedCall(receipt.account, receipt.callId)
            }
            scope.launch { onCallState() }
            // Prior deferred receipt: scope.launch { onCallState(receipt) }
        }
        // Re-evaluate on app foreground/background transitions: ScopedCallCoordinator emits no
        // call-state event for these, so without this feed a foreground ring would keep ringing
        // in the background on top of the system full-screen alert (never both, by design).
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            ProcessLifecycleOwner.get().lifecycle.addObserver(androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_STOP) refreshCallState()
            })
        }
        api.onScopedIncomingMessage = { owner, message, convoTitle, convoMuted ->
            scope.launch { onMessage(owner, message, convoTitle, convoMuted) }
        }
        lane.reconcileOnStart()
    }

    // --- ChatyNotificationSink: the sole NotificationManager IPC surface, invoked on the lane thread only. ---

    override fun postMessage(tag: String, title: String, body: String, whenEpochMs: Long, replyNonce: String?, dismissNonce: String?) {
        val account = api.authSnapshot()
        val prefix = "m:${account.generation}:"
        if (!tag.startsWith(prefix)) return
        val conversationId = tag.removePrefix(prefix)
        val unread = api.conversations.firstOrNull { it.id == conversationId }?.unreadCount ?: 1
        ChatyNotificationPoster.postMessage(context, style, tag, title, body, whenEpochMs, replyNonce, dismissNonce,
            messageCount = unread.coerceAtLeast(1))
    }

    override fun cancelMessage(tag: String) = ChatyNotificationPoster.cancelMessage(context, tag)

    override fun postIncomingCall(tag: String, callerName: String, isVideo: Boolean, declineNonce: String?) {
        val admission = alertAdmission
        val quiet = admission?.takeIf { it.owner === api.authSnapshot() && tag == "c:${it.owner.generation}:${it.callId}" }?.quiet
            ?: (callPresentation != null) // Unknown admission must not leak a ring before preferences restore.
// Prior source:         ChatyNotificationPoster.postIncomingCall(context, style, callerName, isVideo, declineNonce, tag, quiet = quiet)
        // Accept uses an Activity PendingIntent, never a background receiver that starts camera/microphone.
        val entry = registry.resolve(declineNonce)
        val accept = entry?.takeIf { it.action == ChatyNotificationPolicy.ACTION_DECLINE &&
            it.scope === api.authSnapshot() && it.callId == calls.currentSession?.callId &&
            tag == "c:${it.scope.generation}:${it.callId}" }?.let {
            registry.register(it.scope, ChatyNotificationPolicy.ACTION_ACCEPT, callId = it.callId)
        }
        ChatyNotificationPoster.postIncomingCall(context, style, callerName, isVideo, declineNonce, tag, quiet = quiet,
            acceptNonce = accept)
    }

    override fun cancelIncomingCall(tag: String) = ChatyNotificationPoster.cancelIncomingCall(context, tag)
    override fun postMissedCall(tag: String, summaryTag: String, group: String) =
        ChatyNotificationPoster.postMissedCall(context, style, tag, summaryTag, group,
            // Failed/cold restore cannot expose history on the lock screen.
            hideOnLockScreen = callPresentation?.let { !it.isInitialized || it.value.hideCallHistoryOnLockScreen } ?: true)
    fun postCallbackReminder(owner: ApiAccountScope, requestId: String) = lane.postCallbackReminder(owner, requestId)
    override fun postCallbackReminder(tag: String) = ChatyNotificationPoster.postCallbackReminder(context, style, tag)
    override fun cancelCallbackReminder(tag: String) = ChatyNotificationPoster.cancelCallbackReminder(context, tag)
    override fun cancelMissedCall(tag: String) = ChatyNotificationPoster.cancelMissedCall(context, tag)
    fun clearMissedCallAlerts(expected: ApiAccountScope = api.authSnapshot()) = lane.clearMissedCalls(expected)


    /** This app's active m:/c:-prefixed tags only; the lifecycle-bound foreground-service notification
     *  (SERVICE_ID=7401, untagged) and any foreign tags never appear here. */
    override fun activeTags(): List<String> {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.activeNotifications.mapNotNull { it.tag }.filter { it.startsWith("m:") || it.startsWith("c:") || it.startsWith("mc:") || it.startsWith("cr:") }
    }

    /** The live style resolver shared with message/incoming-call posts (style-fix: ongoing-call notification
     *  reads the same user selection instead of a hardcoded constant). */
    override fun currentStyle(): ChatyNotificationStyle = style

    fun cancelFor(conversationId: String) {
        // Fresh snapshot per the lane contract: only that owner's tag for this conversation can be cancelled.
        lane.cancelMessageFor(api.authSnapshot(), conversationId)
    }

    /** Content-free watched-read alert; the existing lane revalidates scope at OS dispatch. */
    fun showReadAlert(accountKey: String, roomId: String) {
        val owner = api.authSnapshot()
        val actualKey = com.example.chat.core.messages.LocalMessageOverridePolicy.accountKey(owner.tenantId, owner.baseUrl, owner.userId.orEmpty())
        if (actualKey != accountKey || !prefs.notification.enableGlobalNotifications) return
        val locked = isChatVaultLocked(roomId) || prefs.security.lockedConversationIds.contains(roomId) || prefs.security.isAppLockEnabled
        val body = if (locked) "A watched message was read" else "${api.conversationTitle(roomId) ?: "Your contact"} read your message"
        lane.postMessage(owner, roomId, "Chaty", body, System.currentTimeMillis(), null, null)
    }

    private fun foreground() = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    private fun onMessage(owner: ApiAccountScope, message: ChatMessage, convoTitle: String?, convoMuted: Boolean) {
        // The source owner survives dispatch; the lane checks it again at OS execution.
        if (api.authSnapshot() !== owner) return
        val account = owner
        val me = account.userId
        val signedIn = !me.isNullOrBlank() && !account.token.isNullOrBlank()
        val gate = ChatyNotificationPolicy.MessageGate(
            globalEnabled = prefs.notification.enableGlobalNotifications, signedIn = signedIn,
            fromMe = message.senderId == me, conversationMuted = convoMuted, appForeground = foreground(),
            conversationOpen = api.activeConversation == message.conversationId,
            systemOrDeleted = message.type == MessageType.system || message.isDeletedForEveryone ||
                message.isDeletedForMe || message.isRevokedBySender,
        )
        if (!ChatyNotificationPolicy.shouldPostMessage(gate)) return
        val chatLocked = isChatVaultLocked(message.conversationId) ||
            prefs.security.lockedConversationIds.contains(message.conversationId)
        val detail = ChatyNotificationPolicy.detail(chatLocked, prefs.security.isAppLockEnabled)
        val raw = com.example.chat.core.messages.ViewOnceTextPolicy.preview(message).ifBlank { if (message.type == MessageType.image) "Photo" else "Attachment" }
        val plan = ChatyNotificationPolicy.plan(detail, convoTitle, raw) { channelManager.formatPrivacyBody(convoTitle ?: "Chaty", it) }
        val id = message.conversationId
        val reply = if (plan.offerReply && !com.example.chat.core.messages.ViewOnceTextPolicy.restricted(message.metadata)) registry.register(account, ChatyNotificationPolicy.ACTION_REPLY, conversationId = id) else null
        val dismiss = if (plan.offerDismiss) registry.register(account, ChatyNotificationPolicy.ACTION_DISMISS, conversationId = id) else null
        // Build-outside rule: gates/prefs/nonces resolved here holding NO auth lock; stale-scope text lives
        // only in a dropped record. Admission is re-checked on the lane thread at execution time - a stale
        // build is dropped with its nonces and never reaches NotificationManager.
        lane.postMessage(account, id, plan.title, plan.body, message.createdAt.toEpochMilli(), reply, dismiss)
    }

    private data class CallSnap(val account: ApiAccountScope, val callId: String, val incoming: Boolean, val outgoing: Boolean,
        val video: Boolean, val name: String, val muted: Boolean, val route: AudioRouteType, val live: Boolean,
        val missed: Boolean)

    /** Snapshot account + session together under the auth guard; build/post/cancel happens outside it. */
    private fun snapshotCall(): CallSnap? {
        val account = api.authSnapshot()
        var snap: CallSnap? = null
        api.withAuthScope(account) {
            val s = calls.currentSession ?: return@withAuthScope
            snap = CallSnap(account, s.callId, s.state == com.example.chat.data.services.CallSessionState.incoming, s.isOutgoing,
                s.isVideo, s.remoteDisplayName.trim().ifEmpty { "Chaty contact" }, s.isMuted, s.audioRoute, !s.state.isTerminal,
                !s.isDemo && !s.isOutgoing && s.connectedAt == null &&
                    (s.state == com.example.chat.data.services.CallSessionState.missed || s.state == com.example.chat.data.services.CallSessionState.ended))
        }
        return snap
    }

    private suspend fun onCallState() {
        val restored = try {
            // A stalled disk restore must not indefinitely hide Open to answer.
            // Initialize runs in the store's own scope; timeout only abandons this wait.
            kotlinx.coroutines.withTimeoutOrNull(3_000L) {
                callPresentation?.awaitInitialized()
                true
            } ?: false
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { false }
        // Snapshot AFTER restore. A call/account may have changed while the disk load awaited.
        val snap = snapshotCall()
        val quiet = if (snap != null && snap.live && snap.incoming && !snap.outgoing) synchronized(alertAdmissionLock) {
            val previous = alertAdmission
            if (previous != null && previous.owner === snap.account && previous.callId == snap.callId) previous.quiet
            else (!restored || callPresentation?.value?.quietHours?.activeNow() == true).also {
                alertAdmission = AlertAdmission(snap.account, snap.callId, it)
            }
        } else { alertAdmission = null; false }
        foregroundRinger.onCallSnapshot(
            liveIncomingCall = snap != null && snap.live && snap.incoming && !snap.outgoing,
            appForeground = foreground(),
            quiet = quiet,
        )
        if (snap == null || !snap.live || !snap.incoming || snap.outgoing) {
            lastIncomingCallId?.let { lane.cancelCallFor(api.authSnapshot(), it) }
            lastIncomingCallId = null
            if (snap == null || !snap.live) registry.clearCalls()
            return
        }
        if (!foreground()) {
            val decline = registry.register(snap.account, ChatyNotificationPolicy.ACTION_DECLINE, callId = snap.callId)
            // Admission (scope + live call) is re-checked on the lane thread at execution time.
            lastIncomingCallId = snap.callId
            lane.postIncomingCall(snap.account, snap.callId, snap.name, snap.video, decline)
        } else {
            // App came to the foreground while the full-screen alert was up: the in-app call
            // UI owns the incoming call now, so the system notification is withdrawn.
            lastIncomingCallId?.let { lane.cancelCallFor(snap.account, it) }
            lastIncomingCallId = null
        }
    }

    /** Re-evaluate the live call after an app foreground/background transition (ServiceLocator
     *  refreshCallAlert): posts the full-screen alert when backgrounded, withdraws it when the
     *  in-app call UI is visible. Message alerts need no re-evaluation (per-event gating). */
    fun refreshCallState() {
        if (!::lane.isInitialized) return
        scope.launch { onCallState() }
    }

    override fun registerCallAction(action: String): Pair<String, CallActionView>? {
        val snap = snapshotCall() ?: return null
        if (!snap.live || snap.incoming) return null
        return registry.register(snap.account, action, callId = snap.callId) to CallActionView(snap.callId, snap.muted, snap.route == AudioRouteType.speaker)
    }

    /** PiP never receives raw account/call identifiers in its action PendingIntent. */
    @androidx.annotation.RequiresApi(26)
    fun pipActions(expected: ApiAccountScope, callId: String): List<android.app.RemoteAction> {
        var mute: String? = null
        var hangup: String? = null
        var muted = false
        api.withAuthScope(expected) {
            val session = calls.currentSession ?: return@withAuthScope
            if (session.callId != callId || !session.isActive) return@withAuthScope
            muted = session.isMuted
            mute = registry.register(expected, ChatyNotificationPolicy.ACTION_MUTE, callId = callId)
            hangup = registry.register(expected, ChatyNotificationPolicy.ACTION_HANGUP, callId = callId)
        }
        val muteNonce = mute ?: return emptyList()
        val hangupNonce = hangup ?: return emptyList()
        return listOf(
            ChatyNotificationPoster.pipAction(context, ChatyNotificationPolicy.ACTION_MUTE, muteNonce,
                if (muted) "Unmute" else "Mute", if (muted) com.example.chat.R.drawable.ic_notif_mic_off else com.example.chat.R.drawable.ic_notif_mic),
            ChatyNotificationPoster.pipAction(context, ChatyNotificationPolicy.ACTION_HANGUP, hangupNonce,
                "Hang up", com.example.chat.R.drawable.ic_notif_hangup),
        )
    }

    /** Activity-only action: reuse the existing in-app permission/accept path after exact-owner validation. */
    fun requestNotificationAccept(nonce: String?): Boolean {
        val entry = registry.resolve(nonce) ?: return false
        val id = entry.callId ?: return false
        if (entry.action != ChatyNotificationPolicy.ACTION_ACCEPT || api.authSnapshot() !== entry.scope ||
            !callOk(id) || calls.currentSession?.state != com.example.chat.data.services.CallSessionState.incoming) return false
        // Opening the app is allowed when locked; queuing automatic acceptance is not.
        if (appLock.lockRequired.value || prefs.security.isAppLockEnabled) return false
        registry.dropNonce(nonce)
        ChatyNotifications.requestCallAction(ChatyNotifications.PendingCallAction(id, answer = true, owner = entry.scope))
        return true
    }

    private fun callOk(id: String) = ChatyNotificationPolicy.callActionAllowed(id, calls.currentSession?.callId, calls.hasActiveSession)

    override suspend fun dispatch(nonce: String?, action: String, replyText: CharSequence?) {
        val e = registry.resolve(nonce) ?: return
        if (e.action != action) return
        val account = e.scope
        // The exact scope the action was built for must still be the live one.
        if (api.authSnapshot() !== account || account.token.isNullOrBlank()) return
        when (action) {
            ChatyNotificationPolicy.ACTION_REPLY -> {
                val id = e.conversationId ?: return
                val text = ChatyNotificationPolicy.cleanReply(replyText) ?: return
                val chatLocked = isChatVaultLocked(id) || prefs.security.lockedConversationIds.contains(id)
                if (!ChatyNotificationPolicy.mayReply(appLock.lockRequired.value, prefs.security.isAppLockEnabled, chatLocked)) return
                // Scope-bound send: token/base URL come from the ORIGINAL scope, request cancelled on any auth transition.
                // Prior source: if (api.sendMessageForScope(account, id, text)) lane.cancelMessageFor(account, id)
                val replyNonce = nonce ?: return
                if (!repliesInFlight.add(replyNonce)) return
                try {
                    // Another delivery may have captured e before the first send retired its nonce.
                    if (!registry.isCurrent(replyNonce, e)) return
                    // Previous regular-only call retained: if (api.sendMessageForScope(account, id, text)) {
                    if (replySender(account, id, text, replyNonce)) {
                        registry.dropNonce(replyNonce)
                        lane.cancelMessageFor(account, id)
                    }
                } finally { repliesInFlight.remove(replyNonce) }
            }
            // Previous implementation retained:
            // ChatyNotificationPolicy.ACTION_DISMISS -> e.conversationId?.let { cancelFor(it) }
            // It resnapshotted auth and could dismiss the next account's alert.
            // Keep the owner captured by this action even if auth changes before the lane drains.
            // Local dismissal only: no read state is sent to the server.
            ChatyNotificationPolicy.ACTION_DISMISS -> e.conversationId?.let { lane.cancelMessageFor(account, it) }
            ChatyNotificationPolicy.ACTION_MUTE -> {
                val id = e.callId ?: return
                api.withAuthScope(account) { if (callOk(id)) calls.toggleMute() }
            }
            ChatyNotificationPolicy.ACTION_HANGUP -> {
                val id = e.callId ?: return
                api.withAuthScope(account) { if (callOk(id)) calls.endCall("ended") }
            }
            ChatyNotificationPolicy.ACTION_SPEAKER -> {
                val id = e.callId ?: return
                val cur = calls.currentSession?.takeIf { it.callId == id } ?: return
                val next = if (cur.audioRoute == AudioRouteType.speaker) AudioRouteType.earpiece else AudioRouteType.speaker
                calls.setAudioRoute(next) // service rejects stale account/call itself
            }
            ChatyNotificationPolicy.ACTION_DECLINE -> {
                val id = e.callId ?: return
                calls.declineCall(expectedCallId = id)
                if (lastIncomingCallId == id) lastIncomingCallId = null
                lane.cancelCallFor(account, id)
            }
        }
    }
}

/** Audible/vibrating incoming-call ring for the in-app call UI. The system notification owns the
 *  ring while backgrounded; this owns it while the app is foreground, so a call never rings twice
 *  and never rings silently. State-driven and idempotent: the coordinator feeds it a snapshot on
 *  every call-state or app-foreground transition; answer/decline/end/background/sign-out all stop it. */
interface RingerPlayer {
    val isPlaying: Boolean
    fun start()
    fun stop()
}

class ForegroundCallRinger(private val player: RingerPlayer) {
    @Volatile private var ringing = false

    @Synchronized
    fun onCallSnapshot(liveIncomingCall: Boolean, appForeground: Boolean, quiet: Boolean = false) {
        // Previous: val shouldRing = liveIncomingCall && appForeground
        val shouldRing = liveIncomingCall && appForeground && !quiet
        if (shouldRing && !ringing) {
            ringing = true
            // A failed start must not wedge the state: the next snapshot retries.
            runCatching { player.start() }.onFailure { ringing = false }
        } else if (!shouldRing && ringing) {
            ringing = false
            runCatching { player.stop() }
        }
    }

    /** Explicit stop for transitions that carry no call snapshot (account switch, sign-out). */
    @Synchronized
    fun stop() {
        if (!ringing) return
        ringing = false
        runCatching { player.stop() }
    }
}

/** Bandi's startRinging/stopRinging semantics: default ringtone, looping, on the ringtone usage
 *  stream (system ringer mode and DND stay under the user's control); vibrates unless the ringer
 *  is fully silent; every failure is contained and logged, never thrown into the call flow. */
class AndroidRingtonePlayer(private val context: Context) : RingerPlayer {
    private var ringtone: android.media.Ringtone? = null
    private var vibrator: android.os.Vibrator? = null

    override val isPlaying: Boolean get() = ringtone?.isPlaying == true

    @Synchronized
    override fun start() {
        if (ringtone?.isPlaying == true) return
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            if (am.ringerMode == android.media.AudioManager.RINGER_MODE_NORMAL) {
                ringtone = android.media.RingtoneManager.getRingtone(
                    context, android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE),
                )?.apply {
                    audioAttributes = android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) isLooping = true
                    play()
                }
            }
            if (am.ringerMode != android.media.AudioManager.RINGER_MODE_SILENT) {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                val pattern = longArrayOf(0, 1000, 800)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    v?.vibrate(android.os.VibrationEffect.createWaveform(pattern, 0))
                } else {
                    @Suppress("DEPRECATION") v?.vibrate(pattern, 0)
                }
                vibrator = v
            }
        } catch (e: Exception) {
            android.util.Log.w("ChatyNotifCoordinator", "foreground ring failed: $e")
        }
    }

    @Synchronized
    override fun stop() {
        try { ringtone?.stop() } catch (_: Exception) {}
        try { vibrator?.cancel() } catch (_: Exception) {}
        ringtone = null
        vibrator = null
    }
}
