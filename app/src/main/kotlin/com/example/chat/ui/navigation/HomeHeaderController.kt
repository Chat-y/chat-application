package com.example.chat.ui.navigation

import androidx.compose.runtime.*
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.domain.models.HomePreferences
import com.example.chat.ui.chats.home.*

/** Account-owned values/events only. No page composable or execution callback is retained. */
internal enum class HeaderAction { RefreshHome, TogglePrivacyCurtain, ClearSelection, TogglePin, Delete, ToggleMute, ToggleArchive, ToggleLock, MarkUnread, MarkRead, SelectAll, Invert, Inspect, ExitSelection, SplitChat, ToggleSearch, IdentityTap, IdentityLongPress, OpenOverflowMenu, CheckHomeAppearance, Dismiss, OpenViewOptions, OpenScratchpad, OpenSavedViews, OpenViewHistory, OpenShortcutHelp, MarkAllAsRead, MessageANumber, OpenArchived, ChangeQuery, SetGhostMode, ShowNotice, OpenPlan, OpenConnectionDetail, NavOpenProfile, NavOpenAccountSwitcher, NavOpenSettings, NavOpenNotifications, NavOpenSecuritySettings, NavOpenGlobalSearch, NavOpenQr, NavOpenLinkedDevices, NavNewCall, NavOpenStarredMessages, NavOpenMessageManagement, NavOpenLockedVault, NavOpenCameraEffects, NavWhoCanCallMe, NavShareApp, NavRestartApp, NavToggleBrightness, NavSelectPlan, NavOpenTasks, NavOpenUpdates, NavOpenSplitChat, NavOpenNewChat, NavMessageNumber }
internal val HeaderAction.selectionSensitive: Boolean get() = this in setOf(
    HeaderAction.ClearSelection, HeaderAction.TogglePin, HeaderAction.Delete, HeaderAction.ToggleMute,
    HeaderAction.ToggleArchive, HeaderAction.ToggleLock, HeaderAction.MarkUnread, HeaderAction.MarkRead,
    HeaderAction.SelectAll, HeaderAction.Invert, HeaderAction.Inspect, HeaderAction.ExitSelection,
    HeaderAction.SplitChat, HeaderAction.NavOpenSplitChat)
internal data class HeaderEvent(val action: HeaderAction, val text: String = "", val flag: Boolean = false,
    val secondFlag: Boolean = false, val ids: List<String> = emptyList())
internal data class HomeHeaderModel(
    val theme: ThemeConfig, val homePrefs: HomePreferences, val headerStyle: HomeHeaderStyle,
    val homeStyle: String, val title: String, val identity: HomeIdentity, val forcedTypeIsSet: Boolean,
    val requiresSubscription: Boolean, val subscriptionMessage: String?, val validActions: Set<HomeBulkAction>,
    val visibleSelectedCount: Int, val anyUnpinned: Boolean, val anyUnmuted: Boolean, val anyUnlocked: Boolean,
    val searchEnabled: Boolean, val searchPlacement: String, val searchHint: String,
    val hasConnectionDetail: Boolean, val hasSplitChat: Boolean, val showTasksEntry: Boolean, val showUpdatesEntry: Boolean,
)
internal data class QueuedHeaderEvent(val sequence: Long, val mount: Long, val selection: Long,
    val scope: Pair<HomeViewState, String>?, val event: HeaderEvent)
@Stable internal class HomeHeaderEntry {
    var query by mutableStateOf("")
    var searchOpen by mutableStateOf(false)
    var searchIconCenter by mutableStateOf<androidx.compose.ui.geometry.Offset?>(null)
    private var selectedState by mutableStateOf<Set<String>>(emptySet())
    var selectionGeneration = 0L
        private set
    var selectedIds: Set<String>
        get() = selectedState
        set(value) {
            if (selectedState != value) { selectedState = value.toSet(); selectionGeneration++ }
        }
    var receiverReady by mutableStateOf(false)
        private set
    var mountGeneration by mutableLongStateOf(0L)
        private set
    fun attach() { mountGeneration++; receiverReady = true }
    fun detach() {
        receiverReady = false; mountGeneration++; events.clear(); model = null
        overflowOpen = false
        // Published Home-tool facts are cleared with the receiver so a stale FAB entry cannot act.
        canRefresh = false; refreshing = false; hasVisibleChats = false
    }
    fun recoverReceiverAfterFailure() {
        // Drop the failed event and all possibly related queued operations; never replay effects.
        detach()
        attach()
    }
    fun accepts(event: QueuedHeaderEvent) = receiverReady && event.mount == mountGeneration &&
        (!event.event.action.selectionSensitive ||
            (event.selection == selectionGeneration && event.scope == selectionScope))
    var selectedFilter by mutableStateOf("All")
    var selectedFolderId by mutableStateOf<String?>(null)
    var quickView by mutableStateOf(HomeQuickView.All)
    var localOrder by mutableStateOf(HomeLocalOrder.Existing)
    var groupingKey by mutableStateOf("")
    var pinnedCollapsed by mutableStateOf(false)
    var savedViews by mutableStateOf(emptyList<HomeSavedView>())
    var viewHistory by mutableStateOf(emptyList<HomeViewState>())
    var previewView by mutableStateOf<HomeSavedView?>(null)
    var saveAfterPreview by mutableStateOf(false)
    var privacyCurtainOn by mutableStateOf(false)
    // Home tools hosted by the chats FAB menu. ChatsHomeScreen publishes read-only facts at its page root;
    // the FAB sends RefreshHome / TogglePrivacyCurtain through the scoped HeaderEvent queue (no raw callbacks).
    // fabHostsHomeTools is set only by a mounted chats FAB, so the old chips stay when no FAB hosts them.
    var canRefresh by mutableStateOf(false)
    var refreshing by mutableStateOf(false)
    // True when the Home list shows entries (the same predicate that gates the old curtain chip).
    var hasVisibleChats by mutableStateOf(false)
    var fabHostsHomeTools by mutableStateOf(false)
    var confirmClearScratch by mutableStateOf(false)
    var overflowOpen by mutableStateOf(false)
    var selectionAnchorId by mutableStateOf<String?>(null)
    var selectionScope: Pair<com.example.chat.ui.chats.home.HomeViewState, String>? by mutableStateOf(null)
    // Modal flags survive pager disposal but remain scoped to this account.
    var showDeleteConfirm by mutableStateOf(false)
    var showMessageNumber by mutableStateOf(false)
    var messageNumberText by mutableStateOf("")
    var showWhoCanCallMe by mutableStateOf(false)
    var showLockedSetup by mutableStateOf(false)
    var showArchived by mutableStateOf(false)
    var showEffects by mutableStateOf(false)
    var showScratchpad by mutableStateOf(false)
    var scratchText by mutableStateOf("")
    var showViewOptions by mutableStateOf(false)
    var showSavedViews by mutableStateOf(false)
    var showViewHistory by mutableStateOf(false)
    var showShortcutHelp by mutableStateOf(false)
    var showSelectionInspector by mutableStateOf(false)
    var showAppearanceResetPreview by mutableStateOf(false)
    var model by mutableStateOf<HomeHeaderModel?>(null)
    val events = mutableStateListOf<QueuedHeaderEvent>()
}
@Stable class HomeHeaderController {
    private val entries = mutableMapOf<String, HomeHeaderEntry>()
    internal var baseModel by mutableStateOf<HomeHeaderModel?>(null)
    internal fun model(id: String): HomeHeaderModel? {
        val base = baseModel ?: return entry(id).model
        val selection = entry(id).model
        return base.copy(forcedTypeIsSet = id == "groups",
            title = when (id) { "groups" -> "Groups"; "chats" -> base.title; else -> NavigationCatalog.labelFor(id) },
            validActions = selection?.validActions ?: emptySet(),
            visibleSelectedCount = selection?.visibleSelectedCount ?: 0,
            anyUnpinned = selection?.anyUnpinned ?: false,
            anyUnmuted = selection?.anyUnmuted ?: false,
            anyUnlocked = selection?.anyUnlocked ?: false,
            hasConnectionDetail = selection?.hasConnectionDetail ?: false,
            hasSplitChat = selection?.hasSplitChat ?: false)
    }
    internal fun entry(id: String): HomeHeaderEntry = entries.getOrPut(id) { HomeHeaderEntry() }
    private var nextSequence = 0L
    internal fun emit(id: String, event: HeaderEvent) {
        val target = entry(id)
        if (!target.receiverReady) return
        target.events.add(QueuedHeaderEvent(++nextSequence, target.mountGeneration, target.selectionGeneration, target.selectionScope, event))
    }
}
internal val LocalHomeHeaderController = staticCompositionLocalOf<HomeHeaderController?> { null }
