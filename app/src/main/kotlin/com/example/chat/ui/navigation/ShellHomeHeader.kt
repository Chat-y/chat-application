package com.example.chat.ui.navigation

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import com.example.chat.ui.chats.home.*
import com.example.chat.domain.models.ConversationType

internal enum class HeaderRegion { Identity, Actions, Banner, Search }

@Composable
internal fun ShellHomeHeader(controller: HomeHeaderController, id: String,
    region: HeaderRegion, allowFocus: Boolean = true) {
    val entry = controller.entry(id)
    val model = controller.model(id) ?: return
    val theme = model.theme
    val homePrefs = model.homePrefs
    val headerStyle = model.headerStyle
    val homeStyle = model.homeStyle
    val forcedType = if (model.forcedTypeIsSet) com.example.chat.domain.models.ConversationType.group else null
    val pageTitle = model.title
    val barTitle = model.title
    val identity = model.identity
    val requiresSubscription = model.requiresSubscription
    val subscriptionMessage = model.subscriptionMessage
    val validBulkActions = model.validActions
    val isSelectionMode = entry.receiverReady && entry.selectedIds.isNotEmpty()
    val isSearchOpen = entry.searchOpen
    val searchQuery = entry.query
    val overflowOpen = entry.overflowOpen
    val searchEnabled = model.searchEnabled
    val searchPlacement = model.searchPlacement
    val searchFocus = remember { FocusRequester() }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    LaunchedEffect(isSearchOpen, allowFocus) {
        if (region == HeaderRegion.Search) {
            if (isSearchOpen && allowFocus) { withFrameNanos { }; runCatching { searchFocus.requestFocus() } }
            else focusManager.clearFocus()
        }
    }
    val eventNav = ChatsHomeNav(
        onOpenProfile = { controller.emit(id, HeaderEvent(HeaderAction.NavOpenProfile)) },
        onOpenAccountSwitcher = { controller.emit(id, HeaderEvent(HeaderAction.NavOpenAccountSwitcher)) },
        onOpenSettings = { controller.emit(id, HeaderEvent(HeaderAction.NavOpenSettings)) },
        onOpenNotifications = { controller.emit(id, HeaderEvent(HeaderAction.NavOpenNotifications)) },
        onOpenSecuritySettings = { controller.emit(id, HeaderEvent(HeaderAction.NavOpenSecuritySettings)) },
        onOpenGlobalSearch = { controller.emit(id, HeaderEvent(HeaderAction.NavOpenGlobalSearch)) },
        onOpenQr = { controller.emit(id, HeaderEvent(HeaderAction.NavOpenQr)) },
        onOpenLinkedDevices = { controller.emit(id, HeaderEvent(HeaderAction.NavOpenLinkedDevices)) },
        onNewCall = { controller.emit(id, HeaderEvent(HeaderAction.NavNewCall)) },
        onOpenStarredMessages = { controller.emit(id, HeaderEvent(HeaderAction.NavOpenStarredMessages)) },
        onOpenMessageManagement = { controller.emit(id, HeaderEvent(HeaderAction.NavOpenMessageManagement)) },
        onOpenLockedVault = { controller.emit(id, HeaderEvent(HeaderAction.NavOpenLockedVault)) },
        onOpenCameraEffects = { controller.emit(id, HeaderEvent(HeaderAction.NavOpenCameraEffects)) },
        onWhoCanCallMe = { controller.emit(id, HeaderEvent(HeaderAction.NavWhoCanCallMe)) },
        onShareApp = { controller.emit(id, HeaderEvent(HeaderAction.NavShareApp)) },
        onRestartApp = { controller.emit(id, HeaderEvent(HeaderAction.NavRestartApp)) },
        onToggleBrightness = { controller.emit(id, HeaderEvent(HeaderAction.NavToggleBrightness)) },
        onSelectPlan = { controller.emit(id, HeaderEvent(HeaderAction.NavSelectPlan)) },
        onOpenTasks = { controller.emit(id, HeaderEvent(HeaderAction.NavOpenTasks)) },
        onOpenUpdates = { controller.emit(id, HeaderEvent(HeaderAction.NavOpenUpdates)) },
        onOpenSplitChat = if (model.hasSplitChat) ({ ids -> controller.emit(id, HeaderEvent(HeaderAction.NavOpenSplitChat, ids = ids)) }) else null,
        onOpenNewChat = { group, broadcast -> controller.emit(id, HeaderEvent(HeaderAction.NavOpenNewChat, flag = group, secondFlag = broadcast)) },
        onMessageNumber = { number -> controller.emit(id, HeaderEvent(HeaderAction.NavMessageNumber, text = number)) },
        showTasksEntry = model.showTasksEntry,
        showUpdatesEntry = model.showUpdatesEntry
    )
    when (region) {
// Inactive large-identity path retained:         HeaderRegion.Identity -> {
// Inactive large-identity path retained:                         StandardHomeAppBar(
// Inactive large-identity path retained:                             theme = theme,
// Inactive large-identity path retained:                             homePrefs = homePrefs,
// Inactive large-identity path retained:                             headerStyle = headerStyle,
// Inactive large-identity path retained:                             homeStyle = homeStyle,
// Inactive large-identity path retained:                             title = if (forcedType != null) pageTitle ?: barTitle else barTitle,
// Inactive large-identity path retained:                             identity = identity,
// Inactive large-identity path retained:                             isSearchOpen = isSearchOpen,
// Inactive large-identity path retained:                             forcedTypeIsSet = forcedType != null,
// Inactive large-identity path retained:                             nav = eventNav,
// Inactive large-identity path retained:                             onToggleSearch = { controller.emit(id, HeaderEvent(HeaderAction.ToggleSearch)) },
// Inactive large-identity path retained:                             onToggleGhost = { value -> controller.emit(id, HeaderEvent(HeaderAction.SetGhostMode, flag = value)) },
// Inactive large-identity path retained:                             // _handleTopLeftIdentityTap: with Locked Chats
// Inactive large-identity path retained:                             // configured the vault stays behind the auth gate
// Inactive large-identity path retained:                             // (fail-closed seam; owner wires the real gate),
// Inactive large-identity path retained:                             // otherwise the guided setup modal explains it.
// Inactive large-identity path retained:                             onIdentityTap = { controller.emit(id, HeaderEvent(HeaderAction.IdentityTap)) },
// Inactive large-identity path retained:                             // Source deviation, deliberate: Dart long-press
// Inactive large-identity path retained:                             // opens the vault directly, but no delivered
// Inactive large-identity path retained:                             // Kotlin slice carries a destination-side auth
// Inactive large-identity path retained:                             // gate, so every entry path goes through the
// Inactive large-identity path retained:                             // fail-closed seam until the shared gate lands.
// Inactive large-identity path retained:                             onIdentityLongPress = { controller.emit(id, HeaderEvent(HeaderAction.IdentityLongPress)) },
// Inactive large-identity path retained:                             onOpenOverflowMenu = { controller.emit(id, HeaderEvent(HeaderAction.OpenOverflowMenu)) },
// Inactive large-identity path retained:                             onSnack = { value -> controller.emit(id, HeaderEvent(HeaderAction.ShowNotice, text = value)) },
// Inactive large-identity path retained:                             headerExtras = null,
// Inactive large-identity path retained:                             rootCompactHeader = true,
// Inactive large-identity path retained:                             renderActions = false,
// Inactive large-identity path retained:                             identityDestinationId = id,
// Inactive large-identity path retained:                             identityEnabled = entry.receiverReady && (id == "chats" || id == "groups"),
// Inactive large-identity path retained:                         )
// Inactive large-identity path retained:         }
        HeaderRegion.Identity -> Unit // Compact root identity is rendered in Actions exactly once.
        HeaderRegion.Actions -> {
            // Header selection-mode crossfade only. Tab content slides in HorizontalPager;
            // shell chrome is not part of that content transition.
            val headerReduceMotion = com.example.chat.ui.kit.rememberChatyReduceMotion() ||
                com.example.chat.ui.kit.LocalReferenceLayoutPresentation.current.animationsDisabled
            val headerSwapMs = if (headerReduceMotion) 0 else com.example.chat.ui.kit.ChatyMotionTokens.BASE_MS
            @Composable fun Actions(selectionMode: Boolean) {
                if (selectionMode) {
                    SelectionHomeAppBar(
                        theme = theme,
                        selectedCount = entry.selectedIds.size,
                        visibleSelectedCount = model.visibleSelectedCount,
                        validActions = validBulkActions,
                        anyUnpinned = model.anyUnpinned,
                        anyUnmuted = model.anyUnmuted,
                        anyUnlocked = model.anyUnlocked,
                        onClearSelection = { controller.emit(id, HeaderEvent(HeaderAction.ClearSelection)) },
                        onTogglePin = { controller.emit(id, HeaderEvent(HeaderAction.TogglePin)) },
                        onDelete = { controller.emit(id, HeaderEvent(HeaderAction.Delete)) },
                        onToggleMute = { controller.emit(id, HeaderEvent(HeaderAction.ToggleMute)) },
                        onToggleArchive = { controller.emit(id, HeaderEvent(HeaderAction.ToggleArchive)) },
                        onToggleLock = { controller.emit(id, HeaderEvent(HeaderAction.ToggleLock)) },
                        onMarkUnread = { controller.emit(id, HeaderEvent(HeaderAction.MarkUnread)) },
                        onMarkRead = { controller.emit(id, HeaderEvent(HeaderAction.MarkRead)) },
                        // R4-11: the target set is the rendered eligible chat entries
                        // (section headers, the archive affordance and hidden chats
                        // excluded). The Bars slice owns showing the exact target count.
                        onSelectAll = { controller.emit(id, HeaderEvent(HeaderAction.SelectAll)) },
                        // R4-12: invert within the shown eligible set only; off-scope
                        // selections are kept out of the operation.
                        onInvert = { controller.emit(id, HeaderEvent(HeaderAction.Invert)) },
                        // R4-14/R4-19: read-only inspection before any bulk effect.
                        onInspect = { controller.emit(id, HeaderEvent(HeaderAction.Inspect)) },
                        // R4-20: named exit; clears selection only - search, folder and
                        // quick-view settings are untouched (Back/Escape equivalent above).
                        onExitSelection = { controller.emit(id, HeaderEvent(HeaderAction.ExitSelection)) },
                        onSplitChat = { controller.emit(id, HeaderEvent(HeaderAction.SplitChat)) },
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
                            nav = eventNav,
                            onToggleSearch = { controller.emit(id, HeaderEvent(HeaderAction.ToggleSearch)) },
                            onToggleGhost = { value -> controller.emit(id, HeaderEvent(HeaderAction.SetGhostMode, flag = value)) },
                            // _handleTopLeftIdentityTap: with Locked Chats
                            // configured the vault stays behind the auth gate
                            // (fail-closed seam; owner wires the real gate),
                            // otherwise the guided setup modal explains it.
                            onIdentityTap = { controller.emit(id, HeaderEvent(HeaderAction.IdentityTap)) },
                            // Source deviation, deliberate: Dart long-press
                            // opens the vault directly, but no delivered
                            // Kotlin slice carries a destination-side auth
                            // gate, so every entry path goes through the
                            // fail-closed seam until the shared gate lands.
                            onIdentityLongPress = { controller.emit(id, HeaderEvent(HeaderAction.IdentityLongPress)) },
                            onOpenOverflowMenu = { controller.emit(id, HeaderEvent(HeaderAction.OpenOverflowMenu)) },
                            onSnack = { value -> controller.emit(id, HeaderEvent(HeaderAction.ShowNotice, text = value)) },
                            headerExtras = null,
                            rootCompactHeader = true,
                            renderIdentity = false,
                            onSearchIconPosition = { entry.searchIconCenter = it },
                        )
                        Box(Modifier.align(Alignment.BottomEnd)) {
                            HomeOverflowMenu(
                                onCheckHomeAppearance = { controller.emit(id, HeaderEvent(HeaderAction.CheckHomeAppearance)) },
                                expanded = overflowOpen,
                                onDismiss = { controller.emit(id, HeaderEvent(HeaderAction.Dismiss)) },
                                onOpenViewOptions = { controller.emit(id, HeaderEvent(HeaderAction.OpenViewOptions)) },
                                onOpenScratchpad = { controller.emit(id, HeaderEvent(HeaderAction.OpenScratchpad)) },
                                onOpenSavedViews = { controller.emit(id, HeaderEvent(HeaderAction.OpenSavedViews)) },
                                onOpenViewHistory = { controller.emit(id, HeaderEvent(HeaderAction.OpenViewHistory)) },
                                // R2-21: discoverable shortcut map (dialog below; Bars slice
                                // adds the menu item via its optional onOpenShortcutHelp param).
                                onOpenShortcutHelp = { controller.emit(id, HeaderEvent(HeaderAction.OpenShortcutHelp)) },
                                theme = theme,
                                nav = eventNav,
                                menuPreferences = homePrefs,
                                onMarkAllAsRead = { controller.emit(id, HeaderEvent(HeaderAction.MarkAllAsRead)) },
                                onMessageANumber = { controller.emit(id, HeaderEvent(HeaderAction.MessageANumber)) },
                                onOpenConnectionDetail = ({ controller.emit(id, HeaderEvent(HeaderAction.OpenConnectionDetail)) }).takeIf { model.hasConnectionDetail },
                                onOpenArchived = { controller.emit(id, HeaderEvent(HeaderAction.OpenArchived)) },
                            )
                        }
                    }
                }
            }
            AnimatedContent(targetState = isSelectionMode, label = "home-header",
                transitionSpec = { fadeIn(tween(headerSwapMs)) togetherWith fadeOut(tween(headerSwapMs)) }) {
                Actions(it)
            }
        }

        HeaderRegion.Banner -> {
            if (requiresSubscription) {
                SubscriptionRequiredBanner(theme, subscriptionMessage) { controller.emit(id, HeaderEvent(HeaderAction.OpenPlan)) }
            }

        }
        HeaderRegion.Search -> {
            if (searchEnabled && searchPlacement in setOf("Below header", "Header action", "Above chat filters")) {
                HomeSearchField(
                    theme = theme,
                    value = searchQuery,
                    onValueChange = { value -> controller.emit(id, HeaderEvent(HeaderAction.ChangeQuery, text = value)) },
                    hint = if (id == "groups") "Search groups…" else model.searchHint,
                    focusRequester = searchFocus,
                    inlineHeader = searchPlacement == "Header action",
                    onClose = if (searchPlacement == "Header action") ({ controller.emit(id, HeaderEvent(HeaderAction.ToggleSearch)) }) else null,
                )
            }

        }
    }
}
