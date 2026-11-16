@file:OptIn(ExperimentalFoundationApi::class)
package com.example.chat.ui.chats.home

import androidx.compose.foundation.horizontalScroll

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MarkChatRead
import androidx.compose.material.icons.outlined.MarkChatUnread
import androidx.compose.material.icons.outlined.PersonAddAlt1
import androidx.compose.material.icons.outlined.PushPin as OutlinedPushPin
import androidx.compose.material.icons.outlined.Reply
import androidx.compose.material.icons.outlined.ScheduleSend
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.rounded.WorkspacePremium
import com.example.chat.ui.kit.ChatyConfirmationDialog as AlertDialog
import com.example.chat.ui.kit.ChatyTextButton
import com.example.chat.ui.theme.LocalAppColors
import com.example.chat.ui.kit.ChatyDropdownMenu as DropdownMenu
import com.example.chat.ui.kit.ChatyDropdownMenuItem as DropdownMenuItem
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.core.theme.argbWithAlpha
import com.example.chat.domain.models.HomePreferences
import com.example.chat.ReleaseFeatures
import androidx.compose.foundation.ExperimentalFoundationApi

/** Identity shown in the home header (mapped by the integrator from whatever
 *  user object the active backend plane exposes). */
data class HomeIdentity(
    val displayName: String = "",
    val username: String = "",
    val about: String = "",
    val avatarInitials: String = "C",
    val avatarColorHex: String? = null,
    val avatarUrl: String? = null,
)

/** Navigation callbacks for destinations that live outside this slice; the
 *  integrator wires each to a real route or an honest WIP stub. */
data class ChatsHomeNav(
    val onOpenProfile: () -> Unit = {},
    val onOpenAccountSwitcher: () -> Unit = {},
    val onOpenSplitChat: ((List<String>) -> Unit)? = null,
    val onOpenSettings: () -> Unit = {},
    val onOpenNotifications: () -> Unit = {},
    val onOpenSecuritySettings: () -> Unit = {},
    val onOpenGlobalSearch: () -> Unit = {},
    val onOpenQr: () -> Unit = {},
    val onOpenLinkedDevices: () -> Unit = {},
    val onOpenNewChat: (groupMode: Boolean, broadcastMode: Boolean) -> Unit = { _, _ -> },
    val onNewCall: () -> Unit = {},
    val onOpenStarredMessages: () -> Unit = {},
    val onOpenMessageManagement: () -> Unit = {},
    val onOpenLockedVault: () -> Unit = {},
    val onOpenCameraEffects: () -> Unit = {},
    val onMessageNumber: (String) -> Unit = {},
    val onWhoCanCallMe: () -> Unit = {},
    val onShareApp: () -> Unit = {},
    val onRestartApp: () -> Unit = {},
    val onToggleBrightness: () -> Unit = {},
    val onSelectPlan: () -> Unit = {},
    /** Rail-hidden Tasks/Updates surface as header overflow entries (user
     *  directive); the flags say the entries show, the callbacks open the real
     *  destination screens. */
    val showTasksEntry: Boolean = false,
    val showUpdatesEntry: Boolean = false,
    val onOpenTasks: () -> Unit = {},
    val onOpenUpdates: () -> Unit = {},
)

/** _ghostModePill: toggles ghost mode with the exact Dart snackbar copy. */
@Composable
fun GhostModePill(
    theme: ThemeConfig,
    isGhost: Boolean,
    onToggle: (Boolean) -> Unit,
    onSnack: (String) -> Unit,
) {
    HomeActionTooltip(label = if (isGhost) "Turn off ghost mode" else "Turn on ghost mode") {
    Row(
        modifier = Modifier
            .semantics { contentDescription = if (isGhost) "Turn off ghost mode" else "Turn on ghost mode" }
            .padding(start = 6.dp)
            .background(
                if (isGhost) Color(0x336366F1) else Color.Transparent,
                RoundedCornerShape(12.dp),
            )
            .border(
                1.dp,
                if (isGhost) Color(0x806366F1) else Color(argbWithAlpha(theme.secondaryTextColor, 0.2)),
                RoundedCornerShape(12.dp),
            )
            .clickable {
                onToggle(!isGhost)
                onSnack(
                    if (!isGhost) {
                        "Ghost mode requested. Check Privacy settings for its protections."
                    } else {
                        "Ghost Mode Deactivated: Normal presence restored"
                    },
                )
            }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HomeGlyphIcon(
            glyph = if (isGhost) HomeGlyph.ghostActive else HomeGlyph.ghostInactive,
            size = 14.dp,
            color = if (isGhost) Color(0xFF818CF8) else Color(theme.secondaryTextColor),
        )
        if (isGhost) {
            Spacer(Modifier.width(5.dp))
            Text(
                "Ghost",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF818CF8),
            )
        }
    }
    }
}

/** _standardAppBar: identity block (tap = locked-chats entry, long-press =
 *  vault), status line, ghost pill, header extras slot, camera / QR / search
 *  / add-account icons, ringed profile avatar (opens Settings, as Dart),
 *  brightness toggle and the canonical overflow button. */
@Composable
fun StandardHomeAppBar(
    theme: ThemeConfig,
    homePrefs: HomePreferences,
    headerStyle: HomeHeaderStyle,
    homeStyle: String,
    title: String,
    identity: HomeIdentity,
    isSearchOpen: Boolean,
    forcedTypeIsSet: Boolean,
    nav: ChatsHomeNav,
    onToggleSearch: () -> Unit,
    onToggleGhost: (Boolean) -> Unit,
    onIdentityTap: () -> Unit,
    onIdentityLongPress: () -> Unit,
    onOpenOverflowMenu: () -> Unit,
    onSnack: (String) -> Unit,
    headerExtras: (@Composable () -> Unit)? = null,
    /** R1-47: opt-in compact identity geometry using the Home-only
     *  ReferenceLayoutMetrics.homeCompact* constants; false keeps the reference
     *  identity block unchanged for existing callers. */
    compactIdentity: Boolean = false,
) {
    val titleFontSize = when (headerStyle) {
        HomeHeaderStyle.compact -> 19.0
        HomeHeaderStyle.prominentIdentity -> 24.0
        HomeHeaderStyle.searchForward -> 19.0
        HomeHeaderStyle.storiesFirst -> 22.0
    }
    val effectiveShowSearch = homePrefs.showSearchBar && !homePrefs.disableSearchBar &&
        homePrefs.searchPlacement.trim().lowercase() == "header action"
    val userAbout = if (identity.about.isNotEmpty()) identity.about else "Available"
    val showStatus = !homePrefs.disableStatusUnderName && userAbout.isNotEmpty()

    val largeDefault = com.example.chat.core.preferences.HomePresentationPolicy.isDefault(homePrefs.homeStyle)
    val headerCollapse = if (largeDefault) com.example.chat.ui.navigation.LocalReferenceHeaderCollapse.current else 0f
    Column(Modifier.fillMaxWidth()) {
    if (largeDefault) {
        // R1-47: the compact treatment only swaps the geometry constants; the
        // collapse animation, font scaling and personalization stay identical.
        val identityHeightDp = if (compactIdentity) com.example.chat.ui.navigation.ReferenceLayoutMetrics.homeCompactIdentityMinHeightDp else com.example.chat.ui.navigation.ReferenceLayoutMetrics.compactIdentityHeightDp
        Column(
            modifier = Modifier.fillMaxWidth()
                .height(with(LocalDensity.current) { (identityHeightDp.toFloat() * theme.fontScale.toFloat()).sp.toDp() } * (1f - headerCollapse))
                .clipToBounds()
                .alpha(1f - headerCollapse)
                .padding(horizontal = 24.dp)
                .combinedClickable(
                    onClick = { if (homePrefs.nameTapOpensLocked) onIdentityTap() },
                    onLongClick = onIdentityLongPress,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                com.example.chat.ui.navigation.NavDestinationIcon(
                    if (forcedTypeIsSet) "groups" else "chats",
                    size = if (compactIdentity) com.example.chat.ui.navigation.ReferenceLayoutMetrics.homeCompactIdentityIconSizeDp else com.example.chat.ui.navigation.ReferenceLayoutMetrics.identityIconSizeDp,
                    tint = Color(theme.primaryTextColor),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    title,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color(theme.primaryTextColor),
                    fontSize = ((if (compactIdentity) com.example.chat.ui.navigation.ReferenceLayoutMetrics.homeCompactIdentityTextSizeSp else com.example.chat.ui.navigation.ReferenceLayoutMetrics.compactIdentityTextSizeSp) * theme.fontScale).sp,
                    fontWeight = FontWeight.W700,
                )
            }
            if (showStatus) Text(
                userAbout,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color(theme.secondaryTextColor),
                fontSize = (14 * theme.fontScale).sp,
            )
        }
    }
    Row(
        modifier = Modifier
            .then(if (largeDefault) Modifier.height(com.example.chat.ui.navigation.ReferenceLayoutMetrics.actionBarHeightDp.dp) else Modifier)
            .padding(start = 16.dp, top = if (largeDefault) 0.dp else 8.dp, end = 4.dp, bottom = if (largeDefault) 0.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .combinedClickable(onClick = { if (homePrefs.nameTapOpensLocked) onIdentityTap() }, onLongClick = onIdentityLongPress)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                if (!largeDefault || headerCollapse > 0f) Text(
                    title,
                    modifier = if (largeDefault) Modifier.alpha(headerCollapse) else Modifier,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (homeStyle == "Classic") Color(theme.accentColor) else Color(theme.primaryTextColor),
                    fontSize = ((if (largeDefault) 20.0 else titleFontSize) * theme.fontScale).sp,
                    fontWeight = if (largeDefault) FontWeight.W700 else FontWeight.W800,
                    letterSpacing = if (headerStyle == HomeHeaderStyle.prominentIdentity) (-0.55).sp else (-0.2).sp,
                )
                if (showStatus && !largeDefault) {
                    Text(
                        userAbout,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color(theme.secondaryTextColor),
                        fontSize = (11 * theme.fontScale).sp,
                        fontWeight = FontWeight.W400,
                    )
                }
            }
            if (homePrefs.showGhostPill) {
                GhostModePill(theme, homePrefs.ghostMode, onToggleGhost, onSnack)
            }
            Spacer(Modifier.width(8.dp))
            headerExtras?.invoke()
        }
        // Keep every configured action reachable on narrow screens and large text.
        val (adaptiveWidthDp, _) = com.example.chat.ui.kit.rememberAdaptiveWindowSize()
        val useCompactActions = adaptiveWidthDp < 600f || LocalDensity.current.fontScale > 1.3f
        if (useCompactActions) {
            CompactHomeHeaderActions(theme, identity, effectiveShowSearch, isSearchOpen, forcedTypeIsSet, nav, onToggleSearch)
        } else {
        Row(
            Modifier.widthIn(max = (adaptiveWidthDp * 0.65f - 48f).coerceAtLeast(0f).dp)
                .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        if (homePrefs.showCameraIcon) {
            HomeActionTooltip(label = "Camera Effects") {
            IconButton(onClick = nav.onOpenCameraEffects) {
                HomeGlyphIcon(glyph = HomeGlyph.camera, size = 20.dp, color = Color(theme.primaryTextColor), semanticLabel = "Camera Effects")
            }
            }
        }
        if (homePrefs.showDesktopIcon) {
            HomeActionTooltip(label = "QR / Scan") {
            IconButton(onClick = nav.onOpenQr) {
                HomeGlyphIcon(glyph = HomeGlyph.qrScan, size = 20.dp, color = Color(theme.primaryTextColor), semanticLabel = "QR / Scan")
            }
            }
        }
        if (effectiveShowSearch) {
            HomeActionTooltip(label = if (isSearchOpen) "Close search" else "Search chats") {
            IconButton(onClick = onToggleSearch) {
                com.example.chat.ui.icons.ChatyIcon(
                    if (isSearchOpen) com.example.chat.ui.icons.ChatyIconId.close else com.example.chat.ui.icons.ChatyIconId.search,
                    semanticLabel = if (isSearchOpen) "Close search" else "Search chats",
                    color = Color(theme.primaryTextColor),
                )
            }
            }
        }
        if (homePrefs.showIconAddAccount) {
            HomeActionTooltip(label = "Account settings") {
            IconButton(onClick = nav.onOpenProfile) {
                Icon(Icons.Outlined.PersonAddAlt1, contentDescription = "Account settings", tint = Color(theme.primaryTextColor))
            }
            }
        }
        // Small profile avatar -> Account Switcher (real photo when uploaded, via
        // the shared ChatyAsyncImage wrapper).
        if (!forcedTypeIsSet) {
            // 48dp touch target and TalkBack semantics around the unchanged ringed 28dp avatar.
            HomeActionTooltip(label = "Account switcher") {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = "Account switcher" }
                    .clickable(
                        onClickLabel = "Open account switcher",
                        role = androidx.compose.ui.semantics.Role.Button,
                        onClick = nav.onOpenAccountSwitcher,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .border(1.8.dp, Color(theme.accentColor), CircleShape)
                        .padding(1.8.dp),
                ) {
                    HomeAvatar(
                        initials = identity.avatarInitials,
                        color = parseAvatarColorHex(identity.avatarColorHex, Color(theme.accentColor)),
                        size = 28f,
                        shape = "circle",
                        url = identity.avatarUrl,
                    )
                }
            }
            }
        }
        if (homePrefs.themesEnabled) {
            HomeActionTooltip(label = "Toggle light / dark") {
            IconButton(onClick = nav.onToggleBrightness) {
                Icon(
                    if (theme.isDark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                    contentDescription = "Toggle light / dark",
                    tint = Color(theme.primaryTextColor),
                )
            }
            }
        }
        } // bounded, scrollable optional action lane
        } // wide header retains its configured actions
        // Primary overflow stays visible and anchored, never hidden by horizontal scrolling.
        HomeActionTooltip(label = "More options") {
        IconButton(onClick = onOpenOverflowMenu, modifier = Modifier.size(48.dp)) {
            com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.moreOptions, semanticLabel = "More options", color = Color(theme.primaryTextColor))
        }
        }
    }
    } // shared large/default header container
}

/** Search and account stay visible; secondary actions have an explicit menu,
 *  rather than relying on horizontal scrolling on narrow or large-text layouts. */
@Composable
private fun CompactHomeHeaderActions(
    theme: ThemeConfig,
    identity: HomeIdentity,
    showSearch: Boolean,
    isSearchOpen: Boolean,
    forcedTypeIsSet: Boolean,
    nav: ChatsHomeNav,
    onToggleSearch: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (showSearch) {
            HomeActionTooltip(label = if (isSearchOpen) "Close search" else "Search chats") {
            IconButton(onClick = onToggleSearch, modifier = Modifier.size(48.dp)) {
                com.example.chat.ui.icons.ChatyIcon(
                    if (isSearchOpen) com.example.chat.ui.icons.ChatyIconId.close else com.example.chat.ui.icons.ChatyIconId.search,
                    semanticLabel = if (isSearchOpen) "Close search" else "Search chats",
                    color = Color(theme.primaryTextColor),
                )
            }
            }
        }
        if (!forcedTypeIsSet) {
            HomeActionTooltip(label = "Account switcher") {
            Box(
                modifier = Modifier.size(48.dp)
                    .semantics { contentDescription = "Account switcher" }
                    .clickable(onClickLabel = "Open account switcher", role = Role.Button, onClick = nav.onOpenAccountSwitcher),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.border(1.8.dp, Color(theme.accentColor), CircleShape).padding(1.8.dp)) {
                    HomeAvatar(
                        initials = identity.avatarInitials,
                        color = parseAvatarColorHex(identity.avatarColorHex, Color(theme.accentColor)),
                        size = 28f,
                        shape = "circle",
                        url = identity.avatarUrl,
                    )
                }
            }
            }
        }
    }
}

/** _openHomeThreeDotMenu: anchored dropdown with the Dart item set and
 *  order. */
@Composable
fun HomeOverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    theme: ThemeConfig,
    nav: ChatsHomeNav,
    onMarkAllAsRead: () -> Unit,
    onMessageANumber: () -> Unit,
    onOpenArchived: () -> Unit = {},
    onOpenViewOptions:()->Unit = {},
    onOpenScratchpad:()->Unit = {},
    onOpenConnectionDetail: (() -> Unit)? = null,
    menuPreferences: HomePreferences = HomePreferences(),
    onOpenShortcutHelp: (() -> Unit)? = null,
    onOpenCommandPalette: (() -> Unit)? = null,
    onOpenSavedViews: (() -> Unit)? = null,
    onOpenViewHistory: (() -> Unit)? = null,
) {
    val (adaptiveWidthDp, _) = com.example.chat.ui.kit.rememberAdaptiveWindowSize()
    val useCompactActions = adaptiveWidthDp < 600f || LocalDensity.current.fontScale > 1.3f
    var catchUpGuideOpen by remember { mutableStateOf(false) }
    if (catchUpGuideOpen) {
        HomeCatchUpGuide(theme = theme, onDismiss = { catchUpGuideOpen = false })
    }
    // Linked Devices / Scheduler / Auto Reply are deferred to the next release (ReleaseFeatures).
    val items: List<Triple<@Composable () -> Unit, String, () -> Unit>> = listOfNotNull(
        if (ReleaseFeatures.MESSAGE_AUTOMATION) Triple({ com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.scheduleSend, color = Color(theme.secondaryTextColor), size = 20.dp) }, "Message Scheduler") { nav.onOpenMessageManagement() } else null,
        if (ReleaseFeatures.MESSAGE_AUTOMATION) Triple({ com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.reply, color = Color(theme.secondaryTextColor), size = 20.dp) }, "Auto Reply") { nav.onOpenMessageManagement() } else null,
        if (menuPreferences.menuShowNewActions) Triple({ Icon(Icons.Outlined.GroupAdd, null, tint = Color(theme.secondaryTextColor), modifier = Modifier.size(20.dp)) }, "New Group") { nav.onOpenNewChat(true, false) } else null,
        if (menuPreferences.menuShowNewActions) Triple({ com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.chats, color = Color(theme.secondaryTextColor), size = 20.dp) }, "New Chat") { nav.onOpenNewChat(false, false) } else null,
        if (menuPreferences.menuShowNewActions) Triple({ com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.callAudio, color = Color(theme.secondaryTextColor), size = 20.dp) }, "New Call") { nav.onNewCall() } else null,
        if (nav.showUpdatesEntry) Triple({ com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.updates, color = Color(theme.secondaryTextColor), size = 20.dp) }, "Updates") { nav.onOpenUpdates() } else null,
        if (nav.showTasksEntry) Triple({ com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.tasks, color = Color(theme.secondaryTextColor), size = 20.dp) }, "Tasks") { nav.onOpenTasks() } else null,
        if (ReleaseFeatures.LINKED_DEVICES) Triple({ com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.desktop, color = Color(theme.secondaryTextColor), size = 20.dp) }, "Linked Devices") { nav.onOpenLinkedDevices() } else null,
        if (menuPreferences.menuShowSavedChats) Triple({ com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.star, color = Color(theme.secondaryTextColor), size = 20.dp) }, "Starred Messages") { nav.onOpenStarredMessages() } else null,
        if (menuPreferences.menuShowSavedChats) Triple({ com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.archive, color = Color(theme.secondaryTextColor), size = 20.dp) }, "Archived Chats") { onOpenArchived() } else null,
        if (menuPreferences.menuShowMarkRead) Triple({ com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.markRead, color = Color(theme.secondaryTextColor), size = 20.dp) }, "Mark All as Read") { onMarkAllAsRead() } else null,
        Triple({ com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.callAudio, color = Color(theme.secondaryTextColor), size = 20.dp) }, "Who can call me?") { nav.onWhoCanCallMe() },
        if (menuPreferences.menuShowShare) Triple({ com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.share, color = Color(theme.secondaryTextColor), size = 20.dp) }, "Share Chaty") { nav.onShareApp() } else null,
        if (menuPreferences.menuShowMessageNumber) Triple({ com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.phone, color = Color(theme.secondaryTextColor), size = 20.dp) }, "Message a number", onMessageANumber) else null,
        onOpenConnectionDetail?.let { openDetails -> Triple({ com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.signal, color = Color(theme.secondaryTextColor), size = 20.dp) }, "Connection health", openDetails) },
    )
    DropdownMenu(shape = com.example.chat.ui.kit.ChatyModalShape,
        expanded = expanded,
        onDismissRequest = onDismiss,
        rememberKey = "home-overflow-menu",
        containerColor = Color(theme.cardColor),
        border = androidx.compose.foundation.BorderStroke(1.1.dp, Color(argbWithAlpha(theme.secondaryTextColor, 0.2))),
    ) {
        // Match the compact header policy; all secondary actions remain in the
        // existing, always-visible Home menu instead of a second overflow button.
        if (useCompactActions) {
            if (menuPreferences.showCameraIcon) {
                DropdownMenuItem(text = { Text("Camera Effects") }, onClick = { onDismiss(); nav.onOpenCameraEffects() })
            }
            if (menuPreferences.showDesktopIcon) {
                DropdownMenuItem(text = { Text("QR / Scan") }, onClick = { onDismiss(); nav.onOpenQr() })
            }
            if (menuPreferences.showIconAddAccount) {
                DropdownMenuItem(text = { Text("Account settings") }, onClick = { onDismiss(); nav.onOpenProfile() })
            }
            if (menuPreferences.themesEnabled) {
                DropdownMenuItem(text = { Text("Toggle light / dark") }, onClick = { onDismiss(); nav.onToggleBrightness() })
            }
        }
        DropdownMenuItem(text = { Text("How to catch up") }, onClick = { onDismiss(); catchUpGuideOpen = true })
        DropdownMenuItem(text={Text("Home view options")},onClick={onDismiss();onOpenViewOptions()})
        DropdownMenuItem(text={Text("Temporary scratchpad")},onClick={onDismiss();onOpenScratchpad()})
        // S13: session saved views and the view back-history appear only when the
        // Home owner supplies the routes; unwired callers keep the current menu.
        onOpenSavedViews?.let { openSavedViews ->
            DropdownMenuItem(
                text = { Text("Saved Home views") },
                onClick = { onDismiss(); openSavedViews() },
            )
        }
        onOpenViewHistory?.let { openViewHistory ->
            DropdownMenuItem(
                text = { Text("Recent Home views") },
                onClick = { onDismiss(); openViewHistory() },
            )
        }
        // No inert entries: the active Home owner supplies only supported routes.
        onOpenShortcutHelp?.let { openHelp ->
            DropdownMenuItem(
                text = { Text("Keyboard shortcuts") },
                onClick = { onDismiss(); openHelp() },
            )
        }
        onOpenCommandPalette?.let { openPalette ->
            DropdownMenuItem(
                text = { Text("Command palette") },
                onClick = { onDismiss(); openPalette() },
            )
        }
        items.forEach { (icon, label, action) ->
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        icon()
                        Spacer(Modifier.width(12.dp))
                        Text(label, color = Color(theme.primaryTextColor), fontSize = 15.5.sp, fontWeight = FontWeight.Normal)
                    }
                },
                onClick = {
                    onDismiss()
                    action()
                },
            )
        }
    }
}

/** Examples are explanatory text only: no conversations, searches or messages
 *  are created, retained or read to show this dismissible guide. */
@Composable
private fun HomeCatchUpGuide(theme: ThemeConfig, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How to catch up", fontWeight = FontWeight.W700) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Examples only, not your live conversations.", color = Color(theme.secondaryTextColor))
                CatchUpGuideState("Unread", "Example: a badge with 2 means two unread messages. Use the Unread filter to find chats with unread messages.", theme)
                CatchUpGuideState("Pinned", "Example: pin a chat you want to find quickly. Pinned chats are grouped ahead of other active chats.", theme)
                CatchUpGuideState("Archived", "Example: archive a conversation to move it out of the main list. Open Archived Chats from the Home menu when that entry is enabled.", theme)
                CatchUpGuideState("Draft", "Example: Draft: See you soon is unfinished text, not a sent message. Open the chat to review it before sending.", theme)
            }
        },
        confirmButton = { ChatyTextButton(onClick = onDismiss, label = "Got it") },
    )
}

@Composable
private fun CatchUpGuideState(title: String, example: String, theme: ThemeConfig) {
    Column {
        Text(title, fontWeight = FontWeight.W700, color = Color(theme.primaryTextColor))
        Spacer(Modifier.height(4.dp))
        Text(example, color = Color(theme.secondaryTextColor))
    }
}

/** _selectionAppBar: count, pin, delete, mute, archive, overflow
 *  (lock/unlock, mark unread/read, select all). */
@Composable
fun SelectionHomeAppBar(
    theme: ThemeConfig,
    selectedCount: Int,
    anyUnpinned: Boolean,
    anyUnmuted: Boolean,
    anyUnlocked: Boolean,
    onClearSelection: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleArchive: () -> Unit,
    onToggleLock: () -> Unit,
    onMarkUnread: () -> Unit,
    onMarkRead: () -> Unit,
    onSelectAll: () -> Unit,
    onSplitChat: (() -> Unit)? = null,
    onInvert: (() -> Unit)? = null,
    onInspect: (() -> Unit)? = null,
    onExitSelection: (() -> Unit)? = null,
    /** Exact selected ∩ rendered-scope count from the owner (R4-11). Null keeps the
     *  legacy selectedCount display for unwired callers. */
    visibleSelectedCount: Int? = null,
    /** HomeSelectionPolicy.validBulkActions snapshot for the current selection. Null
     *  keeps every action enabled; a supplied set disables off-scope directions and
     *  explains the mixed state instead of allowing a tap that does nothing. */
    validActions: Set<HomeBulkAction>? = null,
) {
    var overflowOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(theme.cardColor))
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Keep a single exit control. The owner must clear selection only.
        if (onExitSelection != null) {
            HomeActionTooltip(label = "Close selection") {
                IconButton(onClick = onExitSelection) {
                    com.example.chat.ui.icons.ChatyIcon(
                        com.example.chat.ui.icons.ChatyIconId.close,
                        semanticLabel = "Close selection",
                        color = Color(theme.primaryTextColor),
                    )
                }
            }
        } else {
        HomeActionTooltip(label = "Close selection") {
        IconButton(onClick = onClearSelection) {
            com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.close, semanticLabel = "Clear selection", color = Color(theme.primaryTextColor))
        }
        }
        }
        // Exact Select-visible count: only rendered, in-scope selections are counted
        // once the owner wires visibleSelectedCount; legacy callers keep selectedCount.
        val exactSelectedCount = visibleSelectedCount ?: selectedCount
        Text(
            exactSelectedCount.toString(),
            modifier = Modifier.semantics { contentDescription = "$exactSelectedCount selected" },
            color = Color(theme.primaryTextColor),
            fontSize = 19.sp,
            fontWeight = FontWeight.W800,
        )
        Spacer(Modifier.weight(1f))
        if (onSplitChat != null && selectedCount in 1..4) {
            HomeActionTooltip(label = "Split chat") {
            IconButton(onClick = onSplitChat) {
                com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.chats, semanticLabel = "Split chat", color = Color(theme.accentColor))
            }
            }
        }
        val pinAction = if (anyUnpinned) HomeBulkAction.PIN else HomeBulkAction.UNPIN
        val pinLabel = if (anyUnpinned) "Pin" else "Unpin"
        val pinEnabled = validActions == null || pinAction in validActions
        val pinReason = if (pinEnabled) null else homeBulkDisabledReason(pinAction)
        HomeActionTooltip(label = pinReason?.let { "$pinLabel unavailable. $it" } ?: pinLabel) {
        IconButton(onClick = onTogglePin, enabled = pinEnabled) {
            com.example.chat.ui.icons.ChatyIcon(
                if (anyUnpinned) com.example.chat.ui.icons.ChatyIconId.pin else com.example.chat.ui.icons.ChatyIconId.unpin,
                semanticLabel = pinReason?.let { "$pinLabel, disabled. $it" } ?: pinLabel,
                color = Color(theme.primaryTextColor),
            )
        }
        }
        val deleteEnabled = validActions == null || HomeBulkAction.DELETE in validActions
        val deleteReason = if (deleteEnabled) null else homeBulkDisabledReason(HomeBulkAction.DELETE)
        HomeActionTooltip(label = deleteReason?.let { "Delete unavailable. $it" } ?: "Delete") {
        IconButton(onClick = onDelete, enabled = deleteEnabled) {
            com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.delete, semanticLabel = deleteReason?.let { "Delete, disabled. $it" } ?: "Delete", color = Color(theme.primaryTextColor))
        }
        }
        val muteAction = if (anyUnmuted) HomeBulkAction.MUTE else HomeBulkAction.UNMUTE
        val muteLabel = if (anyUnmuted) "Mute" else "Unmute"
        val muteEnabled = validActions == null || muteAction in validActions
        val muteReason = if (muteEnabled) null else homeBulkDisabledReason(muteAction)
        HomeActionTooltip(label = muteReason?.let { "$muteLabel unavailable. $it" } ?: muteLabel) {
        IconButton(onClick = onToggleMute, enabled = muteEnabled) {
            com.example.chat.ui.icons.ChatyIcon(
                if (anyUnmuted) com.example.chat.ui.icons.ChatyIconId.mute else com.example.chat.ui.icons.ChatyIconId.unmute,
                semanticLabel = muteReason?.let { "$muteLabel, disabled. $it" } ?: muteLabel,
                color = Color(theme.primaryTextColor),
            )
        }
        }
        // The caller chooses ARCHIVE vs UNARCHIVE from its own snapshot; the bar only
        // knows the selection is mixed when neither direction is offered.
        val archiveEnabled = validActions == null || HomeBulkAction.ARCHIVE in validActions || HomeBulkAction.UNARCHIVE in validActions
        val archiveReason = if (archiveEnabled) null else homeBulkDisabledReason(HomeBulkAction.ARCHIVE)
        HomeActionTooltip(label = archiveReason?.let { "Archive unavailable. $it" } ?: "Archive") {
        IconButton(onClick = onToggleArchive, enabled = archiveEnabled) {
            com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.archive, semanticLabel = archiveReason?.let { "Archive, disabled. $it" } ?: "Archive", color = Color(theme.primaryTextColor))
        }
        }
        Box {
            HomeActionTooltip(label = "More actions") {
            IconButton(onClick = { overflowOpen = true }) {
                com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.moreOptions, semanticLabel = "More actions", color = Color(theme.primaryTextColor))
            }
            }
            DropdownMenu(shape = com.example.chat.ui.kit.ChatyModalShape, expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                SelectionMenuItem(
                    theme = theme,
                    icon = if (anyUnlocked) com.example.chat.ui.icons.ChatyIconId.lock else com.example.chat.ui.icons.ChatyIconId.unlock,
                    label = if (anyUnlocked) "Lock chat" else "Unlock chat",
                ) { overflowOpen = false; onToggleLock() }
                val markUnreadReason = if (validActions == null || HomeBulkAction.MARK_UNREAD in validActions) null else homeBulkDisabledReason(HomeBulkAction.MARK_UNREAD)
                SelectionMenuItem(theme, com.example.chat.ui.icons.ChatyIconId.markUnread, "Mark as unread",
                    enabled = markUnreadReason == null, disabledReason = markUnreadReason) {
                    overflowOpen = false; onMarkUnread()
                }
                val markReadReason = if (validActions == null || HomeBulkAction.MARK_READ in validActions) null else homeBulkDisabledReason(HomeBulkAction.MARK_READ)
                SelectionMenuItem(theme, com.example.chat.ui.icons.ChatyIconId.markRead, "Mark as read",
                    enabled = markReadReason == null, disabledReason = markReadReason) {
                    overflowOpen = false; onMarkRead()
                }
                onInvert?.let { invert ->
                    SelectionMenuItem(theme, com.example.chat.ui.icons.ChatyIconId.selectAll, "Invert selection") {
                        overflowOpen = false; invert()
                    }
                }
                onInspect?.let { inspect ->
                    SelectionMenuItem(theme, com.example.chat.ui.icons.ChatyIconId.chats, "Inspect selected chats ($exactSelectedCount)") {
                        overflowOpen = false; inspect()
                    }
                }
                SelectionMenuItem(theme, com.example.chat.ui.icons.ChatyIconId.selectAll, "Select all") {
                    overflowOpen = false; onSelectAll()
                }
            }
        }
    }
}

@Composable
private fun SelectionMenuItem(
    theme: ThemeConfig,
    icon: com.example.chat.ui.icons.ChatyIconId,
    label: String,
    enabled: Boolean = true,
    disabledReason: String? = null,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        enabled = enabled,
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.example.chat.ui.icons.ChatyIcon(icon)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(label)
                    if (disabledReason != null) {
                        Text(disabledReason, fontSize = 12.sp, color = Color(theme.secondaryTextColor))
                    }
                }
            }
        },
        onClick = onClick,
    )
}

/** Mixed-state explanations for selection directions that HomeSelectionPolicy does
 *  not offer for the current snapshot. No counts, titles or chat content here. */
private fun homeBulkDisabledReason(action: HomeBulkAction): String = when (action) {
    HomeBulkAction.PIN, HomeBulkAction.UNPIN ->
        "The selection mixes pinned and unpinned chats."
    HomeBulkAction.MUTE, HomeBulkAction.UNMUTE ->
        "The selection mixes muted and unmuted chats."
    HomeBulkAction.ARCHIVE, HomeBulkAction.UNARCHIVE ->
        "The selection mixes archived and active chats."
    HomeBulkAction.MARK_READ, HomeBulkAction.MARK_UNREAD ->
        "The selection mixes read and unread chats."
    HomeBulkAction.DELETE ->
        "The current selection cannot be deleted."
}

/** _homeSearchField: filled card-colored field, rounded by the theme corner
 *  radius, clear button once non-empty. */
@Composable
fun HomeSearchField(
    theme: ThemeConfig,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    focusRequester: FocusRequester,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .padding(start = 16.dp, top = 2.dp, end = 16.dp, bottom = 8.dp)
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(28.dp), clip = false)
            .focusRequester(focusRequester),
        textStyle = androidx.compose.ui.text.TextStyle(color = Color(theme.primaryTextColor)),
        placeholder = { Text(hint, color = Color(theme.secondaryTextColor)) },
        leadingIcon = {
            com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.search, semanticLabel = null, color = Color(theme.secondaryTextColor))
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                HomeActionTooltip(label = "Clear search") {
                IconButton(onClick = { onValueChange("") }) {
                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.close, semanticLabel = "Clear search")
                }
                }
            }
        },
        label={Text("Search chats")},
        keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(imeAction=androidx.compose.ui.text.input.ImeAction.Search),
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color(theme.cardColor),
            unfocusedContainerColor = Color(theme.cardColor),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = Color(theme.accentColor),
        ),
    )
}

/** Filter chip row (build(): tabBubbleStyle corner mapping, selected chip in
 *  accent at 18% with a 35% border, w700/w500 label weights). */
@Composable
fun HomeFilterChips(
    theme: ThemeConfig,
    tabBubbleStyle: String,
    filters: List<String>,
    selectedFilter: String,
    onSelect: (String) -> Unit,
) {
    val chipRadius = when (tabBubbleStyle) {
        "Capsule Pill" -> 20
        "Glassmorphism Glow" -> 14
        "Soft Rounded Pill" -> 10
        "Outlined Bubble" -> 18
        "Segmented Tab" -> 6
        else -> 20
    }
    val segmented = tabBubbleStyle == "Segmented Tab"
    val glow = tabBubbleStyle == "Glassmorphism Glow"
    val outlined = tabBubbleStyle == "Outlined Bubble"
    val soft = tabBubbleStyle == "Soft Rounded Pill"
    val accent = Color(theme.accentColor)
    // MOTION: one policy read for the strip; 0 ms when the system disables animations.
    val chipReducedMotion = com.example.chat.ui.kit.rememberChatyReduceMotion()
    LazyRow(
        modifier=Modifier.selectableGroup().semantics {contentDescription="Chat filters"},
        contentPadding = PaddingValues(start = 16.dp, top = 3.dp, end = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(if (segmented) 0.dp else 8.dp),
    ) {
        itemsIndexed(filters) { index, filter ->
            val selected = selectedFilter == filter
            // Segmented Tab joins the chips into one strip: only the ends are rounded.
            val shape = if (segmented) {
                val end = chipRadius.dp
                RoundedCornerShape(
                    topStart = if (index == 0) end else 0.dp, bottomStart = if (index == 0) end else 0.dp,
                    topEnd = if (index == filters.lastIndex) end else 0.dp,
                    bottomEnd = if (index == filters.lastIndex) end else 0.dp,
                )
            } else RoundedCornerShape(chipRadius.dp)
            val fill = when {
                outlined -> Color.Transparent
                glow && selected -> Color(argbWithAlpha(theme.accentColor, 0.22))
                glow -> Color(argbWithAlpha(theme.cardColor, 0.72))
                soft && selected -> Color(argbWithAlpha(theme.accentColor, 0.14))
                selected -> Color(argbWithAlpha(theme.accentColor, 0.18))
                else -> Color(theme.cardColor)
            }
            val borderColor = when {
                outlined && selected -> accent
                outlined -> Color(argbWithAlpha(theme.secondaryTextColor, 0.45))
                glow && selected -> Color(argbWithAlpha(theme.accentColor, 0.6))
                glow -> Color(argbWithAlpha(theme.accentColor, 0.18))
                soft -> Color.Transparent
                selected -> Color(argbWithAlpha(theme.accentColor, 0.35))
                else -> Color(theme.surfaceColor)
            }
            val borderWidth = if (outlined) 1.5.dp else 1.dp
            // MOTION (presentational only): ease fill/border color on selection change.
            val chipFade = androidx.compose.animation.core.tween<Color>(durationMillis = if (chipReducedMotion) 0 else 160)
            val animatedFill by androidx.compose.animation.animateColorAsState(fill, chipFade, label = "homeChipFill")
            val animatedBorder by androidx.compose.animation.animateColorAsState(borderColor, chipFade, label = "homeChipBorder")
            Box(
                modifier = Modifier
                    .then(
                        if (glow && selected) {
                            Modifier.shadow(10.dp, shape, ambientColor = accent.copy(alpha = 0.55f), spotColor = accent.copy(alpha = 0.55f))
                        } else Modifier,
                    )
                    .background(animatedFill, shape)
                    .border(borderWidth, animatedBorder, shape)
                    .heightIn(min=48.dp)
                    .selectable(selected=selected,role=Role.Tab,onClick={onSelect(filter)})
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Text(
                    filter,
                    color = if (selected) accent else Color(theme.secondaryTextColor),
                    fontWeight = if (selected) FontWeight.W700 else FontWeight.W500,
                    // R4: capsule chip labels scale with the system font up to 1.3x, then clamp.
                    fontSize = 13.sp,
                )
            }
        }
    }
}

/** _buildSubscriptionRequiredBanner: amber activation banner with a
 *  Select Plan button. */
@Composable
fun SubscriptionRequiredBanner(
    theme: ThemeConfig,
    message: String?,
    onSelectPlan: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(Color(0x26F59E0B), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0x66F59E0B), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.WorkspacePremium,
            contentDescription = null,
            tint = Color(0xFFD97706),
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Workspace Activation Required",
                fontSize = 13.sp,
                fontWeight = FontWeight.W700,
                color = Color(0xFFB45309),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                message ?: "Please subscribe to a plan to start messaging.",
                fontSize = 12.sp,
                color = Color(theme.secondaryTextColor),
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .background(Color(0xFFD97706), RoundedCornerShape(8.dp))
                .clickable(onClick = onSelectPlan)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text("Select Plan", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.W700)
        }
    }
}

/** ChatyConfirmDialog port for destructive confirms. */
@Composable
fun HomeConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.W700) },
        text = { Text(message) },
        confirmButton = {
            ChatyTextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                label = confirmLabel,
                color = LocalAppColors.current.error,
                fontWeight = FontWeight.W700,
            )
        },
        dismissButton = {
            ChatyTextButton(onClick = onDismiss, label = "Cancel")
        },
    )
}

/** _EmptyChats / ChatyEmptyState port. */
@Composable
fun EmptyChatsState(
    theme: ThemeConfig,
    isGroups: Boolean,
    onSearch: () -> Unit,
    filtered:Boolean=false,
    archivedOnly:Boolean=false,
    onOpenArchive:()->Unit={},
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            if (isGroups) Icons.Outlined.Groups else Icons.Outlined.ChatBubbleOutline,
            contentDescription = null,
            tint = Color(theme.accentColor),
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            if(archivedOnly) "Your chats are archived" else if(filtered) "No matching chats" else if (isGroups) "No groups yet" else "No conversations yet",
            color = Color(theme.primaryTextColor),
            fontSize = 17.sp,
            fontWeight = FontWeight.W700,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if(archivedOnly) "Open Archived to see your conversations."
            else if(filtered) "Try a different search or clear the current filters."
            else if (isGroups) {
                "Create a group and add people to start a shared conversation."
            } else {
                "Find a user by @username or scan their Chaty QR code to start a conversation."
            },
            color = Color(theme.secondaryTextColor),
            fontSize = 13.5.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Box(
            modifier = Modifier
                .background(Color(theme.accentColor), RoundedCornerShape(999.dp))
                .heightIn(min=48.dp)
                .clickable(onClick = if(archivedOnly) onOpenArchive else onSearch)
                .padding(horizontal = 18.dp, vertical = 9.dp),
        ) {
            Text(
                if(archivedOnly) "Open archive" else if(filtered) "Clear filters" else "Find people",
                color = Color(theme.onAccentColor),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.W700,
            )
        }
    }
}


/** The guided Locked Chats setup modal from _handleTopLeftIdentityTap's
 *  else-branch (no credential + no locked chats yet): explains the feature
 *  and routes to Privacy & Security. Exact Dart copy and step list. */
@Composable
fun LockedChatsSetupDialog(
    theme: ThemeConfig,
    onClose: () -> Unit,
    onConfigureSecurity: () -> Unit,
) {
    AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
        onDismissRequest = onClose,
        title = {
            Column {
                Text("Locked Chats", fontWeight = FontWeight.W700)
                Text(
                    "Secure conversations with biometric, PIN, or password",
                    fontSize = 13.sp,
                    color = Color(theme.secondaryTextColor),
                )
            }
        },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(Color(argbWithAlpha(theme.accentColor, 0.15)), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                    ) {
                        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.lock,
                            semanticLabel = null,
                            color = Color(theme.accentColor),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Lock individual chats and keep them hidden from the main list.",
                        fontSize = 13.5.sp,
                        color = Color(theme.primaryTextColor),
                        fontWeight = FontWeight.W500,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "How to set up Locked Chats:",
                    fontWeight = FontWeight.W700,
                    fontSize = 13.5.sp,
                    color = Color(theme.accentColor),
                )
                Spacer(Modifier.height(10.dp))
                LockedSetupStep("1", "Set up your Security Credential",
                    "Choose a PIN, Pattern, or Password in Privacy & Security settings.", theme)
                Spacer(Modifier.height(8.dp))
                LockedSetupStep("2", "Lock conversations",
                    "Select any chat in your list and choose \"Lock chat\".", theme)
                Spacer(Modifier.height(8.dp))
                LockedSetupStep("3", "Open your vault anytime",
                    "Tap your name or the Chaty header at the top-left to authenticate and view locked chats.", theme)
            }
        },
        confirmButton = {
            ChatyTextButton(
                onClick = {
                    onClose()
                    onConfigureSecurity()
                },
                label = "Configure Security",
                fontWeight = FontWeight.W700,
            )
        },
        dismissButton = {
            ChatyTextButton(onClick = onClose, label = "Close")
        },
    )
}

@Composable
private fun LockedSetupStep(number: String, title: String, description: String, theme: ThemeConfig) {
    Row {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(Color(argbWithAlpha(theme.accentColor, 0.12)), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(number, fontSize = 12.sp, fontWeight = FontWeight.W700, color = Color(theme.accentColor))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, fontWeight = FontWeight.W600, fontSize = 13.sp, color = Color(theme.primaryTextColor))
            Spacer(Modifier.height(1.dp))
            Text(description, fontSize = 12.sp, color = Color(theme.secondaryTextColor))
        }
    }
}

/** Action labels only: never put chat titles, previews or account data here.
 *  Hover/long-press comes from TooltipBox; focus follows the existing control. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun HomeActionTooltip(label: String, content: @Composable () -> Unit) {
    val tooltipState = androidx.compose.material3.rememberTooltipState()
    var hasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(hasFocus) {
        if (hasFocus) tooltipState.show() else tooltipState.dismiss()
    }
    androidx.compose.material3.TooltipBox(
        positionProvider = androidx.compose.material3.TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFF3B3F44),
                shadowElevation = 2.dp,
            ) {
                Text(
                    label,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = Color.White,
                    fontSize = 12.sp,
                )
            }
        },
        state = tooltipState,
        modifier = Modifier.onFocusChanged { hasFocus = it.hasFocus },
    ) {
        content()
    }
}
