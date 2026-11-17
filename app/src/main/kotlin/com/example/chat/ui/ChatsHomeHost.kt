package com.example.chat.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.example.chat.ServiceLocator
import com.example.chat.data.services.ProtectedResourceGate
import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.ConversationType
import com.example.chat.ui.chats.ChatsHomeScreen
import com.example.chat.ui.chats.home.ChatsHomeNav
import com.example.chat.ui.chats.home.HomeIdentity
import kotlinx.coroutines.launch

/**
 * Shared host for ChatsHomeScreen (chats and groups destinations). Wires the
 * screen's preference, identity, row-action, call, lock and vault seams to the
 * real services. Conversation row state changes go through
 * ConversationsRepository, as Dart ChatyDataStore does (toggle and mark methods).
 */
@Composable
fun ChatsHomeHost(
    forcedType: ConversationType?,
    pageTitle: String?,
    gate: GatePrompts,
    onOpenConversation: (Conversation) -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenLockedChats: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccountSwitcher: () -> Unit = onOpenSettings,
    onVerifyEmail: (String, String) -> Unit = { _, _ -> },
    onOpenSplitChat: (List<Conversation>) -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenNewChat: (groupMode: Boolean, broadcastMode: Boolean) -> Unit = { _, _ -> },
    onNewCall: () -> Unit = {},
    onOpenGlobalSearch: () -> Unit = {},
    onOpenMessageManagement: () -> Unit = {},
    onOpenStarredMessages: () -> Unit = {},
    onSelectPlan: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenQr: () -> Unit = {},
    onOpenLinkedDevices: () -> Unit = {},
    /** Opens a root destination (tasks/updates) as a pushed screen; used when the
     *  user hides them from the rail into the home header overflow menu. */
    onOpenDestination: (String) -> Unit = {},
    /** R7 expanded list-detail: when true and [chatPane] is given, the open
     *  conversation renders beside the list instead of pushing a route. */
    expandedListDetail: Boolean = false,
    paneConversation: Conversation? = null,
    chatPane: (@Composable (Conversation) -> Unit)? = null,
    homeToolRequest: com.example.chat.ui.chats.home.HomeToolRequest? = null,
    onHomeToolConsumed: (com.example.chat.ui.chats.home.HomeToolRequest) -> Unit = {},
) {
    val api = ServiceLocator.apiBackend
    val prefs = ServiceLocator.preferencesController
    val repo = ServiceLocator.conversationsRepository
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var rev by remember { mutableIntStateOf(0) }
    var connectionSheetOpen by remember { androidx.compose.runtime.mutableStateOf(false) }
    var connectionTesting by remember { androidx.compose.runtime.mutableStateOf(false) }
    val templateController = ServiceLocator.templateController
    DisposableEffect(api, prefs, templateController) {
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        val alive = java.util.concurrent.atomic.AtomicBoolean(true)
        val l: () -> Unit = { main.post { if (alive.get()) rev++ } }
        api.addListener(l)
        prefs.addListener(l)
        templateController.addListener(l)
        ServiceLocator.hiddenVaultController.addListener(l)
        onDispose {
            alive.set(false)
            ServiceLocator.hiddenVaultController.removeListener(l)
            api.removeListener(l)
            prefs.removeListener(l)
            templateController.removeListener(l)
        }
    }
    androidx.compose.runtime.LaunchedEffect(api.isAuthenticated, api.currentUser?.id) {
        if (api.isAuthenticated) api.ensureInitialSyncIfNeeded()
    }
    @Suppress("UNUSED_EXPRESSION") rev
    val owner = api.authSnapshot()
    var pendingRecipient by remember(owner) {
        androidx.compose.runtime.mutableStateOf<com.example.chat.domain.models.UserProfile?>(null)
    }
    var recipientDecision by remember(owner) {
        androidx.compose.runtime.mutableStateOf<kotlinx.coroutines.CompletableDeferred<Boolean>?>(null)
    }
    var homeDiagnosticsOpen by remember(owner) { androidx.compose.runtime.mutableStateOf(false) }
    DisposableEffect(owner, recipientDecision) {
        val decision = recipientDecision
        onDispose { decision?.complete(false) }
    }
    var verificationBusy by remember(owner) { androidx.compose.runtime.mutableStateOf(false) }
    var verificationRetryAt by remember(owner) { androidx.compose.runtime.mutableStateOf(0L) }
    var verificationWait by remember(owner) { androidx.compose.runtime.mutableIntStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(owner,verificationRetryAt) {
        while(true) {
            verificationWait=((verificationRetryAt-android.os.SystemClock.elapsedRealtime()+999)/1000).toInt().coerceAtLeast(0)
            if(verificationWait==0) break
            kotlinx.coroutines.delay(1000)
        }
    }
    fun ownsRequest() = api.authSnapshot() === owner
    // Host-owned in-flight guard: one running request per (action, chat) so a double tap
    // cannot start the same mutation twice. Cleared when the request ends or the account changes.
    val inFlightOps = remember(owner) { java.util.concurrent.ConcurrentHashMap.newKeySet<String>() }
    fun beginOp(action: String, chatId: String = ""): Boolean = inFlightOps.add("$action|$chatId")
    fun endOp(action: String, chatId: String = "") { inFlightOps.remove("$action|$chatId") }
    val me = api.currentUser
    val conversations = api.conversations
    fun find(id: String) = conversations.firstOrNull { it.id == id }
    fun toast(m: String) = Toast.makeText(context, m, Toast.LENGTH_SHORT).show()
    fun setState(id: String, field: String, value: Boolean) {
        // Stored on this device: the custom backend has no per-user chat state API yet.
        api.withAuthScope(owner) { api.setConversationLocalState(id, field, value) }
    }
    // Account-owned transient operation state (not persisted, no retry queue). Keyed by owner so an
    // account switch drops it; every write below also re-checks ownsRequest().
    var opState by remember(owner) {
        androidx.compose.runtime.mutableStateOf<Map<com.example.chat.ui.chats.home.HomeOpKey, com.example.chat.ui.chats.home.HomeOpState>>(emptyMap())
    }
    var nextOpRequestId by remember(owner) { androidx.compose.runtime.mutableStateOf(0L) }
    fun dispatchOp(event: com.example.chat.ui.chats.home.HomeOpEvent) { if (ownsRequest()) opState = com.example.chat.ui.chats.home.reduce(opState, event) }
    // One tracked adapter call per (action, chat). Existing beginOp/endOp guard and toasts are kept;
    // this only adds Pending/Succeeded/Failed/Cancelled bookkeeping and typed failure wording.
    fun runTracked(
        action: String,
        chatId: String,
        retryable: Boolean,
        fallback: String,
        onSuccess: () -> Unit = {},
        block: suspend () -> Unit,
    ) {
        if (!ownsRequest() || !beginOp(action, chatId)) return
        val key = com.example.chat.ui.chats.home.HomeOpKey(action, chatId)
        val requestId = nextOpRequestId + 1
        nextOpRequestId = requestId
        dispatchOp(com.example.chat.ui.chats.home.HomeOpEvent.Start(key, requestId))
        if ((opState[key] as? com.example.chat.ui.chats.home.HomeOpState.Pending)?.requestId != requestId) { endOp(action, chatId); return }
        scope.launch {
            try {
                if (!ownsRequest()) return@launch
                block()
                dispatchOp(com.example.chat.ui.chats.home.HomeOpEvent.Succeed(key, requestId))
                if (ownsRequest()) onSuccess()
            } catch (cancel: kotlinx.coroutines.CancellationException) {
                dispatchOp(com.example.chat.ui.chats.home.HomeOpEvent.Cancel(key, requestId))
                throw cancel
            } catch (e: Exception) {
                val kind = try { com.example.chat.ui.chats.home.classifyFailure(e) } catch (c: kotlinx.coroutines.CancellationException) {
                    dispatchOp(com.example.chat.ui.chats.home.HomeOpEvent.Cancel(key, requestId))
                    throw c
                }
                val intent = if (retryable && kind != com.example.chat.ui.chats.home.HomeFailureKind.Cancelled) com.example.chat.ui.chats.home.HomeRetryIntent(key, owner) else null
                dispatchOp(com.example.chat.ui.chats.home.HomeOpEvent.Fail(key, requestId, kind, intent))
                if (ownsRequest() && kind != com.example.chat.ui.chats.home.HomeFailureKind.Cancelled) toast(homeTypedFailureMessage(e, kind, fallback))
            } finally { endOp(action, chatId) }
        }
    }
    fun trackedMarkRead(id: String) = runTracked("read", id, true, "Could not mark chat as read. Try again.") { repo.markAsRead(id, owner) }
    fun trackedMarkUnread(id: String) = runTracked("unread", id, true, "Could not mark chat as unread. Try again.") { repo.markAsUnread(id, owner) }
    fun trackedMarkAllRead(ids: List<String>) = runTracked("read_all", "", false, "Could not mark all chats as read. Try again.",
        onSuccess = { toast("Mark-all-read request completed") }) { repo.markAllAsRead(owner, ids.toSet()) }
    // Manual refresh through the existing scope-guarded API only. That call swallows a failed fetch,
    // so no success or failure is claimed here: the list simply updates if the fetch applied.
    var homeRefreshing by remember(owner) { androidx.compose.runtime.mutableStateOf(false) }
    fun refreshHome() {
        if (homeRefreshing || !ownsRequest()) return
        homeRefreshing = true
        scope.launch {
            try {
                kotlinx.coroutines.withTimeout(20_000L) { api.loadConversationsIfNeeded(force = true) }
            } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
                if (ownsRequest()) toast("Refresh took too long. Try again.")
            } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel
            } catch (e: Exception) {
                if (ownsRequest()) toast(homeTypedFailureMessage(e, com.example.chat.ui.chats.home.classifyFailure(e), "Could not refresh. Try again."))
            } finally { if (ownsRequest()) homeRefreshing = false }
        }
    }
    // Message-a-number lookup lifecycle: one at a time, token-checked, cancelled on dismiss/account change.
    var lookupState by remember(owner) {
        androidx.compose.runtime.mutableStateOf<com.example.chat.ui.chats.home.HomeLookupState>(com.example.chat.ui.chats.home.HomeLookupState.Idle)
    }
    var lookupJob by remember(owner) { androidx.compose.runtime.mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var lookupSeq by remember(owner) { androidx.compose.runtime.mutableStateOf(0L) }
    fun lookupCurrent(token: Long) = (lookupState as? com.example.chat.ui.chats.home.HomeLookupState.Loading)?.requestId == token
    fun cancelLookup() { lookupJob?.cancel(); lookupJob = null; lookupState = com.example.chat.ui.chats.home.HomeLookupState.Idle }
    DisposableEffect(owner) { onDispose { lookupJob?.cancel() } }
    androidx.compose.runtime.LaunchedEffect(owner, api.conversations.map { it.id }) {
        dispatchOp(com.example.chat.ui.chats.home.HomeOpEvent.Prune(api.conversations.map { it.id }.toSet()))
    }
    val security = prefs.security
    val hiddenConversationIds = security.hiddenConversationIds.toSet() + conversations.filter {
        ServiceLocator.hiddenVaultController.isConversationLocked(it.id)
    }.map { it.id }
    val headerTabs = com.example.chat.ui.navigation.LocalHomeHeaderTabs.current
    val headerTabsTheme = com.example.chat.ui.theme.LocalThemeConfig.current
    val headerTabsColors = androidx.compose.runtime.remember(headerTabsTheme) {
        com.example.chat.ui.core.theme.ChatyColors(headerTabsTheme)
    }
    val headerBottom: (@Composable () -> Unit)? = if (headerTabs != null && headerTabs.active) {
        {
            com.example.chat.ui.navigation.HomeHeaderTabsBar(
                headerTabsColors, headerTabs.ids, headerTabs.selectedIndex, headerTabs.heightDp,
                onSelect = { index -> headerTabs.onSelect(index) },
            )
        }
    } else null
    val onRetryOp: (com.example.chat.ui.chats.home.HomeOpKey) -> Unit = { key ->
        val intent = (opState[key] as? com.example.chat.ui.chats.home.HomeOpState.Failed)?.retryIntent
        if (intent != null && ownsRequest() &&
            intent.isValidFor(owner, conversations.map { it.id }.toSet()) &&
            (key.chatId.isEmpty() || (!prefs.isConversationLocked(key.chatId) && key.chatId !in hiddenConversationIds))
        ) {
            when (key.action) {
                "read" -> trackedMarkRead(key.chatId)
                "unread" -> trackedMarkUnread(key.chatId)
                "read_all" -> toast("Use Mark all as read again to review and authorize its current chat scope.")
            }
        }
    }
    ChatsHomeScreen(
        homeToolRequest = homeToolRequest?.takeIf { it.owner === owner },
        onHomeToolConsumed = onHomeToolConsumed,
        opState = opState,
        onRetry = onRetryOp,
        onRefreshHome = { refreshHome() },
        homeRefreshing = homeRefreshing,
        accountSessionIdentity = api.authSnapshot(),
        folderAccountKey = api.currentAccountHash().orEmpty(),
        headerBottom = headerBottom,
        // Home-only banner retired at the owner's request; registration verification is unchanged.
        connectionBanner = null,
//         // Email-verification nudge (research 4a.4): signed-in but unverified
//         // users get a banner with one-tap resend; verification is never forced
//         // and the banner lives only in this composition (reappears next launch).
//         connectionBanner = if (me != null && !me!!.emailVerified) {
//             {
//                 EmailVerificationBanner(
//                     email = me?.email ?: "",
//                     busy = verificationBusy,
//                     retrySeconds = verificationWait,
//                     onResend = {
//                         if(!verificationBusy && verificationWait==0) {
//                         verificationBusy=true
//                         scope.launch {
//                             val address = me?.email ?: ""
//                             try {
//                                 val message = api.sendEmailVerification(address)
//                                 if(ownsRequest()) onVerifyEmail(address, message)
//                             } catch(cancel:kotlinx.coroutines.CancellationException) { throw cancel
//                             } catch (e: Exception) {
//                                 if(ownsRequest()) {
//                                     if(e is com.example.chat.data.api.AuthRateLimitException)
//                                         verificationRetryAt=android.os.SystemClock.elapsedRealtime()+e.retryAfterSeconds*1000L
//                                     toast(com.example.chat.ui.auth.OtpVerificationLogic.displayError(e))
//                                 }
//                             } finally { if(ownsRequest()) verificationBusy=false }
//                         }
//                         }
//                     },
//                 )
//             }
//         } else null,
        templates = com.example.chat.ui.chats.home.resolveHomeTemplates(templateController.config, com.example.chat.core.preferences.HomePresentationPolicy.isDefault(prefs.home.homeStyle), templateController.chatList),
        conversations = conversations,
        requiresSubscription = api.requiresSubscription,
        subscriptionMessage = api.subscriptionErrorMessage,
        onOpenConversation = onOpenConversation,
        expandedListDetail = expandedListDetail,
        paneConversation = paneConversation,
        chatPane = chatPane,
        currentUserId = me?.id ?: "",
        // Presence/typing published by the custom backend socket.
        presenceFor = { conversationId, otherUserId ->
            com.example.chat.ui.chats.home.HomePresence(
                isOnline = api.isUserOnline(otherUserId),
                isTyping = api.isTyping(conversationId, otherUserId),
                lastSeenAt = api.lastSeenOf(otherUserId),
            )
        },
        identity = HomeIdentity(
            displayName = me?.displayName ?: "",
            username = me?.username ?: "",
            about = me?.about ?: "",
            avatarInitials = me?.avatarInitials ?: "C",
            avatarColorHex = me?.avatarColorHex,
            avatarUrl = me?.avatarUrl,
        ),
        contacts = api.users,
        homePreferences = prefs.home,
        securityPreferences = security,
        forcedType = forcedType,
        pageTitle = pageTitle,
        messagesFor = { id -> api.getMessages(id) },
        lastDeliveryStateFor = { id -> api.lastMessageDeliveryState(id) },
        gbBool = { key, fallback -> prefs.gbBool(key, fallback) },
        gbColor = { key -> prefs.gbColor(key) },
        isConversationLocked = { id -> prefs.isConversationLocked(id) },
        isConversationHidden = { id -> id in hiddenConversationIds },
        onToggleLockConversation = { id, lock -> prefs.toggleLockConversation(id, lock) },
        onToggleGhostMode = { on -> prefs.updateHome(prefs.home.copy(ghostMode = on)) },
        verifySecretPhrase = { phrase -> ServiceLocator.localLockService.verifySecretPhrase(phrase) },
        nav = ChatsHomeNav(
            // User directive: rail-hidden Tasks/Updates live in the home header
            // overflow menu and open the real destination screens (no preview).
            showTasksEntry = prefs.gbBool("nav_hide_tasks", false),
            showUpdatesEntry = prefs.gbBool("nav_hide_updates", false) && com.example.chat.ReleaseFeatures.STATUS_UPDATES,
            onOpenTasks = { onOpenDestination(com.example.chat.core.templates.ChatyNavigationDestinationIds.tasks) },
            onOpenUpdates = { onOpenDestination(com.example.chat.core.templates.ChatyNavigationDestinationIds.updates) },
            onOpenAccountSwitcher = onOpenAccountSwitcher,
            onOpenSplitChat = { selectedChatIds ->
                val chats = selectedChatIds.mapNotNull { id -> conversations.firstOrNull { it.id == id } }
                if (chats.isNotEmpty()) onOpenSplitChat(chats)
            },
            onOpenSettings = onOpenSettings,
            onOpenNotifications = onOpenNotifications,
            onOpenSecuritySettings = onOpenSecurity,
            onOpenLockedVault = onOpenLockedChats,
            onOpenNewChat = onOpenNewChat,
            onNewCall = onNewCall,
            onOpenGlobalSearch = onOpenGlobalSearch,
            onOpenMessageManagement = onOpenMessageManagement,
            onOpenStarredMessages = onOpenStarredMessages,
            onSelectPlan = onSelectPlan,
            onOpenProfile = onOpenProfile,
            onOpenQr = onOpenQr,
            onShareApp = { com.example.chat.ui.settings.ChatyShareService.shareApp(context) { toast(it) } },
            onOpenLinkedDevices = onOpenLinkedDevices,
            // Dart _promptMessageNumber: match an existing contact by phone/username, else
            // synthesize user_<digits>, then open the direct conversation.
            onMessageNumber = { raw ->
                val number = raw.trim()
                if (number.isNotEmpty() && lookupState !is com.example.chat.ui.chats.home.HomeLookupState.Loading) {
                    val token = lookupSeq + 1
                    lookupSeq = token
                    lookupState = com.example.chat.ui.chats.home.HomeLookupState.Loading(token)
                    lookupJob = scope.launch {
                        try {
                            if (!ownsRequest() || !lookupCurrent(token)) return@launch
                            val candidates=api.searchUsers(number)
                            if(!ownsRequest() || !lookupCurrent(token)) return@launch
                            val normalized=com.example.chat.data.api.RegistrationPhonePolicy.normalize(number)
                            val exact=candidates.filter { user ->
                                user.username.equals(number.removePrefix("@"),true) ||
                                    (normalized!=null && com.example.chat.data.api.RegistrationPhonePolicy.normalize(user.phone)==normalized)
                            }
                            val target=exact.singleOrNull()
                            if(target==null) { toast("No unique matching account found. Use Find people to choose a recipient."); return@launch }
                            if (target.username.isBlank()) {
                                toast("The server did not return a username. Use Find people to choose a recipient.")
                                return@launch
                            }
                            if (target.id == me?.id) {
                                toast("That is your own account. Use Find people to choose another recipient.")
                                return@launch
                            }
                            if (recipientDecision != null) return@launch
                            val decision = kotlinx.coroutines.CompletableDeferred<Boolean>()
                            pendingRecipient = target
                            recipientDecision = decision
                            try {
                                if (!decision.await() || !ownsRequest()) return@launch
                            } finally {
                                decision.complete(false)
                                if (recipientDecision === decision) {
                                    recipientDecision = null
                                    pendingRecipient = null
                                }
                            }
                            // Removed synthetic user_<digits> fallback: identity must come from the server.
                            if (!ownsRequest() || !lookupCurrent(token)) return@launch
                            val conv = api.createOrGetDirectConversation(target.id, expectedScope = owner)
                            if(ownsRequest() && lookupCurrent(token)) onOpenConversation(conv)
                        } catch (cancel: kotlinx.coroutines.CancellationException) {
                            throw cancel
                        } catch (e: Exception) {
                            val kind = com.example.chat.ui.chats.home.classifyFailure(e)
                            if (ownsRequest() && lookupCurrent(token)) {
                                lookupState = com.example.chat.ui.chats.home.HomeLookupState.Failed(token, kind)
                                if (kind != com.example.chat.ui.chats.home.HomeFailureKind.Cancelled)
                                    toast(homeTypedFailureMessage(e, kind, "Could not start chat. Check the recipient and try again."))
                            }
                        } finally {
                            if (lookupCurrent(token)) lookupState = com.example.chat.ui.chats.home.HomeLookupState.Idle
                        }
                    }
                }
            },
            // Dart: widget.themeController.toggleBrightness.
            onToggleBrightness = { ServiceLocator.themeController.toggleBrightness() },
            // Dart: MethodChannel('chaty/launcher_icon').restartApp -> relaunch the app task.
            onRestartApp = {
                try {
                    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
                        ?: throw IllegalStateException("no launch intent")
                    launch.addFlags(
                        android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK,
                    )
                    context.startActivity(launch)
                    Runtime.getRuntime().exit(0)
                } catch (e: Exception) {
                    // Removed raw exception output: toast("Chaty could not restart: ${e}")
                    toast("Chaty could not restart. Close and reopen the app.")
                }
            },
        ),
        onTogglePin = { id -> find(id)?.let { setState(id, "pinned", !it.isPinned) } },
        onToggleMute = { id -> find(id)?.let { setState(id, "muted", !it.isMuted) } },
        onToggleArchive = { id -> find(id)?.let { setState(id, "archived", !it.isArchived) } },
        onMarkRead = { id -> trackedMarkRead(id) },
        onMarkUnread = { id -> trackedMarkUnread(id) },
        onMarkAllRead = { ids -> trackedMarkAllRead(ids) },
        onDeleteConversations = { ids ->
            // Dart ChatyDataStore.deleteConversation: custom-backend delete, then the Supabase one.
            if (beginOp("delete", ids.sorted().joinToString(","))) scope.launch {
              try {
                var failed=0
                for (id in ids) {
                    if(!ownsRequest()) break
                    try {if(api.isAuthenticated) api.deleteConversation(id) else failed++} catch (cancel:kotlinx.coroutines.CancellationException) {throw cancel} catch (_:Exception) {failed++}
                }
                // Removed second repository delete: Home is backed by api.conversations.
                if(ownsRequest()) toast(if(failed==0) "Selected chat deletion completed" else "$failed chat deletions could not be confirmed. Refresh before retrying.")
              } finally { endOp("delete", ids.sorted().joinToString(",")) }
            }
        },
        onStartCall = { conversation, isVideo ->
            val myId = me?.id
            val otherId = conversation.participantIds.firstOrNull { it != myId }
            val other = api.users.firstOrNull { it.id == otherId }
            if (otherId != null && conversation.type == ConversationType.direct) {
                ServiceLocator.callPresentationController.showFullScreen()
                scope.launch {
                    try {
                        val allowed=ProtectedResourceGate.authorizeConversation(
                            conversationId=conversation.id,isProtected=prefs.isConversationLocked(conversation.id),
                            autoLockTimeout=security.autoLockTimeout,lockMethod=security.lockMethod,
                            lockService=ServiceLocator.localLockService,setupPrompt=gate.setupPrompt,lockPrompt=gate.lockPrompt)
                        if(!allowed || !ownsRequest()) return@launch
                        ServiceLocator.callSignaling.initiateCall(
                            otherId, other?.displayName ?: conversation.title,
                            other?.avatarInitials, other?.avatarColorHex, isVideo,
                        )
                    } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel
                    } catch (e: Exception) { if (ownsRequest()) toast(e.message ?: homeFailureMessage(e, "Unable to start the call.")) }
                }
            }
        },
        onSnack = { toast(it) },
        onAuthorizeConversation = { conversation, proceed ->
            scope.launch {
                val ok = ProtectedResourceGate.authorizeConversation(
                    conversationId = conversation.id,
                    isProtected = prefs.isConversationLocked(conversation.id),
                    autoLockTimeout = security.autoLockTimeout,
                    lockMethod = security.lockMethod,
                    lockService = ServiceLocator.localLockService,
                    setupPrompt = gate.setupPrompt,
                    lockPrompt = gate.lockPrompt,
                )
                if (ok && ownsRequest() && api.conversations.any {it.id==conversation.id}) proceed()
            }
        },
        preferencesController = prefs,
        // b116: same contacts-plane contract as Settings, bound to this auth snapshot.
        loadCallPolicy = remember<suspend () -> com.example.chat.data.api.ApiContactsClient.CallPolicy>(api, owner) { { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            api.contactsPlane(owner).callPolicy()
        } } },
        saveCallPolicy = remember<suspend (String, List<String>?) -> com.example.chat.data.api.ApiContactsClient.CallPolicy>(api, owner) { { policy, exceptions -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            api.contactsPlane(owner).putCallPolicy(policy, exceptions)
        } } },
        connectionHealthState = ServiceLocator.connectionHealth.state,
        // Dart GlobalConnectionBanner/indicator tap: ConnectionDetailSheet.show(context).
        onOpenConnectionDetail = { connectionSheetOpen = true },
        lockedChatsConfigured = security.lockedConversationIds.isNotEmpty() || security.isAppLockEnabled,
        onRequestVaultAuth = { authorized ->
            scope.launch {
                val ok = ProtectedResourceGate.authorizeGeneralAction(
                    lockMethod = security.lockMethod,
                    lockService = ServiceLocator.localLockService,
                    lockPrompt = gate.lockPrompt,
                    title = "Locked Chats",
                    reason = "Authenticate to open Locked Chats",
                )
                if (ok && ownsRequest()) authorized()
            }
        },
    )
    // Dart ConnectionDetailSheet.show: diagnostics dialog with a Run test action.
    if (connectionSheetOpen) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { connectionSheetOpen = false }) {
            androidx.compose.foundation.layout.Column {
                TextButton(onClick = { homeDiagnosticsOpen = true }) {
                    Text("Read-only Home summary")
                }
                androidx.compose.foundation.layout.Box(Modifier.weight(1f, fill = false)) {
            com.example.chat.ui.chats.detail.ConnectionDetailSheetContent(
                state = ServiceLocator.connectionHealth.state,
                theme = com.example.chat.ui.theme.LocalThemeConfig.current,
                testing = connectionTesting,
                onRunTest = {
                    if (!connectionTesting) {
                        connectionTesting = true
                        scope.launch {
                            try { ServiceLocator.connectionHealth.probeConnection() } catch (_: Exception) {} finally { connectionTesting = false }
                        }
                    }
                },
                onDismiss = { connectionSheetOpen = false },
            )
                }
            }
        }
    }
    if (homeDiagnosticsOpen) {
        val health = ServiceLocator.connectionHealth.state
        val loadedCount = conversations.count {
            it.id !in hiddenConversationIds && !prefs.isConversationLocked(it.id)
        }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { homeDiagnosticsOpen = false },
            title = { Text("Read-only Home summary") },
            text = {
                androidx.compose.foundation.layout.Column(
                    Modifier.verticalScroll(rememberScrollState()),
                ) {
                    Text("Loaded visible, unlocked conversations: $loadedCount")
                    Text("Realtime connection: ${health.realtimeStatus.name}")
                    Text("Connection snapshot timestamp: ${health.lastCheckedAt}")
                    Text("Last completed Home sync: unavailable. Connection status does not confirm sync completion.")
                    Text("This summary is shown only on this device. It contains no message text, recipient identifiers or tokens. Opening it does not run a test, save a report or upload it.")
                }
            },
            confirmButton = {
                TextButton(onClick = { homeDiagnosticsOpen = false }) { Text("Close") }
            },
        )
    }
    val recipient = pendingRecipient
    val decision = recipientDecision
    if (recipient != null && decision != null && ownsRequest()) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { decision.complete(false); cancelLookup() },
            title = { Text("Confirm recipient") },
            text = {
                androidx.compose.foundation.layout.Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Server-resolved username: @${recipient.username}")
                    Text("Starting from: @${me?.username.orEmpty()}")
                    Text("Organization context is unavailable. A matching username is not proof that you know or trust this person.")
                }
            },
            confirmButton = {
                TextButton(onClick = { decision.complete(ownsRequest()) }) { Text("Start chat") }
            },
            dismissButton = {
                TextButton(onClick = { decision.complete(false); cancelLookup() }) { Text("Cancel") }
            },
        )
    }

}

/** "Confirm your email" banner for unverified accounts (research 4a.4). */
@Composable
private fun EmailVerificationBanner(email: String, busy:Boolean, retrySeconds:Int, onResend: () -> Unit) {
    val theme = com.example.chat.ui.theme.LocalThemeConfig.current
    Surface(
        color = Color(com.example.chat.core.theme.argbWithAlpha(theme.accentColor, 0.12)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Text(
                "Confirm your email ${com.example.chat.ui.auth.OtpVerificationLogic.maskEmail(email)} to secure your account.",
                color = Color(theme.primaryTextColor),
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onResend,enabled=!busy && retrySeconds==0) {
                Text(
                    if(busy) "Sending..." else if(retrySeconds>0) "Retry in ${retrySeconds}s" else "Send code",
                    color = Color(theme.accentColor),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

/** Host-local failure wording: offline and timeout failures say so, everything else keeps [fallback].
 *  Never includes exception text, so no recipient or message content leaks into a toast. */
private fun homeFailureMessage(t: Throwable, fallback: String): String = when (t) {
    is java.net.UnknownHostException, is java.net.ConnectException ->
        "You appear to be offline. Check your connection and try again."
    is java.net.SocketTimeoutException -> "The server took too long to respond. Try again."
    else -> fallback
}

/** Typed wording for Home operations. Offline and server-rejected use the content-free copy from
 *  [classifyFailure]; anything else keeps the existing host wording. Never includes exception text. */
private fun homeTypedFailureMessage(t: Throwable, kind: com.example.chat.ui.chats.home.HomeFailureKind, fallback: String): String = when (kind) {
    com.example.chat.ui.chats.home.HomeFailureKind.Offline, com.example.chat.ui.chats.home.HomeFailureKind.ServerRejected -> kind.feedback()
    else -> homeFailureMessage(t, fallback)
}

// Pre-review source retained.
// package com.example.chat.ui
//
// import androidx.compose.foundation.rememberScrollState
// import androidx.compose.foundation.verticalScroll
// import androidx.compose.foundation.layout.Row
// import androidx.compose.foundation.layout.fillMaxWidth
// import androidx.compose.foundation.layout.padding
// import androidx.compose.foundation.shape.RoundedCornerShape
// import androidx.compose.material3.Surface
// import androidx.compose.material3.Text
// import androidx.compose.material3.TextButton
// import androidx.compose.runtime.Composable
// import androidx.compose.ui.Alignment
// import androidx.compose.ui.Modifier
// import androidx.compose.ui.graphics.Color
// import androidx.compose.ui.text.font.FontWeight
// import androidx.compose.ui.unit.dp
// import androidx.compose.ui.unit.sp
// import androidx.compose.runtime.DisposableEffect
// import androidx.compose.runtime.getValue
// import androidx.compose.runtime.mutableIntStateOf
// import androidx.compose.runtime.remember
// import androidx.compose.runtime.rememberCoroutineScope
// import androidx.compose.runtime.setValue
// import androidx.compose.ui.platform.LocalContext
// import android.widget.Toast
// import com.example.chat.ServiceLocator
// import com.example.chat.data.services.ProtectedResourceGate
// import com.example.chat.domain.models.Conversation
// import com.example.chat.domain.models.ConversationType
// import com.example.chat.ui.chats.ChatsHomeScreen
// import com.example.chat.ui.chats.home.ChatsHomeNav
// import com.example.chat.ui.chats.home.HomeIdentity
// import kotlinx.coroutines.launch
//
// /**
//  * Shared host for ChatsHomeScreen (chats and groups destinations). Wires the
//  * screen's preference, identity, row-action, call, lock and vault seams to the
//  * real services. Conversation row state changes go through
//  * ConversationsRepository, as Dart ChatyDataStore does (toggle and mark methods).
//  */
// @Composable
// fun ChatsHomeHost(
//     forcedType: ConversationType?,
//     pageTitle: String?,
//     gate: GatePrompts,
//     onOpenConversation: (Conversation) -> Unit,
//     onOpenSecurity: () -> Unit,
//     onOpenLockedChats: () -> Unit,
//     onOpenSettings: () -> Unit,
//     onOpenAccountSwitcher: () -> Unit = onOpenSettings,
//     onVerifyEmail: (String, String) -> Unit = { _, _ -> },
//     onOpenSplitChat: (List<Conversation>) -> Unit = {},
//     onOpenNotifications: () -> Unit = {},
//     onOpenNewChat: (groupMode: Boolean, broadcastMode: Boolean) -> Unit = { _, _ -> },
//     onNewCall: () -> Unit = {},
//     onOpenGlobalSearch: () -> Unit = {},
//     onOpenMessageManagement: () -> Unit = {},
//     onOpenStarredMessages: () -> Unit = {},
//     onSelectPlan: () -> Unit = {},
//     onOpenProfile: () -> Unit = {},
//     onOpenQr: () -> Unit = {},
//     onOpenLinkedDevices: () -> Unit = {},
//     /** Opens a root destination (tasks/updates) as a pushed screen; used when the
//      *  user hides them from the rail into the home header overflow menu. */
//     onOpenDestination: (String) -> Unit = {},
//     /** R7 expanded list-detail: when true and [chatPane] is given, the open
//      *  conversation renders beside the list instead of pushing a route. */
//     expandedListDetail: Boolean = false,
//     paneConversation: Conversation? = null,
//     chatPane: (@Composable (Conversation) -> Unit)? = null,
// ) {
//     val api = ServiceLocator.apiBackend
//     val prefs = ServiceLocator.preferencesController
//     val repo = ServiceLocator.conversationsRepository
//     val scope = rememberCoroutineScope()
//     val context = LocalContext.current
//     var rev by remember { mutableIntStateOf(0) }
//     var connectionSheetOpen by remember { androidx.compose.runtime.mutableStateOf(false) }
//     var connectionTesting by remember { androidx.compose.runtime.mutableStateOf(false) }
//     val templateController = ServiceLocator.templateController
//     DisposableEffect(api, prefs, templateController) {
//         val l: () -> Unit = { rev++ }
//         api.addListener(l)
//         prefs.addListener(l)
//         templateController.addListener(l)
//         onDispose {
//             api.removeListener(l)
//             prefs.removeListener(l)
//             templateController.removeListener(l)
//         }
//     }
//     androidx.compose.runtime.LaunchedEffect(api.isAuthenticated, api.currentUser?.id) {
//         if (api.isAuthenticated) api.ensureInitialSyncIfNeeded()
//     }
//     @Suppress("UNUSED_EXPRESSION") rev
//     val owner = api.authSnapshot()
//     var pendingRecipient by remember(owner) {
//         androidx.compose.runtime.mutableStateOf<com.example.chat.domain.models.UserProfile?>(null)
//     }
//     var recipientDecision by remember(owner) {
//         androidx.compose.runtime.mutableStateOf<kotlinx.coroutines.CompletableDeferred<Boolean>?>(null)
//     }
//     var homeDiagnosticsOpen by remember(owner) { androidx.compose.runtime.mutableStateOf(false) }
//     DisposableEffect(owner, recipientDecision) {
//         val decision = recipientDecision
//         onDispose { decision?.complete(false) }
//     }
//     var verificationBusy by remember(owner) { androidx.compose.runtime.mutableStateOf(false) }
//     var verificationRetryAt by remember(owner) { androidx.compose.runtime.mutableStateOf(0L) }
//     var verificationWait by remember(owner) { androidx.compose.runtime.mutableIntStateOf(0) }
//     androidx.compose.runtime.LaunchedEffect(owner,verificationRetryAt) {
//         while(true) {
//             verificationWait=((verificationRetryAt-android.os.SystemClock.elapsedRealtime()+999)/1000).toInt().coerceAtLeast(0)
//             if(verificationWait==0) break
//             kotlinx.coroutines.delay(1000)
//         }
//     }
//     fun ownsRequest() = api.authSnapshot() === owner
//     // Host-owned in-flight guard: one running request per (action, chat) so a double tap
//     // cannot start the same mutation twice. Cleared when the request ends or the account changes.
//     val inFlightOps = remember(owner) { java.util.concurrent.ConcurrentHashMap.newKeySet<String>() }
//     fun beginOp(action: String, chatId: String = ""): Boolean = inFlightOps.add("$action|$chatId")
//     fun endOp(action: String, chatId: String = "") { inFlightOps.remove("$action|$chatId") }
//     val me = api.currentUser
//     val conversations = api.conversations
//     fun find(id: String) = conversations.firstOrNull { it.id == id }
//     fun toast(m: String) = Toast.makeText(context, m, Toast.LENGTH_SHORT).show()
//     fun setState(id: String, field: String, value: Boolean) {
//         // Stored on this device: the custom backend has no per-user chat state API yet.
//         api.setConversationLocalState(id, field, value)
//     }
//     // Account-owned transient operation state (not persisted, no retry queue). Keyed by owner so an
//     // account switch drops it; every write below also re-checks ownsRequest().
//     var opState by remember(owner) {
//         androidx.compose.runtime.mutableStateOf<Map<com.example.chat.ui.chats.home.HomeOpKey, com.example.chat.ui.chats.home.HomeOpState>>(emptyMap())
//     }
//     var nextOpRequestId by remember(owner) { androidx.compose.runtime.mutableStateOf(0L) }
//     fun dispatchOp(event: com.example.chat.ui.chats.home.HomeOpEvent) { if (ownsRequest()) opState = com.example.chat.ui.chats.home.reduce(opState, event) }
//     // One tracked adapter call per (action, chat). Existing beginOp/endOp guard and toasts are kept;
//     // this only adds Pending/Succeeded/Failed/Cancelled bookkeeping and typed failure wording.
//     fun runTracked(
//         action: String,
//         chatId: String,
//         retryable: Boolean,
//         fallback: String,
//         onSuccess: () -> Unit = {},
//         block: suspend () -> Unit,
//     ) {
//         if (!beginOp(action, chatId)) return
//         val key = com.example.chat.ui.chats.home.HomeOpKey(action, chatId)
//         val requestId = nextOpRequestId + 1
//         nextOpRequestId = requestId
//         dispatchOp(com.example.chat.ui.chats.home.HomeOpEvent.Start(key, requestId))
//         if ((opState[key] as? com.example.chat.ui.chats.home.HomeOpState.Pending)?.requestId != requestId) { endOp(action, chatId); return }
//         scope.launch {
//             try {
//                 block()
//                 dispatchOp(com.example.chat.ui.chats.home.HomeOpEvent.Succeed(key, requestId))
//                 if (ownsRequest()) onSuccess()
//             } catch (cancel: kotlinx.coroutines.CancellationException) {
//                 dispatchOp(com.example.chat.ui.chats.home.HomeOpEvent.Cancel(key, requestId))
//                 throw cancel
//             } catch (e: Exception) {
//                 val kind = try { com.example.chat.ui.chats.home.classifyFailure(e) } catch (c: kotlinx.coroutines.CancellationException) {
//                     dispatchOp(com.example.chat.ui.chats.home.HomeOpEvent.Cancel(key, requestId))
//                     throw c
//                 }
//                 val intent = if (retryable && kind != com.example.chat.ui.chats.home.HomeFailureKind.Cancelled) com.example.chat.ui.chats.home.HomeRetryIntent(key, owner) else null
//                 dispatchOp(com.example.chat.ui.chats.home.HomeOpEvent.Fail(key, requestId, kind, intent))
//                 if (ownsRequest() && kind != com.example.chat.ui.chats.home.HomeFailureKind.Cancelled) toast(homeTypedFailureMessage(e, kind, fallback))
//             } finally { endOp(action, chatId) }
//         }
//     }
//     fun trackedMarkRead(id: String) = runTracked("read", id, true, "Could not mark chat as read. Try again.") { repo.markAsRead(id) }
//     fun trackedMarkUnread(id: String) = runTracked("unread", id, true, "Could not mark chat as unread. Try again.") { repo.markAsUnread(id) }
//     fun trackedMarkAllRead() = runTracked("read_all", "", true, "Could not mark all chats as read. Try again.",
//         onSuccess = { toast("Mark-all-read request completed") }) { repo.markAllAsRead() }
//     // Manual refresh through the existing scope-guarded API only. That call swallows a failed fetch,
//     // so no success or failure is claimed here: the list simply updates if the fetch applied.
//     var homeRefreshing by remember(owner) { androidx.compose.runtime.mutableStateOf(false) }
//     fun refreshHome() {
//         if (homeRefreshing || !ownsRequest()) return
//         homeRefreshing = true
//         scope.launch {
//             try {
//                 kotlinx.coroutines.withTimeout(20_000L) { api.loadConversationsIfNeeded(force = true) }
//             } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
//                 if (ownsRequest()) toast("Refresh took too long. Try again.")
//             } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel
//             } catch (e: Exception) {
//                 if (ownsRequest()) toast(homeTypedFailureMessage(e, com.example.chat.ui.chats.home.classifyFailure(e), "Could not refresh. Try again."))
//             } finally { if (ownsRequest()) homeRefreshing = false }
//         }
//     }
//     // Message-a-number lookup lifecycle: one at a time, token-checked, cancelled on dismiss/account change.
//     var lookupState by remember(owner) {
//         androidx.compose.runtime.mutableStateOf<com.example.chat.ui.chats.home.HomeLookupState>(com.example.chat.ui.chats.home.HomeLookupState.Idle)
//     }
//     var lookupJob by remember(owner) { androidx.compose.runtime.mutableStateOf<kotlinx.coroutines.Job?>(null) }
//     var lookupSeq by remember(owner) { androidx.compose.runtime.mutableStateOf(0L) }
//     fun lookupCurrent(token: Long) = (lookupState as? com.example.chat.ui.chats.home.HomeLookupState.Loading)?.requestId == token
//     fun cancelLookup() { lookupJob?.cancel(); lookupJob = null; lookupState = com.example.chat.ui.chats.home.HomeLookupState.Idle }
//     DisposableEffect(owner) { onDispose { lookupJob?.cancel() } }
//     androidx.compose.runtime.LaunchedEffect(owner, api.conversations.map { it.id }) {
//         dispatchOp(com.example.chat.ui.chats.home.HomeOpEvent.Prune(api.conversations.map { it.id }.toSet()))
//     }
//     val security = prefs.security
//     val hiddenConversationIds = security.hiddenConversationIds.toSet()
//     val headerTabs = com.example.chat.ui.navigation.LocalHomeHeaderTabs.current
//     val headerTabsTheme = com.example.chat.ui.theme.LocalThemeConfig.current
//     val headerTabsColors = androidx.compose.runtime.remember(headerTabsTheme) {
//         com.example.chat.ui.core.theme.ChatyColors(headerTabsTheme)
//     }
//     val headerBottom: (@Composable () -> Unit)? = if (headerTabs != null && headerTabs.active) {
//         {
//             com.example.chat.ui.navigation.HomeHeaderTabsBar(
//                 headerTabsColors, headerTabs.ids, headerTabs.selectedIndex, headerTabs.heightDp,
//                 onSelect = { index -> headerTabs.onSelect(index) },
//             )
//         }
//     } else null
//     val onRetryOp: (com.example.chat.ui.chats.home.HomeOpKey) -> Unit = { key ->
//         val intent = (opState[key] as? com.example.chat.ui.chats.home.HomeOpState.Failed)?.retryIntent
//         if (intent != null && ownsRequest() &&
//             intent.isValidFor(owner, conversations.map { it.id }.toSet()) &&
//             (key.chatId.isEmpty() || (!prefs.isConversationLocked(key.chatId) && key.chatId !in hiddenConversationIds))
//         ) {
//             when (key.action) {
//                 "read" -> trackedMarkRead(key.chatId)
//                 "unread" -> trackedMarkUnread(key.chatId)
//                 "read_all" -> trackedMarkAllRead()
//             }
//         }
//     }
//     ChatsHomeScreen(
//         opState = opState,
//         onRetry = onRetryOp,
//         onRefreshHome = { refreshHome() },
//         homeRefreshing = homeRefreshing,
//         accountSessionIdentity = api.authSnapshot(),
//         folderAccountKey = api.currentAccountHash().orEmpty(),
//         headerBottom = headerBottom,
//         // Email-verification nudge (research 4a.4): signed-in but unverified
//         // users get a banner with one-tap resend; verification is never forced
//         // and the banner lives only in this composition (reappears next launch).
//         connectionBanner = if (me != null && !me!!.emailVerified) {
//             {
//                 EmailVerificationBanner(
//                     email = me?.email ?: "",
//                     busy = verificationBusy,
//                     retrySeconds = verificationWait,
//                     onResend = {
//                         if(!verificationBusy && verificationWait==0) {
//                         verificationBusy=true
//                         scope.launch {
//                             val address = me?.email ?: ""
//                             try {
//                                 val message = api.sendEmailVerification(address)
//                                 if(ownsRequest()) onVerifyEmail(address, message)
//                             } catch(cancel:kotlinx.coroutines.CancellationException) { throw cancel
//                             } catch (e: Exception) {
//                                 if(ownsRequest()) {
//                                     if(e is com.example.chat.data.api.AuthRateLimitException)
//                                         verificationRetryAt=android.os.SystemClock.elapsedRealtime()+e.retryAfterSeconds*1000L
//                                     toast(com.example.chat.ui.auth.OtpVerificationLogic.displayError(e))
//                                 }
//                             } finally { if(ownsRequest()) verificationBusy=false }
//                         }
//                         }
//                     },
//                 )
//             }
//         } else null,
//         templates = com.example.chat.ui.chats.home.resolveHomeTemplates(templateController.config, com.example.chat.core.preferences.HomePresentationPolicy.isDefault(prefs.home.homeStyle), templateController.chatList),
//         conversations = conversations,
//         requiresSubscription = api.requiresSubscription,
//         subscriptionMessage = api.subscriptionErrorMessage,
//         onOpenConversation = onOpenConversation,
//         expandedListDetail = expandedListDetail,
//         paneConversation = paneConversation,
//         chatPane = chatPane,
//         currentUserId = me?.id ?: "",
//         // Presence/typing published by the custom backend socket.
//         presenceFor = { conversationId, otherUserId ->
//             com.example.chat.ui.chats.home.HomePresence(
//                 isOnline = api.isUserOnline(otherUserId),
//                 isTyping = api.isTyping(conversationId, otherUserId),
//                 lastSeenAt = api.lastSeenOf(otherUserId),
//             )
//         },
//         identity = HomeIdentity(
//             displayName = me?.displayName ?: "",
//             username = me?.username ?: "",
//             about = me?.about ?: "",
//             avatarInitials = me?.avatarInitials ?: "C",
//             avatarColorHex = me?.avatarColorHex,
//             avatarUrl = me?.avatarUrl,
//         ),
//         contacts = api.users,
//         homePreferences = prefs.home,
//         securityPreferences = security,
//         forcedType = forcedType,
//         pageTitle = pageTitle,
//         messagesFor = { id -> api.getMessages(id) },
//         lastDeliveryStateFor = { id -> api.lastMessageDeliveryState(id) },
//         gbBool = { key, fallback -> prefs.gbBool(key, fallback) },
//         gbColor = { key -> prefs.gbColor(key) },
//         isConversationLocked = { id -> prefs.isConversationLocked(id) },
//         isConversationHidden = { id -> id in hiddenConversationIds },
//         onToggleLockConversation = { id, lock -> prefs.toggleLockConversation(id, lock) },
//         onToggleGhostMode = { on -> prefs.updateHome(prefs.home.copy(ghostMode = on)) },
//         verifySecretPhrase = { phrase -> ServiceLocator.localLockService.verifySecretPhrase(phrase) },
//         nav = ChatsHomeNav(
//             // User directive: rail-hidden Tasks/Updates live in the home header
//             // overflow menu and open the real destination screens (no preview).
//             showTasksEntry = prefs.gbBool("nav_hide_tasks", false),
//             showUpdatesEntry = prefs.gbBool("nav_hide_updates", false) && com.example.chat.ReleaseFeatures.STATUS_UPDATES,
//             onOpenTasks = { onOpenDestination(com.example.chat.core.templates.ChatyNavigationDestinationIds.tasks) },
//             onOpenUpdates = { onOpenDestination(com.example.chat.core.templates.ChatyNavigationDestinationIds.updates) },
//             onOpenAccountSwitcher = onOpenAccountSwitcher,
//             onOpenSplitChat = { selectedChatIds ->
//                 val chats = selectedChatIds.mapNotNull { id -> conversations.firstOrNull { it.id == id } }
//                 if (chats.isNotEmpty()) onOpenSplitChat(chats)
//             },
//             onOpenSettings = onOpenSettings,
//             onOpenNotifications = onOpenNotifications,
//             onOpenSecuritySettings = onOpenSecurity,
//             onOpenLockedVault = onOpenLockedChats,
//             onOpenNewChat = onOpenNewChat,
//             onNewCall = onNewCall,
//             onOpenGlobalSearch = onOpenGlobalSearch,
//             onOpenMessageManagement = onOpenMessageManagement,
//             onOpenStarredMessages = onOpenStarredMessages,
//             onSelectPlan = onSelectPlan,
//             onOpenProfile = onOpenProfile,
//             onOpenQr = onOpenQr,
//             onShareApp = { com.example.chat.ui.settings.ChatyShareService.shareApp(context) { toast(it) } },
//             onOpenLinkedDevices = onOpenLinkedDevices,
//             // Dart _promptMessageNumber: match an existing contact by phone/username, else
//             // synthesize user_<digits>, then open the direct conversation.
//             onMessageNumber = { raw ->
//                 val number = raw.trim()
//                 if (number.isNotEmpty() && lookupState !is com.example.chat.ui.chats.home.HomeLookupState.Loading) {
//                     val token = lookupSeq + 1
//                     lookupSeq = token
//                     lookupState = com.example.chat.ui.chats.home.HomeLookupState.Loading(token)
//                     lookupJob = scope.launch {
//                         try {
//                             val candidates=api.searchUsers(number)
//                             if(!ownsRequest() || !lookupCurrent(token)) return@launch
//                             val normalized=com.example.chat.data.api.RegistrationPhonePolicy.normalize(number)
//                             val exact=candidates.filter { user ->
//                                 user.username.equals(number.removePrefix("@"),true) ||
//                                     (normalized!=null && com.example.chat.data.api.RegistrationPhonePolicy.normalize(user.phone)==normalized)
//                             }
//                             val target=exact.singleOrNull()
//                             if(target==null) { toast("No unique matching account found. Use Find people to choose a recipient."); return@launch }
//                             if (target.username.isBlank()) {
//                                 toast("The server did not return a username. Use Find people to choose a recipient.")
//                                 return@launch
//                             }
//                             if (target.id == me?.id) {
//                                 toast("That is your own account. Use Find people to choose another recipient.")
//                                 return@launch
//                             }
//                             if (recipientDecision != null) return@launch
//                             val decision = kotlinx.coroutines.CompletableDeferred<Boolean>()
//                             pendingRecipient = target
//                             recipientDecision = decision
//                             try {
//                                 if (!decision.await() || !ownsRequest()) return@launch
//                             } finally {
//                                 decision.complete(false)
//                                 if (recipientDecision === decision) {
//                                     recipientDecision = null
//                                     pendingRecipient = null
//                                 }
//                             }
//                             // Removed synthetic user_<digits> fallback: identity must come from the server.
//                             val conv = repo.getOrCreateDirectConversationAsync(target)
//                             if(ownsRequest() && lookupCurrent(token)) onOpenConversation(conv)
//                         } catch (cancel: kotlinx.coroutines.CancellationException) {
//                             throw cancel
//                         } catch (e: Exception) {
//                             val kind = com.example.chat.ui.chats.home.classifyFailure(e)
//                             if (ownsRequest() && lookupCurrent(token)) {
//                                 lookupState = com.example.chat.ui.chats.home.HomeLookupState.Failed(token, kind)
//                                 if (kind != com.example.chat.ui.chats.home.HomeFailureKind.Cancelled)
//                                     toast(homeTypedFailureMessage(e, kind, "Could not start chat. Check the recipient and try again."))
//                             }
//                         } finally {
//                             if (lookupCurrent(token)) lookupState = com.example.chat.ui.chats.home.HomeLookupState.Idle
//                         }
//                     }
//                 }
//             },
//             // Dart: widget.themeController.toggleBrightness.
//             onToggleBrightness = { ServiceLocator.themeController.toggleBrightness() },
//             // Dart: MethodChannel('chaty/launcher_icon').restartApp -> relaunch the app task.
//             onRestartApp = {
//                 try {
//                     val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
//                         ?: throw IllegalStateException("no launch intent")
//                     launch.addFlags(
//                         android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK,
//                     )
//                     context.startActivity(launch)
//                     Runtime.getRuntime().exit(0)
//                 } catch (e: Exception) {
//                     // Removed raw exception output: toast("Chaty could not restart: ${e}")
//                     toast("Chaty could not restart. Close and reopen the app.")
//                 }
//             },
//         ),
//         onTogglePin = { id -> find(id)?.let { setState(id, "pinned", !it.isPinned) } },
//         onToggleMute = { id -> find(id)?.let { setState(id, "muted", !it.isMuted) } },
//         onToggleArchive = { id -> find(id)?.let { setState(id, "archived", !it.isArchived) } },
//         onMarkRead = { id -> trackedMarkRead(id) },
//         onMarkUnread = { id -> trackedMarkUnread(id) },
//         onMarkAllRead = { trackedMarkAllRead() },
//         onDeleteConversations = { ids ->
//             // Dart ChatyDataStore.deleteConversation: custom-backend delete, then the Supabase one.
//             if (beginOp("delete", ids.sorted().joinToString(","))) scope.launch {
//               try {
//                 var failed=0
//                 for (id in ids) {
//                     if(!ownsRequest()) break
//                     try {if(api.isAuthenticated) api.deleteConversation(id) else failed++}
//                     catch(cancel:kotlinx.coroutines.CancellationException) {throw cancel}
//                     catch(_:Exception) {failed++}
//                 }
//                 // Removed second repository delete: Home is backed by api.conversations.
//                 if(ownsRequest()) toast(if(failed==0) "Selected chat deletion completed" else "$failed chat deletions could not be confirmed. Refresh before retrying.")
//               } finally { endOp("delete", ids.sorted().joinToString(",")) }
//             }
//         },
//         onStartCall = { conversation, isVideo ->
//             val myId = me?.id
//             val otherId = conversation.participantIds.firstOrNull { it != myId }
//             val other = api.users.firstOrNull { it.id == otherId }
//             if (otherId != null && conversation.type == ConversationType.direct) {
//                 ServiceLocator.callPresentationController.showFullScreen()
//                 scope.launch {
//                     try {
//                         val allowed=ProtectedResourceGate.authorizeConversation(
//                             conversationId=conversation.id,isProtected=prefs.isConversationLocked(conversation.id),
//                             autoLockTimeout=security.autoLockTimeout,lockMethod=security.lockMethod,
//                             lockService=ServiceLocator.localLockService,setupPrompt=gate.setupPrompt,lockPrompt=gate.lockPrompt)
//                         if(!allowed || !ownsRequest()) return@launch
//                         ServiceLocator.callSignaling.initiateCall(
//                             otherId, other?.displayName ?: conversation.title,
//                             other?.avatarInitials, other?.avatarColorHex, isVideo,
//                         )
//                     } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel
//                     } catch (e: Exception) { if (ownsRequest()) toast(e.message ?: homeFailureMessage(e, "Unable to start the call.")) }
//                 }
//             }
//         },
//         onSnack = { toast(it) },
//         onAuthorizeConversation = { conversation, proceed ->
//             scope.launch {
//                 val ok = ProtectedResourceGate.authorizeConversation(
//                     conversationId = conversation.id,
//                     isProtected = prefs.isConversationLocked(conversation.id),
//                     autoLockTimeout = security.autoLockTimeout,
//                     lockMethod = security.lockMethod,
//                     lockService = ServiceLocator.localLockService,
//                     setupPrompt = gate.setupPrompt,
//                     lockPrompt = gate.lockPrompt,
//                 )
//                 if (ok && ownsRequest() && api.conversations.any {it.id==conversation.id}) proceed()
//             }
//         },
//         preferencesController = prefs,
//         // b116: same contacts-plane contract as Settings, bound to this auth snapshot.
//         loadCallPolicy = remember<suspend () -> com.example.chat.data.api.ApiContactsClient.CallPolicy>(api, owner) { { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
//             api.contactsPlane(owner).callPolicy()
//         } } },
//         saveCallPolicy = remember<suspend (String, List<String>?) -> com.example.chat.data.api.ApiContactsClient.CallPolicy>(api, owner) { { policy, exceptions -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
//             api.contactsPlane(owner).putCallPolicy(policy, exceptions)
//         } } },
//         connectionHealthState = ServiceLocator.connectionHealth.state,
//         // Dart GlobalConnectionBanner/indicator tap: ConnectionDetailSheet.show(context).
//         onOpenConnectionDetail = { connectionSheetOpen = true },
//         lockedChatsConfigured = security.lockedConversationIds.isNotEmpty() || security.isAppLockEnabled,
//         onRequestVaultAuth = { authorized ->
//             scope.launch {
//                 val ok = ProtectedResourceGate.authorizeGeneralAction(
//                     lockMethod = security.lockMethod,
//                     lockService = ServiceLocator.localLockService,
//                     lockPrompt = gate.lockPrompt,
//                     title = "Locked Chats",
//                     reason = "Authenticate to open Locked Chats",
//                 )
//                 if (ok && ownsRequest()) authorized()
//             }
//         },
//     )
//     // Dart ConnectionDetailSheet.show: diagnostics dialog with a Run test action.
//     if (connectionSheetOpen) {
//         androidx.compose.ui.window.Dialog(onDismissRequest = { connectionSheetOpen = false }) {
//             androidx.compose.foundation.layout.Column {
//                 TextButton(onClick = { homeDiagnosticsOpen = true }) {
//                     Text("Read-only Home summary")
//                 }
//                 androidx.compose.foundation.layout.Box(Modifier.weight(1f, fill = false)) {
//             com.example.chat.ui.chats.detail.ConnectionDetailSheetContent(
//                 state = ServiceLocator.connectionHealth.state,
//                 theme = com.example.chat.ui.theme.LocalThemeConfig.current,
//                 testing = connectionTesting,
//                 onRunTest = {
//                     if (!connectionTesting) {
//                         connectionTesting = true
//                         scope.launch {
//                             try { ServiceLocator.connectionHealth.probeConnection() } catch (_: Exception) {} finally { connectionTesting = false }
//                         }
//                     }
//                 },
//                 onDismiss = { connectionSheetOpen = false },
//             )
//                 }
//             }
//         }
//     }
//     if (homeDiagnosticsOpen) {
//         val health = ServiceLocator.connectionHealth.state
//         val loadedCount = conversations.count {
//             it.id !in hiddenConversationIds && !prefs.isConversationLocked(it.id)
//         }
//         androidx.compose.material3.AlertDialog(
//             onDismissRequest = { homeDiagnosticsOpen = false },
//             title = { Text("Read-only Home summary") },
//             text = {
//                 androidx.compose.foundation.layout.Column(
//                     Modifier.verticalScroll(rememberScrollState()),
//                 ) {
//                     Text("Loaded visible, unlocked conversations: $loadedCount")
//                     Text("Realtime connection: ${health.realtimeStatus.name}")
//                     Text("Connection snapshot timestamp: ${health.lastCheckedAt}")
//                     Text("Last completed Home sync: unavailable. Connection status does not confirm sync completion.")
//                     Text("This summary is shown only on this device. It contains no message text, recipient identifiers or tokens. Opening it does not run a test, save a report or upload it.")
//                 }
//             },
//             confirmButton = {
//                 TextButton(onClick = { homeDiagnosticsOpen = false }) { Text("Close") }
//             },
//         )
//     }
//     val recipient = pendingRecipient
//     val decision = recipientDecision
//     if (recipient != null && decision != null && ownsRequest()) {
//         androidx.compose.material3.AlertDialog(
//             onDismissRequest = { decision.complete(false); cancelLookup() },
//             title = { Text("Confirm recipient") },
//             text = {
//                 androidx.compose.foundation.layout.Column(Modifier.verticalScroll(rememberScrollState())) {
//                     Text("Server-resolved username: @${recipient.username}")
//                     Text("Starting from: @${me?.username.orEmpty()}")
//                     Text("Organization context is unavailable. A matching username is not proof that you know or trust this person.")
//                 }
//             },
//             confirmButton = {
//                 TextButton(onClick = { decision.complete(ownsRequest()) }) { Text("Start chat") }
//             },
//             dismissButton = {
//                 TextButton(onClick = { decision.complete(false); cancelLookup() }) { Text("Cancel") }
//             },
//         )
//     }
//
// }
//
// /** "Confirm your email" banner for unverified accounts (research 4a.4). */
// @Composable
// private fun EmailVerificationBanner(email: String, busy:Boolean, retrySeconds:Int, onResend: () -> Unit) {
//     val theme = com.example.chat.ui.theme.LocalThemeConfig.current
//     Surface(
//         color = Color(com.example.chat.core.theme.argbWithAlpha(theme.accentColor, 0.12)),
//         shape = RoundedCornerShape(12.dp),
//         modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
//     ) {
//         Row(
//             verticalAlignment = Alignment.CenterVertically,
//             modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
//         ) {
//             Text(
//                 "Confirm your email ${com.example.chat.ui.auth.OtpVerificationLogic.maskEmail(email)} to secure your account.",
//                 color = Color(theme.primaryTextColor),
//                 fontSize = 13.sp,
//                 modifier = Modifier.weight(1f),
//             )
//             TextButton(onClick = onResend,enabled=!busy && retrySeconds==0) {
//                 Text(
//                     if(busy) "Sending..." else if(retrySeconds>0) "Retry in ${retrySeconds}s" else "Send code",
//                     color = Color(theme.accentColor),
//                     fontWeight = FontWeight.SemiBold,
//                     fontSize = 13.sp,
//                 )
//             }
//         }
//     }
// }
//
// /** Host-local failure wording: offline and timeout failures say so, everything else keeps [fallback].
//  *  Never includes exception text, so no recipient or message content leaks into a toast. */
// private fun homeFailureMessage(t: Throwable, fallback: String): String = when (t) {
//     is java.net.UnknownHostException, is java.net.ConnectException ->
//         "You appear to be offline. Check your connection and try again."
//     is java.net.SocketTimeoutException -> "The server took too long to respond. Try again."
//     else -> fallback
// }
//
// /** Typed wording for Home operations. Offline and server-rejected use the content-free copy from
//  *  [classifyFailure]; anything else keeps the existing host wording. Never includes exception text. */
// private fun homeTypedFailureMessage(t: Throwable, kind: com.example.chat.ui.chats.home.HomeFailureKind, fallback: String): String = when (kind) {
//     com.example.chat.ui.chats.home.HomeFailureKind.Offline, com.example.chat.ui.chats.home.HomeFailureKind.ServerRejected -> kind.feedback()
//     else -> homeFailureMessage(t, fallback)
// }

// Replaced lines retained for source comparison (not executable).
//     fun trackedMarkRead(id: String) = runTracked("read", id, true, "Could not mark chat as read. Try again.") { repo.markAsRead(id) }
//     fun trackedMarkUnread(id: String) = runTracked("unread", id, true, "Could not mark chat as unread. Try again.") { repo.markAsUnread(id) }
//     fun trackedMarkAllRead() = runTracked("read_all", "", true, "Could not mark all chats as read. Try again.",
//         onSuccess = { toast("Mark-all-read request completed") }) { repo.markAllAsRead() }
//                 "read_all" -> trackedMarkAllRead()
//         onMarkAllRead = { trackedMarkAllRead() },
