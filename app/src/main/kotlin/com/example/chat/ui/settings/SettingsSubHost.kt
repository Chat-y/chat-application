package com.example.chat.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.example.chat.ServiceLocator
import com.example.chat.ui.settings.notifications.NotificationToastSettingsScreen
import com.example.chat.ui.settings.privacy.PrivacyCenterScreen
import com.example.chat.ui.settings.conversation.*
import com.example.chat.ui.settings.home.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.chat.ui.settings.universal.*

private enum class ConvSub { ActionBar, Bubbles, Entry, More }
private enum class HomeSub { Header, Rows, Fab, Status }
private enum class UniversalSub { Colors, Styles, Icons, HideMedia, Backup, General }

val hostedSettingsDestinations: Set<SettingsDestination> = setOf(
    SettingsDestination.Privacy, SettingsDestination.Effects, SettingsDestination.Notifications,
    SettingsDestination.BackupRestore, SettingsDestination.Universal,
    SettingsDestination.Conversation, SettingsDestination.HomeScreen, SettingsDestination.Calls,
    SettingsDestination.Permissions, SettingsDestination.StorageMedia,
    SettingsDestination.TemplateStudio, SettingsDestination.ThemeEngine,
    SettingsDestination.HomeShortcuts, SettingsDestination.WasteCleaning,
    SettingsDestination.DynamicIsland, SettingsDestination.ToastNotifications,
)

/**
 * Hosts the settings pages whose Kotlin screens take only the preference and
 * theme controllers. Returns false for destinations that still have no host
 * (the caller keeps its honest "not hosted" state for those).
 */
@Composable
fun SettingsSubHost(
    destination: SettingsDestination,
    onBack: () -> Unit,
    onSwitch: (SettingsDestination) -> Unit = {},
): Boolean {
    val screenAccountScope = androidx.compose.runtime.remember { ServiceLocator.apiBackend.authSnapshot() }
    val prefs = ServiceLocator.preferencesController
    val theme = ServiceLocator.themeController
    val context = LocalContext.current
    val themeConfig = com.example.chat.ui.theme.LocalThemeConfig.current
    val toast: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    when (destination) {
        SettingsDestination.Privacy -> PrivacyCenterScreen(
            preferences = prefs, onBack = onBack,
            // Server privacy planes (research 5.2/5.3); fail-closed lambdas
            // let the screen keep its legacy local look on pre-plane servers.
            loadPresencePrivacy = { withContext(Dispatchers.IO) { ServiceLocator.apiBackend.presencePrivacy(screenAccountScope) } },
            savePresencePrivacy = { ls, ol, ex -> withContext(Dispatchers.IO) { ServiceLocator.apiBackend.putPresencePrivacy(ls ?: "everyone", ol ?: "same_as_last_seen", ex, screenAccountScope) } },
            loadCallPolicy = { withContext(Dispatchers.IO) { ServiceLocator.apiBackend.contactsPlane(screenAccountScope).callPolicy() } },
            saveCallPolicy = { policy, ex -> withContext(Dispatchers.IO) { ServiceLocator.apiBackend.contactsPlane(screenAccountScope).putCallPolicy(policy, ex) } },
            loadBlocked = { withContext(Dispatchers.IO) { ServiceLocator.apiBackend.contactsPlane(screenAccountScope).blocks() } },
            unblockUser = { id -> withContext(Dispatchers.IO) { ServiceLocator.apiBackend.contactsPlane(screenAccountScope).unblock(id) } },
        )
        SettingsDestination.Effects -> NavigationEffectsScreen(preferences = prefs, onBack = onBack)
        SettingsDestination.ToastNotifications -> NotificationToastSettingsScreen(
            preferences = prefs, onBack = onBack,
        )
        // Dart settings_root_screen '/settings/notifications' => NotificationSettingsPage (the toast-only screen is not
        // instantiated anywhere in Dart). Dynamic Island native bridge has no Kotlin implementation: bridge = null.
// Previous implementation retained:         SettingsDestination.Notifications -> com.example.chat.ui.settings.notifications.NotificationSettingsPage(
// Previous implementation retained:             preferences = prefs, onBack = onBack,
// Previous implementation retained:             bridge = com.example.chat.ui.island.AndroidDynamicIslandBridge(androidx.compose.ui.platform.LocalContext.current),
// Previous implementation retained:             // Dart _previewDynamicIsland fallback: notificationService.triggerEventNotification(chat_bubble_rounded icon).
// Previous implementation retained:             triggerEventNotification = { title, body, accent ->
// Previous implementation retained:                 ServiceLocator.notifications.triggerEventNotification(
// Previous implementation retained:                     title = title, body = body,
// Previous implementation retained:                     icon = com.example.chat.data.realtime.EventNotificationIcon.chatBubble, colorArgb = accent,
// Previous implementation retained:                 )
// Previous implementation retained:             },
// Previous implementation retained:         )
        SettingsDestination.Notifications -> {
            // Dedicated toast customization remains separate; the existing Notifications page is unchanged.
            var showToastSettings by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
            BackHandler(enabled = showToastSettings) { showToastSettings = false }
            if (showToastSettings) {
                NotificationToastSettingsScreen(preferences = prefs, onBack = { showToastSettings = false })
            } else {
                androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.fillMaxSize()) {
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.weight(1f)) {
                        com.example.chat.ui.settings.notifications.NotificationSettingsPage(
            preferences = prefs, onBack = onBack,
            bridge = com.example.chat.ui.island.AndroidDynamicIslandBridge(androidx.compose.ui.platform.LocalContext.current),
            // Dart _previewDynamicIsland fallback: notificationService.triggerEventNotification(chat_bubble_rounded icon).
            triggerEventNotification = { title, body, accent ->
                ServiceLocator.notifications.triggerEventNotification(
                    title = title, body = body,
                    icon = com.example.chat.data.realtime.EventNotificationIcon.chatBubble, colorArgb = accent,
                )
            },
        )
                    }
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.navigationBarsPadding()) {
                    com.example.chat.ui.kit.GbSettingRow(
                        title = context.getString(com.example.chat.R.string.call_toast_settings_entry),
// Previous implementation retained:                         subtitle = context.getString(com.example.chat.R.string.call_toast_settings_subtitle),
                        subtitle = buildList {
                            val t = prefs.toasts
                            if (t.onlineToastEnabled) add("Online")
                            if (t.storyToastEnabled) add("Story")
                            if (t.profileToastEnabled) add("Profile")
                            if (t.typingToastEnabled) add("Typing")
                        }.let { enabled -> if (enabled.isEmpty()) "All toast types off" else enabled.joinToString() + " on" },
                        hasSubScreen = true,
                        onTap = { showToastSettings = true },
                    )
                    }
                }
            }
        }
        SettingsDestination.BackupRestore -> BackupAndRestoreScreen(
            preferences = prefs, onBack = onBack, onMessage = toast,
        )
        SettingsDestination.Universal -> {
            var sub by remember { mutableStateOf<UniversalSub?>(null) }
            val back = { if (sub == null) onBack() else sub = null }
            BackHandler { back() }
            when (sub) {
                null -> UniversalScreen(
                    preferences = prefs, onBack = onBack,
                    onOpenColors = { sub = UniversalSub.Colors },
                    onOpenStyles = { sub = UniversalSub.Styles },
                    onOpenChatyIcons = { sub = UniversalSub.Icons },
                    onOpenHideMedia = { sub = UniversalSub.HideMedia },
                    onOpenBackupRestore = { sub = UniversalSub.Backup },
                    onOpenGeneralSettings = { sub = UniversalSub.General },
                )
                UniversalSub.Colors -> UniversalColorsScreen(preferences = prefs, onBack = { sub = null })
                UniversalSub.Styles -> UniversalStylesScreen(
                    preferences = prefs, onBack = { sub = null }, themeController = theme, onMessage = toast,
                    appIconController = ServiceLocator.appIconController,
                )
                UniversalSub.Icons -> UniversalChatyIconsScreen(preferences = prefs, onBack = { sub = null }, onMessage = toast)
                UniversalSub.HideMedia -> HideMediaScreen(preferences = prefs, themeController = theme, onBack = { sub = null })
                UniversalSub.Backup -> BackupAndRestoreScreen(preferences = prefs, onBack = { sub = null }, onMessage = toast)
                UniversalSub.General -> UniversalGeneralSettingsScreen(preferences = prefs, themeController = theme, onBack = { sub = null })
            }
        }
        SettingsDestination.Conversation -> {
            var sub by remember { mutableStateOf<ConvSub?>(null) }
            BackHandler(enabled = sub != null) { sub = null }
            when (sub) {
                null -> ConversationSettingsPage(
                    preferences = prefs, onBack = onBack,
                    onOpenActionBar = { sub = ConvSub.ActionBar },
                    onOpenBubbleAndTicks = { sub = ConvSub.Bubbles },
                    onOpenEntryStyle = { sub = ConvSub.Entry },
                    onOpenMoreOptions = { sub = ConvSub.More },
                )
                ConvSub.ActionBar -> ConversationActionBarScreen(preferences = prefs, onBack = { sub = null })
                ConvSub.Bubbles -> ConversationBubbleAndTicksScreen(preferences = prefs, onBack = { sub = null })
                ConvSub.Entry -> ConversationEntryStyleScreen(preferences = prefs, onBack = { sub = null })
                ConvSub.More -> ConversationMoreOptionsScreen(preferences = prefs, onBack = { sub = null })
            }
        }
        SettingsDestination.HomeScreen -> {
            var sub by remember { mutableStateOf<HomeSub?>(null) }
            BackHandler(enabled = sub != null) { sub = null }
            when (sub) {
                null -> HomeScreenSettingsPage(
                    preferences = prefs, onBack = onBack,
                    onOpenHeader = { sub = HomeSub.Header },
                    onOpenRows = { sub = HomeSub.Rows },
                    onOpenFab = { sub = HomeSub.Fab },
                    onOpenStatus = { sub = HomeSub.Status },
                )
                HomeSub.Header -> {
                    var overflow by remember { mutableStateOf<(() -> Unit)?>(null) }
                    val me = ServiceLocator.apiBackend.currentUser
                    HeaderSettingsScreen(
                        preferences = prefs,
                        navigationMode = theme.navigationMode,
                        onSetNavigationMode = { theme.setNavigationMode(it) },
                        currentUserDisplayName = me?.displayName,
                        currentUserUsername = me?.username,
                        currentUserAbout = me?.about,
                        headerPreview = { args -> GbLiveHeaderPreview(args, theme.globalTheme) },
                        showOverflowMenu = { onSettings -> overflow = onSettings },
                        onBack = { sub = null },
                    )
                    overflow?.let { onSettings ->
                        GbHeaderOverflowMenuDialog(
                            t = theme.globalTheme,
                            actions = GbHeaderOverflowActions(onSettings = onSettings),
                            onDismiss = { overflow = null },
                        )
                    }
                }
                HomeSub.Rows -> HomeRowsSettingsScreen(preferences = prefs, onBack = { sub = null })
                HomeSub.Fab -> HomeFabSettingsScreen(preferences = prefs, onBack = { sub = null })
                HomeSub.Status -> StatusSettingsScreen(preferences = prefs, onBack = { sub = null })
            }
        }
        SettingsDestination.Calls -> {
            val store = ServiceLocator.callPresentationPrefs
            var rev by remember { androidx.compose.runtime.mutableIntStateOf(0) }
            androidx.compose.runtime.DisposableEffect(store) {
                val l: () -> Unit = { rev++ }
                store.addListener(l)
                onDispose { store.removeListener(l) }
            }
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            var audio by remember { mutableStateOf(false) }
            BackHandler(enabled = audio) { audio = false }
            @Suppress("UNUSED_EXPRESSION") rev
            var showBundles by remember { mutableStateOf(false) }
            if (audio) {
                com.example.chat.ui.settings.AudioSettingsScreen(
                    settings = ServiceLocator.audioSettingsController,
                    capability = ServiceLocator.audioCapabilityService,
                    coordinator = ServiceLocator.audioPolicyCoordinator,
                    liveCalls = ServiceLocator.callSignaling,
                    scope = scope,
                    onBack = { audio = false },
                )
            } else if (showBundles) {
                com.example.chat.ui.calls.bundles.CallExperienceBundlePickerScreen(
                    currentBundleId = store.value.callExperienceBundleId,
                    onApplyScopedBundle = { bundleId, target -> store.applyExperienceBundle(bundleId, target) },
                    onApplyBundle = { bundleId ->
                        scope.launch {
                            try {
                                // Previous detached call: store.setCallExperienceBundle(bundleId)
                                store.applyExperienceBundle(bundleId, com.example.chat.ui.calls.BundleApplyTarget.CALLS)
                            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                            catch (e: Exception) {
                                toast("Unable to apply call bundle. Check the selected bundle and try again.")
                            }
                        }
                    },
                    onBack = { showBundles = false },
                )
            } else {
                val v = store.value
                // Previous launch-only wrapper retained below; awaiting propagates storage failures.
                suspend fun saveCallOption(block: suspend () -> Unit): Result<Unit> = try {
                    check(ServiceLocator.apiBackend.authSnapshot() === screenAccountScope)
                    block()
                    Result.success(Unit)
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (failure: Exception) { Result.failure(failure) }
                // fun run(block: suspend () -> Unit) {
                //     scope.launch { try { block() } catch (e: Exception) { toast(e.message ?: "Unable to save.") } }
                // }
                // CS1-10: same IO contacts-plane load as Privacy Center (above); failure stays null
                // so the screen shows no server claim. Load once per entry into this destination.
                var serverCallAudience by remember(screenAccountScope) { mutableStateOf<String?>(null) }
                androidx.compose.runtime.LaunchedEffect(screenAccountScope) {
                    try {
                        val policy = withContext(Dispatchers.IO) { ServiceLocator.apiBackend.contactsPlane(screenAccountScope).callPolicy().policy }
                        if (ServiceLocator.apiBackend.authSnapshot() === screenAccountScope)
                            serverCallAudience = com.example.chat.ui.settings.privacy.PrivacyOptionsLogic.audienceLabel(policy)
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: Exception) { serverCallAudience = null }
                }
                val audioMode by ServiceLocator.audioSettingsController.preferenceModeState.collectAsState()
                val audioSaveFailed by ServiceLocator.audioSettingsController.saveError.collectAsState()
                CallSettingsScreen(
                    preferences = prefs,
                    audioPreferenceSummary = callAudioPreferenceSummary(audioMode, audioSaveFailed),
                    capturePreferences = v.capture,
                    hideCallHistoryOnLockScreen = v.hideCallHistoryOnLockScreen,
                    onHideCallHistoryOnLockScreen = { hidden -> saveCallOption { store.saveHideCallHistoryOnLockScreen(hidden) } },
                    quietHours = v.quietHours,
                    onSaveQuietHours = { schedule -> saveCallOption { store.saveQuietHours(schedule) } },
                    onSaveVideoQuality = { quality -> saveCallOption { store.saveVideoQuality(quality) } },
                    onSaveCaptureOption = { option, enabled -> saveCallOption { store.saveCaptureOption(option, enabled) } },
                    call = CallPresentationState(
                        dynamicIslandEnabled = v.dynamicIslandEnabled,
                        pictureInPictureEnabled = v.pictureInPictureEnabled,
                        lowDataUsageEnabled = v.lowDataUsageEnabled,
                        incomingCallTemplate = v.incomingCallTemplate,
                        ongoingCallTemplate = v.ongoingCallTemplate,
                        callExperienceBundleId = v.callExperienceBundleId,
                    ),
                    contacts = ServiceLocator.apiBackend.users.map { CallContactOption(it.id, it.displayName) },
                    onBack = onBack,
                    // Previous: onDynamicIsland = { b -> run { store.setDynamicIslandEnabled(b) } },
                    onDynamicIsland = { b -> saveCallOption { store.saveDynamicIslandEnabled(b) } },
                    // Previous: onPictureInPicture = { b -> run { store.setPictureInPictureEnabled(b) } },
                    onPictureInPicture = { b -> saveCallOption { store.savePictureInPictureEnabled(b) } },
                    // Previous: onLowData = { b -> run { store.setLowDataUsageEnabled(b) } },
                    onLowData = { b -> saveCallOption { store.saveLowDataUsageEnabled(b) } },
                    onOpenAudioSettings = { audio = true },
                    onOpenCallBundles = { showBundles = true },
                    serverCallAudience = serverCallAudience,
                    // CS1-42 / CS2-04: blocked accounts are managed in Privacy Center.
                    onOpenBlockedContacts = { onSwitch(SettingsDestination.Privacy) },
                    // CS4-49: reset only the presentation options the screen exposes, from the
                    // single defaults source (CallPresentationPreferences.defaults). Templates keep
                    // their persisted values; privacy preferences are untouched.
                    onResetCallSettings = {
                        check(ServiceLocator.apiBackend.authSnapshot() === screenAccountScope)
// Previous sequential reset:                         val d = com.example.chat.ui.calls.CallPresentationPreferences.defaults
// Previous sequential reset:                         // Previous: store.setDynamicIslandEnabled(d.dynamicIslandEnabled)
// Previous sequential reset:                         store.saveDynamicIslandEnabled(d.dynamicIslandEnabled)
// Previous sequential reset:                         // Previous: store.setPictureInPictureEnabled(d.pictureInPictureEnabled)
// Previous sequential reset:                         store.savePictureInPictureEnabled(d.pictureInPictureEnabled)
// Previous sequential reset:                         // Previous: store.setLowDataUsageEnabled(d.lowDataUsageEnabled)
// Previous sequential reset:                         store.saveLowDataUsageEnabled(d.lowDataUsageEnabled)
// Previous sequential reset:                         // Previous: store.setCallExperienceBundle(d.callExperienceBundleId)
// Previous sequential reset:                         store.applyExperienceBundle(d.callExperienceBundleId, com.example.chat.ui.calls.BundleApplyTarget.CALLS)
                        check(ServiceLocator.apiBackend.authSnapshot() === screenAccountScope)
                        store.resetPresentationDefaults()
                    },
                )
            }
        }
        SettingsDestination.Permissions -> com.example.chat.ui.settings.permissions.SystemPermissionsScreen(
            onBack = onBack,
            triggerTestNotification = {
                // Dart _sendTestNotification: title/body verbatim, color = context.colors.primary (accent),
                // icon notifications_active_rounded (closest EventNotificationIcon is notifications).
                ServiceLocator.notifications.triggerEventNotification(
                    title = "Chaty test notification", body = "This is a sample notification from Chaty.",
                    icon = com.example.chat.data.realtime.EventNotificationIcon.notifications,
                    colorArgb = themeConfig.accentColor,
                )
            },
            canUseBiometrics = { ServiceLocator.localLockService.canUseBiometrics() },
            authenticateBiometric = { reason -> ServiceLocator.localLockService.authenticateBiometric(reason) },
        )
        // Storage quality controls are backed by the shared media-upload preferences.
        SettingsDestination.StorageMedia -> {
            var cacheLimitMb by remember { mutableStateOf(com.example.chat.data.local.MediaCacheSettings.limitMb(context)) }
            var cacheUsedBytes by remember { mutableStateOf(0L) }
            val storageScope = androidx.compose.runtime.rememberCoroutineScope()
            androidx.compose.runtime.LaunchedEffect(Unit) {
                cacheUsedBytes = withContext(Dispatchers.IO) { runCatching { ServiceLocator.mediaCache.totalBytes() }.getOrDefault(0L) }
            }
            StorageMediaScreen(
                preferences = prefs,
                onBack = onBack,
                onClearCache = {
                    // ST1-22: delete off the main thread.
                    storageScope.launch {
                        withContext(Dispatchers.IO) { ServiceLocator.mediaCache.clearCurrentAccount() }
                        cacheUsedBytes = withContext(Dispatchers.IO) { runCatching { ServiceLocator.mediaCache.totalBytes() }.getOrDefault(0L) }
                        toast("Local media cache cleared. Server copies are untouched.")
                    }
                },
                cacheLimitMb = cacheLimitMb,
                cacheUsedBytes = cacheUsedBytes,
                onCacheLimitChange = { mb ->
                    com.example.chat.data.local.MediaCacheSettings.setLimitMb(context, mb)
                    cacheLimitMb = com.example.chat.data.local.MediaCacheSettings.limitMb(context)
                    storageScope.launch {
                        withContext(Dispatchers.IO) { ServiceLocator.mediaCache.trimToLimit() }
                        cacheUsedBytes = withContext(Dispatchers.IO) { runCatching { ServiceLocator.mediaCache.totalBytes() }.getOrDefault(0L) }
                    }
                },
            )
        }
        SettingsDestination.ThemeEngine -> {
            // Dart file_picker/image_picker seams -> Activity Result launchers.
            var jsonWaiter by remember { mutableStateOf<kotlinx.coroutines.CompletableDeferred<String?>?>(null) }
            var imageWaiter by remember { mutableStateOf<kotlinx.coroutines.CompletableDeferred<java.io.File?>?>(null) }
            val jsonLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
            ) { uri ->
                val text = try {
                    uri?.let { context.contentResolver.openInputStream(it)?.bufferedReader()?.use { r -> r.readText() } }
                } catch (_: Exception) { null }
                jsonWaiter?.complete(text); jsonWaiter = null
            }
            val imageLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.GetContent(),
            ) { uri ->
                val file = try {
                    uri?.let {
                        val out = java.io.File(context.cacheDir, "theme_image_${System.currentTimeMillis()}")
                        context.contentResolver.openInputStream(it)?.use { input -> out.outputStream().use { o -> input.copyTo(o) } }
                        out
                    }
                } catch (_: Exception) { null }
                imageWaiter?.complete(file); imageWaiter = null
            }
            com.example.chat.ui.settings.theme.ThemeEditorScreen(
                themeController = theme,
                preferences = prefs,
                templateController = ServiceLocator.templateController,
                appearance = ServiceLocator.appearanceVariantController,
                exportDirectory = context.getExternalFilesDir(null) ?: context.filesDir,
                pickJsonText = {
                    val d = kotlinx.coroutines.CompletableDeferred<String?>()
                    jsonWaiter = d
                    jsonLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    d.await()
                },
                pickImageFile = {
                    val d = kotlinx.coroutines.CompletableDeferred<java.io.File?>()
                    imageWaiter = d
                    imageLauncher.launch("image/*")
                    d.await()
                },
                onBack = onBack,
            )
        }
        SettingsDestination.TemplateStudio -> {
            var component by remember { mutableStateOf<com.example.chat.core.templates.TemplateComponentType?>(null) }
            BackHandler(enabled = component != null) { component = null }
            val c = component
            if (c == null) {
                com.example.chat.ui.settings.templates.TemplatesSettingsScreen(
                    templateController = ServiceLocator.templateController,
                    preferences = prefs,
                    appearance = ServiceLocator.appearanceVariantController,
                    palette = theme.globalTheme,
                    templateRuntime = ServiceLocator.templateRuntime,
                    onBack = onBack,
                    onOpenComponent = { component = it },
                    onNotify = toast,
                )
            } else {
                com.example.chat.ui.settings.templates.ComponentOverrideScreen(
                    component = c,
                    templateController = ServiceLocator.templateController,
                    onBack = { component = null },
                    runtime = ServiceLocator.templateRuntime,
                    onMessage = toast,
                )
            }
        }
        // Dart openWasteCleaning / openHomeShortcuts: modal sheets over the settings root.
        SettingsDestination.WasteCleaning -> com.example.chat.ui.settings.gb.GbStandaloneSheet(onDismiss = onBack) {
            com.example.chat.ui.settings.gb.GbWasteCleaningSheetContent(onDismissSheet = onBack, toast = toast)
        }
        SettingsDestination.HomeShortcuts -> com.example.chat.ui.settings.gb.GbStandaloneSheet(onDismiss = onBack) {
            com.example.chat.ui.settings.gb.GbHomeShortcutsSheetContent(
                onDismissSheet = onBack,
                onOpenHome = { onSwitch(SettingsDestination.HomeScreen) },
            )
        }
        // Dynamic Island: the full island settings UI.
        SettingsDestination.DynamicIsland -> com.example.chat.island.ui.DynamicIslandHostScreen(onBack = onBack)
        else -> return false
    }
    return true
}

// Superseded call-settings source retained inactive:
//                     templates = com.example.chat.ui.calls.CallScreenTemplate.all.map { CallTemplateOption(it.wireName, it.displayName) },
//                     onIncomingTemplate = { t -> run { store.setIncomingCallTemplate(t) } },
//                     onOngoingTemplate = { t -> run { store.setOutgoingCallTemplate(t) } },
