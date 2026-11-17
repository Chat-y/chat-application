package com.example.chat.ui.chats

import com.example.chat.ui.kit.chatyPredictiveBack

import kotlinx.coroutines.flow.filterNotNull

import androidx.lifecycle.repeatOnLifecycle
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.outlined.MarkChatRead
import androidx.compose.material.icons.outlined.MarkChatUnread
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.PushPin as OutlinedPushPin
import androidx.compose.material.icons.rounded.Videocam
import com.example.chat.ui.kit.ChatyConfirmationDialog as AlertDialog
import com.example.chat.ui.kit.ChatyTextButton
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.preferences.HomePresetNormalizer
import com.example.chat.ui.effects.listEntrance
import com.example.chat.domain.models.Conversation
import com.example.chat.ui.kit.ChatyAdaptiveLogic
import com.example.chat.domain.models.ConversationType
import com.example.chat.domain.models.HomePreferences
import com.example.chat.domain.models.SecurityPreferences
import com.example.chat.domain.models.UserProfile
import com.example.chat.domain.models.ChatMessage
import com.example.chat.ui.chats.home.ArchivedChatsScreen
import com.example.chat.ui.chats.home.ChatsHomeNav
import com.example.chat.ui.chats.home.ConversationRow
import com.example.chat.ui.chats.home.EmptyChatsState
import com.example.chat.ui.chats.home.HomeConfirmDialog
import com.example.chat.ui.chats.home.HomeEntry
import com.example.chat.ui.chats.home.HomeFilterChips
import com.example.chat.ui.chats.home.HomeIdentity
import com.example.chat.ui.chats.home.HomeOverflowMenu
import com.example.chat.ui.chats.home.HomePresence
import com.example.chat.ui.chats.home.HomeSearchField
import com.example.chat.ui.chats.home.HomeSectionHeader
import com.example.chat.ui.chats.home.HomeStyleSpec
import com.example.chat.ui.chats.home.HomeSwipeAction
import com.example.chat.ui.chats.home.HomeSwipeActions
import com.example.chat.ui.chats.home.LockedChatsSetupDialog
import com.example.chat.ui.chats.home.HomeTemplates
import com.example.chat.ui.chats.home.SelectionHomeAppBar
import com.example.chat.ui.chats.home.SplitQuickPane
import com.example.chat.ui.chats.home.StandardHomeAppBar
import com.example.chat.ui.chats.home.StoryStrip
import com.example.chat.ui.chats.home.SubscriptionRequiredBanner
import com.example.chat.ui.chats.home.buildHomeEntries
import com.example.chat.ui.chats.home.chatyStoryLookFor
import com.example.chat.ui.chats.home.chatyStoryStripHeight
import com.example.chat.ui.chats.home.compactBarTitle
import com.example.chat.ui.chats.home.defaultHomeTemplates
import com.example.chat.ui.chats.home.displayConversationTitle
import com.example.chat.ui.chats.home.effectiveHomeHeaderStyle
import com.example.chat.ui.chats.home.filterHomeConversations
import com.example.chat.ui.chats.home.homeFilters
import com.example.chat.ui.chats.home.resolveSearchPlacement
import com.example.chat.ui.theme.LocalThemeConfig
import androidx.compose.foundation.horizontalScroll
import com.example.chat.ui.chats.home.withCurrentDateContext
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import com.example.chat.ui.chats.home.HomeBulkAction
import com.example.chat.ui.chats.home.rememberHomeDateGrouping
import com.example.chat.ui.chats.home.HomeGroupMode
import com.example.chat.ui.chats.home.HomeOpKey
import com.example.chat.ui.chats.home.HomeOpState
import com.example.chat.ui.chats.home.HomePaneMax
import com.example.chat.ui.chats.home.HomePaneState
import com.example.chat.ui.chats.home.HomeSelectionPolicy
import com.example.chat.ui.chats.home.clampDivider
import com.example.chat.ui.chats.home.dedupeConversations
import com.example.chat.ui.chats.home.groupHomeEntries
import com.example.chat.ui.chats.home.homeEntryKey
import com.example.chat.ui.chats.home.homeFocusRing
import com.example.chat.ui.chats.home.homeRowInput
import com.example.chat.ui.chats.home.homeTypeahead
import kotlinx.coroutines.launch

/** Full port of lib/features/chats/chats_home_screen.dart (3072 lines):
 *  search with placements + secret-phrase vault entry, selection mode with
 *  bulk pin/delete/mute/archive/lock/read-state actions, sectioned
 *  PINNED/UNREAD/MESSAGES ordering with Productivity unread-first, archive
 *  entry top/bottom, filter chips, stories strip, iOS large-title collapse,
 *  WA-style swipe actions (call/video + pin/mute/archive), Tablet Split
 *  View, ghost pill, overflow menu and subscription banner.
 *
 *  The original four parameters keep their meaning so the existing AppRoot
 *  wiring compiles unchanged; every new dependency has a default that
 *  degrades to the Dart behavior for absent data (empty presence, no locks).
 *
 *  Honest boundaries (later slices, not dropped): ProtectedResourceGate auth
 *  before opening a locked chat, the locked-chats guided-setup modal,
 *  ChatyGlyph font icons (emoji stand-ins), network avatars (integrator's
 *  Coil batch), GlobalConnectionBanner/ConnectionHealthIndicator (slots
 *  provided), NotificationPermissionSheet (host seam below, W8 owns the UI),
 *  swipe-call relationship checks (calls slice), GB story *content* (the
 *  strip lists contacts exactly like Dart). */
@Composable
fun ChatsHomeScreen(
    conversations: List<Conversation>,
    requiresSubscription: Boolean,
    subscriptionMessage: String?,
    onOpenConversation: (Conversation) -> Unit,
    /** R7 expanded list-detail: the open conversation renders beside the list. */
    expandedListDetail: Boolean = false,
    paneConversation: Conversation? = null,
    chatPane: (@Composable (Conversation) -> Unit)? = null,
    currentUserId: String = "",
    /** Opaque auth generation, not merely uid: resets pending selection/dialog state across account transitions. */
    accountSessionIdentity: Any? = currentUserId,
    homeToolRequest: com.example.chat.ui.chats.home.HomeToolRequest? = null,
    onHomeToolConsumed: (com.example.chat.ui.chats.home.HomeToolRequest) -> Unit = {},
    folderAccountKey: String = "",
    identity: HomeIdentity = HomeIdentity(),
    contacts: List<UserProfile> = emptyList(),
    homePreferences: HomePreferences = HomePreferences(),
    securityPreferences: SecurityPreferences = SecurityPreferences(),
    forcedType: ConversationType? = null,
    pageTitle: String? = null,
    templates: HomeTemplates = if (com.example.chat.core.preferences.HomePresentationPolicy.isDefault(homePreferences.homeStyle)) com.example.chat.ui.chats.home.referenceDefaultHomeTemplates else defaultHomeTemplates,
    presenceFor: (conversationId: String, otherUserId: String) -> HomePresence? = { _, _ -> null },
    isOnline: (String) -> Boolean = { false },
    messagesFor: (String) -> List<ChatMessage> = { emptyList() },
    /** Cheap tick accessor; when the host provides it, rows avoid copying the
     *  whole message list via messagesFor on every recomposition. */
    lastDeliveryStateFor: ((String) -> com.example.chat.domain.models.DeliveryState?)? = null,
    gbBool: (String, Boolean) -> Boolean = { _, fallback -> fallback },
    gbColor: (String) -> Long? = { null },
    isConversationLocked: (String) -> Boolean = { false },
    isConversationHidden: (String) -> Boolean = { false },
    onToggleLockConversation: (String, Boolean) -> Unit = { _, _ -> },
    onToggleGhostMode: (Boolean) -> Unit = {},
    verifySecretPhrase: suspend (String) -> Boolean = { false },
    nav: ChatsHomeNav = ChatsHomeNav(),
    onOpenArchived: () -> Unit = {},
    onTogglePin: (String) -> Unit = {},
    onToggleMute: (String) -> Unit = {},
    onToggleArchive: (String) -> Unit = {},
    onMarkRead: (String) -> Unit = {},
    onMarkUnread: (String) -> Unit = {},
    onMarkAllRead: (List<String>) -> Unit = {},
    onDeleteConversations: (List<String>) -> Unit = {},
    onStartCall: (Conversation, isVideo: Boolean) -> Unit = { _, _ -> },
    onSnack: (String) -> Unit = {},
    onAuthorizeConversation: (Conversation, proceed: () -> Unit) -> Unit = { _, proceed -> proceed() },
    preferencesController: com.example.chat.core.preferences.ChatyPreferencesController? = null,
    connectionHealthState: com.example.chat.domain.models.ConnectionHealthState? = null,
    onOpenConnectionDetail: () -> Unit = {},
    lockedChatsConfigured: Boolean = false,
    onRequestVaultAuth: (onAuthorized: () -> Unit) -> Unit = {},
    onShowNotificationPermissionIfNeeded: () -> Unit = {},
    headerExtras: (@Composable () -> Unit)? = null,
    connectionBanner: (@Composable () -> Unit)? = null,
    headerBottom: (@Composable () -> Unit)? = null,
    /** C8 (R5-2x): host-owned per-row operation state, keyed by action+chat. Empty
     *  (the default) surfaces nothing, and the Host slice currently runs its own
     *  in-flight guard, so no Host change is required for existing callers. */
    opState: Map<HomeOpKey, HomeOpState> = emptyMap(),
    /** C8: retry intent for a failed row operation; dispatch and the reducer stay host-owned. */
    onRetry: (HomeOpKey) -> Unit = {},
    /** R2-29: host-owned bounded refresh of the currently loaded account. Null keeps
     *  the action hidden; the host owns the sync call, its bounds and its completion. */
    onRefreshHome: (() -> Unit)? = null,
    /** R2-29: true while that bounded refresh runs; drives the visible progress cue. */
    homeRefreshing: Boolean = false,
    /** R2-32: a VERIFIED platform screen-share signal from the host. Defaults false:
     *  until a real signal is wired no reminder shows and no detection is claimed. */
    screenShareActive: Boolean = false,
    /** True only for a successfully loaded, complete active-account ID snapshot.
     * Partial/paged data must keep stale-reference cleanup unavailable. */
    homeConversationIdsComplete: Boolean = false,
    // b116: account-scoped server policy callbacks. Missing callbacks must not offer a local-only substitute.
    loadCallPolicy: (suspend () -> com.example.chat.data.api.ApiContactsClient.CallPolicy)? = null,
    saveCallPolicy: (suspend (String, List<String>?) -> com.example.chat.data.api.ApiContactsClient.CallPolicy)? = null,
) {
    val bundleTemplates = com.example.chat.ui.chats.bundles.messageBundleHome(templates)
    val theme = com.example.chat.ui.chats.bundles.messageBundleTheme(LocalThemeConfig.current)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val searchFocus = remember { FocusRequester() }

    val shellHeaderController = com.example.chat.ui.navigation.LocalHomeHeaderController.current
    val shellHeaderId = if (forcedType == ConversationType.group) "groups" else "chats"
    val headerEntry = shellHeaderController?.entry(shellHeaderId)
        ?: remember(accountSessionIdentity) { com.example.chat.ui.navigation.HomeHeaderEntry() }
    var searchQuery by headerEntry::query
    var isSearchOpen by headerEntry::searchOpen
    var selectedIds by headerEntry::selectedIds
    var selectedFilter by headerEntry::selectedFilter
    var overflowOpen by headerEntry::overflowOpen
    var showDeleteConfirm by headerEntry::showDeleteConfirm
    var showMessageNumber by headerEntry::showMessageNumber
    var messageNumberText by headerEntry::messageNumberText
    var showWhoCanCallMe by headerEntry::showWhoCanCallMe
    var showLockedSetup by headerEntry::showLockedSetup
    var showArchived by headerEntry::showArchived
    var showEffects by headerEntry::showEffects
    var showScratchpad by headerEntry::showScratchpad
    var scratchText by headerEntry::scratchText
    var confirmClearScratch by headerEntry::confirmClearScratch
    var showViewOptions by headerEntry::showViewOptions
    var quickView by headerEntry::quickView
    var localOrder by headerEntry::localOrder
    var savedViews by headerEntry::savedViews
    var viewHistory by headerEntry::viewHistory
    var showSavedViews by headerEntry::showSavedViews
    var showViewHistory by headerEntry::showViewHistory
    var previewView by headerEntry::previewView
    var saveAfterPreview by headerEntry::saveAfterPreview
    // R2-18: in-memory only, account-scoped; never saved, synced or sent.
    var lastOpenedConversationId by remember(accountSessionIdentity) {mutableStateOf<String?>(null)}
    // R2-23: offered only after deep scrolling.
    val showJumpToTop by remember(listState) {derivedStateOf {listState.firstVisibleItemIndex >= 8}}
    // R2-21: shortcut help only ever displays; it never mutates Home state.
    var showShortcutHelp by headerEntry::showShortcutHelp
    // R2-31: session-only presentation curtain. A display aid - not a lock, not
    // encryption, not a screenshot guarantee.
    var privacyCurtainOn by headerEntry::privacyCurtainOn
    val curtainFocusManager = androidx.compose.ui.platform.LocalFocusManager.current
    LaunchedEffect(privacyCurtainOn) {
        if (privacyCurtainOn) curtainFocusManager.clearFocus(force = true)
    }
    // R2-32: one reminder per share session; re-arms when the signal drops.
    var screenShareReminderDismissed by remember(accountSessionIdentity) {mutableStateOf(false)}
    // R4-14/R4-19: read-only selection inspector.
    var showSelectionInspector by headerEntry::showSelectionInspector
    // R4-13: stable anchor chat ID for range selection; never a list index.
    var selectionAnchorId by headerEntry::selectionAnchorId
    // R4-41/42/45: session-only grouping mode; None reproduces the stock list exactly.
    // Grouping stores only a stable key; function-bearing modes are rebuilt below.
    // R4-43: session-only pinned collapse; chats stay pinned and unread totals are kept.
    var pinnedCollapsed by headerEntry::pinnedCollapsed
    // R2-17: not saveable; account change or leaving this composition ends the session.
    var triageSnapshot by remember(accountSessionIdentity, folderAccountKey) {
        mutableStateOf<com.example.chat.ui.chats.home.HomeTriageSnapshot?>(null)
    }
    var showAppearanceResetPreview by headerEntry::showAppearanceResetPreview
    var showCatchUpGuide by remember(accountSessionIdentity) { mutableStateOf(false) }
    if (showCatchUpGuide) com.example.chat.ui.chats.home.HomeCatchUpGuide(theme) { showCatchUpGuide = false }

    // R4-21..23: session-only pane layout. Persisting the width needs an explicit
    // preference choice first (R4-21), so nothing is stored.
    var homePaneState by remember(accountSessionIdentity) {mutableStateOf(HomePaneState())}
    // R4-33..36: keyboard row focus by stable chat ID, never by position.
    var focusedConversationId by remember(accountSessionIdentity) {mutableStateOf<String?>(null)}
    val rowFocusRequesters = remember(accountSessionIdentity) {mutableMapOf<String, FocusRequester>()}
    // R4-31/32: pointer affordance state; hover never alters chat data.
    var rowMenuConversationId by remember(accountSessionIdentity) {mutableStateOf<String?>(null)}
    var hoveredRowId by remember(accountSessionIdentity) {mutableStateOf<String?>(null)}

    // 'Who can call me?' reuses the shared privacy dialog when the real
    // preferences controller is wired; otherwise the nav seam stands.
    val effectiveNav = (if (preferencesController != null) {
        nav.copy(onWhoCanCallMe = { showWhoCanCallMe = true })
    } else {
        nav
    }).copy(onOpenCameraEffects = { nav.onOpenCameraEffects(); showEffects = true })
    LaunchedEffect(homeToolRequest, accountSessionIdentity) {
        val request = homeToolRequest ?: return@LaunchedEffect
        if (request.owner !== accountSessionIdentity) return@LaunchedEffect
        // Consume before navigation, so another composition cannot replay a shortcut.
        onHomeToolConsumed(request)
        when (request.destination) {
            "HomeViewOptions" -> showViewOptions = true
            "HomeScratchpad" -> showScratchpad = true
            "HomeSavedViews" -> showSavedViews = true
            "HomeRecentViews" -> showViewHistory = true
            "HomeKeyboardShortcuts" -> showShortcutHelp = true
            "HomeAppearanceCheck" -> showAppearanceResetPreview = true
            "HomeCatchUpGuide" -> showCatchUpGuide = true
            "HomeNewGroup" -> effectiveNav.onOpenNewChat(true, false)
            "HomeCameraEffects" -> effectiveNav.onOpenCameraEffects()
            "HomeQrScan" -> effectiveNav.onOpenQr()
            "HomeAccountSettings" -> effectiveNav.onOpenProfile()
            "HomeToggleBrightness" -> effectiveNav.onToggleBrightness()
        }
    }

    // Connection health is available in the overflow, not the header signal lane.
    // Preserve explicit non-connection host slots.
    val effectiveConnectionBanner: (@Composable () -> Unit)? = connectionBanner
    val effectiveHeaderExtras: (@Composable () -> Unit)? = headerExtras

    val isSelectionMode = selectedIds.isNotEmpty()

    // initState: NotificationPermissionSheet.showIfNeeded (W8 owns the UI;
    // this is only the host seam).
    LaunchedEffect(Unit) { onShowNotificationPermissionIfNeeded() }

    // PopScope(canPop: !selectionMode): back clears the selection first.
    val homeBack = com.example.chat.ui.kit.rememberChatyBackPresentation(Triple(isSearchOpen, isSelectionMode, homePaneState.maximized))
    // Previous registration retained inactive: BackHandler(enabled = isSearchOpen) { isSearchOpen = false; searchQuery = "" }
    com.example.chat.ui.kit.ChatyPredictiveBackHandler(presentation = homeBack, enabled = isSearchOpen) { isSearchOpen = false; searchQuery = "" }
    // Previous registration retained inactive: BackHandler(enabled = isSelectionMode) { selectedIds = emptySet(); selectionAnchorId = null }
    com.example.chat.ui.kit.ChatyPredictiveBackHandler(presentation = homeBack, enabled = isSelectionMode) { selectedIds = emptySet(); selectionAnchorId = null }
    // R4-37: one layer per dismissal, in a defined order. Destructive confirmations
    // and the dialogs below consume Back inside their own windows first; then the
    // overflow menu, a maximized pane (R4-28: the one detail-pane Back step Home
    // owns - restoring the split; route-level Back stays with the host navigation
    // graph, no second hidden history stack), then (existing handlers above)
    // selection and search. A single event never clears two layers.
    // Previous registration retained inactive: BackHandler(enabled = overflowOpen) { overflowOpen = false }
    com.example.chat.ui.kit.ChatyPredictiveBackHandler(presentation = homeBack, enabled = overflowOpen) { overflowOpen = false }
    // Previous registration retained inactive: BackHandler(enabled = homePaneState.maximized != HomePaneMax.None) {
    com.example.chat.ui.kit.ChatyPredictiveBackHandler(presentation = homeBack, enabled = homePaneState.maximized != HomePaneMax.None) {
        homePaneState = homePaneState.copy(maximized = HomePaneMax.None)
    }

    // ---- build() derivations, in the Dart source's order ----
    val homePrefs = homePreferences
    val dateGroupingEnabled = rememberHomeDateGrouping(preferencesController)

    val rawHomeUiStyle = homePrefs.homeStyle.trim().lowercase()
    val homeStyle = com.example.chat.core.preferences.HomePresentationPolicy.listStyle(homePrefs.homeStyle)
    val referenceRows = com.example.chat.core.preferences.HomePresentationPolicy.isDefault(homePrefs.homeStyle) && bundleTemplates.chatList.referenceDefault
    val spec = HomeStyleSpec.resolve(homeStyle, rawHomeUiStyle).let {
        if (referenceRows) it.copy(showSections = false) else it
    }
    val searchPlacement = resolveSearchPlacement(homePrefs.searchPlacement, homePrefs.disableSearchBar)
    val searchEnabled = homePrefs.showSearchBar
    val storiesStyle = com.example.chat.core.preferences.HomePresentationPolicy.effectiveStoriesStyle(
        homePrefs.separateChatsAndGroups, homePrefs.storiesStyle,
    )
    val storyLook = chatyStoryLookFor(storiesStyle)
    val storyShowOnlineRing = storiesStyle != "Minimal"
    val storyCardWrap = storiesStyle == "Card" || storiesStyle == "Card View" || homePrefs.carouselView
    // User directive: no stories strip on the home screen unless he explicitly
    // enables it - separation/template no longer force it on.
    val showStoriesStrip = homePrefs.enableStoriesStrip
    val storyCompact = storiesStyle == "Compact"
    val storyTileWidth = if (storyCardWrap) 74.0 else if (storyCompact) 48.0 else 58.0
    val storyNameFontSize = if (storyCompact) 9.0 else 10.5

    // Snapshot privacy values on every composition. Captured mutable controller state may
    // change without changing a lambda's identity, especially with strong skipping enabled.
    val hiddenConversationIds = conversations.asSequence().map { it.id }.filter(isConversationHidden).toSet()
    val lockedConversationIds=conversations.asSequence().map {it.id}.filter(isConversationLocked).toSet()
    LaunchedEffect(screenShareActive) { if (!screenShareActive) screenShareReminderDismissed = false }

    // G7: local, account-scoped chat folders. Membership never leaves the
    // device; the folder filter applies after the stock filter/search pass.
    val folderStoreContext = androidx.compose.ui.platform.LocalContext.current
    val chatFolderStore = remember(accountSessionIdentity, folderAccountKey) {
        com.example.chat.data.local.ChatFolderStore(folderStoreContext, folderAccountKey.ifBlank { "unavailable" })
    }
    val chatFolderRevision by chatFolderStore.revision.collectAsState()
    var selectedFolderId by headerEntry::selectedFolderId
    LaunchedEffect(chatFolderRevision, selectedFolderId) {
        if (selectedFolderId != null && chatFolderStore.folders().none { it.id == selectedFolderId }) {
            selectedFolderId = null
            selectedIds = emptySet()
            selectionAnchorId = null
        }
    }

    val homeKnownContactIds=remember(contacts) {contacts.map {it.id}.toSet()}
    val homeDisplayTitles=remember(conversations,homePrefs.hideUnsavedNumbers,currentUserId,homeKnownContactIds) {
        conversations.associate {it.id to displayConversationTitle(it,homePrefs.hideUnsavedNumbers,currentUserId,homeKnownContactIds)}
    }
    val homeSortLocale=androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val filteredConversations = remember(accountSessionIdentity, folderAccountKey, chatFolderStore, conversations, searchQuery, selectedFilter, forcedType, hiddenConversationIds, lockedConversationIds, selectedFolderId, chatFolderRevision, homeDisplayTitles) {
        val base = com.example.chat.ui.chats.home.filterPrivateHomeConversations(
            conversations, searchQuery, selectedFilter, forcedType, hiddenConversationIds::contains,
            isConversationLocked=lockedConversationIds::contains,
            displayTitleFor={homeDisplayTitles[it.id] ?: it.title},
        )
        val folderId = selectedFolderId
        if (folderId == null) base else chatFolderStore.memberIds(folderId).let { ids -> base.filter { it.id in ids } }
    }
    val homeClockMinute=com.example.chat.ui.chats.home.rememberHomeMinute()
    val homeDateContext = com.example.chat.ui.chats.home.rememberHomeDateContext(homeClockMinute)
    val homeNow = java.time.Instant.ofEpochMilli(homeClockMinute * 60000)
    // Recreate function-bearing modes from current privacy/name/date inputs. Never
    // retain the Date click-time snapshot or a stale locked-ID predicate.
    fun currentGroupMode(key: String): HomeGroupMode {
        val mode = com.example.chat.ui.chats.home.homeGroupModeForKey(
            key, { homeDisplayTitles[it.id] ?: it.title }, lockedConversationIds::contains, homeSortLocale)
        return when {
            mode is HomeGroupMode.Date -> mode.withCurrentDateContext(homeDateContext, homeClockMinute)
            mode is HomeGroupMode.Alphabetical && localOrder != com.example.chat.ui.chats.home.HomeLocalOrder.Name -> HomeGroupMode.None
            mode is HomeGroupMode.DirectGroup && selectedFolderId == null -> HomeGroupMode.None
            else -> mode
        }
    }
    // A real account-local preference, OFF by default. Old saved-view modes remain readable,
    // but Date headings only show when the explicit Home settings switch is enabled.
    val legacyGroupMode = currentGroupMode(headerEntry.groupingKey)
    val effectiveGroupMode = when {
        dateGroupingEnabled -> currentGroupMode("date")
        legacyGroupMode is HomeGroupMode.Date -> HomeGroupMode.None
        else -> legacyGroupMode
    }
    val currentViewState = com.example.chat.ui.chats.home.HomeViewState(
        selectedFilter, selectedFolderId, quickView, localOrder, com.example.chat.ui.chats.home.homeGroupModeKey(effectiveGroupMode),
    )
    var previousViewState by remember(accountSessionIdentity) { mutableStateOf(currentViewState) }
    LaunchedEffect(currentViewState) {
        if (previousViewState != currentViewState) {
            viewHistory = com.example.chat.ui.chats.home.HomeQuickViewPolicy.pushHistory(viewHistory, previousViewState)
            previousViewState = currentViewState
        }
    }
    fun applyView(state: com.example.chat.ui.chats.home.HomeViewState) {
        if (state.filter !in homeFilters(forcedType) ||
            (state.folderId != null && chatFolderStore.folders().none { it.id == state.folderId })) {
            onSnack("This view is no longer available for this Home scope.")
            return
        }
        searchQuery = ""
        selectedFilter = state.filter
        selectedFolderId = state.folderId
        quickView = state.view
        localOrder = state.order
        // Eligibility is checked again next composition using the applied scope.
        pinnedCollapsed = false
        headerEntry.groupingKey = state.groupingKey
    }
    if (showSavedViews) com.example.chat.ui.chats.home.HomeSavedViewsDialog(
        saved = savedViews,
        onSave = { name ->
            previewView = com.example.chat.ui.chats.home.HomeQuickViewPolicy.savedViewFromState(name, currentViewState)
            saveAfterPreview = true
            showSavedViews = false
        },
        onPreview = { previewView = it; saveAfterPreview = false; showSavedViews = false },
        onDelete = { savedViews = com.example.chat.ui.chats.home.HomeQuickViewPolicy.removeView(savedViews, it) },
        onDismiss = { showSavedViews = false },
    )
    previewView?.let { config ->
        val folderExists = config.folderId == null || chatFolderStore.folders().any { it.id == config.folderId }
        // Search is deliberately empty here and on apply. Exclude protected rows
        // entirely, including titles/counts, rather than falling back to raw titles.
        val previewBase = com.example.chat.ui.chats.home.filterPrivateHomeConversations(
            dedupeConversations(conversations), "", config.filter, forcedType,
            hiddenConversationIds::contains, isConversationLocked = lockedConversationIds::contains,
            displayTitleFor = { homeDisplayTitles[it.id] ?: it.title },
        ).filter { it.id !in lockedConversationIds }
        val previewRows = com.example.chat.ui.chats.home.HomeQuickViewPolicy.apply(
            previewBase.filter { config.folderId == null || it.id in chatFolderStore.memberIds(config.folderId) },
            config.view, config.order, homeNow, lockedConversationIds::contains,
            { homeDisplayTitles[it.id] ?: it.title }, homeSortLocale,
        )
        val previewEntries = buildHomeEntries(previewRows, homeStyle, spec, false,
            homePrefs.hideArchivedChats, gbBool("key_mas_hide_archive_home", false), homePrefs.archiveChatsOnTop)
        val previewMode = when (config.groupingKey) {
            "date" -> HomeGroupMode.Date(lockedConversationIds::contains, homeNow, homeDateContext.zoneId)
            "name" -> if (config.order == com.example.chat.ui.chats.home.HomeLocalOrder.Name)
                HomeGroupMode.Alphabetical({ homeDisplayTitles[it.id] ?: it.title }, homeSortLocale) else HomeGroupMode.None
            "direct-group" -> if (config.folderId != null) HomeGroupMode.DirectGroup else HomeGroupMode.None
            else -> HomeGroupMode.None
        }
        val previewChats = groupHomeEntries(previewEntries, previewMode, false)
            .filterIsInstance<HomeEntry.Item>().map { it.conversation }
        com.example.chat.ui.chats.home.HomeViewPreviewDialog(
            config = config,
            confirmLabel = if (!saveAfterPreview) "Apply view" else if (
                com.example.chat.ui.chats.home.HomeQuickViewPolicy.savedViewNameExists(savedViews, config.name)
            ) "Replace saved view" else "Save view",
            preview = com.example.chat.ui.chats.home.HomeViewPreview(
                if (folderExists) previewChats.size else 0,
                if (folderExists) previewChats.take(3).map { homeDisplayTitles[it.id] ?: it.title } else emptyList(),
            ),
            onApply = {
                if (!folderExists) onSnack("That folder no longer exists. Nothing changed.")
                else if (saveAfterPreview) {
                    savedViews = com.example.chat.ui.chats.home.HomeQuickViewPolicy.saveView(savedViews, config)
                    onSnack("View saved for this Home session only.")
                } else applyView(com.example.chat.ui.chats.home.HomeQuickViewPolicy.viewStateOf(config))
                previewView = null
                showSavedViews = true
            },
            onDismiss = { previewView = null; showSavedViews = true },
        )
    }
    if (showViewHistory) com.example.chat.ui.chats.home.HomeViewHistoryDialog(
        history = viewHistory,
        describe = { state -> com.example.chat.ui.chats.home.HomeQuickViewPolicy.describe(state) { id ->
            chatFolderStore.folders().firstOrNull { it.id == id }?.name
        } },
        onRestore = { state ->
            val (target, remaining) = com.example.chat.ui.chats.home.HomeQuickViewPolicy.restoreFromHistory(viewHistory, state)
            if (target != null && target.filter in homeFilters(forcedType) &&
                (target.folderId == null || chatFolderStore.folders().any { it.id == target.folderId })) {
                applyView(target)
                viewHistory = remaining
                previousViewState = target
            } else onSnack("This earlier view is no longer available.")
            showViewHistory = false
        },
        onDismiss = { showViewHistory = false },
    )
    val visibleConversations=remember(filteredConversations,quickView,localOrder,lockedConversationIds,homeClockMinute,homeDisplayTitles,homeSortLocale) {
        com.example.chat.ui.chats.home.HomeQuickViewPolicy.apply(filteredConversations,quickView,localOrder,
            java.time.Instant.ofEpochMilli(homeClockMinute*60000),lockedConversationIds::contains,
            {homeDisplayTitles[it.id] ?: it.title},homeSortLocale)
    }
    if(showScratchpad) com.example.chat.ui.chats.home.HomeScratchpadDialog(
        text=scratchText,onText={scratchText=it},onClear={confirmClearScratch=true},onDismiss={showScratchpad=false})
    if(confirmClearScratch) com.example.chat.ui.kit.ChatyConfirmationDialog(
        onDismissRequest={confirmClearScratch=false},title={Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_clear_temporary_text))},  // was: "Clear temporary text?"
        text={Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_this_only_clears_the_home_scratchpad_no_chats_or_m))},  // was: "This only clears the Home scratchpad. No chats or messages are changed."
        confirmButton={com.example.chat.ui.kit.ChatyTextButton(onClick={scratchText="";confirmClearScratch=false},label="Clear")},
        dismissButton={com.example.chat.ui.kit.ChatyTextButton(onClick={confirmClearScratch=false},label="Cancel")})
    if(showViewOptions) com.example.chat.ui.chats.home.HomeQuickViewDialog(
        quickView,localOrder,
        com.example.chat.ui.chats.home.HomeQuickViewPolicy.summary(conversations.filter {it.id !in hiddenConversationIds},lockedConversationIds::contains),
        onView={quickView=it},onOrder={localOrder=it},
        onReset={quickView=com.example.chat.ui.chats.home.HomeQuickView.All;localOrder=com.example.chat.ui.chats.home.HomeLocalOrder.Existing},
        onDismiss={showViewOptions=false})
    if (showAppearanceResetPreview) com.example.chat.ui.chats.home.HomeAppearanceResetPreview(
        current = homePrefs, onDismiss = { showAppearanceResetPreview = false },
    )
    // Keep snapshot order and read rows. Search/filter/order still exist underneath,
    // but cannot silently remove session rows. Always resolve fresh account records.
    val triageConversations = triageSnapshot?.resolve(conversations) { id ->
        !isConversationHidden(id) && !isConversationLocked(id)
    }
    val gbHideArchiveHome = gbBool("key_mas_hide_archive_home", false)
    val entries = remember(
        visibleConversations, triageConversations, homeStyle, spec, isSearchOpen,
        homePrefs.hideArchivedChats, gbHideArchiveHome, homePrefs.archiveChatsOnTop,
    ) {
        // R5-02: duplicate conversation IDs are reconciled here, before entries reach
        // LazyColumn (dedupeConversations keeps one deterministic current-account
        // record per ID); Compose never has to resolve duplicate keys. R5-09: these
        // remember keys deliberately exclude presentation-only inputs (theme,
        // connection health) so a theme-only change is not treated as new content.
        if (triageConversations != null) triageConversations.map { HomeEntry.Item(it) }
        else buildHomeEntries(
            dedupeConversations(visibleConversations), homeStyle, spec, isSearchOpen,
            homePrefs.hideArchivedChats, gbHideArchiveHome,
            homePrefs.archiveChatsOnTop,
        )
    }
    // C6 (R4-41/42/43/45): optional visual grouping applied AFTER buildHomeEntries;
    // HomeGroupMode.None returns the entries unchanged and collapsing is disabled
    // while searching. Date mode uses the shared clock and date context, including
    // same-minute timezone/configuration changes and fresh privacy predicates.
    val groupedEntries = remember(entries, dateGroupingEnabled, headerEntry.groupingKey, pinnedCollapsed, isSearchOpen,
        homeClockMinute, homeDateContext, lockedConversationIds, homeDisplayTitles, homeSortLocale,
        localOrder, selectedFolderId, triageSnapshot) {
        if (triageSnapshot != null) entries
        else groupHomeEntries(entries, effectiveGroupMode, if (isSearchOpen) false else pinnedCollapsed)
    }
    // R4-11/12/13/19: the rendered eligible chat order - section headers and the
    // archive affordance excluded, hidden chats filtered upstream - is the single
    // source for the Select-all scope, invert, range selection and inspector order.
    val renderedConversationOrder = remember(groupedEntries, hiddenConversationIds) {
        groupedEntries.filterIsInstance<HomeEntry.Item>().map { it.conversation }.filter { it.id !in hiddenConversationIds }
    }
    // R4-17: capabilities for the selected set; an unavailable action explains itself
    // instead of silently applying to a subset. Locked-chat checks stay with the
    // existing per-action callbacks, which re-check locks themselves.
    // R1-40: settled (debounced) search-result count for a polite screen-reader announcement;
    // nothing is announced per keystroke or when search is closed.
    val liveResultCount = renderedConversationOrder.size
    var settledResultCount by remember(accountSessionIdentity) { mutableStateOf<Int?>(null) }
    LaunchedEffect(accountSessionIdentity, isSearchOpen, searchQuery, liveResultCount) {
        // Clear the prior query's count while settling; restart after account changes too.
        settledResultCount = null
        if (isSearchOpen && searchQuery.isNotBlank()) {
            kotlinx.coroutines.delay(800)
            settledResultCount = liveResultCount
        }
    }
    val resultCountAnnouncement: String? = settledResultCount?.let {
        androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_search_results_count, it)
    }
    val validBulkActions = remember(renderedConversationOrder, selectedIds) {
        val selected = renderedConversationOrder.filter { it.id in selectedIds }
        if (selected.map { it.id }.toSet() == selectedIds) HomeSelectionPolicy.validBulkActions(selected)
        else emptySet()
    }
    // Resume an authorization only while this exact screen/account and action scope still exist.
    val actionsAlive = remember(accountSessionIdentity) { java.util.concurrent.atomic.AtomicBoolean(true) }
    DisposableEffect(accountSessionIdentity) { onDispose { actionsAlive.set(false) } }
    val liveActionChats by rememberUpdatedState(conversations.associateBy { it.id })
    val liveActionHidden by rememberUpdatedState(isConversationHidden)
    val liveActionCurtain by rememberUpdatedState(privacyCurtainOn)
    val liveActionSelection by rememberUpdatedState(selectedIds)
    val liveActionBulk by rememberUpdatedState(validBulkActions)
    fun authorizeAction(ids: List<String>, valid: () -> Boolean = { true }, block: () -> Unit) {
        val targets = ids.distinct()
        if (targets.isEmpty()) return
        fun current() = actionsAlive.get() && !liveActionCurtain && valid() &&
            targets.all { liveActionChats[it] != null && !liveActionHidden(it) }
        fun authorizeAt(index: Int) {
            if (!current()) return
            if (index == targets.size) { block(); return }
            val target = liveActionChats[targets[index]] ?: return
            // Each authorization callback is single-use, even if a host retries delivery.
            val consumed = java.util.concurrent.atomic.AtomicBoolean(false)
            onAuthorizeConversation(target) {
                if (consumed.compareAndSet(false, true) && current()) authorizeAt(index + 1)
            }
        }
        authorizeAt(0)
    }
    fun runBulkAction(action: HomeBulkAction, block: () -> Unit) {
        val captured = selectedIds.toSet()
        if (action in validBulkActions) {
            // Authorize the full unchanged selection before any effect, never only a subset.
            authorizeAction(captured.toList(), { liveActionSelection == captured && action in liveActionBulk }, block)
        } else onSnack("That action does not apply to every selected chat, so nothing was changed.")
    }
    /* Previous capability-only gate retained.
    fun runBulkAction(action: HomeBulkAction, block: () -> Unit) {
        if (action in validBulkActions) {
            block()
        } else {
            onSnack("That action does not apply to every selected chat, so nothing was changed.")
        }
    }
    */
    val filters = homeFilters(forcedType)
    androidx.compose.runtime.SideEffect {
        if (headerEntry.selectionScope == null) headerEntry.selectionScope = currentViewState to searchQuery
    }
    LaunchedEffect(renderedConversationOrder, currentViewState, searchQuery, isSearchOpen) {
        val scopeKey = currentViewState to searchQuery
        if (headerEntry.selectionScope != null && headerEntry.selectionScope != scopeKey) {
            selectedIds = emptySet()
            selectionAnchorId = null
            headerEntry.selectionScope = scopeKey
        }
        val eligible = renderedConversationOrder.map { it.id }.toSet()
        selectedIds = selectedIds.intersect(eligible)
        if (selectionAnchorId !in eligible) selectionAnchorId = null
        if (focusedConversationId !in eligible) focusedConversationId = null
        if (rowMenuConversationId !in eligible) rowMenuConversationId = null
        rowFocusRequesters.keys.retainAll(eligible)
        if (selectedIds.isEmpty()) showDeleteConfirm = false
    }

    val headerStyle = effectiveHomeHeaderStyle(rawHomeUiStyle, homeStyle, bundleTemplates.headerStyle)

    // P4 large-title collapse: 0 expanded -> 1 collapsed, driven by the
    // primary list's vertical scroll only.
    val titleCollapseDistancePx = with(LocalDensity.current) { 44.dp.toPx() }
    val largeTitleCollapse by remember(listState, titleCollapseDistancePx) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / titleCollapseDistancePx).coerceIn(0f, 1f)
        }
    }
    val effectiveTitleCollapse = if (isSelectionMode || isSearchOpen) 1f else largeTitleCollapse

    val barTitle = compactBarTitle(
        homeStyle = homeStyle,
        headerStyle = headerStyle,
        pageTitle = pageTitle,
        setMyName = homePrefs.setMyName,
        myNameOverride = homePrefs.myNameOverride,
        userDisplayName = identity.displayName,
        userUsername = identity.username,
        largeTitleShown = effectiveTitleCollapse < 0.5f,
    )

    // Focus only after the search field has been composed and attached; requesting it inside the
    // click handler threw "FocusRequester is not initialized" (field not composed yet) and killed the app.
    LaunchedEffect(isSearchOpen) {
        if (isSearchOpen && shellHeaderController == null) {
            androidx.compose.runtime.withFrameNanos { }
            runCatching { searchFocus.requestFocus() }
        }
    }

    fun toggleSearch() {
        isSearchOpen = !isSearchOpen
        if (!isSearchOpen) searchQuery = ""
    }

    fun handleConversationTap(conversation: Conversation) {
        // b116: a stale activation/keyboard action cannot open covered content.
        if (privacyCurtainOn) return
        if (isConversationHidden(conversation.id)) return
        if (isSelectionMode) {
            selectedIds = if (selectedIds.contains(conversation.id)) {
                selectedIds - conversation.id
            } else {
                selectedIds + conversation.id
            }
            // R4-13: the last toggled chat is the range anchor (stable ID, never an index).
            selectionAnchorId = conversation.id
            return
        }
        onAuthorizeConversation(conversation) { lastOpenedConversationId = conversation.id; onOpenConversation(conversation) }
    }

    // R2-18: re-check hidden status at activation; locked chats still go through onAuthorizeConversation.
    fun openLastOpenedConversation() {
        val id = lastOpenedConversationId ?: return
        val target = conversations.firstOrNull { it.id == id }
        if (target == null || isConversationHidden(id)) {
            lastOpenedConversationId = null
            return
        }
        handleConversationTap(target)
    }

    fun togglePinSelected() {
        val selected = renderedConversationOrder.filter { selectedIds.contains(it.id) }
        val pin = selected.any { !it.isPinned }
        selected.forEach { if (it.isPinned != pin) onTogglePin(it.id) }
        selectedIds = emptySet()
    }

    fun toggleMuteSelected() {
        val selected = renderedConversationOrder.filter { selectedIds.contains(it.id) }
        val mute = selected.any { !it.isMuted }
        selected.forEach { if (it.isMuted != mute) onToggleMute(it.id) }
        selectedIds = emptySet()
    }

    if (showDeleteConfirm) {
        val count = selectedIds.size
        HomeConfirmDialog(
            title = "Delete $count chat${if (count == 1) "" else "s"}?",
            message = "This removes the selected conversations for this account. " +
                "Shared media objects are not silently deleted.",
            confirmLabel = "Delete",
            onConfirm = {
                runBulkAction(HomeBulkAction.DELETE) {
                    onDeleteConversations(selectedIds.toList())
                    selectedIds = emptySet()
                    selectionAnchorId = null
                }
                showDeleteConfirm = false
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }

    if (showEffects) com.example.chat.ui.camera.EffectPickerHost(onDismiss = { showEffects = false })

    if (showArchived) {
        // Dart _archivedTile: Navigator.push(_ArchivedChatsScreen). Hosted as a full-screen dialog so system
        // Back pops it; opening a conversation replaces this host's route (list position not restored).
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showArchived = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = true,
            ),
        ) {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                ArchivedChatsScreen(
                    isConversationHidden = isConversationHidden,
                    // Locked archived rows are masked and excluded from bulk actions, as on main Home.
                    isConversationLocked = lockedConversationIds::contains,
                    conversations = conversations,
                    theme = theme,
                    homePrefs = homePreferences,
                    onBack = { showArchived = false },
                    onOpenConversation = { conversation ->
                        showArchived = false
                        onAuthorizeConversation(conversation) { onOpenConversation(conversation) }
                    },
                    onToggleArchive = onToggleArchive,
                    onDeleteConversations = onDeleteConversations,
                )
            }
        }
    }

    if (showWhoCanCallMe && preferencesController != null) {
        // b116: the old call omitted server callbacks and changed only local preferences:
        // WhoCanCallMeDialog(preferences = preferencesController, onDismiss = { showWhoCanCallMe = false })
        if (loadCallPolicy != null && saveCallPolicy != null) {
            com.example.chat.ui.settings.privacy.WhoCanCallMeDialog(
                preferences = preferencesController,
                onDismiss = { showWhoCanCallMe = false },
                loadCallPolicy = loadCallPolicy,
                saveCallPolicy = saveCallPolicy,
            )
        } else {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showWhoCanCallMe = false },
                title = { Text("Call privacy unavailable") },
                text = { Text("Server call privacy could not be opened here. No policy was changed.") },
                confirmButton = { ChatyTextButton(onClick = { showWhoCanCallMe = false }, label = "Close") },
            )
        }
    }

    if (showLockedSetup) {
        LockedChatsSetupDialog(
            theme = theme,
            onClose = { showLockedSetup = false },
            onConfigureSecurity = nav.onOpenSecuritySettings,
        )
    }

    // R2-21: platform key names, display only. Every binding listed here is inert
    // inside text fields: search and the message-number field keep their editing keys.
    if (showShortcutHelp) {
        AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
            onDismissRequest = { showShortcutHelp = false },
            title = { Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_keyboard_shortcuts), fontWeight = FontWeight.W700) },  // was: "Keyboard shortcuts"
            text = {
                Column {
                    Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_ctrl_tab_move_between_the_chat_list_and_the_detail), fontSize = 13.sp, color = Color(theme.primaryTextColor))  // was: "Ctrl + Tab - move between the chat list and the detail pane; Tab keeps normal traversal"
                    Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_alt_n_focus_the_next_visible_unlocked_unread_chat_), fontSize = 13.sp, color = Color(theme.primaryTextColor))  // was: "Alt + N - focus the next visible unlocked unread chat (opens nothing, marks nothing read)"
                    Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_up_down_move_row_focus_opens_nothing_marks_nothing), fontSize = 13.sp, color = Color(theme.primaryTextColor))  // was: "Up / Down - move row focus (opens nothing, marks nothing read)"
                    Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_home_end_jump_to_the_first_last_chat), fontSize = 13.sp, color = Color(theme.primaryTextColor))  // was: "Home / End - jump to the first / last chat"
                    Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_enter_or_space_open_the_focused_chat), fontSize = 13.sp, color = Color(theme.primaryTextColor))  // was: "Enter or Space - open the focused chat"
                    Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_shift_up_down_with_a_selection_extend_the_selected), fontSize = 13.sp, color = Color(theme.primaryTextColor))  // was: "Shift + Up / Down with a selection - extend the selected range"
                    Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_esc_back_close_the_top_layer_menu_pane_selection_t), fontSize = 13.sp, color = Color(theme.primaryTextColor))  // was: "Esc / Back - close the top layer (menu, pane, selection, then search)"
                    Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_type_while_the_list_is_focused_to_jump_to_a_chat_n), fontSize = 13.sp, color = Color(theme.primaryTextColor))  // was: "Type while the list is focused to jump to a chat name."
                    Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_these_never_type_into_search_or_text_fields), fontSize = 12.sp, color = Color(theme.secondaryTextColor))  // was: "These never type into search or text fields."
                }
            },
            confirmButton = { ChatyTextButton(onClick = { showShortcutHelp = false }, label = "Close") },
        )
    }

    // R4-14/R4-19: read-only target list in the current rendered (Home) order with
    // the scope label; entries are removable and nothing else mutates. Titles come
    // from the same display resolver as the list, so hidden/protected rows expose
    // nothing extra here.
    if (showSelectionInspector) {
        val inspectorTargets = HomeSelectionPolicy.inspectorOrder(renderedConversationOrder, selectedIds)
        AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
            onDismissRequest = { showSelectionInspector = false },
            title = { Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_selected_chats_count, inspectorTargets.size), fontWeight = FontWeight.W700) },  // was: Text("Selected chats (${inspectorTargets.size})"
            text = {
                if (inspectorTargets.isEmpty()) {
                    Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_no_chats_selected), fontSize = 13.sp, color = Color(theme.secondaryTextColor))  // was: "No chats selected."
                } else {
                    // Preserve reviewed outer-dialog scroll ownership.
        Column {
                        Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_in_the_order_shown_on_home), fontSize = 12.sp, color = Color(theme.secondaryTextColor))  // was: "In the order shown on Home"
                        inspectorTargets.take(50).forEach { conversation ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            ) {
                                Text(
                                    if (isConversationLocked(conversation.id)) "Locked conversation"
                                    else homeDisplayTitles[conversation.id] ?: conversation.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = Color(theme.primaryTextColor),
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f),
                                )
                                ChatyTextButton(
                                    onClick = {
                                        selectedIds = selectedIds - conversation.id
                                        if (selectionAnchorId == conversation.id) selectionAnchorId = null
                                    },
                                    label = "Remove",
                                )
                            }
                        }
                        if (inspectorTargets.size > 50) {
                            Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_more_count, inspectorTargets.size - 50), fontSize = 12.sp, color = Color(theme.secondaryTextColor))  // was: Text("+ ${inspectorTargets.size - 50} more"
                        }
                    }
                }
            },
            confirmButton = { ChatyTextButton(onClick = { showSelectionInspector = false }, label = "Done") },
        )
    }

    // R4-31: secondary-click menu with the same reviewed actions as swipe/selection.
    // Opening it selects no recipient, starts no call and mutates no chat state.
    val rowMenuConversation = rowMenuConversationId?.let { id -> conversations.firstOrNull { it.id == id } }
    if (rowMenuConversation != null) {
        val menuConversation = rowMenuConversation
        AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
            onDismissRequest = { rowMenuConversationId = null },
            title = {
                Text(
                    if (isConversationLocked(menuConversation.id)) "Locked conversation"
                    else homeDisplayTitles[menuConversation.id] ?: menuConversation.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.W700,
                )
            },
            text = {
                Column {
                    ChatyTextButton(
                        icon = androidx.compose.material.icons.Icons.Rounded.PushPin,
                        onClick = { rowMenuConversationId = null; authorizeAction(listOf(menuConversation.id)) { onTogglePin(menuConversation.id) } },
                        label = if (menuConversation.isPinned) "Unpin" else "Pin",
                    )
                    ChatyTextButton(
                        icon = androidx.compose.material.icons.Icons.Rounded.NotificationsOff,
                        onClick = { rowMenuConversationId = null; authorizeAction(listOf(menuConversation.id)) { onToggleMute(menuConversation.id) } },
                        label = if (menuConversation.isMuted) "Unmute" else "Mute",
                    )
                    ChatyTextButton(
                        icon = androidx.compose.material.icons.Icons.Outlined.Archive,
                        onClick = { rowMenuConversationId = null; authorizeAction(listOf(menuConversation.id)) { onToggleArchive(menuConversation.id) } },
                        label = if (menuConversation.isArchived) "Unarchive" else "Archive",
                    )
                    ChatyTextButton(
                        icon = androidx.compose.material.icons.Icons.Outlined.MarkChatRead,
                        onClick = { rowMenuConversationId = null; authorizeAction(listOf(menuConversation.id)) { onMarkRead(menuConversation.id) } },
                        label = "Mark read",
                    )
                    ChatyTextButton(
                        icon = androidx.compose.material.icons.Icons.Outlined.MarkChatUnread,
                        onClick = { rowMenuConversationId = null; authorizeAction(listOf(menuConversation.id)) { onMarkUnread(menuConversation.id) } },
                        label = "Mark unread",
                    )
                }
            },
            confirmButton = { ChatyTextButton(icon = androidx.compose.material.icons.Icons.Rounded.Close, onClick = { rowMenuConversationId = null }, label = "Close") },
        )
    }

    // R2-32: shown only when the host supplies a VERIFIED platform screen-share
    // signal; detection is not claimed otherwise (the param defaults false).
    if (screenShareActive && !screenShareReminderDismissed) {
        AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
            onDismissRequest = { screenShareReminderDismissed = true },
            title = { Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_screen_sharing_may_be_active), fontWeight = FontWeight.W700) },  // was: "Screen sharing may be active"
            text = {
                Text(
                    androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_home_titles_and_previews_can_be_visible_to_anyone_),  // was: "Home titles and previews can be visible to anyone watching. You can hide them with the privacy curtain - a display aid only, not an app lock or encryption."
                    fontSize = 13.sp,
                    color = Color(theme.primaryTextColor),
                )
            },
            confirmButton = {
                ChatyTextButton(
                    onClick = { privacyCurtainOn = true; screenShareReminderDismissed = true },
                    label = "Hide content",
                    fontWeight = FontWeight.W700,
                )
            },
            dismissButton = {
                ChatyTextButton(onClick = { screenShareReminderDismissed = true }, label = "Continue")
            },
        )
    }

    if (showMessageNumber) {
        AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
            onDismissRequest = { showMessageNumber = false },
            title = {
                Column {
                    Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_message_a_number), fontWeight = FontWeight.W700)  // was: "Message a number"
                    Text(
                        androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_start_a_direct_chat_without_saving_contact),  // was: "Start a direct chat without saving contact"
                        fontSize = 13.sp,
                        color = Color(theme.secondaryTextColor),
                    )
                }
            },
            text = {
                OutlinedTextField(
                    value = messageNumberText,
                    onValueChange = { messageNumberText = it },
                    label = { Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_phone_number)) },  // was: "Phone number"
                    placeholder = { Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_include_country_code_1)) },  // was: "Include country code (+1...)"
                    leadingIcon = { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.callAudio, semanticLabel = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                )
            },
            confirmButton = {
                ChatyTextButton(
                    onClick = {
                        val number = messageNumberText.trim()
                        showMessageNumber = false
                        messageNumberText = ""
                        if (number.isNotEmpty()) nav.onMessageNumber(number)
                    },
                    label = "Chat",
                    fontWeight = FontWeight.W700,
                )
            },
            dismissButton = {
                ChatyTextButton(
                    onClick = { showMessageNumber = false; messageNumberText = "" },
                    label = "Cancel",
                )
            },
        )
    }

    if (shellHeaderController != null) {
        // Re-publish current capabilities after a receiver restart, not a retained failed snapshot.
        val receiverMount = headerEntry.mountGeneration
        val selected = renderedConversationOrder.filter { selectedIds.contains(it.id) }
        androidx.compose.runtime.SideEffect {
            if (receiverMount == headerEntry.mountGeneration) headerEntry.model = com.example.chat.ui.navigation.HomeHeaderModel(
                theme, homePrefs, headerStyle, homeStyle,
                if (forcedType != null) pageTitle ?: barTitle else barTitle, identity, forcedType != null,
                requiresSubscription, subscriptionMessage, validBulkActions,
                HomeSelectionPolicy.inspectorOrder(renderedConversationOrder, selectedIds).size,
                selected.any { !it.isPinned }, selected.any { !it.isMuted }, selectedIds.any { !isConversationLocked(it) },
                searchEnabled, searchPlacement,
                if (forcedType == ConversationType.group) "Search groups…" else "Search chat names and latest messages…",
                connectionHealthState != null, effectiveNav.onOpenSplitChat != null, effectiveNav.showTasksEntry, effectiveNav.showUpdatesEntry,
            )
        }
        // Publish read-only Home-tool facts at the page root (independent of list content) for the chats FAB menu.
        // Cleared by HomeHeaderEntry.detach() together with the receiver.
        androidx.compose.runtime.SideEffect {
            headerEntry.canRefresh = onRefreshHome != null
            headerEntry.refreshing = homeRefreshing
            // Same predicate as the branch that renders the old curtain chip: the list shows entries.
            headerEntry.hasVisibleChats = entries.isNotEmpty()
        }
        val dispatchHeaderEvent by androidx.compose.runtime.rememberUpdatedState<(com.example.chat.ui.navigation.HeaderEvent) -> Unit>({ event ->
            val selected = renderedConversationOrder.filter { selectedIds.contains(it.id) }
            when (event.action) {
                com.example.chat.ui.navigation.HeaderAction.ClearSelection -> { selectedIds = emptySet(); selectionAnchorId = null }
                com.example.chat.ui.navigation.HeaderAction.TogglePin -> { runBulkAction(if (selected.any { !it.isPinned }) HomeBulkAction.PIN else HomeBulkAction.UNPIN) { togglePinSelected() } }
                com.example.chat.ui.navigation.HeaderAction.Delete -> { runBulkAction(HomeBulkAction.DELETE) { showDeleteConfirm = true } }
                com.example.chat.ui.navigation.HeaderAction.ToggleMute -> { runBulkAction(if (selected.any { !it.isMuted }) HomeBulkAction.MUTE else HomeBulkAction.UNMUTE) { toggleMuteSelected() } }
                com.example.chat.ui.navigation.HeaderAction.ToggleArchive -> { runBulkAction(if (selected.any { !it.isArchived }) HomeBulkAction.ARCHIVE else HomeBulkAction.UNARCHIVE) {
                                selectedIds.toList().forEach(onToggleArchive)
                                selectedIds = emptySet()
                                selectionAnchorId = null
                            } }
                com.example.chat.ui.navigation.HeaderAction.ToggleLock -> { val ids = selectedIds.toList()
                            val lock = ids.any { !isConversationLocked(it) }
                            ids.forEach { onToggleLockConversation(it, lock) }
                            selectedIds = emptySet() }
                com.example.chat.ui.navigation.HeaderAction.MarkUnread -> { runBulkAction(HomeBulkAction.MARK_UNREAD) {
                                selectedIds.forEach(onMarkUnread)
                                selectedIds = emptySet()
                                selectionAnchorId = null
                            } }
                com.example.chat.ui.navigation.HeaderAction.MarkRead -> { runBulkAction(HomeBulkAction.MARK_READ) {
                                selectedIds.forEach(onMarkRead)
                                selectedIds = emptySet()
                                selectionAnchorId = null
                            } }
                com.example.chat.ui.navigation.HeaderAction.SelectAll -> { selectedIds = HomeSelectionPolicy.selectAll(renderedConversationOrder) }
                com.example.chat.ui.navigation.HeaderAction.Invert -> { selectedIds = HomeSelectionPolicy.invert(renderedConversationOrder, selectedIds) }
                com.example.chat.ui.navigation.HeaderAction.Inspect -> { showSelectionInspector = true }
                com.example.chat.ui.navigation.HeaderAction.ExitSelection -> { selectedIds = emptySet(); selectionAnchorId = null }
                com.example.chat.ui.navigation.HeaderAction.SplitChat -> { nav.onOpenSplitChat?.invoke(selectedIds.toList())
                            selectedIds = emptySet() }
                com.example.chat.ui.navigation.HeaderAction.ToggleSearch -> { toggleSearch() }
                com.example.chat.ui.navigation.HeaderAction.IdentityTap -> { if (lockedChatsConfigured) {
                                    onRequestVaultAuth { nav.onOpenLockedVault() }
                                } else {
                                    showLockedSetup = true
                                } }
                com.example.chat.ui.navigation.HeaderAction.IdentityLongPress -> { onRequestVaultAuth { nav.onOpenLockedVault() } }
                com.example.chat.ui.navigation.HeaderAction.OpenOverflowMenu -> { overflowOpen = true }
                com.example.chat.ui.navigation.HeaderAction.CheckHomeAppearance -> { showAppearanceResetPreview = true }
                com.example.chat.ui.navigation.HeaderAction.Dismiss -> { overflowOpen = false }
                com.example.chat.ui.navigation.HeaderAction.OpenViewOptions -> { showViewOptions=true }
                com.example.chat.ui.navigation.HeaderAction.OpenScratchpad -> { showScratchpad=true }
                com.example.chat.ui.navigation.HeaderAction.OpenSavedViews -> { showSavedViews = true }
                com.example.chat.ui.navigation.HeaderAction.OpenViewHistory -> { showViewHistory = true }
                com.example.chat.ui.navigation.HeaderAction.OpenShortcutHelp -> { showShortcutHelp = true }
                com.example.chat.ui.navigation.HeaderAction.MarkAllAsRead -> { val targets = conversations.filter { !isConversationHidden(it.id) }.map { it.id }
                                    authorizeAction(targets) { onMarkAllRead(targets) }
                                    // Completion feedback belongs to the host, not the click handler.
                }
                com.example.chat.ui.navigation.HeaderAction.MessageANumber -> { showMessageNumber = true }
                com.example.chat.ui.navigation.HeaderAction.OpenArchived -> { showArchived = true }
                com.example.chat.ui.navigation.HeaderAction.ChangeQuery -> { searchQuery = event.text
                        // Ordinary search must never verify a secret or consume lock attempts.
                }
                com.example.chat.ui.navigation.HeaderAction.SetGhostMode -> { onToggleGhostMode(event.flag) }
                com.example.chat.ui.navigation.HeaderAction.ShowNotice -> { onSnack(event.text) }
                com.example.chat.ui.navigation.HeaderAction.OpenPlan -> { nav.onSelectPlan() }
                com.example.chat.ui.navigation.HeaderAction.OpenConnectionDetail -> { onOpenConnectionDetail() }
                com.example.chat.ui.navigation.HeaderAction.NavOpenProfile -> { effectiveNav.onOpenProfile() }
                com.example.chat.ui.navigation.HeaderAction.NavOpenAccountSwitcher -> { effectiveNav.onOpenAccountSwitcher() }
                com.example.chat.ui.navigation.HeaderAction.NavOpenSettings -> { effectiveNav.onOpenSettings() }
                com.example.chat.ui.navigation.HeaderAction.NavOpenNotifications -> { effectiveNav.onOpenNotifications() }
                com.example.chat.ui.navigation.HeaderAction.NavOpenSecuritySettings -> { effectiveNav.onOpenSecuritySettings() }
                com.example.chat.ui.navigation.HeaderAction.NavOpenGlobalSearch -> { effectiveNav.onOpenGlobalSearch() }
                com.example.chat.ui.navigation.HeaderAction.NavOpenQr -> { effectiveNav.onOpenQr() }
                com.example.chat.ui.navigation.HeaderAction.NavOpenLinkedDevices -> { effectiveNav.onOpenLinkedDevices() }
                com.example.chat.ui.navigation.HeaderAction.NavNewCall -> { effectiveNav.onNewCall() }
                com.example.chat.ui.navigation.HeaderAction.NavOpenStarredMessages -> { effectiveNav.onOpenStarredMessages() }
                com.example.chat.ui.navigation.HeaderAction.NavOpenMessageManagement -> { effectiveNav.onOpenMessageManagement() }
                com.example.chat.ui.navigation.HeaderAction.NavOpenLockedVault -> { effectiveNav.onOpenLockedVault() }
                com.example.chat.ui.navigation.HeaderAction.NavOpenCameraEffects -> { effectiveNav.onOpenCameraEffects() }
                com.example.chat.ui.navigation.HeaderAction.NavWhoCanCallMe -> { effectiveNav.onWhoCanCallMe() }
                com.example.chat.ui.navigation.HeaderAction.NavShareApp -> { effectiveNav.onShareApp() }
                com.example.chat.ui.navigation.HeaderAction.NavRestartApp -> { effectiveNav.onRestartApp() }
                com.example.chat.ui.navigation.HeaderAction.NavToggleBrightness -> { effectiveNav.onToggleBrightness() }
                com.example.chat.ui.navigation.HeaderAction.NavSelectPlan -> { effectiveNav.onSelectPlan() }
                com.example.chat.ui.navigation.HeaderAction.NavOpenTasks -> { effectiveNav.onOpenTasks() }
                com.example.chat.ui.navigation.HeaderAction.NavOpenUpdates -> { effectiveNav.onOpenUpdates() }
                com.example.chat.ui.navigation.HeaderAction.NavOpenSplitChat -> { effectiveNav.onOpenSplitChat?.invoke(event.ids) }
                com.example.chat.ui.navigation.HeaderAction.NavOpenNewChat -> { effectiveNav.onOpenNewChat(event.flag, event.secondFlag) }
                // Same guards as the old Refresh chip: not while selecting, only when wired, never while a refresh runs.
                com.example.chat.ui.navigation.HeaderAction.RefreshHome -> { if (selectedIds.isEmpty() && !homeRefreshing) onRefreshHome?.invoke() }
                // Same toggle as the old chip. A curtain can always be turned off; turning it on needs displayed entries and no selection.
                com.example.chat.ui.navigation.HeaderAction.TogglePrivacyCurtain -> {
                    if (privacyCurtainOn || (selectedIds.isEmpty() && entries.isNotEmpty())) privacyCurtainOn = !privacyCurtainOn
                }
                com.example.chat.ui.navigation.HeaderAction.NavMessageNumber -> { effectiveNav.onMessageNumber(event.text) }
            }
        })
        DisposableEffect(headerEntry) {
            headerEntry.attach()
            onDispose { headerEntry.detach() }
        }
        LaunchedEffect(headerEntry) {
            androidx.compose.runtime.snapshotFlow { headerEntry.events.firstOrNull() }
                .filterNotNull().collect { event ->
                    // Non-suspending dispatch is atomic with its acknowledgement on the UI thread.
                    // Existing async authorizations retain their own page/account liveness guards.
                    try { if (headerEntry.accepts(event)) dispatchHeaderEvent(event.event) }
                    catch (failure: Exception) {
                        // A callback may throw CancellationException without cancelling this receiver.
                        // Only structural coroutine cancellation terminates collection.
                        if (failure is kotlinx.coroutines.CancellationException &&
                            kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]?.isActive != true) {
                            throw failure
                        }
                        android.util.Log.e("HomeHeader", "Header action failed; receiver reset", failure)
                        headerEntry.recoverReceiverAfterFailure()
                        runCatching { onSnack("That header action could not finish. Please try again.") }
                    }
                    finally { headerEntry.events.removeAll { it.sequence == event.sequence } }
                }
        }
    }

    Scaffold(modifier = Modifier.chatyPredictiveBack(homeBack), containerColor = Color(theme.backgroundColor)) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Inline header retained for layouts without the persistent account-owned shell.
            if (shellHeaderController == null) {
            effectiveConnectionBanner?.invoke()
            if (requiresSubscription) {
                SubscriptionRequiredBanner(theme, subscriptionMessage, nav.onSelectPlan)
            }
            // Header swap with the Dart AnimatedSwitcher timing (230ms,
            // easeOutCubic in / easeInCubic out, slight upward slide).
            val headerReduceMotion = com.example.chat.ui.kit.rememberChatyReduceMotion()
            val headerSwapMs = if (headerReduceMotion) 0 else 230
            AnimatedContent(
                targetState = isSelectionMode,
                transitionSpec = {
                    (
                        fadeIn(tween(headerSwapMs, easing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f))) +
                            slideInVertically(
                                tween(headerSwapMs, easing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)),
                            ) { (it * 0.035f).toInt() }
                        ) togetherWith
                        fadeOut(tween(headerSwapMs, easing = CubicBezierEasing(0.64f, 0f, 0.92f, 0f)))
                },
                label = "home-header",
            ) { selectionMode ->
                if (selectionMode) {
                    val selected = renderedConversationOrder.filter { selectedIds.contains(it.id) }
                    SelectionHomeAppBar(
                        theme = theme,
                        selectedCount = selectedIds.size,
                        visibleSelectedCount = HomeSelectionPolicy.inspectorOrder(renderedConversationOrder, selectedIds).size,
                        validActions = validBulkActions,
                        anyUnpinned = selected.any { !it.isPinned },
                        anyUnmuted = selected.any { !it.isMuted },
                        anyUnlocked = selectedIds.any { !isConversationLocked(it) },
                        onClearSelection = { selectedIds = emptySet(); selectionAnchorId = null },
                        onTogglePin = {
                            runBulkAction(if (selected.any { !it.isPinned }) HomeBulkAction.PIN else HomeBulkAction.UNPIN) { togglePinSelected() }
                        },
                        onDelete = { runBulkAction(HomeBulkAction.DELETE) { showDeleteConfirm = true } },
                        onToggleMute = {
                            runBulkAction(if (selected.any { !it.isMuted }) HomeBulkAction.MUTE else HomeBulkAction.UNMUTE) { toggleMuteSelected() }
                        },
                        onToggleArchive = {
                            runBulkAction(if (selected.any { !it.isArchived }) HomeBulkAction.ARCHIVE else HomeBulkAction.UNARCHIVE) {
                                selectedIds.toList().forEach(onToggleArchive)
                                selectedIds = emptySet()
                                selectionAnchorId = null
                            }
                        },
                        onToggleLock = {
                            val ids = selectedIds.toList()
                            val lock = ids.any { !isConversationLocked(it) }
                            ids.forEach { onToggleLockConversation(it, lock) }
                            selectedIds = emptySet()
                        },
                        onMarkUnread = {
                            runBulkAction(HomeBulkAction.MARK_UNREAD) {
                                selectedIds.forEach(onMarkUnread)
                                selectedIds = emptySet()
                                selectionAnchorId = null
                            }
                        },
                        onMarkRead = {
                            runBulkAction(HomeBulkAction.MARK_READ) {
                                selectedIds.forEach(onMarkRead)
                                selectedIds = emptySet()
                                selectionAnchorId = null
                            }
                        },
                        // R4-11: the target set is the rendered eligible chat entries
                        // (section headers, the archive affordance and hidden chats
                        // excluded). The Bars slice owns showing the exact target count.
                        onSelectAll = { selectedIds = HomeSelectionPolicy.selectAll(renderedConversationOrder) },
                        // R4-12: invert within the shown eligible set only; off-scope
                        // selections are kept out of the operation.
                        onInvert = { selectedIds = HomeSelectionPolicy.invert(renderedConversationOrder, selectedIds) },
                        // R4-14/R4-19: read-only inspection before any bulk effect.
                        onInspect = { showSelectionInspector = true },
                        // R4-20: named exit; clears selection only - search, folder and
                        // quick-view settings are untouched (Back/Escape equivalent above).
                        onExitSelection = { selectedIds = emptySet(); selectionAnchorId = null },
                        onSplitChat = {
                            nav.onOpenSplitChat?.invoke(selectedIds.toList())
                            selectedIds = emptySet()
                        },
                    )
                } else {
                    Box {
                        StandardHomeAppBar(
                            theme = theme,
                            homePrefs = homePrefs,
                            headerStyle = headerStyle,
                            homeStyle = homeStyle,
                            title = if (forcedType != null) pageTitle ?: barTitle else barTitle,
                            identity = identity,
                            isSearchOpen = isSearchOpen,
                            forcedTypeIsSet = forcedType != null,
                            nav = effectiveNav,
                            onToggleSearch = { toggleSearch() },
                            onToggleGhost = onToggleGhostMode,
                            // _handleTopLeftIdentityTap: with Locked Chats
                            // configured the vault stays behind the auth gate
                            // (fail-closed seam; owner wires the real gate),
                            // otherwise the guided setup modal explains it.
                            onIdentityTap = {
                                if (lockedChatsConfigured) {
                                    onRequestVaultAuth { nav.onOpenLockedVault() }
                                } else {
                                    showLockedSetup = true
                                }
                            },
                            // Source deviation, deliberate: Dart long-press
                            // opens the vault directly, but no delivered
                            // Kotlin slice carries a destination-side auth
                            // gate, so every entry path goes through the
                            // fail-closed seam until the shared gate lands.
                            onIdentityLongPress = {
                                onRequestVaultAuth { nav.onOpenLockedVault() }
                            },
                            onOpenOverflowMenu = { overflowOpen = true },
                            onSnack = onSnack,
                            headerExtras = effectiveHeaderExtras,
                        )
                        Box(Modifier.align(Alignment.BottomEnd)) {
                            HomeOverflowMenu(
                                onCheckHomeAppearance = { showAppearanceResetPreview = true },
                                expanded = overflowOpen,
                                onDismiss = { overflowOpen = false },
                                onOpenViewOptions={showViewOptions=true},
                                onOpenScratchpad={showScratchpad=true},
                                onOpenSavedViews = { showSavedViews = true },
                                onOpenViewHistory = { showViewHistory = true },
                                // R2-21: discoverable shortcut map (dialog below; Bars slice
                                // adds the menu item via its optional onOpenShortcutHelp param).
                                onOpenShortcutHelp = { showShortcutHelp = true },
                                theme = theme,
                                nav = effectiveNav,
                                menuPreferences = homePrefs,
                                onMarkAllAsRead = {
                                    val targets = conversations.filter { !isConversationHidden(it.id) }.map { it.id }
                                    authorizeAction(targets) { onMarkAllRead(targets) }
                                    // Completion feedback belongs to the host, not the click handler.
                                },
                                onMessageANumber = { showMessageNumber = true },
                                onOpenConnectionDetail = onOpenConnectionDetail.takeIf { connectionHealthState != null },
                                onOpenArchived = { showArchived = true },
                            )
                        }
                    }
                }
            }
            if (!isSelectionMode) headerBottom?.invoke()
            if (!isSelectionMode && searchEnabled && searchPlacement == "Below header") {
                HomeSearchField(
                    theme = theme,
                    value = searchQuery,
                    onValueChange = { value ->
                        searchQuery = value
                        // Ordinary search must never verify a secret or consume lock attempts.
                    },
                    hint = if (forcedType == ConversationType.group) "Search groups…" else "Search chat names and latest messages…",
                    focusRequester = searchFocus,
                )
            }
            }
            // Legacy page-owned connectionBanner/headerExtras render only in the inline header above.
            // Top-tab layouts use MainNavigationShell.rootHeaderExtension, owned above the pager.
            /* Previous page-local top-shell placement, retained:
            if (shellHeaderController != null) {
                // Extension slots remain supported, page-local and never stored by the controller.
                effectiveConnectionBanner?.invoke()
                effectiveHeaderExtras?.invoke()
            }
            */
            // The supplied Default / OLD / iOS headers already own their single title.
            // Preserve the large-title treatment only for other Home presentations.
            if (shellHeaderController == null && !isSelectionMode && pageTitle != null &&
                !com.example.chat.core.preferences.HomePresentationPolicy.isDefault(homePrefs.homeStyle) &&
                rawHomeUiStyle !in setOf("ios style", "whatsapp old ui")) {
                Box(
                    modifier = Modifier
                        // Collapse drives height to exactly 0; the expanded height scales
                        // with font scale (sp->dp) so large text is never pinned to 44dp.
                        .height(with(LocalDensity.current) { (44f * theme.fontScale.toFloat()).sp.toDp() } * (1 - effectiveTitleCollapse))
                        .alpha(1 - effectiveTitleCollapse),
                ) {
                    Text(
                        pageTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color(theme.primaryTextColor),
                        fontSize = (32 * theme.fontScale).sp,
                        fontWeight = FontWeight.W800,
                        letterSpacing = (-0.5).sp,
                        lineHeight = (32 * theme.fontScale).sp,
                        modifier = Modifier
                            .padding(start = 16.dp, top = 2.dp, end = 16.dp, bottom = 6.dp)
                            .align(Alignment.CenterStart)
                            .clickable {
                                if (securityPreferences.hideLockedChats && securityPreferences.entryByAppTitle) {
                                    // Source opens the vault directly here and
                                    // relies on destination-side auth; no such
                                    // gate exists in the Kotlin slice yet, so
                                    // this entry also fails closed.
                                    onRequestVaultAuth { nav.onOpenLockedVault() }
                                }
                            },
                    )
                }
            }
            val defaultPanel = com.example.chat.core.preferences.HomePresentationPolicy.isDefault(homePrefs.homeStyle)
            com.example.chat.ui.navigation.ReferenceLayoutContentSurface(
                rounded = defaultPanel, isDark = theme.isDark, lightSurface = Color(theme.surfaceColor),
                surfaceOverride = preferencesController?.universal?.listBackgroundColor?.let(::Color),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
            // R4-25: on wide expanded windows a vertical folder rail replaces the
            // horizontal folder strip; the two never show together and the chosen
            // folder is untouched. 917dp reserves 181dp rail + 16dp divider + two
            // 360dp readable panes, measured against this content surface.
            val homeWindowWidthDp = maxWidth.value
            // Tag chips replace both the redundant folder row and its wide-screen rail.
            val showFolderRail = false
            /* Previous rail eligibility retained.
            val showFolderRail = chatPane != null && expandedListDetail && folderAccountKey.isNotBlank() &&
                spec.showFilters && !homePrefs.hideChatSortList && homeWindowWidthDp >= 917 &&
                homePaneState.maximized != HomePaneMax.Detail
            */
            // R2-19: per-folder activity summaries for the chips strip, computed
            // read-only from the post-wave-1 store API via HomeFolderPolicy.
            val folderActivitySummaries = remember(chatFolderRevision, conversations, hiddenConversationIds, lockedConversationIds, homeClockMinute, homeDateContext) {
                chatFolderStore.folders().associate { folder ->
                    folder.id to com.example.chat.ui.chats.home.HomeFolderPolicy.folderActivitySummary(
                        store = chatFolderStore,
                        folderId = folder.id,
                        visible = conversations.filter { it.id !in hiddenConversationIds && it.id !in lockedConversationIds },
                        now = java.time.Instant.ofEpochMilli(homeClockMinute * 60000),
                    )
                }
            }
            val listColumnContent: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
                if (!isSelectionMode) {
                    if (shellHeaderController == null && searchEnabled && searchPlacement == "Header action" && isSearchOpen) {
                        HomeSearchField(
                            theme = theme,
                            value = searchQuery,
                            onValueChange = { value ->
                                searchQuery = value
                                // Explicit vault entry remains behind the authorization gate.
                            },
                            hint = if (forcedType == ConversationType.group) "Search groups…" else "Search chat names and latest messages…",
                            focusRequester = searchFocus,
                        )
                    }
                    if (showStoriesStrip && (!spec.storiesHidden || homePrefs.separateChatsAndGroups) && forcedType != ConversationType.group) {
                        StoryStrip(
                            contacts = contacts,
                            theme = theme,
                            look = storyLook,
                            stripHeight = chatyStoryStripHeight(storyLook, spec.storiesHeight),
                            storiesAvatar = spec.storiesAvatar,
                            storiesStyle = storiesStyle,
                            storyCardWrap = storyCardWrap,
                            storyShowOnlineRing = storyShowOnlineRing,
                            storyCompact = storyCompact,
                            storyTileWidth = storyTileWidth,
                            storyNameFontSize = storyNameFontSize,
                            isOnline = isOnline,
                        )
                    }
                    if (shellHeaderController == null && searchEnabled && searchPlacement == "Above chat filters") {
                        HomeSearchField(
                            theme = theme,
                            value = searchQuery,
                            onValueChange = { value ->
                                searchQuery = value
                                // Dart _homeSearchField is shared by all placements: onChanged runs
                                // _checkSecretSearchPhrase everywhere, so this placement unlocks too.
                                // Explicit vault entry remains behind the authorization gate.
                            },
                            hint = if (forcedType == ConversationType.group) "Search groups…" else "Search chat names and latest messages…",
                            focusRequester = searchFocus,
                        )
                    }
                    /* Home controls moved/hidden per owner review. Triage is redundant with Unread for the primary flow.
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                        ChatyTextButton(onClick = {
                            if (triageSnapshot != null) triageSnapshot = null
                            else triageSnapshot = com.example.chat.ui.chats.home.HomeTriageSnapshot.capture(
                                renderedConversationOrder,
                            ) { id -> !isConversationHidden(id) && !isConversationLocked(id) }
                        }, label = if (triageSnapshot == null) "Start unread triage" else "End unread triage")
                        ChatyTextButton(onClick = { showAppearanceResetPreview = true }, label = "Preview appearance reset")
                    }
                    if (triageSnapshot != null) Text(
                        "Unread triage: snapshot of this view. Read chats stay until End. New unread chats wait for the next session. Hidden, locked or unavailable chats are not shown. Filters, order and grouping apply after End.",
                        color = Color(theme.secondaryTextColor), fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    */
                    if (spec.showFilters && !homePrefs.hideChatSortList) {
                        com.example.chat.ui.chats.home.HomeTagFilters(
                            theme = theme, store = chatFolderStore, enabled = folderAccountKey.isNotBlank(),
                            filters = filters,
                            accountIdentity = accountSessionIdentity, conversations = conversations.filter { !it.isTask },
                            visibleConversations = dedupeConversations(conversations).filter {
                                !it.isTask && (forcedType == null || it.type == forcedType) && it.id !in hiddenConversationIds && it.id !in lockedConversationIds
                            },
                            titles = homeDisplayTitles,
                            selectedFilter = selectedFilter, selectedTagId = selectedFolderId,
                            onFilter = { filter -> selectedFolderId = null; selectedFilter = filter },
                            onTag = { id -> selectedFilter = "All"; selectedFolderId = id },
                            onSaved = { id -> selectedFilter = "All"; selectedFolderId = id },
                        )
                        /* Previous stock filter row retained; tags now share one ordered row.
                        HomeFilterChips(
                            theme = theme,
                            tabBubbleStyle = homePrefs.tabBubbleStyle,
                            filters = filters,
                            selectedFilter = selectedFilter,
                            onSelect = { filter -> selectedFilter = filter },
                        )
                        */
                        /* Superseded by tag chips: keep folder management code for reuse from tags.
                        if (folderAccountKey.isNotBlank() && !showFolderRail) com.example.chat.ui.chats.folders.ChatFolderChips(
                            theme = theme,
                            store = chatFolderStore,
                            conversations = dedupeConversations(conversations).filter { it.id !in hiddenConversationIds && it.id !in lockedConversationIds },
                            selectedFolderId = selectedFolderId,
                            onSelect = { selectedFolderId = it },
                            // S4 optional inputs, wired where safe from Screen state.
                            selectedConversationIds = selectedIds.filterTo(mutableSetOf()) { !isConversationHidden(it) && !isConversationLocked(it) },
                            canInspectConversation = { id -> !isConversationHidden(id) && !isConversationLocked(id) },
                            resolvedConversationIds = if (homeConversationIdsComplete) conversations.map { it.id }.toSet() else null,
                            onOpenConversation = { id ->
                                if (!isConversationHidden(id) && !isConversationLocked(id))
                                    conversations.firstOrNull { it.id == id }?.let { handleConversationTap(it) }
                            },
                            summaries = folderActivitySummaries,
                        )
                        */
                        if (triageSnapshot == null) {
                        // R4-41/42/45: explicit, session-only grouping. Off keeps the
                        // stock list; Date uses loaded last-message dates; Direct/Group
                        // is offered inside a chosen folder (R4-45); A-Z appears only
                        // when name order is deliberately selected (R4-41) and uses the
                        // same displayed-name comparator and locale collation.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(start = 12.dp, top = 2.dp, bottom = 2.dp),
                        ) {
                            if (!isSearchOpen && entries.any { it is HomeEntry.Item && it.conversation.isPinned }) {
                                Text(
                                    if (pinnedCollapsed) "Expand pinned" else "Collapse pinned",
                                    color = Color(theme.accentColor), fontSize = 11.sp,
                                    modifier = Modifier.clickable { pinnedCollapsed = !pinnedCollapsed }
                                        .padding(end = 8.dp, top = 6.dp, bottom = 6.dp),
                                )
                            }
                            /* Group Off/Date moved to the Home settings switch; old alternate-mode UI retained.
                            Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_group), color = Color(theme.secondaryTextColor), fontSize = 11.sp)  // was: "Group:"
                            val groupOptions = buildList<Pair<String, HomeGroupMode>> {
                                add("Off" to HomeGroupMode.None)
                                add("Date" to currentGroupMode("date"))
                                if (selectedFolderId != null) add("Direct / Group" to HomeGroupMode.DirectGroup)
                                if (localOrder == com.example.chat.ui.chats.home.HomeLocalOrder.Name) {
                                    add("A-Z" to HomeGroupMode.Alphabetical(
                                        displayTitleFor = { homeDisplayTitles[it.id] ?: it.title },
                                        locale = homeSortLocale,
                                    ))
                                }
                            }
                            groupOptions.forEach { (label, mode) ->
                                val active = effectiveGroupMode::class == mode::class
                                Text(
                                    label,
                                    color = Color(if (active) theme.accentColor else theme.secondaryTextColor),
                                    fontSize = 11.sp,
                                    fontWeight = if (active) FontWeight.W700 else FontWeight.W400,
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                        .clickable { homeGroupMode = if (active) HomeGroupMode.None else mode }
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }                            */
                        }
                        if (effectiveGroupMode is HomeGroupMode.Alphabetical) {
                            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                                com.example.chat.ui.chats.home.homeSectionJumpTargets(groupedEntries)
                                    .filterNot { com.example.chat.ui.chats.home.isPinnedHomeSectionLabel(it.label) }
                                    .forEach { target ->
                                        ChatyTextButton(onClick = {
                                            scope.launch { listState.animateScrollToItem(target.entryIndex) }
                                        }, label = target.label)
                                    }
                            }
                        }
                        }
                    }
                }
                if (entries.isEmpty()) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        EmptyChatsState(
                            theme = theme,
                            isGroups = forcedType == ConversationType.group,
                            onSearch = if(searchQuery.isNotBlank() || selectedFilter!="All" || selectedFolderId!=null || quickView!=com.example.chat.ui.chats.home.HomeQuickView.All) {
                                {searchQuery="";selectedFilter="All";selectedFolderId=null;quickView=com.example.chat.ui.chats.home.HomeQuickView.All}
                            } else nav.onOpenGlobalSearch,
                            filtered = searchQuery.isNotBlank() || selectedFilter!="All" || selectedFolderId!=null || quickView!=com.example.chat.ui.chats.home.HomeQuickView.All,
                            archivedOnly = visibleConversations.isNotEmpty(),
                            onOpenArchive = {showArchived=true},
                        )
                    }
                } else {
                    BoxWithConstraints(Modifier.weight(1f)) {
                        val listContent: @Composable () -> Unit = {
                            // b116: do not compose covered rows, their semantics or keyboard targets.
                            // Previously: androidx.compose.runtime.key(accountSessionIdentity) { HomeConversationList(...) }
                            if (!privacyCurtainOn) androidx.compose.runtime.key(accountSessionIdentity) {
                            HomeConversationList(
                                listAnimation = preferencesController?.effects?.homeListAnimation ?: "Standard",
                                entries = groupedEntries,
                                clockMinute = homeClockMinute,
                                theme = theme,
                                homePrefs = homePrefs,
                                spec = spec,
                                bundleTemplates = bundleTemplates,
                                currentUserId = currentUserId,
                                contacts = contacts,
                                selectedIds = selectedIds,
                                isSelectionMode = isSelectionMode,
                                presenceFor = presenceFor,
                                messagesFor = messagesFor,
                                lastDeliveryStateFor = lastDeliveryStateFor,
                                gbBool = gbBool,
                                gbColor = gbColor,
                                isConversationLocked = isConversationLocked,
                                onOpenArchived = { onOpenArchived(); showArchived = true },
                                onRowTap = { handleConversationTap(it) },
                                onRowLongPress = { conversation ->
                                    selectionAnchorId = conversation.id
                                    selectedIds = if (selectedIds.contains(conversation.id)) {
                                        selectedIds - conversation.id
                                    } else {
                                        selectedIds + conversation.id
                                    }
                                },
                                onTogglePin = { id -> authorizeAction(listOf(id)) { onTogglePin(id) } },
                                onToggleMute = { id -> authorizeAction(listOf(id)) { onToggleMute(id) } },
                                onToggleArchive = { id -> authorizeAction(listOf(id)) { onToggleArchive(id) } },
                                onStartCall = onStartCall,
                                listState = listState,
                                openConversationId = paneConversation?.id,
                                focusedConversationId = focusedConversationId,
                                hoveredRowId = hoveredRowId,
                                orderedConversationIds = renderedConversationOrder.map { it.id },
                                rowLabels = renderedConversationOrder.map { homeDisplayTitles[it.id] ?: it.title },
                                opState = opState,
                                onRetry = onRetry,
                                focusRequesterFor = { id -> rowFocusRequesters.getOrPut(id) { FocusRequester() } },
                                onRowFocused = { focusedConversationId = it },
                                onHoverRow = { hoveredRowId = it },
                                onRowContextMenu = { rowMenuConversationId = it.id },
                                onRowActivate = { handleConversationTap(it) },
                                onRangeSelectTo = { targetId ->
                                    // R4-13: extend from the anchor across the current ordered
                                    // visible list; the anchor resets on account/scope change.
                                    val anchor = selectionAnchorId ?: targetId
                                    selectedIds = selectedIds + HomeSelectionPolicy.range(renderedConversationOrder, anchor, targetId)
                                },
                                onSectionHeaderTap = { label ->
                                    if (com.example.chat.ui.chats.home.isPinnedHomeSectionLabel(label)) pinnedCollapsed = !pinnedCollapsed
                                },
                                onNextUnread = {
                                    val after = renderedConversationOrder.indexOfFirst { it.id == focusedConversationId }
                                    val targets = renderedConversationOrder.drop(after + 1) + renderedConversationOrder.take(after + 1)
                                    val next = targets.firstOrNull {
                                        it.unreadCount > 0 && !isConversationHidden(it.id) && !isConversationLocked(it.id)
                                    }
                                    if (next == null) onSnack("No eligible unread chats in this view.")
                                    else focusedConversationId = next.id
                                },
                            )
                            }
                        }
                        // 'Tablet Split View': list + quick pane on wide layouts;
                        // narrow layouts fall back to the single column.
                        if (spec.splitView && maxWidth >= 640.dp) {
                            Row {
                                Box(Modifier.width(360.dp)) { listContent() }
                                // VerticalDivider(width: 1, color: surfaceColor)
                                Spacer(
                                    Modifier
                                        .width(1.dp)
                                        .fillMaxHeight()
                                        .background(Color(theme.surfaceColor)),
                                )
                                Box(Modifier.weight(1f)) {
                                    val splitPinned = remember(conversations, hiddenConversationIds, lockedConversationIds, forcedType) {
                                        conversations.filter { it.isPinned && !it.isArchived && it.id !in hiddenConversationIds && it.id !in lockedConversationIds && (forcedType == null || it.type == forcedType) }
                                    }
                                    val splitGroups = remember(conversations, hiddenConversationIds, lockedConversationIds, forcedType) {
                                        conversations.filter {
                                            it.type == ConversationType.group && !it.isArchived && it.id !in hiddenConversationIds && it.id !in lockedConversationIds && (forcedType == null || it.type == forcedType)
                                        }
                                    }
                                    // Previously composed even beneath the visual curtain.
                                    if (!privacyCurtainOn) SplitQuickPane(
                                        theme = theme,
                                        pinned = splitPinned,
                                        groups = splitGroups,
                                        displayTitleFor = { homeDisplayTitles[it.id] ?: it.title },
                                        onOpen = { handleConversationTap(it) },
                                    )
                                }
                            }
                        } else {
                            listContent()
                        }
                        if (privacyCurtainOn) {
                            // R2-31: covers list titles and previews only; the header,
                            // filters, pills and navigation outside this surface stay
                            // available. Display aid - not an app lock, not encryption,
                            // no screenshot guarantee. Taps here never reveal content;
                            // the explicit "Show content" control below does.
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(Color(theme.backgroundColor))
                                    .clickable { },
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_privacy_curtain_on), color = Color(theme.primaryTextColor), fontSize = 15.sp, fontWeight = FontWeight.W700)  // was: "Privacy curtain on"
                                    Text(
                                        androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_a_display_aid_only_not_an_app_lock_encryption_or_a),  // was: "A display aid only - not an app lock, encryption or a screenshot guarantee."
                                        color = Color(theme.secondaryTextColor),
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(top = 6.dp, start = 24.dp, end = 24.dp),
                                    )
                                }
                            }
                        }
                        // R2-18 / R2-23: explicit shortcuts; system Back and active filters are untouched.
                        val lastOpenedTarget = lastOpenedConversationId?.let { id ->
                            conversations.firstOrNull { it.id == id && id !in hiddenConversationIds }
                        }
                        // Previously: if (!isSelectionMode && lastOpenedTarget != null)
                        // Do not emit a locked title, or any conversation shortcut above the curtain.
                        if (!privacyCurtainOn && !isSelectionMode && lastOpenedTarget != null &&
                            !isConversationLocked(lastOpenedTarget.id)) {
                            Text(
                                androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_back_to_title, homeDisplayTitles[lastOpenedTarget.id] ?: lastOpenedTarget.title),  // was: "Back to ${homeDisplayTitles[lastOpenedTarget.id] ?: lastOpenedTarget.title}",
                                color = Color(theme.primaryTextColor),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(start = 12.dp, bottom = 12.dp)
                                    .width(180.dp)
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                                    .background(Color(theme.surfaceColor))
                                    .clickable { openLastOpenedConversation() }
                                    .heightIn(min = 48.dp)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                        // Previously: if (resultCountAnnouncement != null)
                        if (!privacyCurtainOn && resultCountAnnouncement != null) {
                            Box(
                                Modifier
                                    .width(1.dp)
                                    .height(1.dp)
                                    .semantics {
                                        liveRegion = LiveRegionMode.Polite
                                        contentDescription = resultCountAnnouncement
                                    },
                            )
                        }
                        if (!isSelectionMode && showJumpToTop) {
                            Text(
                                androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_top),  // was: "Top"
                                color = Color(theme.primaryTextColor),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.W700,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 8.dp)
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                                    .background(Color(theme.surfaceColor))
                                    .clickable { scope.launch { listState.animateScrollToItem(0) } }
                                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        // R2-29: bounded manual refresh affordance. The host owns the
                        // sync call, its bounds and its completion; this is only the
                        // visible trigger plus in-progress cue. Hidden when unwired.
                        // was: if (!isSelectionMode && onRefreshHome != null) {
                        // Hidden only while the chats FAB menu hosts Refresh (see HomeHeaderEntry.fabHostsHomeTools).
                        if (!isSelectionMode && onRefreshHome != null && !headerEntry.fabHostsHomeTools) {
                            // b117: previous Text-only target retained below; explicit 48dp clickable box.
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 12.dp, bottom = 12.dp)
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                                    .background(Color(theme.surfaceColor))
                                    .clickable(enabled = !homeRefreshing) { onRefreshHome.invoke() }
                                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(if (homeRefreshing) "Refreshing…" else "Refresh",
                                    color = Color(theme.primaryTextColor), fontSize = 13.sp)
                            }
                            // Text(
                            //                                 if (homeRefreshing) "Refreshing…" else "Refresh",
                            //                                 color = Color(theme.primaryTextColor),
                            //                                 fontSize = 13.sp,
                            //                                 modifier = Modifier
                            //                                     .align(Alignment.BottomEnd)
                            //                                     .padding(end = 12.dp, bottom = 12.dp)
                            //                                     .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                            //                                     .background(Color(theme.surfaceColor))
                            //                                     .clickable(enabled = !homeRefreshing) { onRefreshHome.invoke() }
                            //                                     .padding(horizontal = 12.dp, vertical = 8.dp),
                            //                             )

                        }
                        // R2-31: the curtain's explicit user trigger; stays reachable
                        // while the curtain is up so content can be shown again.
                        // Previously: if (!isSelectionMode)
                        // was: if (privacyCurtainOn || !isSelectionMode) {
                        if ((privacyCurtainOn || !isSelectionMode) && !headerEntry.fabHostsHomeTools) {
                            // b117: 12dp + 48dp refresh target + 12dp gap = 72dp bottom offset.
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 12.dp, bottom = 72.dp)
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                                    .background(Color(theme.surfaceColor))
                                    .clickable { privacyCurtainOn = !privacyCurtainOn }
                                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(if (privacyCurtainOn) "Show content" else "Privacy curtain",
                                    color = Color(theme.primaryTextColor), fontSize = 13.sp)
                            }
                            // Text(
                            //                                 if (privacyCurtainOn) "Show content" else "Privacy curtain",
                            //                                 color = Color(theme.primaryTextColor),
                            //                                 fontSize = 13.sp,
                            //                                 modifier = Modifier
                            //                                     .align(Alignment.BottomEnd)
                            //                                     .padding(end = 12.dp, bottom = 52.dp)
                            //                                     .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                            //                                     .background(Color(theme.surfaceColor))
                            //                                     .clickable { privacyCurtainOn = !privacyCurtainOn }
                            //                                     .padding(horizontal = 12.dp, vertical = 8.dp),
                            //                             )

                        }
                    }
                }
            }
            // R7 (responsive plan): at expanded width the chat list becomes a
            // fixed 360-400dp pane with the open chat beside it (WhatsApp
            // tablet behavior); otherwise the single column is unchanged.
            if (chatPane != null && expandedListDetail) {
                // R4-21..23/27/30: session-only pane layout from HomeLayoutPolicy.
                // The divider is bounded by clampDivider so both panes stay usable;
                // hinge bounds stay empty until R4-26's window-manager decision lands
                // (held). Width persistence requires an explicit preference choice
                // first (R4-21), so nothing is stored.
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val availableWidthDp = maxWidth.value
                    // Hide the rail when it would consume the two readable pane minimums.
                    val railWidthDp = if (showFolderRail && availableWidthDp >= 917f &&
                        homePaneState.maximized != HomePaneMax.Detail) 181f else 0f
                    val dividerWidthDp = 48f
                    val paneWidthDp = (availableWidthDp - railWidthDp).coerceAtLeast(0f)
                    val paneLayout = homePaneState.layout(
                        availableWidthDp = paneWidthDp,
                        detailOpen = paneConversation != null,
                        expandedListDetail = true,
                        dividerWidthDp = dividerWidthDp,
                    )
                    val listPaneFocusRequester = remember { FocusRequester() }
                    val detailPaneFocusRequester = remember { FocusRequester() }
                    var detailPaneHasFocus by remember(accountSessionIdentity) { mutableStateOf(false) }
                    Row(
                        Modifier
                            .fillMaxSize()
                            // R4-27: Ctrl+Tab moves focus between list and detail; plain
                            // Tab keeps field/control traversal. This shortcut
                            // submits no text and marks nothing read.
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && event.key == Key.Tab && event.isCtrlPressed &&
                                    // Previously: !event.isAltPressed && !event.isMetaPressed && paneLayout.isSplit
                                    !privacyCurtainOn && !event.isAltPressed && !event.isMetaPressed && paneLayout.isSplit) {
                                    val target = if (detailPaneHasFocus) listPaneFocusRequester else detailPaneFocusRequester
                                    detailPaneHasFocus = !detailPaneHasFocus
                                    runCatching { target.requestFocus() }
                                    true
                                } else {
                                    false
                                }
                            },
                    ) {
                        if (railWidthDp > 0f && paneLayout.showList) {
                            // R4-25: the rail replaces the horizontal strip (suppressed
                            // above); it shows no duplicate navigation and never alters
                            // the chosen folder.
                            Column(
                                Modifier
                                    .width(180.dp)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState())
                                    .padding(vertical = 8.dp),
                            ) {
                                Text(
                                    androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_all_chats),  // was: "All chats"
                                    color = Color(if (selectedFolderId == null) theme.accentColor else theme.primaryTextColor),
                                    fontSize = 13.sp,
                                    fontWeight = if (selectedFolderId == null) FontWeight.W700 else FontWeight.W400,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedFolderId = null }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                )
                                chatFolderStore.folders().forEach { folder ->
                                    Text(
                                        folder.name,
                                        color = Color(if (selectedFolderId == folder.id) theme.accentColor else theme.primaryTextColor),
                                        fontSize = 13.sp,
                                        fontWeight = if (selectedFolderId == folder.id) FontWeight.W700 else FontWeight.W400,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedFolderId = folder.id }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                    )
                                }
                            }
                            Spacer(
                                Modifier
                                    .width(1.dp)
                                    .fillMaxHeight()
                                    .background(Color(theme.surfaceColor)),
                            )
                        }
                        if (paneLayout.showList) {
                            val listWidthDp = if (paneLayout.showDetail) {
                                (paneWidthDp - dividerWidthDp).coerceAtLeast(0f) * paneLayout.dividerFraction
                            } else {
                                paneWidthDp
                            }
                            Box(
                                Modifier
                                    .width(listWidthDp.dp)
                                    .fillMaxHeight()
                                    .focusRequester(listPaneFocusRequester)
                                    .focusable(),
                            ) {
                                Column(Modifier.fillMaxSize(), content = listColumnContent)
                            }
                        }
                        if (paneLayout.showList && paneLayout.showDetail) {
                            Column(
                                Modifier
                                    .width(dividerWidthDp.dp)
                                    .fillMaxHeight()
                                    .pointerInput(availableWidthDp, showFolderRail) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            val spanDp = (paneWidthDp - dividerWidthDp).coerceAtLeast(1f)
                                            val next = homePaneState.dividerFraction + dragAmount.x.toDp().value / spanDp
                                            homePaneState = homePaneState.copy(dividerFraction = clampDivider(next, emptyList()))
                                        }
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Spacer(Modifier.width(1.dp).weight(1f).background(Color(theme.surfaceColor)))
                                // R4-22/23: temporary maximize with a visible restore.
                                // Selection, folder, search, the list anchor and the open
                                // conversation are untouched; Back also restores (R4-28).
                                listOf("List" to HomePaneMax.List, "Both" to HomePaneMax.None, "Detail" to HomePaneMax.Detail).forEach { (label, max) ->
                                    Text(
                                        label,
                                        color = Color(if (homePaneState.maximized == max) theme.accentColor else theme.secondaryTextColor),
                                        fontSize = 8.sp,
                                        modifier = Modifier
                                            .clickable { homePaneState = homePaneState.copy(maximized = max) }
                                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                            .padding(vertical = 6.dp),
                                    )
                                }
                                Spacer(Modifier.width(1.dp).weight(1f).background(Color(theme.surfaceColor)))
                            }
                        }
                        if (paneLayout.showDetail) {
                            Box(
                                Modifier
                                    .then(if (paneLayout.showList) Modifier.weight(1f) else Modifier.fillMaxSize())
                                    .fillMaxHeight()
                                    .focusRequester(detailPaneFocusRequester)
                                    .onFocusChanged { detailPaneHasFocus = it.hasFocus }
                                    // Previously: .focusable()
                                    .then(if (privacyCurtainOn) Modifier else Modifier.focusable()),
                            ) {
                                val paneConv = paneConversation
                                // Previously: if (paneConv != null) { chatPane(paneConv) }
                                // Removing detail from composition also removes its semantics/focus targets.
                                if (privacyCurtainOn) {
                                    Box(Modifier.fillMaxSize().background(Color(theme.backgroundColor)), contentAlignment = Alignment.Center) {
                                        Text("Privacy curtain on", color = Color(theme.primaryTextColor))
                                        ChatyTextButton(
                                            onClick = { privacyCurtainOn = false },
                                            label = "Show content",
                                            modifier = Modifier.align(Alignment.BottomCenter),
                                        )
                                    }
                                } else if (paneConv != null) {
                                    chatPane(paneConv)
                                } else {
                                    // R4-30: concise keyboard/touch guidance plus a safe
                                    // New chat entry; no private automatic recommendations.
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_select_a_chat), color = Color(theme.secondaryTextColor), fontSize = 15.sp)  // was: "Select a chat"
                                            Text(
                                                androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_tap_a_chat_on_the_left_or_press_tab_to_move_here_a),  // was: "Tap a chat on the left, or press Tab to move here and Enter to open the focused chat."
                                                color = Color(theme.secondaryTextColor),
                                                fontSize = 12.sp,
                                                modifier = Modifier.padding(top = 6.dp, start = 24.dp, end = 24.dp),
                                            )
                                            ChatyTextButton(
                                                onClick = { nav.onOpenNewChat(false, false) },
                                                label = "New chat",
                                                fontWeight = FontWeight.W700,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (homePaneState.maximized != HomePaneMax.None) {
                        ChatyTextButton(
                            onClick = { homePaneState = homePaneState.restoreSplit() },
                            label = "Restore split",
                            modifier = Modifier.align(Alignment.TopEnd),
                        )
                    }
                }
            } else {
                // R4-29: on narrowing the host keeps paneConversation as the selected
                // detail route (host-owned) and Home keeps its session pane state
                // above, so re-expanding returns to the same layout and list context.
                Column(Modifier.fillMaxSize(), content = listColumnContent)
            }
            }
            }
        }
    }
}

/** _conversationsList: sections, archived entry and swipe-wrapped rows. */
@Composable
private fun HomeConversationList(
    listAnimation: String = "Standard",
    entries: List<HomeEntry>,
    clockMinute: Long,
    theme: com.example.chat.core.theme.ThemeConfig,
    homePrefs: HomePreferences,
    spec: HomeStyleSpec,
    bundleTemplates: HomeTemplates,
    currentUserId: String,
    contacts: List<UserProfile>,
    selectedIds: Set<String>,
    isSelectionMode: Boolean,
    presenceFor: (String, String) -> HomePresence?,
    messagesFor: (String) -> List<ChatMessage>,
    lastDeliveryStateFor: ((String) -> com.example.chat.domain.models.DeliveryState?)? = null,
    gbBool: (String, Boolean) -> Boolean,
    gbColor: (String) -> Long?,
    isConversationLocked: (String) -> Boolean,
    onOpenArchived: () -> Unit,
    onRowTap: (Conversation) -> Unit,
    onRowLongPress: (Conversation) -> Unit,
    onTogglePin: (String) -> Unit,
    onToggleMute: (String) -> Unit,
    onToggleArchive: (String) -> Unit,
    onStartCall: (Conversation, Boolean) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    openConversationId: String? = null,
    focusedConversationId: String? = null,
    hoveredRowId: String? = null,
    orderedConversationIds: List<String> = emptyList(),
    rowLabels: List<String> = emptyList(),
    opState: Map<HomeOpKey, HomeOpState> = emptyMap(),
    onRetry: (HomeOpKey) -> Unit = {},
    focusRequesterFor: (String) -> FocusRequester = { FocusRequester() },
    onRowFocused: (String?) -> Unit = {},
    onHoverRow: (String?) -> Unit = {},
    onRowContextMenu: (Conversation) -> Unit = {},
    onRowActivate: (Conversation) -> Unit = {},
    onRangeSelectTo: (String) -> Unit = {},
    onSectionHeaderTap: (String) -> Unit = {},
    onNextUnread: () -> Unit = {},
) {
    val knownContactIds=remember(contacts) {contacts.map {it.id}.toSet()}
    // R5-01/R5-10: eagerly built, type-namespaced keys (chat:/section:/archive:
    // namespaces) so an opaque conversation ID can never equal a synthetic
    // section/archive key, and a chat moving between sections keeps its key - row
    // state, swipe and selection follow the chat ID, never the position.
    val keyedEntries = remember(entries) {
        val seen = mutableSetOf<String>()
        entries.map { homeEntryKey(it, seen) }
    }
    // R4-33..36: keep the keyboard-focused row visible and actually focused.
    LaunchedEffect(focusedConversationId, entries) {
        val id = focusedConversationId ?: return@LaunchedEffect
        val idx = entries.indexOfFirst { it is HomeEntry.Item && it.conversation.id == id }
        if (idx >= 0) {
            listState.animateScrollToItem(idx)
            androidx.compose.runtime.withFrameNanos { }
            runCatching { focusRequesterFor(id).requestFocus() }
        }
    }
    val listReduceMotion = com.example.chat.ui.kit.rememberChatyReduceMotion()
    // The shared clock is supplied as a changing value, including across strong-skipped rows.
    LazyColumn(
        // R4-35: short-lived typeahead over displayed names, cleared on idle by the
        // modifier itself; never stored as search history, never a secret phrase.
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                // List subtree only: never steals editing keys from search/composer.
                if (event.type == KeyEventType.KeyDown && event.key == Key.N &&
                    event.isAltPressed && !event.isCtrlPressed && !event.isMetaPressed && !event.isShiftPressed) {
                    onNextUnread()
                    true
                } else false
            }
            .homeTypeahead(
                labels = rowLabels,
                onMatch = { matchIndex -> orderedConversationIds.getOrNull(matchIndex)?.let(onRowFocused) },
                enabled = !isSelectionMode,
            ),
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = if (bundleTemplates.chatList.referenceDefault && com.example.chat.core.preferences.HomePresentationPolicy.isDefault(homePrefs.homeStyle)) 0.dp else 8.dp,
            top = 0.dp,
            end = if (bundleTemplates.chatList.referenceDefault && com.example.chat.core.preferences.HomePresentationPolicy.isDefault(homePrefs.homeStyle)) 0.dp else 8.dp,
            bottom = 96.dp,
        ),
    ) {
        items(
            count = entries.size,
            contentType = { index -> when(entries[index]) {is HomeEntry.Item -> "chat";is HomeEntry.Section -> "section";is HomeEntry.Archived -> "archive"} },
            // R5-01: namespaced keys, still never index-derived: an insert, archive
            // or reorder must not change another row's key, or Compose discards its
            // state and replays entrance animations.
            key = { index -> keyedEntries[index] },
        ) { index ->
            val entranceKey = keyedEntries[index].toString()
            // was: Box(Modifier.listEntrance(listAnimation, entranceKey)) {
            // Rows glide to new positions on pin/archive/reorder (spring placement); off with reduce-motion.
            Box((if (listReduceMotion) Modifier else Modifier.animateItem()).listEntrance(listAnimation, entranceKey)) {
            when (val entry = entries[index]) {
                is HomeEntry.Archived -> {
                    // _archivedTile
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenArchived)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.archive,
                            semanticLabel = null,
                            color = Color(theme.secondaryTextColor),
                            modifier = Modifier.width(22.dp).height(22.dp),
                        )
                        Spacer(Modifier.width(24.dp))
                        Text(
                            androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_archived),  // was: "Archived"
                            color = Color(theme.primaryTextColor),
                            fontSize = (16 * theme.fontScale).sp,
                            fontWeight = FontWeight.W700,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            entry.archivedCount.toString(),
                            color = Color(theme.accentColor),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.W700,
                        )
                    }
                }
                is HomeEntry.Section -> {
                    // R4-43: tapping the PINNED header toggles the session-only
                    // collapse. Chats stay pinned; the grouping post-processor keeps
                    // unread totals and search results intact.
                    Box(
                        Modifier
                            .padding(start = 12.dp, top = 7.dp, end = 12.dp, bottom = 4.dp)
                            .clickable { onSectionHeaderTap(entry.label) },
                    ) {
                        HomeSectionHeader(
                            text = entry.label,
                            color = Color(theme.secondaryTextColor),
                            fontSize = 10.5f,
                        )
                    }
                }
                is HomeEntry.Item -> {
                    val conversation = entry.conversation
                    val otherId = conversation.participantIds.firstOrNull { it != currentUserId } ?: ""
                    val lastOwn = if (conversation.lastMessageSenderId == currentUserId) {
                        lastDeliveryStateFor?.invoke(conversation.id)
                            ?: messagesFor(conversation.id).lastOrNull()?.deliveryState
                    } else {
                        null
                    }
                    // R4-24/31..36 + C8: every pointer, keyboard, focus and operation
                    // affordance rides the caller's Modifier and overlays here -
                    // ConversationRow keeps its signature and behavior untouched.
                    val rowFocusRequester = focusRequesterFor(conversation.id)
                    val rowModifier = Modifier
                        .onKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                            val ids = orderedConversationIds
                            val position = ids.indexOf(conversation.id)
                            when (event.key) {
                                Key.DirectionDown, Key.DirectionUp -> {
                                    if (position < 0) return@onKeyEvent false
                                    val step = if (event.key == Key.DirectionDown) 1 else -1
                                    val target = ids.getOrNull(position + step) ?: return@onKeyEvent true
                                    // R4-33: arrows move focus by stable chat identity,
                                    // skipping section headers; they open no chat and
                                    // send no read receipt.
                                    onRowFocused(target)
                                    // R4-13: Shift extends the selection from the anchor.
                                    if (event.isShiftPressed && isSelectionMode) onRangeSelectTo(target)
                                    true
                                }
                                // R4-34: Home/End within the focused list only; text
                                // fields keep their editing shortcuts because this
                                // handler runs solely while a row holds focus.
                                Key.MoveHome -> { ids.firstOrNull()?.let(onRowFocused); true }
                                Key.MoveEnd -> { ids.lastOrNull()?.let(onRowFocused); true }
                                Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                                    // Activation is explicit; focus alone never opens.
                                    onRowActivate(conversation)
                                    true
                                }
                                else -> false
                            }
                        }
                        .onFocusChanged { state -> if (state.hasFocus) onRowFocused(conversation.id) }
                        // R4-36: token-colored focus ring, distinct from unread,
                        // current-detail and bulk selection. Mouse/touch activation
                        // never requests focus, so no misleading permanent marker.
                        .homeFocusRing(color = Color(theme.accentColor))
                        // R4-31/32: secondary-click menu + hover affordances.
                        .homeRowInput(
                            onContext = { onRowContextMenu(conversation) },
                            onHover = { hovering -> onHoverRow(if (hovering) conversation.id else null) },
                            focus = rowFocusRequester,
                        )
                        // R4-24: current-conversation indicator, separate from bulk
                        // selection; theme colors keep it readable in dark mode and
                        // high contrast, and unread styling is untouched.
                        .then(
                            if (conversation.id == openConversationId) {
                                Modifier.background(Color(theme.surfaceColor))
                            } else {
                                Modifier
                            }
                        )
                    HomeSwipeActions(
                        enabled = !isSelectionMode,
                        backgroundColor = com.example.chat.ui.navigation.LocalReferenceContentSurface.current
                            ?: Color(theme.backgroundColor),
                        leadingActions = if (conversation.type == ConversationType.direct) {
                            listOf(
                                HomeSwipeAction(
                                    icon = com.example.chat.ui.icons.ChatyIconId.callAudio,
                                    label = "Call",
                                    color = Color(theme.successColor),
                                    onTriggered = { onStartCall(conversation, false) },
                                ),
                                HomeSwipeAction(
                                    icon = com.example.chat.ui.icons.ChatyIconId.callVideo,
                                    label = "Video",
                                    color = Color(theme.accentColor),
                                    onTriggered = { onStartCall(conversation, true) },
                                ),
                            )
                        } else {
                            emptyList()
                        },
                        actions = listOf(
                            HomeSwipeAction(
                                icon = if (conversation.isPinned) com.example.chat.ui.icons.ChatyIconId.unpin else com.example.chat.ui.icons.ChatyIconId.pin,
                                label = if (conversation.isPinned) "Unpin" else "Pin",
                                color = Color(theme.accentColor),
                                onTriggered = { onTogglePin(conversation.id) },
                            ),
                            HomeSwipeAction(
                                icon = if (conversation.isMuted) com.example.chat.ui.icons.ChatyIconId.unmute else com.example.chat.ui.icons.ChatyIconId.mute,
                                label = if (conversation.isMuted) "Unmute" else "Mute",
                                color = com.example.chat.ui.core.theme.ChatyColors(theme).warning,
                                onTriggered = { onToggleMute(conversation.id) },
                            ),
                            HomeSwipeAction(
                                icon = com.example.chat.ui.icons.ChatyIconId.archive,
                                label = "Archive",
                                color = Color(theme.secondaryTextColor),
                                onTriggered = { onToggleArchive(conversation.id) },
                            ),
                        ),
                    ) {
                        Box {
                        ConversationRow(
                            conversation = conversation,
                            theme = theme,
                            homePrefs = homePrefs,
                            chatList = bundleTemplates.chatList,
                            density = spec.tileDensity,
                            isDark = theme.isDark,
                            cardRows = spec.cardRows && !com.example.chat.core.preferences.HomePresentationPolicy.isDefault(homePrefs.homeStyle),
                            isTelegramRows = rawIsTelegram(homePrefs.homeStyle),
                            displayTitle = displayConversationTitle(
                                conversation,
                                homePrefs.hideUnsavedNumbers,
                                currentUserId,
                                knownContactIds,
                            ),
                            presence = presenceFor(conversation.id, otherId),
                            onPinAction={onTogglePin(conversation.id)},onMuteAction={onToggleMute(conversation.id)},
                            onArchiveAction={onToggleArchive(conversation.id)},
                            clockMinute = clockMinute,
                            currentUserId = currentUserId,
                            lastOwnMessageState = lastOwn,
                            selected = selectedIds.contains(conversation.id),
                            locked = isConversationLocked(conversation.id),
                            elapsedTime = homePrefs.elapsedTime,
                            gbOnlineChat = gbBool("onlinechat", true),
                            gbOnlineDotChat = gbBool("onlineDotchat", true),
                            gbModOnlineColor = gbColor("ModOnlineColor"),
                            gbModLastSeenColor = gbColor("ModlastseenColor"),
                            gbOnlineDotColor = gbColor("onlineDotchatColor"),
                            gbHomeCounterBk = gbColor("HomeCounterBK"),
                            gbHomeCounterText = gbColor("HomeCounterText"),
                            modifier = rowModifier,
                            onTap = { onRowTap(conversation) },
                            onLongPress = { onRowLongPress(conversation) },
                        )
                        // R4-32: hover reveals safe pin/mute affordances without
                        // replacing the touch layout and without exposing locked
                        // message text; every action stays reachable without hover.
                        if (hoveredRowId == conversation.id && !isSelectionMode) {
                            Row(Modifier.align(Alignment.CenterEnd).padding(end = 10.dp)) {
                                Text(
                                    if (conversation.isPinned) "Unpin" else "Pin",
                                    color = Color(theme.accentColor),
                                    fontSize = 11.sp,
                                    modifier = Modifier
                                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                        .background(Color(theme.surfaceColor))
                                        .clickable { onTogglePin(conversation.id) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (conversation.isMuted) "Unmute" else "Mute",
                                    color = Color(theme.accentColor),
                                    fontSize = 11.sp,
                                    modifier = Modifier
                                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                        .background(Color(theme.surfaceColor))
                                        .clickable { onToggleMute(conversation.id) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                        // C8: host-owned per-row operation state; the default empty map
                        // renders nothing. Failed offers the retry intent; Pending shows
                        // a quiet cue. Succeeded/Cancelled leave no residue here.
                        val failedOp = opState.entries.firstOrNull { it.key.chatId == conversation.id && it.value is HomeOpState.Failed }
                        val pendingOp = opState.entries.firstOrNull { it.key.chatId == conversation.id && it.value is HomeOpState.Pending }
                        if (failedOp != null) {
                            Text(
                                androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_failed_tap_to_retry),  // was: "Failed - tap to retry"
                                color = Color(theme.primaryTextColor),
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 10.dp, bottom = 4.dp)
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                    .background(Color(theme.surfaceColor))
                                    .clickable { onRetry(failedOp.key) }
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        } else if (pendingOp != null) {
                            Text(
                                androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_working),  // was: "Working…"
                                color = Color(theme.secondaryTextColor),
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 10.dp, bottom = 4.dp)
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                    .background(Color(theme.surfaceColor))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                        }
                    }
                }
            }
            }
        }
    }
}

private fun rawIsTelegram(homeStyle: String): Boolean =
    homeStyle.trim().lowercase() == "telegram style"

/* Before per-chat contextual-menu icons, retained for reference.
    // R4-31: secondary-click menu with the same reviewed actions as swipe/selection.
    // Opening it selects no recipient, starts no call and mutates no chat state.
    val rowMenuConversation = rowMenuConversationId?.let { id -> conversations.firstOrNull { it.id == id } }
    if (rowMenuConversation != null) {
        val menuConversation = rowMenuConversation
        AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
            onDismissRequest = { rowMenuConversationId = null },
            title = {
                Text(
                    if (isConversationLocked(menuConversation.id)) "Locked conversation"
                    else homeDisplayTitles[menuConversation.id] ?: menuConversation.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.W700,
                )
            },
            text = {
                Column {
                    ChatyTextButton(
                        onClick = { onTogglePin(menuConversation.id); rowMenuConversationId = null },
                        label = if (menuConversation.isPinned) "Unpin" else "Pin",
                    )
                    ChatyTextButton(
                        onClick = { onToggleMute(menuConversation.id); rowMenuConversationId = null },
                        label = if (menuConversation.isMuted) "Unmute" else "Mute",
                    )
                    ChatyTextButton(
                        onClick = { onToggleArchive(menuConversation.id); rowMenuConversationId = null },
                        label = if (menuConversation.isArchived) "Unarchive" else "Archive",
                    )
                    ChatyTextButton(
                        onClick = { onMarkRead(menuConversation.id); rowMenuConversationId = null },
                        label = "Mark read",
                    )
                    ChatyTextButton(
                        onClick = { onMarkUnread(menuConversation.id); rowMenuConversationId = null },
                        label = "Mark unread",
                    )
                }
            },
            confirmButton = { ChatyTextButton(onClick = { rowMenuConversationId = null }, label = "Close") },
        )
    }


*/

// Pre-Home-review source retained.
// package com.example.chat.ui.chats
//
// import androidx.lifecycle.repeatOnLifecycle
// import androidx.activity.compose.BackHandler
// import androidx.compose.animation.AnimatedContent
// import androidx.compose.animation.core.CubicBezierEasing
// import androidx.compose.animation.core.tween
// import androidx.compose.animation.fadeIn
// import androidx.compose.animation.fadeOut
// import androidx.compose.animation.slideInVertically
// import androidx.compose.animation.togetherWith
// import androidx.compose.foundation.background
// import androidx.compose.foundation.clickable
// import androidx.compose.foundation.layout.Box
// import androidx.compose.foundation.layout.BoxWithConstraints
// import androidx.compose.foundation.layout.Column
// import androidx.compose.foundation.layout.Row
// import androidx.compose.foundation.layout.Spacer
// import androidx.compose.foundation.layout.fillMaxSize
// import androidx.compose.foundation.layout.fillMaxHeight
// import androidx.compose.foundation.layout.fillMaxWidth
// import androidx.compose.foundation.layout.height
// import androidx.compose.foundation.layout.padding
// import androidx.compose.foundation.layout.width
// import androidx.compose.foundation.lazy.LazyColumn
// import androidx.compose.foundation.lazy.rememberLazyListState
// import androidx.compose.foundation.text.KeyboardOptions
// import androidx.compose.material.icons.Icons
// import androidx.compose.material.icons.rounded.Call
// import androidx.compose.material.icons.rounded.NotificationsActive
// import androidx.compose.material.icons.rounded.NotificationsOff
// import androidx.compose.material.icons.rounded.Phone
// import androidx.compose.material.icons.rounded.PushPin
// import androidx.compose.material.icons.outlined.MarkChatRead
// import androidx.compose.material.icons.outlined.MarkChatUnread
// import androidx.compose.material.icons.rounded.Close
// import androidx.compose.material.icons.outlined.Archive
// import androidx.compose.material.icons.outlined.PushPin as OutlinedPushPin
// import androidx.compose.material.icons.rounded.Videocam
// import com.example.chat.ui.kit.ChatyConfirmationDialog as AlertDialog
// import com.example.chat.ui.kit.ChatyTextButton
// import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
// import androidx.compose.material3.OutlinedTextField
// import androidx.compose.material3.Scaffold
// import androidx.compose.material3.Text
// import androidx.compose.runtime.collectAsState
// import androidx.compose.runtime.Composable
// import androidx.compose.runtime.LaunchedEffect
// import androidx.compose.runtime.derivedStateOf
// import androidx.compose.runtime.getValue
// import androidx.compose.runtime.mutableStateOf
// import androidx.compose.runtime.remember
// import androidx.compose.runtime.rememberCoroutineScope
// import androidx.compose.runtime.setValue
// import androidx.compose.ui.Alignment
// import androidx.compose.ui.draw.clip
// import androidx.compose.ui.Modifier
// import androidx.compose.ui.draw.alpha
// import androidx.compose.ui.focus.FocusRequester
// import androidx.compose.ui.graphics.Color
// import androidx.compose.ui.text.font.FontWeight
// import androidx.compose.ui.text.input.KeyboardType
// import androidx.compose.ui.text.style.TextOverflow
// import androidx.compose.ui.platform.LocalDensity
// import androidx.compose.ui.semantics.LiveRegionMode
// import androidx.compose.ui.semantics.contentDescription
// import androidx.compose.ui.semantics.liveRegion
// import androidx.compose.ui.semantics.semantics
// import androidx.compose.ui.unit.dp
// import androidx.compose.ui.unit.sp
// import com.example.chat.core.preferences.HomePresetNormalizer
// import com.example.chat.ui.effects.listEntrance
// import com.example.chat.domain.models.Conversation
// import com.example.chat.ui.kit.ChatyAdaptiveLogic
// import com.example.chat.domain.models.ConversationType
// import com.example.chat.domain.models.HomePreferences
// import com.example.chat.domain.models.SecurityPreferences
// import com.example.chat.domain.models.UserProfile
// import com.example.chat.domain.models.ChatMessage
// import com.example.chat.ui.chats.home.ArchivedChatsScreen
// import com.example.chat.ui.chats.home.ChatsHomeNav
// import com.example.chat.ui.chats.home.ConversationRow
// import com.example.chat.ui.chats.home.EmptyChatsState
// import com.example.chat.ui.chats.home.HomeConfirmDialog
// import com.example.chat.ui.chats.home.HomeEntry
// import com.example.chat.ui.chats.home.HomeFilterChips
// import com.example.chat.ui.chats.home.HomeIdentity
// import com.example.chat.ui.chats.home.HomeOverflowMenu
// import com.example.chat.ui.chats.home.HomePresence
// import com.example.chat.ui.chats.home.HomeSearchField
// import com.example.chat.ui.chats.home.HomeSectionHeader
// import com.example.chat.ui.chats.home.HomeStyleSpec
// import com.example.chat.ui.chats.home.HomeSwipeAction
// import com.example.chat.ui.chats.home.HomeSwipeActions
// import com.example.chat.ui.chats.home.LockedChatsSetupDialog
// import com.example.chat.ui.chats.home.HomeTemplates
// import com.example.chat.ui.chats.home.SelectionHomeAppBar
// import com.example.chat.ui.chats.home.SplitQuickPane
// import com.example.chat.ui.chats.home.StandardHomeAppBar
// import com.example.chat.ui.chats.home.StoryStrip
// import com.example.chat.ui.chats.home.SubscriptionRequiredBanner
// import com.example.chat.ui.chats.home.buildHomeEntries
// import com.example.chat.ui.chats.home.chatyStoryLookFor
// import com.example.chat.ui.chats.home.chatyStoryStripHeight
// import com.example.chat.ui.chats.home.compactBarTitle
// import com.example.chat.ui.chats.home.defaultHomeTemplates
// import com.example.chat.ui.chats.home.displayConversationTitle
// import com.example.chat.ui.chats.home.effectiveHomeHeaderStyle
// import com.example.chat.ui.chats.home.filterHomeConversations
// import com.example.chat.ui.chats.home.homeFilters
// import com.example.chat.ui.chats.home.resolveSearchPlacement
// import com.example.chat.ui.theme.LocalThemeConfig
// import androidx.compose.foundation.horizontalScroll
// import com.example.chat.ui.chats.home.withCurrentDateContext
// import androidx.compose.foundation.focusable
// import androidx.compose.foundation.gestures.detectDragGestures
// import androidx.compose.foundation.rememberScrollState
// import androidx.compose.foundation.verticalScroll
// import androidx.compose.ui.focus.focusRequester
// import androidx.compose.ui.focus.onFocusChanged
// import androidx.compose.ui.input.key.Key
// import androidx.compose.ui.input.key.KeyEventType
// import androidx.compose.ui.input.key.isAltPressed
// import androidx.compose.ui.input.key.isCtrlPressed
// import androidx.compose.ui.input.key.isMetaPressed
// import androidx.compose.ui.input.key.isShiftPressed
// import androidx.compose.ui.input.key.key
// import androidx.compose.ui.input.key.onKeyEvent
// import androidx.compose.ui.input.key.onPreviewKeyEvent
// import androidx.compose.ui.input.key.type
// import androidx.compose.ui.input.pointer.pointerInput
// import com.example.chat.ui.chats.home.HomeBulkAction
// import com.example.chat.ui.chats.home.rememberHomeDateGrouping
// import com.example.chat.ui.chats.home.HomeGroupMode
// import com.example.chat.ui.chats.home.HomeOpKey
// import com.example.chat.ui.chats.home.HomeOpState
// import com.example.chat.ui.chats.home.HomePaneMax
// import com.example.chat.ui.chats.home.HomePaneState
// import com.example.chat.ui.chats.home.HomeSelectionPolicy
// import com.example.chat.ui.chats.home.clampDivider
// import com.example.chat.ui.chats.home.dedupeConversations
// import com.example.chat.ui.chats.home.groupHomeEntries
// import com.example.chat.ui.chats.home.homeEntryKey
// import com.example.chat.ui.chats.home.homeFocusRing
// import com.example.chat.ui.chats.home.homeRowInput
// import com.example.chat.ui.chats.home.homeTypeahead
// import kotlinx.coroutines.launch
//
// /** Full port of lib/features/chats/chats_home_screen.dart (3072 lines):
//  *  search with placements + secret-phrase vault entry, selection mode with
//  *  bulk pin/delete/mute/archive/lock/read-state actions, sectioned
//  *  PINNED/UNREAD/MESSAGES ordering with Productivity unread-first, archive
//  *  entry top/bottom, filter chips, stories strip, iOS large-title collapse,
//  *  WA-style swipe actions (call/video + pin/mute/archive), Tablet Split
//  *  View, ghost pill, overflow menu and subscription banner.
//  *
//  *  The original four parameters keep their meaning so the existing AppRoot
//  *  wiring compiles unchanged; every new dependency has a default that
//  *  degrades to the Dart behavior for absent data (empty presence, no locks).
//  *
//  *  Honest boundaries (later slices, not dropped): ProtectedResourceGate auth
//  *  before opening a locked chat, the locked-chats guided-setup modal,
//  *  ChatyGlyph font icons (emoji stand-ins), network avatars (integrator's
//  *  Coil batch), GlobalConnectionBanner/ConnectionHealthIndicator (slots
//  *  provided), NotificationPermissionSheet (host seam below, W8 owns the UI),
//  *  swipe-call relationship checks (calls slice), GB story *content* (the
//  *  strip lists contacts exactly like Dart). */
// @Composable
// fun ChatsHomeScreen(
//     conversations: List<Conversation>,
//     requiresSubscription: Boolean,
//     subscriptionMessage: String?,
//     onOpenConversation: (Conversation) -> Unit,
//     /** R7 expanded list-detail: the open conversation renders beside the list. */
//     expandedListDetail: Boolean = false,
//     paneConversation: Conversation? = null,
//     chatPane: (@Composable (Conversation) -> Unit)? = null,
//     currentUserId: String = "",
//     /** Opaque auth generation, not merely uid: resets pending selection/dialog state across account transitions. */
//     accountSessionIdentity: Any? = currentUserId,
//     folderAccountKey: String = "",
//     identity: HomeIdentity = HomeIdentity(),
//     contacts: List<UserProfile> = emptyList(),
//     homePreferences: HomePreferences = HomePreferences(),
//     securityPreferences: SecurityPreferences = SecurityPreferences(),
//     forcedType: ConversationType? = null,
//     pageTitle: String? = null,
//     templates: HomeTemplates = if (com.example.chat.core.preferences.HomePresentationPolicy.isDefault(homePreferences.homeStyle)) com.example.chat.ui.chats.home.referenceDefaultHomeTemplates else defaultHomeTemplates,
//     presenceFor: (conversationId: String, otherUserId: String) -> HomePresence? = { _, _ -> null },
//     isOnline: (String) -> Boolean = { false },
//     messagesFor: (String) -> List<ChatMessage> = { emptyList() },
//     /** Cheap tick accessor; when the host provides it, rows avoid copying the
//      *  whole message list via messagesFor on every recomposition. */
//     lastDeliveryStateFor: ((String) -> com.example.chat.domain.models.DeliveryState?)? = null,
//     gbBool: (String, Boolean) -> Boolean = { _, fallback -> fallback },
//     gbColor: (String) -> Long? = { null },
//     isConversationLocked: (String) -> Boolean = { false },
//     isConversationHidden: (String) -> Boolean = { false },
//     onToggleLockConversation: (String, Boolean) -> Unit = { _, _ -> },
//     onToggleGhostMode: (Boolean) -> Unit = {},
//     verifySecretPhrase: suspend (String) -> Boolean = { false },
//     nav: ChatsHomeNav = ChatsHomeNav(),
//     onOpenArchived: () -> Unit = {},
//     onTogglePin: (String) -> Unit = {},
//     onToggleMute: (String) -> Unit = {},
//     onToggleArchive: (String) -> Unit = {},
//     onMarkRead: (String) -> Unit = {},
//     onMarkUnread: (String) -> Unit = {},
//     onMarkAllRead: () -> Unit = {},
//     onDeleteConversations: (List<String>) -> Unit = {},
//     onStartCall: (Conversation, isVideo: Boolean) -> Unit = { _, _ -> },
//     onSnack: (String) -> Unit = {},
//     onAuthorizeConversation: (Conversation, proceed: () -> Unit) -> Unit = { _, proceed -> proceed() },
//     preferencesController: com.example.chat.core.preferences.ChatyPreferencesController? = null,
//     connectionHealthState: com.example.chat.domain.models.ConnectionHealthState? = null,
//     onOpenConnectionDetail: () -> Unit = {},
//     lockedChatsConfigured: Boolean = false,
//     onRequestVaultAuth: (onAuthorized: () -> Unit) -> Unit = {},
//     onShowNotificationPermissionIfNeeded: () -> Unit = {},
//     headerExtras: (@Composable () -> Unit)? = null,
//     connectionBanner: (@Composable () -> Unit)? = null,
//     headerBottom: (@Composable () -> Unit)? = null,
//     /** C8 (R5-2x): host-owned per-row operation state, keyed by action+chat. Empty
//      *  (the default) surfaces nothing, and the Host slice currently runs its own
//      *  in-flight guard, so no Host change is required for existing callers. */
//     opState: Map<HomeOpKey, HomeOpState> = emptyMap(),
//     /** C8: retry intent for a failed row operation; dispatch and the reducer stay host-owned. */
//     onRetry: (HomeOpKey) -> Unit = {},
//     /** R2-29: host-owned bounded refresh of the currently loaded account. Null keeps
//      *  the action hidden; the host owns the sync call, its bounds and its completion. */
//     onRefreshHome: (() -> Unit)? = null,
//     /** R2-29: true while that bounded refresh runs; drives the visible progress cue. */
//     homeRefreshing: Boolean = false,
//     /** R2-32: a VERIFIED platform screen-share signal from the host. Defaults false:
//      *  until a real signal is wired no reminder shows and no detection is claimed. */
//     screenShareActive: Boolean = false,
//     /** True only for a successfully loaded, complete active-account ID snapshot.
//      * Partial/paged data must keep stale-reference cleanup unavailable. */
//     homeConversationIdsComplete: Boolean = false,
//     // b116: account-scoped server policy callbacks. Missing callbacks must not offer a local-only substitute.
//     loadCallPolicy: (suspend () -> com.example.chat.data.api.ApiContactsClient.CallPolicy)? = null,
//     saveCallPolicy: (suspend (String, List<String>?) -> com.example.chat.data.api.ApiContactsClient.CallPolicy)? = null,
// ) {
//     val bundleTemplates = com.example.chat.ui.chats.bundles.messageBundleHome(templates)
//     val theme = com.example.chat.ui.chats.bundles.messageBundleTheme(LocalThemeConfig.current)
//     val scope = rememberCoroutineScope()
//     val listState = rememberLazyListState()
//     val searchFocus = remember { FocusRequester() }
//
//     var searchQuery by remember(accountSessionIdentity) { mutableStateOf("") }
//     var isSearchOpen by remember(accountSessionIdentity) { mutableStateOf(false) }
//     var selectedIds by remember(accountSessionIdentity) { mutableStateOf(setOf<String>()) }
//     var selectedFilter by remember(accountSessionIdentity) { mutableStateOf("All") }
//     var overflowOpen by remember(accountSessionIdentity) { mutableStateOf(false) }
//     var showDeleteConfirm by remember(accountSessionIdentity) { mutableStateOf(false) }
//     var showMessageNumber by remember(accountSessionIdentity) { mutableStateOf(false) }
//     var messageNumberText by remember(accountSessionIdentity) { mutableStateOf("") }
//     var showWhoCanCallMe by remember(accountSessionIdentity) { mutableStateOf(false) }
//     var showLockedSetup by remember(accountSessionIdentity) { mutableStateOf(false) }
//     var showArchived by remember(accountSessionIdentity) { mutableStateOf(false) }
//     var showEffects by remember(accountSessionIdentity) { mutableStateOf(false) }
//     var showScratchpad by remember(accountSessionIdentity) {mutableStateOf(false)}
//     var scratchText by remember(accountSessionIdentity) {mutableStateOf("")}
//     var confirmClearScratch by remember(accountSessionIdentity) {mutableStateOf(false)}
//     var showViewOptions by remember(accountSessionIdentity) {mutableStateOf(false)}
//     var quickView by remember(accountSessionIdentity) {mutableStateOf(com.example.chat.ui.chats.home.HomeQuickView.All)}
//     var localOrder by remember(accountSessionIdentity) {mutableStateOf(com.example.chat.ui.chats.home.HomeLocalOrder.Existing)}
//     var savedViews by remember(accountSessionIdentity) { mutableStateOf(emptyList<com.example.chat.ui.chats.home.HomeSavedView>()) }
//     var viewHistory by remember(accountSessionIdentity) { mutableStateOf(emptyList<com.example.chat.ui.chats.home.HomeViewState>()) }
//     var showSavedViews by remember(accountSessionIdentity) { mutableStateOf(false) }
//     var showViewHistory by remember(accountSessionIdentity) { mutableStateOf(false) }
//     var previewView by remember(accountSessionIdentity) { mutableStateOf<com.example.chat.ui.chats.home.HomeSavedView?>(null) }
//     var saveAfterPreview by remember(accountSessionIdentity) { mutableStateOf(false) }
//     // R2-18: in-memory only, account-scoped; never saved, synced or sent.
//     var lastOpenedConversationId by remember(accountSessionIdentity) {mutableStateOf<String?>(null)}
//     // R2-23: offered only after deep scrolling.
//     val showJumpToTop by remember(listState) {derivedStateOf {listState.firstVisibleItemIndex >= 8}}
//     // R2-21: shortcut help only ever displays; it never mutates Home state.
//     var showShortcutHelp by remember(accountSessionIdentity) {mutableStateOf(false)}
//     // R2-31: session-only presentation curtain. A display aid - not a lock, not
//     // encryption, not a screenshot guarantee.
//     var privacyCurtainOn by remember(accountSessionIdentity) {mutableStateOf(false)}
//     val curtainFocusManager = androidx.compose.ui.platform.LocalFocusManager.current
//     LaunchedEffect(privacyCurtainOn) {
//         if (privacyCurtainOn) curtainFocusManager.clearFocus(force = true)
//     }
//     // R2-32: one reminder per share session; re-arms when the signal drops.
//     var screenShareReminderDismissed by remember(accountSessionIdentity) {mutableStateOf(false)}
//     // R4-14/R4-19: read-only selection inspector.
//     var showSelectionInspector by remember(accountSessionIdentity) {mutableStateOf(false)}
//     // R4-13: stable anchor chat ID for range selection; never a list index.
//     var selectionAnchorId by remember(accountSessionIdentity) {mutableStateOf<String?>(null)}
//     // R4-41/42/45: session-only grouping mode; None reproduces the stock list exactly.
//     var homeGroupMode by remember(accountSessionIdentity) {mutableStateOf<HomeGroupMode>(HomeGroupMode.None)}
//     // R4-43: session-only pinned collapse; chats stay pinned and unread totals are kept.
//     var pinnedCollapsed by remember(accountSessionIdentity) {mutableStateOf(false)}
//     // R2-17: not saveable; account change or leaving this composition ends the session.
//     var triageSnapshot by remember(accountSessionIdentity, folderAccountKey) {
//         mutableStateOf<com.example.chat.ui.chats.home.HomeTriageSnapshot?>(null)
//     }
//     var showAppearanceResetPreview by remember(accountSessionIdentity) { mutableStateOf(false) }
//
//     // R4-21..23: session-only pane layout. Persisting the width needs an explicit
//     // preference choice first (R4-21), so nothing is stored.
//     var homePaneState by remember(accountSessionIdentity) {mutableStateOf(HomePaneState())}
//     // R4-33..36: keyboard row focus by stable chat ID, never by position.
//     var focusedConversationId by remember(accountSessionIdentity) {mutableStateOf<String?>(null)}
//     val rowFocusRequesters = remember(accountSessionIdentity) {mutableMapOf<String, FocusRequester>()}
//     // R4-31/32: pointer affordance state; hover never alters chat data.
//     var rowMenuConversationId by remember(accountSessionIdentity) {mutableStateOf<String?>(null)}
//     var hoveredRowId by remember(accountSessionIdentity) {mutableStateOf<String?>(null)}
//
//     // 'Who can call me?' reuses the shared privacy dialog when the real
//     // preferences controller is wired; otherwise the nav seam stands.
//     val effectiveNav = (if (preferencesController != null) {
//         nav.copy(onWhoCanCallMe = { showWhoCanCallMe = true })
//     } else {
//         nav
//     }).copy(onOpenCameraEffects = { nav.onOpenCameraEffects(); showEffects = true })
//     // Connection health is available in the overflow, not the header signal lane.
//     // Preserve explicit non-connection host slots.
//     val effectiveConnectionBanner: (@Composable () -> Unit)? = connectionBanner
//     val effectiveHeaderExtras: (@Composable () -> Unit)? = headerExtras
//
//     val isSelectionMode = selectedIds.isNotEmpty()
//
//     // initState: NotificationPermissionSheet.showIfNeeded (W8 owns the UI;
//     // this is only the host seam).
//     LaunchedEffect(Unit) { onShowNotificationPermissionIfNeeded() }
//
//     // PopScope(canPop: !selectionMode): back clears the selection first.
//     BackHandler(enabled = isSearchOpen) { isSearchOpen = false; searchQuery = "" }
//     BackHandler(enabled = isSelectionMode) { selectedIds = emptySet(); selectionAnchorId = null }
//     // R4-37: one layer per dismissal, in a defined order. Destructive confirmations
//     // and the dialogs below consume Back inside their own windows first; then the
//     // overflow menu, a maximized pane (R4-28: the one detail-pane Back step Home
//     // owns - restoring the split; route-level Back stays with the host navigation
//     // graph, no second hidden history stack), then (existing handlers above)
//     // selection and search. A single event never clears two layers.
//     BackHandler(enabled = overflowOpen) { overflowOpen = false }
//     BackHandler(enabled = homePaneState.maximized != HomePaneMax.None) {
//         homePaneState = homePaneState.copy(maximized = HomePaneMax.None)
//     }
//
//     // ---- build() derivations, in the Dart source's order ----
//     val homePrefs = homePreferences
//     val dateGroupingEnabled = rememberHomeDateGrouping(preferencesController)
//
//     val rawHomeUiStyle = homePrefs.homeStyle.trim().lowercase()
//     val homeStyle = com.example.chat.core.preferences.HomePresentationPolicy.listStyle(homePrefs.homeStyle)
//     val referenceRows = com.example.chat.core.preferences.HomePresentationPolicy.isDefault(homePrefs.homeStyle) && bundleTemplates.chatList.referenceDefault
//     val spec = HomeStyleSpec.resolve(homeStyle, rawHomeUiStyle).let {
//         if (referenceRows) it.copy(showSections = false) else it
//     }
//     val searchPlacement = resolveSearchPlacement(homePrefs.searchPlacement, homePrefs.disableSearchBar)
//     val searchEnabled = homePrefs.showSearchBar
//     val storiesStyle = com.example.chat.core.preferences.HomePresentationPolicy.effectiveStoriesStyle(
//         homePrefs.separateChatsAndGroups, homePrefs.storiesStyle,
//     )
//     val storyLook = chatyStoryLookFor(storiesStyle)
//     val storyShowOnlineRing = storiesStyle != "Minimal"
//     val storyCardWrap = storiesStyle == "Card" || storiesStyle == "Card View" || homePrefs.carouselView
//     // User directive: no stories strip on the home screen unless he explicitly
//     // enables it - separation/template no longer force it on.
//     val showStoriesStrip = homePrefs.enableStoriesStrip
//     val storyCompact = storiesStyle == "Compact"
//     val storyTileWidth = if (storyCardWrap) 74.0 else if (storyCompact) 48.0 else 58.0
//     val storyNameFontSize = if (storyCompact) 9.0 else 10.5
//
//     // Snapshot privacy values on every composition. Captured mutable controller state may
//     // change without changing a lambda's identity, especially with strong skipping enabled.
//     val hiddenConversationIds = conversations.asSequence().map { it.id }.filter(isConversationHidden).toSet()
//     val lockedConversationIds=conversations.asSequence().map {it.id}.filter(isConversationLocked).toSet()
//     LaunchedEffect(screenShareActive) { if (!screenShareActive) screenShareReminderDismissed = false }
//
//     // G7: local, account-scoped chat folders. Membership never leaves the
//     // device; the folder filter applies after the stock filter/search pass.
//     val folderStoreContext = androidx.compose.ui.platform.LocalContext.current
//     val chatFolderStore = remember(accountSessionIdentity, folderAccountKey) {
//         com.example.chat.data.local.ChatFolderStore(folderStoreContext, folderAccountKey.ifBlank { "unavailable" })
//     }
//     val chatFolderRevision by chatFolderStore.revision.collectAsState()
//     var selectedFolderId by remember(accountSessionIdentity, folderAccountKey) { mutableStateOf<String?>(null) }
//
//     val homeKnownContactIds=remember(contacts) {contacts.map {it.id}.toSet()}
//     val homeDisplayTitles=remember(conversations,homePrefs.hideUnsavedNumbers,currentUserId,homeKnownContactIds) {
//         conversations.associate {it.id to displayConversationTitle(it,homePrefs.hideUnsavedNumbers,currentUserId,homeKnownContactIds)}
//     }
//     val homeSortLocale=androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
//     val filteredConversations = remember(accountSessionIdentity, folderAccountKey, chatFolderStore, conversations, searchQuery, selectedFilter, forcedType, hiddenConversationIds, lockedConversationIds, selectedFolderId, chatFolderRevision, homeDisplayTitles) {
//         val base = com.example.chat.ui.chats.home.filterPrivateHomeConversations(
//             conversations, searchQuery, selectedFilter, forcedType, hiddenConversationIds::contains,
//             isConversationLocked=lockedConversationIds::contains,
//             displayTitleFor={homeDisplayTitles[it.id] ?: it.title},
//         )
//         val folderId = selectedFolderId
//         if (folderId == null) base else chatFolderStore.memberIds(folderId).let { ids -> base.filter { it.id in ids } }
//     }
//     val homeClockMinute=com.example.chat.ui.chats.home.rememberHomeMinute()
//     val homeDateContext = com.example.chat.ui.chats.home.rememberHomeDateContext(homeClockMinute)
//     val homeNow = java.time.Instant.ofEpochMilli(homeClockMinute * 60000)
//     // Recreate function-bearing modes from current privacy/name/date inputs. Never
//     // retain the Date click-time snapshot or a stale locked-ID predicate.
//     fun currentGroupMode(key: String): HomeGroupMode {
//         val mode = com.example.chat.ui.chats.home.homeGroupModeForKey(
//             key, { homeDisplayTitles[it.id] ?: it.title }, lockedConversationIds::contains, homeSortLocale)
//         return when {
//             mode is HomeGroupMode.Date -> mode.withCurrentDateContext(homeDateContext, homeClockMinute)
//             mode is HomeGroupMode.Alphabetical && localOrder != com.example.chat.ui.chats.home.HomeLocalOrder.Name -> HomeGroupMode.None
//             mode is HomeGroupMode.DirectGroup && selectedFolderId == null -> HomeGroupMode.None
//             else -> mode
//         }
//     }
//     // A real account-local preference, OFF by default. Old saved-view modes remain readable,
//     // but Date headings only show when the explicit Home settings switch is enabled.
//     val legacyGroupMode = currentGroupMode(com.example.chat.ui.chats.home.homeGroupModeKey(homeGroupMode))
//     val effectiveGroupMode = when {
//         dateGroupingEnabled -> currentGroupMode("date")
//         legacyGroupMode is HomeGroupMode.Date -> HomeGroupMode.None
//         else -> legacyGroupMode
//     }
//     val currentViewState = com.example.chat.ui.chats.home.HomeViewState(
//         selectedFilter, selectedFolderId, quickView, localOrder, com.example.chat.ui.chats.home.homeGroupModeKey(effectiveGroupMode),
//     )
//     var previousViewState by remember(accountSessionIdentity) { mutableStateOf(currentViewState) }
//     LaunchedEffect(currentViewState) {
//         if (previousViewState != currentViewState) {
//             viewHistory = com.example.chat.ui.chats.home.HomeQuickViewPolicy.pushHistory(viewHistory, previousViewState)
//             previousViewState = currentViewState
//         }
//     }
//     fun applyView(state: com.example.chat.ui.chats.home.HomeViewState) {
//         if (state.filter !in homeFilters(forcedType) ||
//             (state.folderId != null && chatFolderStore.folders().none { it.id == state.folderId })) {
//             onSnack("This view is no longer available for this Home scope.")
//             return
//         }
//         searchQuery = ""
//         selectedFilter = state.filter
//         selectedFolderId = state.folderId
//         quickView = state.view
//         localOrder = state.order
//         // Eligibility is checked again next composition using the applied scope.
//         pinnedCollapsed = false
//         homeGroupMode = com.example.chat.ui.chats.home.homeGroupModeForKey(
//             state.groupingKey, { homeDisplayTitles[it.id] ?: it.title }, lockedConversationIds::contains, homeSortLocale)
//     }
//     if (showSavedViews) com.example.chat.ui.chats.home.HomeSavedViewsDialog(
//         saved = savedViews,
//         onSave = { name ->
//             previewView = com.example.chat.ui.chats.home.HomeQuickViewPolicy.savedViewFromState(name, currentViewState)
//             saveAfterPreview = true
//             showSavedViews = false
//         },
//         onPreview = { previewView = it; saveAfterPreview = false; showSavedViews = false },
//         onDelete = { savedViews = com.example.chat.ui.chats.home.HomeQuickViewPolicy.removeView(savedViews, it) },
//         onDismiss = { showSavedViews = false },
//     )
//     previewView?.let { config ->
//         val folderExists = config.folderId == null || chatFolderStore.folders().any { it.id == config.folderId }
//         // Search is deliberately empty here and on apply. Exclude protected rows
//         // entirely, including titles/counts, rather than falling back to raw titles.
//         val previewBase = com.example.chat.ui.chats.home.filterPrivateHomeConversations(
//             dedupeConversations(conversations), "", config.filter, forcedType,
//             hiddenConversationIds::contains, isConversationLocked = lockedConversationIds::contains,
//             displayTitleFor = { homeDisplayTitles[it.id] ?: it.title },
//         ).filter { it.id !in lockedConversationIds }
//         val previewRows = com.example.chat.ui.chats.home.HomeQuickViewPolicy.apply(
//             previewBase.filter { config.folderId == null || it.id in chatFolderStore.memberIds(config.folderId) },
//             config.view, config.order, homeNow, lockedConversationIds::contains,
//             { homeDisplayTitles[it.id] ?: it.title }, homeSortLocale,
//         )
//         val previewEntries = buildHomeEntries(previewRows, homeStyle, spec, false,
//             homePrefs.hideArchivedChats, gbBool("key_mas_hide_archive_home", false), homePrefs.archiveChatsOnTop)
//         val previewMode = when (config.groupingKey) {
//             "date" -> HomeGroupMode.Date(lockedConversationIds::contains, homeNow, homeDateContext.zoneId)
//             "name" -> if (config.order == com.example.chat.ui.chats.home.HomeLocalOrder.Name)
//                 HomeGroupMode.Alphabetical({ homeDisplayTitles[it.id] ?: it.title }, homeSortLocale) else HomeGroupMode.None
//             "direct-group" -> if (config.folderId != null) HomeGroupMode.DirectGroup else HomeGroupMode.None
//             else -> HomeGroupMode.None
//         }
//         val previewChats = groupHomeEntries(previewEntries, previewMode, false)
//             .filterIsInstance<HomeEntry.Item>().map { it.conversation }
//         com.example.chat.ui.chats.home.HomeViewPreviewDialog(
//             config = config,
//             confirmLabel = if (!saveAfterPreview) "Apply view" else if (
//                 com.example.chat.ui.chats.home.HomeQuickViewPolicy.savedViewNameExists(savedViews, config.name)
//             ) "Replace saved view" else "Save view",
//             preview = com.example.chat.ui.chats.home.HomeViewPreview(
//                 if (folderExists) previewChats.size else 0,
//                 if (folderExists) previewChats.take(3).map { homeDisplayTitles[it.id] ?: it.title } else emptyList(),
//             ),
//             onApply = {
//                 if (!folderExists) onSnack("That folder no longer exists. Nothing changed.")
//                 else if (saveAfterPreview) {
//                     savedViews = com.example.chat.ui.chats.home.HomeQuickViewPolicy.saveView(savedViews, config)
//                     onSnack("View saved for this Home session only.")
//                 } else applyView(com.example.chat.ui.chats.home.HomeQuickViewPolicy.viewStateOf(config))
//                 previewView = null
//                 showSavedViews = true
//             },
//             onDismiss = { previewView = null; showSavedViews = true },
//         )
//     }
//     if (showViewHistory) com.example.chat.ui.chats.home.HomeViewHistoryDialog(
//         history = viewHistory,
//         describe = { state -> com.example.chat.ui.chats.home.HomeQuickViewPolicy.describe(state) { id ->
//             chatFolderStore.folders().firstOrNull { it.id == id }?.name
//         } },
//         onRestore = { state ->
//             val (target, remaining) = com.example.chat.ui.chats.home.HomeQuickViewPolicy.restoreFromHistory(viewHistory, state)
//             if (target != null && target.filter in homeFilters(forcedType) &&
//                 (target.folderId == null || chatFolderStore.folders().any { it.id == target.folderId })) {
//                 applyView(target)
//                 viewHistory = remaining
//                 previousViewState = target
//             } else onSnack("This earlier view is no longer available.")
//             showViewHistory = false
//         },
//         onDismiss = { showViewHistory = false },
//     )
//     val visibleConversations=remember(filteredConversations,quickView,localOrder,lockedConversationIds,homeClockMinute,homeDisplayTitles,homeSortLocale) {
//         com.example.chat.ui.chats.home.HomeQuickViewPolicy.apply(filteredConversations,quickView,localOrder,
//             java.time.Instant.ofEpochMilli(homeClockMinute*60000),lockedConversationIds::contains,
//             {homeDisplayTitles[it.id] ?: it.title},homeSortLocale)
//     }
//     if(showScratchpad) com.example.chat.ui.chats.home.HomeScratchpadDialog(
//         text=scratchText,onText={scratchText=it},onClear={confirmClearScratch=true},onDismiss={showScratchpad=false})
//     if(confirmClearScratch) com.example.chat.ui.kit.ChatyConfirmationDialog(
//         onDismissRequest={confirmClearScratch=false},title={Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_clear_temporary_text))},  // was: "Clear temporary text?"
//         text={Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_this_only_clears_the_home_scratchpad_no_chats_or_m))},  // was: "This only clears the Home scratchpad. No chats or messages are changed."
//         confirmButton={com.example.chat.ui.kit.ChatyTextButton(onClick={scratchText="";confirmClearScratch=false},label="Clear")},
//         dismissButton={com.example.chat.ui.kit.ChatyTextButton(onClick={confirmClearScratch=false},label="Cancel")})
//     if(showViewOptions) com.example.chat.ui.chats.home.HomeQuickViewDialog(
//         quickView,localOrder,
//         com.example.chat.ui.chats.home.HomeQuickViewPolicy.summary(conversations.filter {it.id !in hiddenConversationIds},lockedConversationIds::contains),
//         onView={quickView=it},onOrder={localOrder=it},
//         onReset={quickView=com.example.chat.ui.chats.home.HomeQuickView.All;localOrder=com.example.chat.ui.chats.home.HomeLocalOrder.Existing},
//         onDismiss={showViewOptions=false})
//     if (showAppearanceResetPreview) com.example.chat.ui.chats.home.HomeAppearanceResetPreview(
//         current = homePrefs, onDismiss = { showAppearanceResetPreview = false },
//     )
//     // Keep snapshot order and read rows. Search/filter/order still exist underneath,
//     // but cannot silently remove session rows. Always resolve fresh account records.
//     val triageConversations = triageSnapshot?.resolve(conversations) { id ->
//         !isConversationHidden(id) && !isConversationLocked(id)
//     }
//     val gbHideArchiveHome = gbBool("key_mas_hide_archive_home", false)
//     val entries = remember(
//         visibleConversations, triageConversations, homeStyle, spec, isSearchOpen,
//         homePrefs.hideArchivedChats, gbHideArchiveHome, homePrefs.archiveChatsOnTop,
//     ) {
//         // R5-02: duplicate conversation IDs are reconciled here, before entries reach
//         // LazyColumn (dedupeConversations keeps one deterministic current-account
//         // record per ID); Compose never has to resolve duplicate keys. R5-09: these
//         // remember keys deliberately exclude presentation-only inputs (theme,
//         // connection health) so a theme-only change is not treated as new content.
//         if (triageConversations != null) triageConversations.map { HomeEntry.Item(it) }
//         else buildHomeEntries(
//             dedupeConversations(visibleConversations), homeStyle, spec, isSearchOpen,
//             homePrefs.hideArchivedChats, gbHideArchiveHome,
//             homePrefs.archiveChatsOnTop,
//         )
//     }
//     // C6 (R4-41/42/43/45): optional visual grouping applied AFTER buildHomeEntries;
//     // HomeGroupMode.None returns the entries unchanged and collapsing is disabled
//     // while searching. Date mode uses the shared clock and date context, including
//     // same-minute timezone/configuration changes and fresh privacy predicates.
//     val groupedEntries = remember(entries, dateGroupingEnabled, homeGroupMode, pinnedCollapsed, isSearchOpen,
//         homeClockMinute, homeDateContext, lockedConversationIds, homeDisplayTitles, homeSortLocale,
//         localOrder, selectedFolderId, triageSnapshot) {
//         if (triageSnapshot != null) entries
//         else groupHomeEntries(entries, effectiveGroupMode, if (isSearchOpen) false else pinnedCollapsed)
//     }
//     // R4-11/12/13/19: the rendered eligible chat order - section headers and the
//     // archive affordance excluded, hidden chats filtered upstream - is the single
//     // source for the Select-all scope, invert, range selection and inspector order.
//     val renderedConversationOrder = remember(groupedEntries, hiddenConversationIds) {
//         groupedEntries.filterIsInstance<HomeEntry.Item>().map { it.conversation }.filter { it.id !in hiddenConversationIds }
//     }
//     // R4-17: capabilities for the selected set; an unavailable action explains itself
//     // instead of silently applying to a subset. Locked-chat checks stay with the
//     // existing per-action callbacks, which re-check locks themselves.
//     // R1-40: settled (debounced) search-result count for a polite screen-reader announcement;
//     // nothing is announced per keystroke or when search is closed.
//     val liveResultCount = renderedConversationOrder.size
//     var settledResultCount by remember(accountSessionIdentity) { mutableStateOf<Int?>(null) }
//     LaunchedEffect(accountSessionIdentity, isSearchOpen, searchQuery, liveResultCount) {
//         // Clear the prior query's count while settling; restart after account changes too.
//         settledResultCount = null
//         if (isSearchOpen && searchQuery.isNotBlank()) {
//             kotlinx.coroutines.delay(800)
//             settledResultCount = liveResultCount
//         }
//     }
//     val resultCountAnnouncement: String? = settledResultCount?.let {
//         androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_search_results_count, it)
//     }
//     val validBulkActions = remember(renderedConversationOrder, selectedIds) {
//         val selected = renderedConversationOrder.filter { it.id in selectedIds }
//         if (selected.map { it.id }.toSet() == selectedIds) HomeSelectionPolicy.validBulkActions(selected)
//         else emptySet()
//     }
//     fun runBulkAction(action: HomeBulkAction, block: () -> Unit) {
//         if (action in validBulkActions) {
//             block()
//         } else {
//             onSnack("That action does not apply to every selected chat, so nothing was changed.")
//         }
//     }
//     val filters = homeFilters(forcedType)
//     var selectionScope by remember(accountSessionIdentity) { mutableStateOf(currentViewState to searchQuery) }
//     LaunchedEffect(renderedConversationOrder, currentViewState, searchQuery, isSearchOpen) {
//         val scopeKey = currentViewState to searchQuery
//         if (selectionScope != scopeKey) {
//             selectedIds = emptySet()
//             selectionAnchorId = null
//             selectionScope = scopeKey
//         }
//         val eligible = renderedConversationOrder.map { it.id }.toSet()
//         selectedIds = selectedIds.intersect(eligible)
//         if (selectionAnchorId !in eligible) selectionAnchorId = null
//         if (focusedConversationId !in eligible) focusedConversationId = null
//         if (rowMenuConversationId !in eligible) rowMenuConversationId = null
//         rowFocusRequesters.keys.retainAll(eligible)
//         if (selectedIds.isEmpty()) showDeleteConfirm = false
//     }
//
//     val headerStyle = effectiveHomeHeaderStyle(rawHomeUiStyle, homeStyle, bundleTemplates.headerStyle)
//
//     // P4 large-title collapse: 0 expanded -> 1 collapsed, driven by the
//     // primary list's vertical scroll only.
//     val titleCollapseDistancePx = with(LocalDensity.current) { 44.dp.toPx() }
//     val largeTitleCollapse by remember(listState, titleCollapseDistancePx) {
//         derivedStateOf {
//             if (listState.firstVisibleItemIndex > 0) 1f
//             else (listState.firstVisibleItemScrollOffset / titleCollapseDistancePx).coerceIn(0f, 1f)
//         }
//     }
//     val effectiveTitleCollapse = if (isSelectionMode || isSearchOpen) 1f else largeTitleCollapse
//
//     val barTitle = compactBarTitle(
//         homeStyle = homeStyle,
//         headerStyle = headerStyle,
//         pageTitle = pageTitle,
//         setMyName = homePrefs.setMyName,
//         myNameOverride = homePrefs.myNameOverride,
//         userDisplayName = identity.displayName,
//         userUsername = identity.username,
//         largeTitleShown = effectiveTitleCollapse < 0.5f,
//     )
//
//     // Focus only after the search field has been composed and attached; requesting it inside the
//     // click handler threw "FocusRequester is not initialized" (field not composed yet) and killed the app.
//     LaunchedEffect(isSearchOpen) {
//         if (isSearchOpen) {
//             androidx.compose.runtime.withFrameNanos { }
//             runCatching { searchFocus.requestFocus() }
//         }
//     }
//
//     fun toggleSearch() {
//         isSearchOpen = !isSearchOpen
//         if (!isSearchOpen) searchQuery = ""
//     }
//
//     fun handleConversationTap(conversation: Conversation) {
//         // b116: a stale activation/keyboard action cannot open covered content.
//         if (privacyCurtainOn) return
//         if (isConversationHidden(conversation.id)) return
//         if (isSelectionMode) {
//             selectedIds = if (selectedIds.contains(conversation.id)) {
//                 selectedIds - conversation.id
//             } else {
//                 selectedIds + conversation.id
//             }
//             // R4-13: the last toggled chat is the range anchor (stable ID, never an index).
//             selectionAnchorId = conversation.id
//             return
//         }
//         onAuthorizeConversation(conversation) { lastOpenedConversationId = conversation.id; onOpenConversation(conversation) }
//     }
//
//     // R2-18: re-check hidden status at activation; locked chats still go through onAuthorizeConversation.
//     fun openLastOpenedConversation() {
//         val id = lastOpenedConversationId ?: return
//         val target = conversations.firstOrNull { it.id == id }
//         if (target == null || isConversationHidden(id)) {
//             lastOpenedConversationId = null
//             return
//         }
//         handleConversationTap(target)
//     }
//
//     fun togglePinSelected() {
//         val selected = renderedConversationOrder.filter { selectedIds.contains(it.id) }
//         val pin = selected.any { !it.isPinned }
//         selected.forEach { if (it.isPinned != pin) onTogglePin(it.id) }
//         selectedIds = emptySet()
//     }
//
//     fun toggleMuteSelected() {
//         val selected = renderedConversationOrder.filter { selectedIds.contains(it.id) }
//         val mute = selected.any { !it.isMuted }
//         selected.forEach { if (it.isMuted != mute) onToggleMute(it.id) }
//         selectedIds = emptySet()
//     }
//
//     if (showDeleteConfirm) {
//         val count = selectedIds.size
//         HomeConfirmDialog(
//             title = "Delete $count chat${if (count == 1) "" else "s"}?",
//             message = "This removes the selected conversations for this account. " +
//                 "Shared media objects are not silently deleted.",
//             confirmLabel = "Delete",
//             onConfirm = {
//                 runBulkAction(HomeBulkAction.DELETE) {
//                     onDeleteConversations(selectedIds.toList())
//                     selectedIds = emptySet()
//                     selectionAnchorId = null
//                 }
//                 showDeleteConfirm = false
//             },
//             onDismiss = { showDeleteConfirm = false },
//         )
//     }
//
//     if (showEffects) com.example.chat.ui.camera.EffectPickerHost(onDismiss = { showEffects = false })
//
//     if (showArchived) {
//         // Dart _archivedTile: Navigator.push(_ArchivedChatsScreen). Hosted as a full-screen dialog so system
//         // Back pops it; opening a conversation replaces this host's route (list position not restored).
//         androidx.compose.ui.window.Dialog(
//             onDismissRequest = { showArchived = false },
//             properties = androidx.compose.ui.window.DialogProperties(
//                 usePlatformDefaultWidth = false,
//                 decorFitsSystemWindows = true,
//             ),
//         ) {
//             androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
//                 ArchivedChatsScreen(
//                     isConversationHidden = isConversationHidden,
//                     // Locked archived rows are masked and excluded from bulk actions, as on main Home.
//                     isConversationLocked = lockedConversationIds::contains,
//                     conversations = conversations,
//                     theme = theme,
//                     homePrefs = homePreferences,
//                     onBack = { showArchived = false },
//                     onOpenConversation = { conversation ->
//                         showArchived = false
//                         onAuthorizeConversation(conversation) { onOpenConversation(conversation) }
//                     },
//                     onToggleArchive = onToggleArchive,
//                     onDeleteConversations = onDeleteConversations,
//                 )
//             }
//         }
//     }
//
//     if (showWhoCanCallMe && preferencesController != null) {
//         // b116: the old call omitted server callbacks and changed only local preferences:
//         // WhoCanCallMeDialog(preferences = preferencesController, onDismiss = { showWhoCanCallMe = false })
//         if (loadCallPolicy != null && saveCallPolicy != null) {
//             com.example.chat.ui.settings.privacy.WhoCanCallMeDialog(
//                 preferences = preferencesController,
//                 onDismiss = { showWhoCanCallMe = false },
//                 loadCallPolicy = loadCallPolicy,
//                 saveCallPolicy = saveCallPolicy,
//             )
//         } else {
//             androidx.compose.material3.AlertDialog(
//                 onDismissRequest = { showWhoCanCallMe = false },
//                 title = { Text("Call privacy unavailable") },
//                 text = { Text("Server call privacy could not be opened here. No policy was changed.") },
//                 confirmButton = { ChatyTextButton(onClick = { showWhoCanCallMe = false }, label = "Close") },
//             )
//         }
//     }
//
//     if (showLockedSetup) {
//         LockedChatsSetupDialog(
//             theme = theme,
//             onClose = { showLockedSetup = false },
//             onConfigureSecurity = nav.onOpenSecuritySettings,
//         )
//     }
//
//     // R2-21: platform key names, display only. Every binding listed here is inert
//     // inside text fields: search and the message-number field keep their editing keys.
//     if (showShortcutHelp) {
//         AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
//             onDismissRequest = { showShortcutHelp = false },
//             title = { Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_keyboard_shortcuts), fontWeight = FontWeight.W700) },  // was: "Keyboard shortcuts"
//             text = {
//                 Column {
//                     Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_ctrl_tab_move_between_the_chat_list_and_the_detail), fontSize = 13.sp, color = Color(theme.primaryTextColor))  // was: "Ctrl + Tab - move between the chat list and the detail pane; Tab keeps normal traversal"
//                     Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_alt_n_focus_the_next_visible_unlocked_unread_chat_), fontSize = 13.sp, color = Color(theme.primaryTextColor))  // was: "Alt + N - focus the next visible unlocked unread chat (opens nothing, marks nothing read)"
//                     Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_up_down_move_row_focus_opens_nothing_marks_nothing), fontSize = 13.sp, color = Color(theme.primaryTextColor))  // was: "Up / Down - move row focus (opens nothing, marks nothing read)"
//                     Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_home_end_jump_to_the_first_last_chat), fontSize = 13.sp, color = Color(theme.primaryTextColor))  // was: "Home / End - jump to the first / last chat"
//                     Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_enter_or_space_open_the_focused_chat), fontSize = 13.sp, color = Color(theme.primaryTextColor))  // was: "Enter or Space - open the focused chat"
//                     Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_shift_up_down_with_a_selection_extend_the_selected), fontSize = 13.sp, color = Color(theme.primaryTextColor))  // was: "Shift + Up / Down with a selection - extend the selected range"
//                     Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_esc_back_close_the_top_layer_menu_pane_selection_t), fontSize = 13.sp, color = Color(theme.primaryTextColor))  // was: "Esc / Back - close the top layer (menu, pane, selection, then search)"
//                     Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_type_while_the_list_is_focused_to_jump_to_a_chat_n), fontSize = 13.sp, color = Color(theme.primaryTextColor))  // was: "Type while the list is focused to jump to a chat name."
//                     Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_these_never_type_into_search_or_text_fields), fontSize = 12.sp, color = Color(theme.secondaryTextColor))  // was: "These never type into search or text fields."
//                 }
//             },
//             confirmButton = { ChatyTextButton(onClick = { showShortcutHelp = false }, label = "Close") },
//         )
//     }
//
//     // R4-14/R4-19: read-only target list in the current rendered (Home) order with
//     // the scope label; entries are removable and nothing else mutates. Titles come
//     // from the same display resolver as the list, so hidden/protected rows expose
//     // nothing extra here.
//     if (showSelectionInspector) {
//         val inspectorTargets = HomeSelectionPolicy.inspectorOrder(renderedConversationOrder, selectedIds)
//         AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
//             onDismissRequest = { showSelectionInspector = false },
//             title = { Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_selected_chats_count, inspectorTargets.size), fontWeight = FontWeight.W700) },  // was: Text("Selected chats (${inspectorTargets.size})"
//             text = {
//                 if (inspectorTargets.isEmpty()) {
//                     Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_no_chats_selected), fontSize = 13.sp, color = Color(theme.secondaryTextColor))  // was: "No chats selected."
//                 } else {
//                     // Preserve reviewed outer-dialog scroll ownership.
//         Column {
//                         Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_in_the_order_shown_on_home), fontSize = 12.sp, color = Color(theme.secondaryTextColor))  // was: "In the order shown on Home"
//                         inspectorTargets.take(50).forEach { conversation ->
//                             Row(
//                                 verticalAlignment = Alignment.CenterVertically,
//                                 modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
//                             ) {
//                                 Text(
//                                     if (isConversationLocked(conversation.id)) "Locked conversation"
//                                     else homeDisplayTitles[conversation.id] ?: conversation.title,
//                                     maxLines = 1,
//                                     overflow = TextOverflow.Ellipsis,
//                                     color = Color(theme.primaryTextColor),
//                                     fontSize = 14.sp,
//                                     modifier = Modifier.weight(1f),
//                                 )
//                                 ChatyTextButton(
//                                     onClick = {
//                                         selectedIds = selectedIds - conversation.id
//                                         if (selectionAnchorId == conversation.id) selectionAnchorId = null
//                                     },
//                                     label = "Remove",
//                                 )
//                             }
//                         }
//                         if (inspectorTargets.size > 50) {
//                             Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_more_count, inspectorTargets.size - 50), fontSize = 12.sp, color = Color(theme.secondaryTextColor))  // was: Text("+ ${inspectorTargets.size - 50} more"
//                         }
//                     }
//                 }
//             },
//             confirmButton = { ChatyTextButton(onClick = { showSelectionInspector = false }, label = "Done") },
//         )
//     }
//
//     // R4-31: secondary-click menu with the same reviewed actions as swipe/selection.
//     // Opening it selects no recipient, starts no call and mutates no chat state.
//     val rowMenuConversation = rowMenuConversationId?.let { id -> conversations.firstOrNull { it.id == id } }
//     if (rowMenuConversation != null) {
//         val menuConversation = rowMenuConversation
//         AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
//             onDismissRequest = { rowMenuConversationId = null },
//             title = {
//                 Text(
//                     if (isConversationLocked(menuConversation.id)) "Locked conversation"
//                     else homeDisplayTitles[menuConversation.id] ?: menuConversation.title,
//                     maxLines = 1,
//                     overflow = TextOverflow.Ellipsis,
//                     fontWeight = FontWeight.W700,
//                 )
//             },
//             text = {
//                 Column {
//                     ChatyTextButton(
//                         icon = androidx.compose.material.icons.Icons.Rounded.PushPin,
//                         onClick = { onTogglePin(menuConversation.id); rowMenuConversationId = null },
//                         label = if (menuConversation.isPinned) "Unpin" else "Pin",
//                     )
//                     ChatyTextButton(
//                         icon = androidx.compose.material.icons.Icons.Rounded.NotificationsOff,
//                         onClick = { onToggleMute(menuConversation.id); rowMenuConversationId = null },
//                         label = if (menuConversation.isMuted) "Unmute" else "Mute",
//                     )
//                     ChatyTextButton(
//                         icon = androidx.compose.material.icons.Icons.Outlined.Archive,
//                         onClick = { onToggleArchive(menuConversation.id); rowMenuConversationId = null },
//                         label = if (menuConversation.isArchived) "Unarchive" else "Archive",
//                     )
//                     ChatyTextButton(
//                         icon = androidx.compose.material.icons.Icons.Outlined.MarkChatRead,
//                         onClick = { onMarkRead(menuConversation.id); rowMenuConversationId = null },
//                         label = "Mark read",
//                     )
//                     ChatyTextButton(
//                         icon = androidx.compose.material.icons.Icons.Outlined.MarkChatUnread,
//                         onClick = { onMarkUnread(menuConversation.id); rowMenuConversationId = null },
//                         label = "Mark unread",
//                     )
//                 }
//             },
//             confirmButton = { ChatyTextButton(icon = androidx.compose.material.icons.Icons.Rounded.Close, onClick = { rowMenuConversationId = null }, label = "Close") },
//         )
//     }
//
//     // R2-32: shown only when the host supplies a VERIFIED platform screen-share
//     // signal; detection is not claimed otherwise (the param defaults false).
//     if (screenShareActive && !screenShareReminderDismissed) {
//         AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
//             onDismissRequest = { screenShareReminderDismissed = true },
//             title = { Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_screen_sharing_may_be_active), fontWeight = FontWeight.W700) },  // was: "Screen sharing may be active"
//             text = {
//                 Text(
//                     androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_home_titles_and_previews_can_be_visible_to_anyone_),  // was: "Home titles and previews can be visible to anyone watching. You can hide them with the privacy curtain - a display aid only, not an app lock or encryption."
//                     fontSize = 13.sp,
//                     color = Color(theme.primaryTextColor),
//                 )
//             },
//             confirmButton = {
//                 ChatyTextButton(
//                     onClick = { privacyCurtainOn = true; screenShareReminderDismissed = true },
//                     label = "Hide content",
//                     fontWeight = FontWeight.W700,
//                 )
//             },
//             dismissButton = {
//                 ChatyTextButton(onClick = { screenShareReminderDismissed = true }, label = "Continue")
//             },
//         )
//     }
//
//     if (showMessageNumber) {
//         AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
//             onDismissRequest = { showMessageNumber = false },
//             title = {
//                 Column {
//                     Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_message_a_number), fontWeight = FontWeight.W700)  // was: "Message a number"
//                     Text(
//                         androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_start_a_direct_chat_without_saving_contact),  // was: "Start a direct chat without saving contact"
//                         fontSize = 13.sp,
//                         color = Color(theme.secondaryTextColor),
//                     )
//                 }
//             },
//             text = {
//                 OutlinedTextField(
//                     value = messageNumberText,
//                     onValueChange = { messageNumberText = it },
//                     label = { Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_phone_number)) },  // was: "Phone number"
//                     placeholder = { Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_include_country_code_1)) },  // was: "Include country code (+1...)"
//                     leadingIcon = { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.callAudio, semanticLabel = null) },
//                     keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
//                     singleLine = true,
//                 )
//             },
//             confirmButton = {
//                 ChatyTextButton(
//                     onClick = {
//                         val number = messageNumberText.trim()
//                         showMessageNumber = false
//                         messageNumberText = ""
//                         if (number.isNotEmpty()) nav.onMessageNumber(number)
//                     },
//                     label = "Chat",
//                     fontWeight = FontWeight.W700,
//                 )
//             },
//             dismissButton = {
//                 ChatyTextButton(
//                     onClick = { showMessageNumber = false; messageNumberText = "" },
//                     label = "Cancel",
//                 )
//             },
//         )
//     }
//
//     Scaffold(containerColor = Color(theme.backgroundColor)) { padding ->
//         Column(Modifier.fillMaxSize().padding(padding)) {
//             effectiveConnectionBanner?.invoke()
//             if (requiresSubscription) {
//                 SubscriptionRequiredBanner(theme, subscriptionMessage, nav.onSelectPlan)
//             }
//             // Header swap with the Dart AnimatedSwitcher timing (230ms,
//             // easeOutCubic in / easeInCubic out, slight upward slide).
//             val headerReduceMotion = com.example.chat.ui.kit.rememberChatyReduceMotion()
//             val headerSwapMs = if (headerReduceMotion) 0 else 230
//             AnimatedContent(
//                 targetState = isSelectionMode,
//                 transitionSpec = {
//                     (
//                         fadeIn(tween(headerSwapMs, easing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f))) +
//                             slideInVertically(
//                                 tween(headerSwapMs, easing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)),
//                             ) { (it * 0.035f).toInt() }
//                         ) togetherWith
//                         fadeOut(tween(headerSwapMs, easing = CubicBezierEasing(0.64f, 0f, 0.92f, 0f)))
//                 },
//                 label = "home-header",
//             ) { selectionMode ->
//                 if (selectionMode) {
//                     val selected = renderedConversationOrder.filter { selectedIds.contains(it.id) }
//                     SelectionHomeAppBar(
//                         theme = theme,
//                         selectedCount = selectedIds.size,
//                         visibleSelectedCount = HomeSelectionPolicy.inspectorOrder(renderedConversationOrder, selectedIds).size,
//                         validActions = validBulkActions,
//                         anyUnpinned = selected.any { !it.isPinned },
//                         anyUnmuted = selected.any { !it.isMuted },
//                         anyUnlocked = selectedIds.any { !isConversationLocked(it) },
//                         onClearSelection = { selectedIds = emptySet(); selectionAnchorId = null },
//                         onTogglePin = {
//                             runBulkAction(if (selected.any { !it.isPinned }) HomeBulkAction.PIN else HomeBulkAction.UNPIN) { togglePinSelected() }
//                         },
//                         onDelete = { runBulkAction(HomeBulkAction.DELETE) { showDeleteConfirm = true } },
//                         onToggleMute = {
//                             runBulkAction(if (selected.any { !it.isMuted }) HomeBulkAction.MUTE else HomeBulkAction.UNMUTE) { toggleMuteSelected() }
//                         },
//                         onToggleArchive = {
//                             runBulkAction(if (selected.any { !it.isArchived }) HomeBulkAction.ARCHIVE else HomeBulkAction.UNARCHIVE) {
//                                 selectedIds.toList().forEach(onToggleArchive)
//                                 selectedIds = emptySet()
//                                 selectionAnchorId = null
//                             }
//                         },
//                         onToggleLock = {
//                             val ids = selectedIds.toList()
//                             val lock = ids.any { !isConversationLocked(it) }
//                             ids.forEach { onToggleLockConversation(it, lock) }
//                             selectedIds = emptySet()
//                         },
//                         onMarkUnread = {
//                             runBulkAction(HomeBulkAction.MARK_UNREAD) {
//                                 selectedIds.forEach(onMarkUnread)
//                                 selectedIds = emptySet()
//                                 selectionAnchorId = null
//                             }
//                         },
//                         onMarkRead = {
//                             runBulkAction(HomeBulkAction.MARK_READ) {
//                                 selectedIds.forEach(onMarkRead)
//                                 selectedIds = emptySet()
//                                 selectionAnchorId = null
//                             }
//                         },
//                         // R4-11: the target set is the rendered eligible chat entries
//                         // (section headers, the archive affordance and hidden chats
//                         // excluded). The Bars slice owns showing the exact target count.
//                         onSelectAll = { selectedIds = HomeSelectionPolicy.selectAll(renderedConversationOrder) },
//                         // R4-12: invert within the shown eligible set only; off-scope
//                         // selections are kept out of the operation.
//                         onInvert = { selectedIds = HomeSelectionPolicy.invert(renderedConversationOrder, selectedIds) },
//                         // R4-14/R4-19: read-only inspection before any bulk effect.
//                         onInspect = { showSelectionInspector = true },
//                         // R4-20: named exit; clears selection only - search, folder and
//                         // quick-view settings are untouched (Back/Escape equivalent above).
//                         onExitSelection = { selectedIds = emptySet(); selectionAnchorId = null },
//                         onSplitChat = {
//                             nav.onOpenSplitChat?.invoke(selectedIds.toList())
//                             selectedIds = emptySet()
//                         },
//                     )
//                 } else {
//                     Box {
//                         StandardHomeAppBar(
//                             theme = theme,
//                             homePrefs = homePrefs,
//                             headerStyle = headerStyle,
//                             homeStyle = homeStyle,
//                             title = if (forcedType != null) pageTitle ?: barTitle else barTitle,
//                             identity = identity,
//                             isSearchOpen = isSearchOpen,
//                             forcedTypeIsSet = forcedType != null,
//                             nav = effectiveNav,
//                             onToggleSearch = { toggleSearch() },
//                             onToggleGhost = onToggleGhostMode,
//                             // _handleTopLeftIdentityTap: with Locked Chats
//                             // configured the vault stays behind the auth gate
//                             // (fail-closed seam; owner wires the real gate),
//                             // otherwise the guided setup modal explains it.
//                             onIdentityTap = {
//                                 if (lockedChatsConfigured) {
//                                     onRequestVaultAuth { nav.onOpenLockedVault() }
//                                 } else {
//                                     showLockedSetup = true
//                                 }
//                             },
//                             // Source deviation, deliberate: Dart long-press
//                             // opens the vault directly, but no delivered
//                             // Kotlin slice carries a destination-side auth
//                             // gate, so every entry path goes through the
//                             // fail-closed seam until the shared gate lands.
//                             onIdentityLongPress = {
//                                 onRequestVaultAuth { nav.onOpenLockedVault() }
//                             },
//                             onOpenOverflowMenu = { overflowOpen = true },
//                             onSnack = onSnack,
//                             headerExtras = effectiveHeaderExtras,
//                         )
//                         Box(Modifier.align(Alignment.BottomEnd)) {
//                             HomeOverflowMenu(
//                                 onCheckHomeAppearance = { showAppearanceResetPreview = true },
//                                 expanded = overflowOpen,
//                                 onDismiss = { overflowOpen = false },
//                                 onOpenViewOptions={showViewOptions=true},
//                                 onOpenScratchpad={showScratchpad=true},
//                                 onOpenSavedViews = { showSavedViews = true },
//                                 onOpenViewHistory = { showViewHistory = true },
//                                 // R2-21: discoverable shortcut map (dialog below; Bars slice
//                                 // adds the menu item via its optional onOpenShortcutHelp param).
//                                 onOpenShortcutHelp = { showShortcutHelp = true },
//                                 theme = theme,
//                                 nav = effectiveNav,
//                                 menuPreferences = homePrefs,
//                                 onMarkAllAsRead = {
//                                     onMarkAllRead()
//                                     // Completion feedback belongs to the host, not the click handler.
//                                 },
//                                 onMessageANumber = { showMessageNumber = true },
//                                 onOpenConnectionDetail = onOpenConnectionDetail.takeIf { connectionHealthState != null },
//                                 onOpenArchived = { showArchived = true },
//                             )
//                         }
//                     }
//                 }
//             }
//             if (!isSelectionMode) headerBottom?.invoke()
//             if (!isSelectionMode && searchEnabled && searchPlacement == "Below header") {
//                 HomeSearchField(
//                     theme = theme,
//                     value = searchQuery,
//                     onValueChange = { value ->
//                         searchQuery = value
//                         // Ordinary search must never verify a secret or consume lock attempts.
//                     },
//                     hint = if (forcedType == ConversationType.group) "Search groups…" else "Search chat names and latest messages…",
//                     focusRequester = searchFocus,
//                 )
//             }
//             // The supplied Default / OLD / iOS headers already own their single title.
//             // Preserve the large-title treatment only for other Home presentations.
//             if (!isSelectionMode && pageTitle != null &&
//                 !com.example.chat.core.preferences.HomePresentationPolicy.isDefault(homePrefs.homeStyle) &&
//                 rawHomeUiStyle !in setOf("ios style", "whatsapp old ui")) {
//                 Box(
//                     modifier = Modifier
//                         // Collapse drives height to exactly 0; the expanded height scales
//                         // with font scale (sp->dp) so large text is never pinned to 44dp.
//                         .height(with(LocalDensity.current) { (44f * theme.fontScale.toFloat()).sp.toDp() } * (1 - effectiveTitleCollapse))
//                         .alpha(1 - effectiveTitleCollapse),
//                 ) {
//                     Text(
//                         pageTitle,
//                         maxLines = 1,
//                         overflow = TextOverflow.Ellipsis,
//                         color = Color(theme.primaryTextColor),
//                         fontSize = (32 * theme.fontScale).sp,
//                         fontWeight = FontWeight.W800,
//                         letterSpacing = (-0.5).sp,
//                         lineHeight = (32 * theme.fontScale).sp,
//                         modifier = Modifier
//                             .padding(start = 16.dp, top = 2.dp, end = 16.dp, bottom = 6.dp)
//                             .align(Alignment.CenterStart)
//                             .clickable {
//                                 if (securityPreferences.hideLockedChats && securityPreferences.entryByAppTitle) {
//                                     // Source opens the vault directly here and
//                                     // relies on destination-side auth; no such
//                                     // gate exists in the Kotlin slice yet, so
//                                     // this entry also fails closed.
//                                     onRequestVaultAuth { nav.onOpenLockedVault() }
//                                 }
//                             },
//                     )
//                 }
//             }
//             val defaultPanel = com.example.chat.core.preferences.HomePresentationPolicy.isDefault(homePrefs.homeStyle)
//             com.example.chat.ui.navigation.ReferenceLayoutContentSurface(
//                 rounded = defaultPanel, isDark = theme.isDark, lightSurface = Color(theme.surfaceColor),
//                 surfaceOverride = preferencesController?.universal?.listBackgroundColor?.let(::Color),
//                 modifier = Modifier.weight(1f).fillMaxWidth(),
//             ) {
//             BoxWithConstraints(Modifier.fillMaxSize()) {
//             // R4-25: on wide expanded windows a vertical folder rail replaces the
//             // horizontal folder strip; the two never show together and the chosen
//             // folder is untouched. 917dp reserves 181dp rail + 16dp divider + two
//             // 360dp readable panes, measured against this content surface.
//             val homeWindowWidthDp = maxWidth.value
//             // Tag chips replace both the redundant folder row and its wide-screen rail.
//             val showFolderRail = false
//             /* Previous rail eligibility retained.
//             val showFolderRail = chatPane != null && expandedListDetail && folderAccountKey.isNotBlank() &&
//                 spec.showFilters && !homePrefs.hideChatSortList && homeWindowWidthDp >= 917 &&
//                 homePaneState.maximized != HomePaneMax.Detail
//             */
//             // R2-19: per-folder activity summaries for the chips strip, computed
//             // read-only from the post-wave-1 store API via HomeFolderPolicy.
//             val folderActivitySummaries = remember(chatFolderRevision, conversations, hiddenConversationIds, lockedConversationIds, homeClockMinute, homeDateContext) {
//                 chatFolderStore.folders().associate { folder ->
//                     folder.id to com.example.chat.ui.chats.home.HomeFolderPolicy.folderActivitySummary(
//                         store = chatFolderStore,
//                         folderId = folder.id,
//                         visible = conversations.filter { it.id !in hiddenConversationIds && it.id !in lockedConversationIds },
//                         now = java.time.Instant.ofEpochMilli(homeClockMinute * 60000),
//                     )
//                 }
//             }
//             val listColumnContent: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
//                 if (!isSelectionMode) {
//                     if (searchEnabled && searchPlacement == "Header action" && isSearchOpen) {
//                         HomeSearchField(
//                             theme = theme,
//                             value = searchQuery,
//                             onValueChange = { value ->
//                                 searchQuery = value
//                                 // Explicit vault entry remains behind the authorization gate.
//                             },
//                             hint = if (forcedType == ConversationType.group) "Search groups…" else "Search chat names and latest messages…",
//                             focusRequester = searchFocus,
//                         )
//                     }
//                     if (showStoriesStrip && (!spec.storiesHidden || homePrefs.separateChatsAndGroups) && forcedType != ConversationType.group) {
//                         StoryStrip(
//                             contacts = contacts,
//                             theme = theme,
//                             look = storyLook,
//                             stripHeight = chatyStoryStripHeight(storyLook, spec.storiesHeight),
//                             storiesAvatar = spec.storiesAvatar,
//                             storiesStyle = storiesStyle,
//                             storyCardWrap = storyCardWrap,
//                             storyShowOnlineRing = storyShowOnlineRing,
//                             storyCompact = storyCompact,
//                             storyTileWidth = storyTileWidth,
//                             storyNameFontSize = storyNameFontSize,
//                             isOnline = isOnline,
//                         )
//                     }
//                     if (searchEnabled && searchPlacement == "Above chat filters") {
//                         HomeSearchField(
//                             theme = theme,
//                             value = searchQuery,
//                             onValueChange = { value ->
//                                 searchQuery = value
//                                 // Dart _homeSearchField is shared by all placements: onChanged runs
//                                 // _checkSecretSearchPhrase everywhere, so this placement unlocks too.
//                                 // Explicit vault entry remains behind the authorization gate.
//                             },
//                             hint = if (forcedType == ConversationType.group) "Search groups…" else "Search chat names and latest messages…",
//                             focusRequester = searchFocus,
//                         )
//                     }
//                     /* Home controls moved/hidden per owner review. Triage is redundant with Unread for the primary flow.
//                     Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
//                         ChatyTextButton(onClick = {
//                             if (triageSnapshot != null) triageSnapshot = null
//                             else triageSnapshot = com.example.chat.ui.chats.home.HomeTriageSnapshot.capture(
//                                 renderedConversationOrder,
//                             ) { id -> !isConversationHidden(id) && !isConversationLocked(id) }
//                         }, label = if (triageSnapshot == null) "Start unread triage" else "End unread triage")
//                         ChatyTextButton(onClick = { showAppearanceResetPreview = true }, label = "Preview appearance reset")
//                     }
//                     if (triageSnapshot != null) Text(
//                         "Unread triage: snapshot of this view. Read chats stay until End. New unread chats wait for the next session. Hidden, locked or unavailable chats are not shown. Filters, order and grouping apply after End.",
//                         color = Color(theme.secondaryTextColor), fontSize = 12.sp,
//                         modifier = Modifier.padding(horizontal = 12.dp),
//                     )
//                     */
//                     if (spec.showFilters && !homePrefs.hideChatSortList) {
//                         com.example.chat.ui.chats.home.HomeTagFilters(
//                             theme = theme, store = chatFolderStore, enabled = folderAccountKey.isNotBlank(),
//                             accountIdentity = accountSessionIdentity, conversations = conversations.filter { !it.isTask },
//                             visibleConversations = dedupeConversations(conversations).filter {
//                                 !it.isTask && it.id !in hiddenConversationIds && it.id !in lockedConversationIds
//                             },
//                             titles = homeDisplayTitles,
//                             selectedFilter = selectedFilter, selectedTagId = selectedFolderId,
//                             onFilter = { filter -> selectedFolderId = null; selectedFilter = filter },
//                             onTag = { id -> selectedFilter = "All"; selectedFolderId = id },
//                             onSaved = { id -> selectedFilter = "All"; selectedFolderId = id },
//                         )
//                         /* Previous stock filter row retained; tags now share one ordered row.
//                         HomeFilterChips(
//                             theme = theme,
//                             tabBubbleStyle = homePrefs.tabBubbleStyle,
//                             filters = filters,
//                             selectedFilter = selectedFilter,
//                             onSelect = { filter -> selectedFilter = filter },
//                         )
//                         */
//                         /* Superseded by tag chips: keep folder management code for reuse from tags.
//                         if (folderAccountKey.isNotBlank() && !showFolderRail) com.example.chat.ui.chats.folders.ChatFolderChips(
//                             theme = theme,
//                             store = chatFolderStore,
//                             conversations = dedupeConversations(conversations).filter { it.id !in hiddenConversationIds && it.id !in lockedConversationIds },
//                             selectedFolderId = selectedFolderId,
//                             onSelect = { selectedFolderId = it },
//                             // S4 optional inputs, wired where safe from Screen state.
//                             selectedConversationIds = selectedIds.filterTo(mutableSetOf()) { !isConversationHidden(it) && !isConversationLocked(it) },
//                             canInspectConversation = { id -> !isConversationHidden(id) && !isConversationLocked(id) },
//                             resolvedConversationIds = if (homeConversationIdsComplete) conversations.map { it.id }.toSet() else null,
//                             onOpenConversation = { id ->
//                                 if (!isConversationHidden(id) && !isConversationLocked(id))
//                                     conversations.firstOrNull { it.id == id }?.let { handleConversationTap(it) }
//                             },
//                             summaries = folderActivitySummaries,
//                         )
//                         */
//                         if (triageSnapshot == null) {
//                         // R4-41/42/45: explicit, session-only grouping. Off keeps the
//                         // stock list; Date uses loaded last-message dates; Direct/Group
//                         // is offered inside a chosen folder (R4-45); A-Z appears only
//                         // when name order is deliberately selected (R4-41) and uses the
//                         // same displayed-name comparator and locale collation.
//                         Row(
//                             verticalAlignment = Alignment.CenterVertically,
//                             modifier = Modifier.horizontalScroll(rememberScrollState()).padding(start = 12.dp, top = 2.dp, bottom = 2.dp),
//                         ) {
//                             if (!isSearchOpen && entries.any { it is HomeEntry.Item && it.conversation.isPinned }) {
//                                 Text(
//                                     if (pinnedCollapsed) "Expand pinned" else "Collapse pinned",
//                                     color = Color(theme.accentColor), fontSize = 11.sp,
//                                     modifier = Modifier.clickable { pinnedCollapsed = !pinnedCollapsed }
//                                         .padding(end = 8.dp, top = 6.dp, bottom = 6.dp),
//                                 )
//                             }
//                             /* Group Off/Date moved to the Home settings switch; old alternate-mode UI retained.
//                             Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_group), color = Color(theme.secondaryTextColor), fontSize = 11.sp)  // was: "Group:"
//                             val groupOptions = buildList<Pair<String, HomeGroupMode>> {
//                                 add("Off" to HomeGroupMode.None)
//                                 add("Date" to currentGroupMode("date"))
//                                 if (selectedFolderId != null) add("Direct / Group" to HomeGroupMode.DirectGroup)
//                                 if (localOrder == com.example.chat.ui.chats.home.HomeLocalOrder.Name) {
//                                     add("A-Z" to HomeGroupMode.Alphabetical(
//                                         displayTitleFor = { homeDisplayTitles[it.id] ?: it.title },
//                                         locale = homeSortLocale,
//                                     ))
//                                 }
//                             }
//                             groupOptions.forEach { (label, mode) ->
//                                 val active = effectiveGroupMode::class == mode::class
//                                 Text(
//                                     label,
//                                     color = Color(if (active) theme.accentColor else theme.secondaryTextColor),
//                                     fontSize = 11.sp,
//                                     fontWeight = if (active) FontWeight.W700 else FontWeight.W400,
//                                     modifier = Modifier
//                                         .padding(start = 8.dp)
//                                         .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
//                                         .clickable { homeGroupMode = if (active) HomeGroupMode.None else mode }
//                                         .padding(horizontal = 6.dp, vertical = 2.dp),
//                                 )
//                             }                            */
//                         }
//                         if (effectiveGroupMode is HomeGroupMode.Alphabetical) {
//                             Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
//                                 com.example.chat.ui.chats.home.homeSectionJumpTargets(groupedEntries)
//                                     .filterNot { com.example.chat.ui.chats.home.isPinnedHomeSectionLabel(it.label) }
//                                     .forEach { target ->
//                                         ChatyTextButton(onClick = {
//                                             scope.launch { listState.animateScrollToItem(target.entryIndex) }
//                                         }, label = target.label)
//                                     }
//                             }
//                         }
//                         }
//                     }
//                 }
//                 if (entries.isEmpty()) {
//                     Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
//                         EmptyChatsState(
//                             theme = theme,
//                             isGroups = forcedType == ConversationType.group,
//                             onSearch = if(searchQuery.isNotBlank() || selectedFilter!="All" || selectedFolderId!=null || quickView!=com.example.chat.ui.chats.home.HomeQuickView.All) {
//                                 {searchQuery="";selectedFilter="All";selectedFolderId=null;quickView=com.example.chat.ui.chats.home.HomeQuickView.All}
//                             } else nav.onOpenGlobalSearch,
//                             filtered = searchQuery.isNotBlank() || selectedFilter!="All" || selectedFolderId!=null || quickView!=com.example.chat.ui.chats.home.HomeQuickView.All,
//                             archivedOnly = visibleConversations.isNotEmpty(),
//                             onOpenArchive = {showArchived=true},
//                         )
//                     }
//                 } else {
//                     BoxWithConstraints(Modifier.weight(1f)) {
//                         val listContent: @Composable () -> Unit = {
//                             // b116: do not compose covered rows, their semantics or keyboard targets.
//                             // Previously: androidx.compose.runtime.key(accountSessionIdentity) { HomeConversationList(...) }
//                             if (!privacyCurtainOn) androidx.compose.runtime.key(accountSessionIdentity) {
//                             HomeConversationList(
//                                 listAnimation = preferencesController?.effects?.homeListAnimation ?: "Standard",
//                                 entries = groupedEntries,
//                                 clockMinute = homeClockMinute,
//                                 theme = theme,
//                                 homePrefs = homePrefs,
//                                 spec = spec,
//                                 bundleTemplates = bundleTemplates,
//                                 currentUserId = currentUserId,
//                                 contacts = contacts,
//                                 selectedIds = selectedIds,
//                                 isSelectionMode = isSelectionMode,
//                                 presenceFor = presenceFor,
//                                 messagesFor = messagesFor,
//                                 lastDeliveryStateFor = lastDeliveryStateFor,
//                                 gbBool = gbBool,
//                                 gbColor = gbColor,
//                                 isConversationLocked = isConversationLocked,
//                                 onOpenArchived = { onOpenArchived(); showArchived = true },
//                                 onRowTap = { handleConversationTap(it) },
//                                 onRowLongPress = { conversation ->
//                                     selectionAnchorId = conversation.id
//                                     selectedIds = if (selectedIds.contains(conversation.id)) {
//                                         selectedIds - conversation.id
//                                     } else {
//                                         selectedIds + conversation.id
//                                     }
//                                 },
//                                 onTogglePin = onTogglePin,
//                                 onToggleMute = onToggleMute,
//                                 onToggleArchive = onToggleArchive,
//                                 onStartCall = onStartCall,
//                                 listState = listState,
//                                 openConversationId = paneConversation?.id,
//                                 focusedConversationId = focusedConversationId,
//                                 hoveredRowId = hoveredRowId,
//                                 orderedConversationIds = renderedConversationOrder.map { it.id },
//                                 rowLabels = renderedConversationOrder.map { homeDisplayTitles[it.id] ?: it.title },
//                                 opState = opState,
//                                 onRetry = onRetry,
//                                 focusRequesterFor = { id -> rowFocusRequesters.getOrPut(id) { FocusRequester() } },
//                                 onRowFocused = { focusedConversationId = it },
//                                 onHoverRow = { hoveredRowId = it },
//                                 onRowContextMenu = { rowMenuConversationId = it.id },
//                                 onRowActivate = { handleConversationTap(it) },
//                                 onRangeSelectTo = { targetId ->
//                                     // R4-13: extend from the anchor across the current ordered
//                                     // visible list; the anchor resets on account/scope change.
//                                     val anchor = selectionAnchorId ?: targetId
//                                     selectedIds = selectedIds + HomeSelectionPolicy.range(renderedConversationOrder, anchor, targetId)
//                                 },
//                                 onSectionHeaderTap = { label ->
//                                     if (com.example.chat.ui.chats.home.isPinnedHomeSectionLabel(label)) pinnedCollapsed = !pinnedCollapsed
//                                 },
//                                 onNextUnread = {
//                                     val after = renderedConversationOrder.indexOfFirst { it.id == focusedConversationId }
//                                     val targets = renderedConversationOrder.drop(after + 1) + renderedConversationOrder.take(after + 1)
//                                     val next = targets.firstOrNull {
//                                         it.unreadCount > 0 && !isConversationHidden(it.id) && !isConversationLocked(it.id)
//                                     }
//                                     if (next == null) onSnack("No eligible unread chats in this view.")
//                                     else focusedConversationId = next.id
//                                 },
//                             )
//                             }
//                         }
//                         // 'Tablet Split View': list + quick pane on wide layouts;
//                         // narrow layouts fall back to the single column.
//                         if (spec.splitView && maxWidth >= 640.dp) {
//                             Row {
//                                 Box(Modifier.width(360.dp)) { listContent() }
//                                 // VerticalDivider(width: 1, color: surfaceColor)
//                                 Spacer(
//                                     Modifier
//                                         .width(1.dp)
//                                         .fillMaxHeight()
//                                         .background(Color(theme.surfaceColor)),
//                                 )
//                                 Box(Modifier.weight(1f)) {
//                                     val splitPinned = remember(conversations, hiddenConversationIds) {
//                                         conversations.filter { it.isPinned && !it.isArchived && it.id !in hiddenConversationIds }
//                                     }
//                                     val splitGroups = remember(conversations, hiddenConversationIds) {
//                                         conversations.filter {
//                                             it.type == ConversationType.group && !it.isArchived && it.id !in hiddenConversationIds
//                                         }
//                                     }
//                                     // Previously composed even beneath the visual curtain.
//                                     if (!privacyCurtainOn) SplitQuickPane(
//                                         theme = theme,
//                                         pinned = splitPinned,
//                                         groups = splitGroups,
//                                         onOpen = { handleConversationTap(it) },
//                                     )
//                                 }
//                             }
//                         } else {
//                             listContent()
//                         }
//                         if (privacyCurtainOn) {
//                             // R2-31: covers list titles and previews only; the header,
//                             // filters, pills and navigation outside this surface stay
//                             // available. Display aid - not an app lock, not encryption,
//                             // no screenshot guarantee. Taps here never reveal content;
//                             // the explicit "Show content" control below does.
//                             Box(
//                                 Modifier
//                                     .fillMaxSize()
//                                     .background(Color(theme.backgroundColor))
//                                     .clickable { },
//                                 contentAlignment = Alignment.Center,
//                             ) {
//                                 Column(horizontalAlignment = Alignment.CenterHorizontally) {
//                                     Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_privacy_curtain_on), color = Color(theme.primaryTextColor), fontSize = 15.sp, fontWeight = FontWeight.W700)  // was: "Privacy curtain on"
//                                     Text(
//                                         androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_a_display_aid_only_not_an_app_lock_encryption_or_a),  // was: "A display aid only - not an app lock, encryption or a screenshot guarantee."
//                                         color = Color(theme.secondaryTextColor),
//                                         fontSize = 12.sp,
//                                         modifier = Modifier.padding(top = 6.dp, start = 24.dp, end = 24.dp),
//                                     )
//                                 }
//                             }
//                         }
//                         // R2-18 / R2-23: explicit shortcuts; system Back and active filters are untouched.
//                         val lastOpenedTarget = lastOpenedConversationId?.let { id ->
//                             conversations.firstOrNull { it.id == id && id !in hiddenConversationIds }
//                         }
//                         // Previously: if (!isSelectionMode && lastOpenedTarget != null)
//                         // Do not emit a locked title, or any conversation shortcut above the curtain.
//                         if (!privacyCurtainOn && !isSelectionMode && lastOpenedTarget != null &&
//                             !isConversationLocked(lastOpenedTarget.id)) {
//                             Text(
//                                 androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_back_to_title, homeDisplayTitles[lastOpenedTarget.id] ?: lastOpenedTarget.title),  // was: "Back to ${homeDisplayTitles[lastOpenedTarget.id] ?: lastOpenedTarget.title}",
//                                 color = Color(theme.primaryTextColor),
//                                 fontSize = 13.sp,
//                                 maxLines = 1,
//                                 overflow = TextOverflow.Ellipsis,
//                                 modifier = Modifier
//                                     .align(Alignment.BottomStart)
//                                     .padding(start = 12.dp, bottom = 12.dp)
//                                     .width(180.dp)
//                                     .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
//                                     .background(Color(theme.surfaceColor))
//                                     .clickable { openLastOpenedConversation() }
//                                     .padding(horizontal = 12.dp, vertical = 8.dp),
//                             )
//                         }
//                         // Previously: if (resultCountAnnouncement != null)
//                         if (!privacyCurtainOn && resultCountAnnouncement != null) {
//                             Box(
//                                 Modifier
//                                     .width(1.dp)
//                                     .height(1.dp)
//                                     .semantics {
//                                         liveRegion = LiveRegionMode.Polite
//                                         contentDescription = resultCountAnnouncement
//                                     },
//                             )
//                         }
//                         if (!isSelectionMode && showJumpToTop) {
//                             Text(
//                                 androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_top),  // was: "Top"
//                                 color = Color(theme.primaryTextColor),
//                                 fontSize = 13.sp,
//                                 fontWeight = FontWeight.W700,
//                                 modifier = Modifier
//                                     .align(Alignment.TopCenter)
//                                     .padding(top = 8.dp)
//                                     .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
//                                     .background(Color(theme.surfaceColor))
//                                     .clickable { scope.launch { listState.animateScrollToItem(0) } }
//                                     .padding(horizontal = 16.dp, vertical = 8.dp),
//                             )
//                         }
//                         // R2-29: bounded manual refresh affordance. The host owns the
//                         // sync call, its bounds and its completion; this is only the
//                         // visible trigger plus in-progress cue. Hidden when unwired.
//                         if (!isSelectionMode && onRefreshHome != null) {
//                             Text(
//                                 if (homeRefreshing) "Refreshing…" else "Refresh",
//                                 color = Color(theme.primaryTextColor),
//                                 fontSize = 13.sp,
//                                 modifier = Modifier
//                                     .align(Alignment.BottomEnd)
//                                     .padding(end = 12.dp, bottom = 12.dp)
//                                     .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
//                                     .background(Color(theme.surfaceColor))
//                                     .clickable(enabled = !homeRefreshing) { onRefreshHome.invoke() }
//                                     .padding(horizontal = 12.dp, vertical = 8.dp),
//                             )
//                         }
//                         // R2-31: the curtain's explicit user trigger; stays reachable
//                         // while the curtain is up so content can be shown again.
//                         // Previously: if (!isSelectionMode)
//                         if (privacyCurtainOn || !isSelectionMode) {
//                             Text(
//                                 if (privacyCurtainOn) "Show content" else "Privacy curtain",
//                                 color = Color(theme.primaryTextColor),
//                                 fontSize = 13.sp,
//                                 modifier = Modifier
//                                     .align(Alignment.BottomEnd)
//                                     .padding(end = 12.dp, bottom = 52.dp)
//                                     .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
//                                     .background(Color(theme.surfaceColor))
//                                     .clickable { privacyCurtainOn = !privacyCurtainOn }
//                                     .padding(horizontal = 12.dp, vertical = 8.dp),
//                             )
//                         }
//                     }
//                 }
//             }
//             // R7 (responsive plan): at expanded width the chat list becomes a
//             // fixed 360-400dp pane with the open chat beside it (WhatsApp
//             // tablet behavior); otherwise the single column is unchanged.
//             if (chatPane != null && expandedListDetail) {
//                 // R4-21..23/27/30: session-only pane layout from HomeLayoutPolicy.
//                 // The divider is bounded by clampDivider so both panes stay usable;
//                 // hinge bounds stay empty until R4-26's window-manager decision lands
//                 // (held). Width persistence requires an explicit preference choice
//                 // first (R4-21), so nothing is stored.
//                 BoxWithConstraints(Modifier.fillMaxSize()) {
//                     val availableWidthDp = maxWidth.value
//                     // Hide the rail when it would consume the two readable pane minimums.
//                     val railWidthDp = if (showFolderRail && availableWidthDp >= 917f &&
//                         homePaneState.maximized != HomePaneMax.Detail) 181f else 0f
//                     val dividerWidthDp = 16f
//                     val paneWidthDp = (availableWidthDp - railWidthDp).coerceAtLeast(0f)
//                     val paneLayout = homePaneState.layout(
//                         availableWidthDp = paneWidthDp,
//                         detailOpen = paneConversation != null,
//                         expandedListDetail = true,
//                         dividerWidthDp = dividerWidthDp,
//                     )
//                     val listPaneFocusRequester = remember { FocusRequester() }
//                     val detailPaneFocusRequester = remember { FocusRequester() }
//                     var detailPaneHasFocus by remember(accountSessionIdentity) { mutableStateOf(false) }
//                     Row(
//                         Modifier
//                             .fillMaxSize()
//                             // R4-27: Ctrl+Tab moves focus between list and detail; plain
//                             // Tab keeps field/control traversal. This shortcut
//                             // submits no text and marks nothing read.
//                             .onPreviewKeyEvent { event ->
//                                 if (event.type == KeyEventType.KeyDown && event.key == Key.Tab && event.isCtrlPressed &&
//                                     // Previously: !event.isAltPressed && !event.isMetaPressed && paneLayout.isSplit
//                                     !privacyCurtainOn && !event.isAltPressed && !event.isMetaPressed && paneLayout.isSplit) {
//                                     val target = if (detailPaneHasFocus) listPaneFocusRequester else detailPaneFocusRequester
//                                     detailPaneHasFocus = !detailPaneHasFocus
//                                     runCatching { target.requestFocus() }
//                                     true
//                                 } else {
//                                     false
//                                 }
//                             },
//                     ) {
//                         if (railWidthDp > 0f && paneLayout.showList) {
//                             // R4-25: the rail replaces the horizontal strip (suppressed
//                             // above); it shows no duplicate navigation and never alters
//                             // the chosen folder.
//                             Column(
//                                 Modifier
//                                     .width(180.dp)
//                                     .fillMaxHeight()
//                                     .verticalScroll(rememberScrollState())
//                                     .padding(vertical = 8.dp),
//                             ) {
//                                 Text(
//                                     androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_all_chats),  // was: "All chats"
//                                     color = Color(if (selectedFolderId == null) theme.accentColor else theme.primaryTextColor),
//                                     fontSize = 13.sp,
//                                     fontWeight = if (selectedFolderId == null) FontWeight.W700 else FontWeight.W400,
//                                     maxLines = 1,
//                                     overflow = TextOverflow.Ellipsis,
//                                     modifier = Modifier
//                                         .fillMaxWidth()
//                                         .clickable { selectedFolderId = null }
//                                         .padding(horizontal = 12.dp, vertical = 8.dp),
//                                 )
//                                 chatFolderStore.folders().forEach { folder ->
//                                     Text(
//                                         folder.name,
//                                         color = Color(if (selectedFolderId == folder.id) theme.accentColor else theme.primaryTextColor),
//                                         fontSize = 13.sp,
//                                         fontWeight = if (selectedFolderId == folder.id) FontWeight.W700 else FontWeight.W400,
//                                         maxLines = 1,
//                                         overflow = TextOverflow.Ellipsis,
//                                         modifier = Modifier
//                                             .fillMaxWidth()
//                                             .clickable { selectedFolderId = folder.id }
//                                             .padding(horizontal = 12.dp, vertical = 8.dp),
//                                     )
//                                 }
//                             }
//                             Spacer(
//                                 Modifier
//                                     .width(1.dp)
//                                     .fillMaxHeight()
//                                     .background(Color(theme.surfaceColor)),
//                             )
//                         }
//                         if (paneLayout.showList) {
//                             val listWidthDp = if (paneLayout.showDetail) {
//                                 (paneWidthDp - dividerWidthDp).coerceAtLeast(0f) * paneLayout.dividerFraction
//                             } else {
//                                 paneWidthDp
//                             }
//                             Box(
//                                 Modifier
//                                     .width(listWidthDp.dp)
//                                     .fillMaxHeight()
//                                     .focusRequester(listPaneFocusRequester)
//                                     .focusable(),
//                             ) {
//                                 Column(Modifier.fillMaxSize(), content = listColumnContent)
//                             }
//                         }
//                         if (paneLayout.showList && paneLayout.showDetail) {
//                             Column(
//                                 Modifier
//                                     .width(dividerWidthDp.dp)
//                                     .fillMaxHeight()
//                                     .pointerInput(availableWidthDp, showFolderRail) {
//                                         detectDragGestures { change, dragAmount ->
//                                             change.consume()
//                                             val spanDp = (paneWidthDp - dividerWidthDp).coerceAtLeast(1f)
//                                             val next = homePaneState.dividerFraction + dragAmount.x.toDp().value / spanDp
//                                             homePaneState = homePaneState.copy(dividerFraction = clampDivider(next, emptyList()))
//                                         }
//                                     },
//                                 horizontalAlignment = Alignment.CenterHorizontally,
//                             ) {
//                                 Spacer(Modifier.width(1.dp).weight(1f).background(Color(theme.surfaceColor)))
//                                 // R4-22/23: temporary maximize with a visible restore.
//                                 // Selection, folder, search, the list anchor and the open
//                                 // conversation are untouched; Back also restores (R4-28).
//                                 listOf("List" to HomePaneMax.List, "Both" to HomePaneMax.None, "Detail" to HomePaneMax.Detail).forEach { (label, max) ->
//                                     Text(
//                                         label,
//                                         color = Color(if (homePaneState.maximized == max) theme.accentColor else theme.secondaryTextColor),
//                                         fontSize = 8.sp,
//                                         modifier = Modifier
//                                             .clickable { homePaneState = homePaneState.copy(maximized = max) }
//                                             .padding(vertical = 6.dp),
//                                     )
//                                 }
//                                 Spacer(Modifier.width(1.dp).weight(1f).background(Color(theme.surfaceColor)))
//                             }
//                         }
//                         if (paneLayout.showDetail) {
//                             Box(
//                                 Modifier
//                                     .then(if (paneLayout.showList) Modifier.weight(1f) else Modifier.fillMaxSize())
//                                     .fillMaxHeight()
//                                     .focusRequester(detailPaneFocusRequester)
//                                     .onFocusChanged { detailPaneHasFocus = it.hasFocus }
//                                     // Previously: .focusable()
//                                     .then(if (privacyCurtainOn) Modifier else Modifier.focusable()),
//                             ) {
//                                 val paneConv = paneConversation
//                                 // Previously: if (paneConv != null) { chatPane(paneConv) }
//                                 // Removing detail from composition also removes its semantics/focus targets.
//                                 if (privacyCurtainOn) {
//                                     Box(Modifier.fillMaxSize().background(Color(theme.backgroundColor)), contentAlignment = Alignment.Center) {
//                                         Text("Privacy curtain on", color = Color(theme.primaryTextColor))
//                                         ChatyTextButton(
//                                             onClick = { privacyCurtainOn = false },
//                                             label = "Show content",
//                                             modifier = Modifier.align(Alignment.BottomCenter),
//                                         )
//                                     }
//                                 } else if (paneConv != null) {
//                                     chatPane(paneConv)
//                                 } else {
//                                     // R4-30: concise keyboard/touch guidance plus a safe
//                                     // New chat entry; no private automatic recommendations.
//                                     Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
//                                         Column(horizontalAlignment = Alignment.CenterHorizontally) {
//                                             Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_select_a_chat), color = Color(theme.secondaryTextColor), fontSize = 15.sp)  // was: "Select a chat"
//                                             Text(
//                                                 androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_tap_a_chat_on_the_left_or_press_tab_to_move_here_a),  // was: "Tap a chat on the left, or press Tab to move here and Enter to open the focused chat."
//                                                 color = Color(theme.secondaryTextColor),
//                                                 fontSize = 12.sp,
//                                                 modifier = Modifier.padding(top = 6.dp, start = 24.dp, end = 24.dp),
//                                             )
//                                             ChatyTextButton(
//                                                 onClick = { nav.onOpenNewChat(false, false) },
//                                                 label = "New chat",
//                                                 fontWeight = FontWeight.W700,
//                                             )
//                                         }
//                                     }
//                                 }
//                             }
//                         }
//                     }
//                     if (homePaneState.maximized != HomePaneMax.None) {
//                         ChatyTextButton(
//                             onClick = { homePaneState = homePaneState.restoreSplit() },
//                             label = "Restore split",
//                             modifier = Modifier.align(Alignment.TopEnd),
//                         )
//                     }
//                 }
//             } else {
//                 // R4-29: on narrowing the host keeps paneConversation as the selected
//                 // detail route (host-owned) and Home keeps its session pane state
//                 // above, so re-expanding returns to the same layout and list context.
//                 Column(Modifier.fillMaxSize(), content = listColumnContent)
//             }
//             }
//             }
//         }
//     }
// }
//
// /** _conversationsList: sections, archived entry and swipe-wrapped rows. */
// @Composable
// private fun HomeConversationList(
//     listAnimation: String = "Standard",
//     entries: List<HomeEntry>,
//     clockMinute: Long,
//     theme: com.example.chat.core.theme.ThemeConfig,
//     homePrefs: HomePreferences,
//     spec: HomeStyleSpec,
//     bundleTemplates: HomeTemplates,
//     currentUserId: String,
//     contacts: List<UserProfile>,
//     selectedIds: Set<String>,
//     isSelectionMode: Boolean,
//     presenceFor: (String, String) -> HomePresence?,
//     messagesFor: (String) -> List<ChatMessage>,
//     lastDeliveryStateFor: ((String) -> com.example.chat.domain.models.DeliveryState?)? = null,
//     gbBool: (String, Boolean) -> Boolean,
//     gbColor: (String) -> Long?,
//     isConversationLocked: (String) -> Boolean,
//     onOpenArchived: () -> Unit,
//     onRowTap: (Conversation) -> Unit,
//     onRowLongPress: (Conversation) -> Unit,
//     onTogglePin: (String) -> Unit,
//     onToggleMute: (String) -> Unit,
//     onToggleArchive: (String) -> Unit,
//     onStartCall: (Conversation, Boolean) -> Unit,
//     listState: androidx.compose.foundation.lazy.LazyListState,
//     openConversationId: String? = null,
//     focusedConversationId: String? = null,
//     hoveredRowId: String? = null,
//     orderedConversationIds: List<String> = emptyList(),
//     rowLabels: List<String> = emptyList(),
//     opState: Map<HomeOpKey, HomeOpState> = emptyMap(),
//     onRetry: (HomeOpKey) -> Unit = {},
//     focusRequesterFor: (String) -> FocusRequester = { FocusRequester() },
//     onRowFocused: (String?) -> Unit = {},
//     onHoverRow: (String?) -> Unit = {},
//     onRowContextMenu: (Conversation) -> Unit = {},
//     onRowActivate: (Conversation) -> Unit = {},
//     onRangeSelectTo: (String) -> Unit = {},
//     onSectionHeaderTap: (String) -> Unit = {},
//     onNextUnread: () -> Unit = {},
// ) {
//     val knownContactIds=remember(contacts) {contacts.map {it.id}.toSet()}
//     // R5-01/R5-10: eagerly built, type-namespaced keys (chat:/section:/archive:
//     // namespaces) so an opaque conversation ID can never equal a synthetic
//     // section/archive key, and a chat moving between sections keeps its key - row
//     // state, swipe and selection follow the chat ID, never the position.
//     val keyedEntries = remember(entries) {
//         val seen = mutableSetOf<String>()
//         entries.map { homeEntryKey(it, seen) }
//     }
//     // R4-33..36: keep the keyboard-focused row visible and actually focused.
//     LaunchedEffect(focusedConversationId, entries) {
//         val id = focusedConversationId ?: return@LaunchedEffect
//         val idx = entries.indexOfFirst { it is HomeEntry.Item && it.conversation.id == id }
//         if (idx >= 0) {
//             listState.animateScrollToItem(idx)
//             androidx.compose.runtime.withFrameNanos { }
//             runCatching { focusRequesterFor(id).requestFocus() }
//         }
//     }
//     // The shared clock is supplied as a changing value, including across strong-skipped rows.
//     LazyColumn(
//         // R4-35: short-lived typeahead over displayed names, cleared on idle by the
//         // modifier itself; never stored as search history, never a secret phrase.
//         modifier = Modifier
//             .fillMaxSize()
//             .onPreviewKeyEvent { event ->
//                 // List subtree only: never steals editing keys from search/composer.
//                 if (event.type == KeyEventType.KeyDown && event.key == Key.N &&
//                     event.isAltPressed && !event.isCtrlPressed && !event.isMetaPressed && !event.isShiftPressed) {
//                     onNextUnread()
//                     true
//                 } else false
//             }
//             .homeTypeahead(
//                 labels = rowLabels,
//                 onMatch = { matchIndex -> orderedConversationIds.getOrNull(matchIndex)?.let(onRowFocused) },
//                 enabled = !isSelectionMode,
//             ),
//         state = listState,
//         contentPadding = androidx.compose.foundation.layout.PaddingValues(
//             start = if (bundleTemplates.chatList.referenceDefault && com.example.chat.core.preferences.HomePresentationPolicy.isDefault(homePrefs.homeStyle)) 0.dp else 8.dp,
//             top = 0.dp,
//             end = if (bundleTemplates.chatList.referenceDefault && com.example.chat.core.preferences.HomePresentationPolicy.isDefault(homePrefs.homeStyle)) 0.dp else 8.dp,
//             bottom = 96.dp,
//         ),
//     ) {
//         items(
//             count = entries.size,
//             contentType = { index -> when(entries[index]) {is HomeEntry.Item -> "chat";is HomeEntry.Section -> "section";is HomeEntry.Archived -> "archive"} },
//             // R5-01: namespaced keys, still never index-derived: an insert, archive
//             // or reorder must not change another row's key, or Compose discards its
//             // state and replays entrance animations.
//             key = { index -> keyedEntries[index] },
//         ) { index ->
//             val entranceKey = keyedEntries[index].toString()
//             Box(Modifier.listEntrance(listAnimation, entranceKey)) {
//             when (val entry = entries[index]) {
//                 is HomeEntry.Archived -> {
//                     // _archivedTile
//                     Row(
//                         modifier = Modifier
//                             .fillMaxWidth()
//                             .clickable(onClick = onOpenArchived)
//                             .padding(horizontal = 16.dp, vertical = 12.dp),
//                         verticalAlignment = Alignment.CenterVertically,
//                     ) {
//                         com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.archive,
//                             semanticLabel = null,
//                             color = Color(theme.secondaryTextColor),
//                             modifier = Modifier.width(22.dp).height(22.dp),
//                         )
//                         Spacer(Modifier.width(24.dp))
//                         Text(
//                             androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_archived),  // was: "Archived"
//                             color = Color(theme.primaryTextColor),
//                             fontSize = (16 * theme.fontScale).sp,
//                             fontWeight = FontWeight.W700,
//                             modifier = Modifier.weight(1f),
//                         )
//                         Text(
//                             entry.archivedCount.toString(),
//                             color = Color(theme.accentColor),
//                             fontSize = 13.sp,
//                             fontWeight = FontWeight.W700,
//                         )
//                     }
//                 }
//                 is HomeEntry.Section -> {
//                     // R4-43: tapping the PINNED header toggles the session-only
//                     // collapse. Chats stay pinned; the grouping post-processor keeps
//                     // unread totals and search results intact.
//                     Box(
//                         Modifier
//                             .padding(start = 12.dp, top = 7.dp, end = 12.dp, bottom = 4.dp)
//                             .clickable { onSectionHeaderTap(entry.label) },
//                     ) {
//                         HomeSectionHeader(
//                             text = entry.label,
//                             color = Color(theme.secondaryTextColor),
//                             fontSize = 10.5f,
//                         )
//                     }
//                 }
//                 is HomeEntry.Item -> {
//                     val conversation = entry.conversation
//                     val otherId = conversation.participantIds.firstOrNull { it != currentUserId } ?: ""
//                     val lastOwn = if (conversation.lastMessageSenderId == currentUserId) {
//                         lastDeliveryStateFor?.invoke(conversation.id)
//                             ?: messagesFor(conversation.id).lastOrNull()?.deliveryState
//                     } else {
//                         null
//                     }
//                     // R4-24/31..36 + C8: every pointer, keyboard, focus and operation
//                     // affordance rides the caller's Modifier and overlays here -
//                     // ConversationRow keeps its signature and behavior untouched.
//                     val rowFocusRequester = focusRequesterFor(conversation.id)
//                     val rowModifier = Modifier
//                         .onKeyEvent { event ->
//                             if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
//                             val ids = orderedConversationIds
//                             val position = ids.indexOf(conversation.id)
//                             when (event.key) {
//                                 Key.DirectionDown, Key.DirectionUp -> {
//                                     if (position < 0) return@onKeyEvent false
//                                     val step = if (event.key == Key.DirectionDown) 1 else -1
//                                     val target = ids.getOrNull(position + step) ?: return@onKeyEvent true
//                                     // R4-33: arrows move focus by stable chat identity,
//                                     // skipping section headers; they open no chat and
//                                     // send no read receipt.
//                                     onRowFocused(target)
//                                     // R4-13: Shift extends the selection from the anchor.
//                                     if (event.isShiftPressed && isSelectionMode) onRangeSelectTo(target)
//                                     true
//                                 }
//                                 // R4-34: Home/End within the focused list only; text
//                                 // fields keep their editing shortcuts because this
//                                 // handler runs solely while a row holds focus.
//                                 Key.MoveHome -> { ids.firstOrNull()?.let(onRowFocused); true }
//                                 Key.MoveEnd -> { ids.lastOrNull()?.let(onRowFocused); true }
//                                 Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
//                                     // Activation is explicit; focus alone never opens.
//                                     onRowActivate(conversation)
//                                     true
//                                 }
//                                 else -> false
//                             }
//                         }
//                         .onFocusChanged { state -> if (state.hasFocus) onRowFocused(conversation.id) }
//                         // R4-36: token-colored focus ring, distinct from unread,
//                         // current-detail and bulk selection. Mouse/touch activation
//                         // never requests focus, so no misleading permanent marker.
//                         .homeFocusRing(color = Color(theme.accentColor))
//                         // R4-31/32: secondary-click menu + hover affordances.
//                         .homeRowInput(
//                             onContext = { onRowContextMenu(conversation) },
//                             onHover = { hovering -> onHoverRow(if (hovering) conversation.id else null) },
//                             focus = rowFocusRequester,
//                         )
//                         // R4-24: current-conversation indicator, separate from bulk
//                         // selection; theme colors keep it readable in dark mode and
//                         // high contrast, and unread styling is untouched.
//                         .then(
//                             if (conversation.id == openConversationId) {
//                                 Modifier.background(Color(theme.surfaceColor))
//                             } else {
//                                 Modifier
//                             }
//                         )
//                     HomeSwipeActions(
//                         enabled = !isSelectionMode,
//                         backgroundColor = com.example.chat.ui.navigation.LocalReferenceContentSurface.current
//                             ?: Color(theme.backgroundColor),
//                         leadingActions = if (conversation.type == ConversationType.direct) {
//                             listOf(
//                                 HomeSwipeAction(
//                                     icon = com.example.chat.ui.icons.ChatyIconId.callAudio,
//                                     label = "Call",
//                                     color = Color(theme.successColor),
//                                     onTriggered = { onStartCall(conversation, false) },
//                                 ),
//                                 HomeSwipeAction(
//                                     icon = com.example.chat.ui.icons.ChatyIconId.callVideo,
//                                     label = "Video",
//                                     color = Color(theme.accentColor),
//                                     onTriggered = { onStartCall(conversation, true) },
//                                 ),
//                             )
//                         } else {
//                             emptyList()
//                         },
//                         actions = listOf(
//                             HomeSwipeAction(
//                                 icon = if (conversation.isPinned) com.example.chat.ui.icons.ChatyIconId.unpin else com.example.chat.ui.icons.ChatyIconId.pin,
//                                 label = if (conversation.isPinned) "Unpin" else "Pin",
//                                 color = Color(theme.accentColor),
//                                 onTriggered = { onTogglePin(conversation.id) },
//                             ),
//                             HomeSwipeAction(
//                                 icon = if (conversation.isMuted) com.example.chat.ui.icons.ChatyIconId.unmute else com.example.chat.ui.icons.ChatyIconId.mute,
//                                 label = if (conversation.isMuted) "Unmute" else "Mute",
//                                 color = com.example.chat.ui.core.theme.ChatyColors(theme).warning,
//                                 onTriggered = { onToggleMute(conversation.id) },
//                             ),
//                             HomeSwipeAction(
//                                 icon = com.example.chat.ui.icons.ChatyIconId.archive,
//                                 label = "Archive",
//                                 color = Color(theme.secondaryTextColor),
//                                 onTriggered = { onToggleArchive(conversation.id) },
//                             ),
//                         ),
//                     ) {
//                         Box {
//                         ConversationRow(
//                             conversation = conversation,
//                             theme = theme,
//                             homePrefs = homePrefs,
//                             chatList = bundleTemplates.chatList,
//                             density = spec.tileDensity,
//                             isDark = theme.isDark,
//                             cardRows = spec.cardRows && !com.example.chat.core.preferences.HomePresentationPolicy.isDefault(homePrefs.homeStyle),
//                             isTelegramRows = rawIsTelegram(homePrefs.homeStyle),
//                             displayTitle = displayConversationTitle(
//                                 conversation,
//                                 homePrefs.hideUnsavedNumbers,
//                                 currentUserId,
//                                 knownContactIds,
//                             ),
//                             presence = presenceFor(conversation.id, otherId),
//                             onPinAction={onTogglePin(conversation.id)},onMuteAction={onToggleMute(conversation.id)},
//                             onArchiveAction={onToggleArchive(conversation.id)},
//                             clockMinute = clockMinute,
//                             currentUserId = currentUserId,
//                             lastOwnMessageState = lastOwn,
//                             selected = selectedIds.contains(conversation.id),
//                             locked = isConversationLocked(conversation.id),
//                             elapsedTime = homePrefs.elapsedTime,
//                             gbOnlineChat = gbBool("onlinechat", true),
//                             gbOnlineDotChat = gbBool("onlineDotchat", true),
//                             gbModOnlineColor = gbColor("ModOnlineColor"),
//                             gbModLastSeenColor = gbColor("ModlastseenColor"),
//                             gbOnlineDotColor = gbColor("onlineDotchatColor"),
//                             gbHomeCounterBk = gbColor("HomeCounterBK"),
//                             gbHomeCounterText = gbColor("HomeCounterText"),
//                             modifier = rowModifier,
//                             onTap = { onRowTap(conversation) },
//                             onLongPress = { onRowLongPress(conversation) },
//                         )
//                         // R4-32: hover reveals safe pin/mute affordances without
//                         // replacing the touch layout and without exposing locked
//                         // message text; every action stays reachable without hover.
//                         if (hoveredRowId == conversation.id && !isSelectionMode) {
//                             Row(Modifier.align(Alignment.CenterEnd).padding(end = 10.dp)) {
//                                 Text(
//                                     if (conversation.isPinned) "Unpin" else "Pin",
//                                     color = Color(theme.accentColor),
//                                     fontSize = 11.sp,
//                                     modifier = Modifier
//                                         .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
//                                         .background(Color(theme.surfaceColor))
//                                         .clickable { onTogglePin(conversation.id) }
//                                         .padding(horizontal = 8.dp, vertical = 4.dp),
//                                 )
//                                 Spacer(Modifier.width(6.dp))
//                                 Text(
//                                     if (conversation.isMuted) "Unmute" else "Mute",
//                                     color = Color(theme.accentColor),
//                                     fontSize = 11.sp,
//                                     modifier = Modifier
//                                         .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
//                                         .background(Color(theme.surfaceColor))
//                                         .clickable { onToggleMute(conversation.id) }
//                                         .padding(horizontal = 8.dp, vertical = 4.dp),
//                                 )
//                             }
//                         }
//                         // C8: host-owned per-row operation state; the default empty map
//                         // renders nothing. Failed offers the retry intent; Pending shows
//                         // a quiet cue. Succeeded/Cancelled leave no residue here.
//                         val failedOp = opState.entries.firstOrNull { it.key.chatId == conversation.id && it.value is HomeOpState.Failed }
//                         val pendingOp = opState.entries.firstOrNull { it.key.chatId == conversation.id && it.value is HomeOpState.Pending }
//                         if (failedOp != null) {
//                             Text(
//                                 androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_failed_tap_to_retry),  // was: "Failed - tap to retry"
//                                 color = Color(theme.primaryTextColor),
//                                 fontSize = 11.sp,
//                                 modifier = Modifier
//                                     .align(Alignment.BottomEnd)
//                                     .padding(end = 10.dp, bottom = 4.dp)
//                                     .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
//                                     .background(Color(theme.surfaceColor))
//                                     .clickable { onRetry(failedOp.key) }
//                                     .padding(horizontal = 8.dp, vertical = 3.dp),
//                             )
//                         } else if (pendingOp != null) {
//                             Text(
//                                 androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_working),  // was: "Working…"
//                                 color = Color(theme.secondaryTextColor),
//                                 fontSize = 11.sp,
//                                 modifier = Modifier
//                                     .align(Alignment.BottomEnd)
//                                     .padding(end = 10.dp, bottom = 4.dp)
//                                     .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
//                                     .background(Color(theme.surfaceColor))
//                                     .padding(horizontal = 8.dp, vertical = 3.dp),
//                             )
//                         }
//                         }
//                     }
//                 }
//             }
//             }
//         }
//     }
// }
//
// private fun rawIsTelegram(homeStyle: String): Boolean =
//     homeStyle.trim().lowercase() == "telegram style"
//
// /* Before per-chat contextual-menu icons, retained for reference.
//     // R4-31: secondary-click menu with the same reviewed actions as swipe/selection.
//     // Opening it selects no recipient, starts no call and mutates no chat state.
//     val rowMenuConversation = rowMenuConversationId?.let { id -> conversations.firstOrNull { it.id == id } }
//     if (rowMenuConversation != null) {
//         val menuConversation = rowMenuConversation
//         AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
//             onDismissRequest = { rowMenuConversationId = null },
//             title = {
//                 Text(
//                     if (isConversationLocked(menuConversation.id)) "Locked conversation"
//                     else homeDisplayTitles[menuConversation.id] ?: menuConversation.title,
//                     maxLines = 1,
//                     overflow = TextOverflow.Ellipsis,
//                     fontWeight = FontWeight.W700,
//                 )
//             },
//             text = {
//                 Column {
//                     ChatyTextButton(
//                         onClick = { onTogglePin(menuConversation.id); rowMenuConversationId = null },
//                         label = if (menuConversation.isPinned) "Unpin" else "Pin",
//                     )
//                     ChatyTextButton(
//                         onClick = { onToggleMute(menuConversation.id); rowMenuConversationId = null },
//                         label = if (menuConversation.isMuted) "Unmute" else "Mute",
//                     )
//                     ChatyTextButton(
//                         onClick = { onToggleArchive(menuConversation.id); rowMenuConversationId = null },
//                         label = if (menuConversation.isArchived) "Unarchive" else "Archive",
//                     )
//                     ChatyTextButton(
//                         onClick = { onMarkRead(menuConversation.id); rowMenuConversationId = null },
//                         label = "Mark read",
//                     )
//                     ChatyTextButton(
//                         onClick = { onMarkUnread(menuConversation.id); rowMenuConversationId = null },
//                         label = "Mark unread",
//                     )
//                 }
//             },
//             confirmButton = { ChatyTextButton(onClick = { rowMenuConversationId = null }, label = "Close") },
//         )
//     }
//
//
// */

// Replaced lines retained for source comparison (not executable).
//     onMarkAllRead: () -> Unit = {},
//                         onClick = { onTogglePin(menuConversation.id); rowMenuConversationId = null },
//                         onClick = { onToggleMute(menuConversation.id); rowMenuConversationId = null },
//                         onClick = { onToggleArchive(menuConversation.id); rowMenuConversationId = null },
//                         onClick = { onMarkRead(menuConversation.id); rowMenuConversationId = null },
//                         onClick = { onMarkUnread(menuConversation.id); rowMenuConversationId = null },
//                                     onMarkAllRead()
//                                 onTogglePin = onTogglePin,
//                                 onToggleMute = onToggleMute,
//                                 onToggleArchive = onToggleArchive,
