package com.example.chat.ui.navigation

import com.example.chat.ui.kit.chatyDepth
import com.example.chat.core.templates.ChatyNavigationDestinationIds
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddIcCall
import androidx.compose.material.icons.rounded.AddTask
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.HistoryToggleOff
import androidx.compose.material.icons.rounded.MarkChatUnread
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.ReplyAll
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.ScheduleSend
import androidx.compose.material.icons.rounded.Settings
import com.example.chat.ui.kit.ChatyActionButton
import com.example.chat.ui.kit.ChatyConfirmationDialog as AlertDialog
import com.example.chat.ui.kit.ChatyTextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.ui.core.theme.ChatyColors
import com.example.chat.ui.updates.CapturedStoryMedia
import kotlinx.coroutines.launch
import androidx.compose.foundation.interaction.collectIsPressedAsState

/** Home/FAB preference flags the Dart shell reads from
 *  ChatyPreferencesController.home (+ privacy.freezeLastSeen). */
data class NavigationFabPrefs(
    val hideFab: Boolean = false,
    val showMetaAiIcon: Boolean = false,
    val fabNormalColor: Long? = null,
    val fabPressedColor: Long? = null,
    val fabIconsColor: Long? = null,
    val hideNewMessageFab: Boolean = false,
    val hideLastSeenFab: Boolean = false,
    val hideCutEditFab: Boolean = false,
    val hidePluginsList: Boolean = false,
    val hideGbwaSettingsFab: Boolean = false,
    val freezeLastSeen: Boolean = false,
)

/** Navigation/action seams for _buildContextualFab. */
class NavigationFabActions(
    val onNewChat: (groupMode: Boolean) -> Unit = {},
    val onCreateTask: () -> Unit = {},
    val onNewCall: () -> Unit = {},
    val onScanQr: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onOpenThemeStudio: () -> Unit = {},
    val onOpenMessageScheduler: () -> Unit = {},
    val onOpenAutoReplyRules: () -> Unit = {},
    val onToggleFreezeLastSeen: (frozen: Boolean) -> Unit = {},
    /** Dart MethodChannel('chaty/launcher_icon').invokeMethod('restartApp'). */
    val onRestartApp: suspend () -> Unit = {},
    /** ChatyCameraCaptureScreen story mode. */
    val captureStory: suspend () -> CapturedStoryMedia? = { null },
    /** StatusService.publishMediaFile equivalent for the story capture. */
    val publishStory: suspend (CapturedStoryMedia) -> Unit = {},
    /** ChatyDataStore.getOrCreateDirectConversation for the direct-number
     *  dialog; returns the conversation id. */
    val openDirectConversation: suspend (target: String) -> String = { "" },
    val onOpenConversation: (String) -> Unit = {},
)

/** Exact port of _buildContextualFab: null when hideFab or the active
 *  destination is 'calls'; per-destination icon/tooltip/action; FAB color
 *  overrides; the chats destination swaps in the speed dial. */
@Composable
fun ContextualNavigationFab(
    activeDestinationId: String,
    colors: ChatyColors,
    modifier: Modifier = Modifier,
    prefs: NavigationFabPrefs = NavigationFabPrefs(),
    actions: NavigationFabActions = NavigationFabActions(),
    onNotify: (String) -> Unit = {},
    /** Optional Home entry point. The owner must resolve commands against the
     * current account when opened/executed, and expose navigation only. */
    onOpenHomeCommandPalette: (() -> Unit)? = null,
    /** Explicit user choice only. The containing shell must also move the FAB
     * slot to the physical bottom-left and apply system/gesture safe insets. */
    leftHandedCompose: Boolean = false,
) {
    if (prefs.hideFab || activeDestinationId == ChatyNavigationDestinationIds.calls) return
    val scope = rememberCoroutineScope()
    var restartConfirm by remember { mutableStateOf(false) }
    var quickActionsOpen by remember { mutableStateOf(false) }
    var directNumberOpen by remember { mutableStateOf(false) }

    val referenceShape = LocalReferenceDefaultPresentation.current && activeDestinationId in setOf(ChatyNavigationDestinationIds.chats, ChatyNavigationDestinationIds.groups)
    val mainFabShape: androidx.compose.ui.graphics.Shape = CircleShape
    val accent = colors.primary
    val effectiveFabColor = prefs.fabNormalColor?.let(::Color) ?: accent
    val effectiveIconColor = prefs.fabIconsColor?.let(::Color) ?: colors.onPrimary

    data class FabSpec(val tooltip: String, val action: () -> Unit)

    val spec = when (activeDestinationId) {
        ChatyNavigationDestinationIds.chats -> FabSpec("New conversation") { actions.onNewChat(false) }
        ChatyNavigationDestinationIds.groups -> FabSpec("New group") { actions.onNewChat(true) }
        ChatyNavigationDestinationIds.updates -> FabSpec("New status") {
            scope.launch {
                val result = actions.captureStory() ?: return@launch
                try {
                    actions.publishStory(result)
                    onNotify("Status posted successfully.")
                } catch (error: Throwable) {
                    onNotify("Error: ${error.message ?: error}")
                }
            }
        }
        ChatyNavigationDestinationIds.tasks -> FabSpec("Create task") { actions.onCreateTask() }
        "calls" -> FabSpec("New call") { actions.onNewCall() }
        ChatyNavigationDestinationIds.desktop -> FabSpec("Scan QR") { actions.onScanQr() }
        else -> FabSpec("New message") { actions.onNewChat(false) }
    }

    @Composable
    fun fabIcon(defaultContent: @Composable () -> Unit) {
        if (activeDestinationId == ChatyNavigationDestinationIds.chats) {
            Box {
                com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.chats, semanticLabel = null, color = effectiveIconColor, modifier = Modifier.size(24.dp))
                if (prefs.showMetaAiIcon) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(5.dp, (-5).dp)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(effectiveFabColor)
                            .border(1.dp, effectiveIconColor.copy(alpha = 0.95f), CircleShape),
                    ) {
                        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.autoAwesome, semanticLabel = null, color = effectiveIconColor, modifier = Modifier.size(10.dp))
                    }
                }
            }
        } else {
            defaultContent()
        }
    }

    @Composable
    fun defaultDestinationIcon() {
        val icon = when (activeDestinationId) {
            ChatyNavigationDestinationIds.chats ->
                if (prefs.showMetaAiIcon) Icons.Rounded.Psychology else Icons.Rounded.ChatBubble
            ChatyNavigationDestinationIds.groups -> Icons.Rounded.GroupAdd
            ChatyNavigationDestinationIds.updates -> Icons.Rounded.CameraAlt
            ChatyNavigationDestinationIds.tasks -> Icons.Rounded.AddTask
            ChatyNavigationDestinationIds.desktop -> Icons.Rounded.QrCodeScanner
            else -> Icons.Rounded.ChatBubble
        }
        Icon(icon, contentDescription = null, tint = effectiveIconColor, modifier = Modifier.size(24.dp))
    }

    @Composable
    fun standardFab(onClick: () -> Unit) {
        // MOTION (presentational only): brief press scale; no scale when animations are disabled.
        val fabInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        val fabPressed by fabInteraction.collectIsPressedAsState()
        val fabReducedMotion = com.example.chat.ui.kit.rememberChatyReduceMotion()
        val fabScale by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (fabPressed && !fabReducedMotion) 0.94f else 1f,
            animationSpec = androidx.compose.animation.core.tween(durationMillis = if (fabReducedMotion) 0 else 90),
            label = "fabPressScale",
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .graphicsLayer { scaleX = fabScale; scaleY = fabScale }
                // Previous (default black shadow): .shadow(4.5.dp, mainFabShape)
                .chatyDepth(4.5.dp, mainFabShape)
                .clip(mainFabShape)
                .background(effectiveFabColor)
                // A11Y: the icon is decorative, so name the control for TalkBack; focusable by default for keyboard.
                .semantics(mergeDescendants = true) { contentDescription = spec.tooltip }
                .clickable(interactionSource = fabInteraction, indication = androidx.compose.foundation.LocalIndication.current, role = Role.Button, onClick = onClick)
                .padding(16.dp),
        ) {
            fabIcon { defaultDestinationIcon() }
        }
    }

    if (activeDestinationId != ChatyNavigationDestinationIds.chats) {
        standardFab(spec.action)
    } else {
        // Speed dial assembly (Dart quickActions order and flags).
        val quickActions = mutableListOf<DialActionLike>()
        // Opt-in, chats-only entry: no inactive or placeholder command is shown.
        onOpenHomeCommandPalette?.let { openPalette ->
            quickActions.add(DialActionLike("home-command-palette", "Home commands", Icons.Rounded.GridView) {
                openPalette()
            })
        }
        // Refresh and Privacy curtain moved here from the Home bottom chips (those stay as a fallback when no FAB hosts them).
        // Actions go through the scoped header event queue, which drops them if the Home page is not mounted.
        val homeToolsController = LocalHomeHeaderController.current
        val homeToolsEntry = homeToolsController?.entry("chats")
        androidx.compose.runtime.DisposableEffect(homeToolsEntry) {
            homeToolsEntry?.fabHostsHomeTools = true
            onDispose { homeToolsEntry?.fabHostsHomeTools = false }
        }
        if (homeToolsController != null && homeToolsEntry != null) {
            val homeSelecting = homeToolsEntry.selectedIds.isNotEmpty()
            if (!homeSelecting && homeToolsEntry.canRefresh) {
                quickActions.add(DialActionLike("home-refresh", if (homeToolsEntry.refreshing) "Refreshing…" else "Refresh", Icons.Rounded.Refresh,
                    enabled = !homeToolsEntry.refreshing) {
                    homeToolsController.emit("chats", HeaderEvent(HeaderAction.RefreshHome))
                })
            }
            // The curtain only has an effect over displayed entries; offer it then, or to turn an active curtain off.
            if (homeToolsEntry.privacyCurtainOn || (!homeSelecting && homeToolsEntry.hasVisibleChats)) {
                quickActions.add(DialActionLike("home-privacy-curtain", if (homeToolsEntry.privacyCurtainOn) "Show content" else "Privacy curtain",
                    if (homeToolsEntry.privacyCurtainOn) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff) {
                    homeToolsController.emit("chats", HeaderEvent(HeaderAction.TogglePrivacyCurtain))
                })
            }
        }
        if (!prefs.hideNewMessageFab) {
            quickActions.add(DialActionLike("new-message", "New message", Icons.Rounded.MarkChatUnread) { spec.action() })
        }
        if (!prefs.hideLastSeenFab) {
            quickActions.add(
                DialActionLike(
                    "freeze-last-seen",
                    if (prefs.freezeLastSeen) "Unfreeze last seen" else "Freeze last seen",
                    Icons.Rounded.HistoryToggleOff,
                ) {
                    val next = !prefs.freezeLastSeen
                    actions.onToggleFreezeLastSeen(next)
                    onNotify(if (next) "Last seen frozen" else "Last seen restored")
                },
            )
        }
        if (!prefs.hideCutEditFab) {
            quickActions.add(DialActionLike("restart-chaty", "Restart Chaty", Icons.Rounded.RestartAlt) { restartConfirm = true })
        }
        if (!prefs.hidePluginsList) {
            quickActions.add(DialActionLike("features", "Features & plugins", Icons.Rounded.Extension) { actions.onOpenSettings() })
        }
        if (!prefs.hideGbwaSettingsFab) {
            quickActions.add(DialActionLike("chaty-settings", "Settings", Icons.Rounded.Settings) { actions.onOpenSettings() })
        }
        quickActions.add(DialActionLike("more-tools", "More Actions & Tools", Icons.Rounded.GridView) { quickActionsOpen = true })

        /* Removed unreachable fallback, preserved for review:
        if (quickActions.isEmpty()) {
            if (!prefs.hideNewMessageFab) standardFab(spec.action)
        }
        More Actions & Tools is always present, including when other actions are hidden. */
        HomeSpeedDialFab(
            actions = quickActions,
            normalColor = effectiveFabColor,
            iconColor = effectiveIconColor,
            surfaceColor = colors.surfaceElevated,
            textColor = colors.foreground,
            showMetaAiIcon = prefs.showMetaAiIcon,
            mainFabShape = mainFabShape,
            modifier = modifier,
            leftHandedCompose = leftHandedCompose,
        )
    }

    if (restartConfirm) {
        AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
            onDismissRequest = { restartConfirm = false },
            title = { Text("Restart Chaty?") },
            text = { Text("Chaty will close and reopen. Make sure any unsent text is saved first.") },
            confirmButton = {
                ChatyActionButton(onClick = {
                    restartConfirm = false
                    scope.launch {
                        try {
                            actions.onRestartApp()
                        } catch (error: Throwable) {
                            // Removed raw exception output: onNotify("Chaty could not restart: ${error.message ?: error}")
                            onNotify("Chaty could not restart. Close and reopen the app.")
                        }
                    }
                }) { Text("Restart") }
            },
            dismissButton = { ChatyTextButton(onClick = { restartConfirm = false }, label = "Cancel") },
        )
    }

    if (quickActionsOpen) {
        FabSecondaryActionsSheet(
            colors = colors,
            onOpenThemeStudio = actions.onOpenThemeStudio,
            onOpenDirectNumber = {
                quickActionsOpen = false
                directNumberOpen = true
            },
            onOpenMessageScheduler = actions.onOpenMessageScheduler,
            onOpenAutoReplyRules = actions.onOpenAutoReplyRules,
            onDismiss = { quickActionsOpen = false },
        )
    }

    if (directNumberOpen) {
        DirectNumberDialog(
            colors = colors,
            openDirectConversation = actions.openDirectConversation,
            onOpenConversation = actions.onOpenConversation,
            onDismiss = { directNumberOpen = false },
            onError = { onNotify("Could not start chat. Check the recipient and try again.") },
        )
    }
}

/** Compact shared action control and anchored menu; action inventory is unchanged. */
@Suppress("UNUSED_PARAMETER")
@Composable
private fun HomeSpeedDialFab(
    mainFabShape: androidx.compose.ui.graphics.Shape = CircleShape,
    actions: List<DialActionLike>,
    normalColor: Color,
    iconColor: Color,
    surfaceColor: Color,
    textColor: Color,
    showMetaAiIcon: Boolean,
    modifier: Modifier = Modifier,
    leftHandedCompose: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    val themeColors = com.example.chat.ui.theme.LocalAppColors.current
    val typography = androidx.compose.material3.MaterialTheme.typography
    val buttonShape = com.example.chat.ui.kit.ChatyActionShape
    // The shared popup is anchored to this control, not to the full screen.
    Box(modifier = modifier) {
        ChatyActionButton(
            onClick = { expanded = !expanded },
            containerColor = normalColor,
            contentColor = iconColor,
            modifier = Modifier
                .align(if (leftHandedCompose) androidx.compose.ui.AbsoluteAlignment.TopLeft else Alignment.TopEnd)
                .heightIn(min = ReferenceQuickActionMetrics.quickActionsMinHeightDp.dp)
                .shadow(
                    ReferenceQuickActionMetrics.quickActionsElevationDp.dp, buttonShape,
                    ambientColor = themeColors.shadow, spotColor = themeColors.shadow,
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = "Quick actions"
                    stateDescription = if (expanded) "Expanded" else "Collapsed"
                },
        ) {
            if (expanded) {
                com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.close,
                    semanticLabel = null, color = iconColor,
                    modifier = Modifier.size(ReferenceQuickActionMetrics.quickActionsIconSizeDp.dp))
            } else {
                Box {
                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.chats,
                        semanticLabel = null, color = iconColor,
                        modifier = Modifier.size(ReferenceQuickActionMetrics.quickActionsIconSizeDp.dp))
                    if (showMetaAiIcon) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(ReferenceQuickActionMetrics.quickActionsBadgeOffsetDp.dp, (-ReferenceQuickActionMetrics.quickActionsBadgeOffsetDp).dp)
                                .size(ReferenceQuickActionMetrics.quickActionsBadgeSizeDp.dp)
                                .clip(CircleShape)
                                .background(normalColor)
                                .border(ReferenceQuickActionMetrics.quickActionsBadgeBorderDp.dp, iconColor.copy(alpha = 0.95f), CircleShape),
                        ) {
                            com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.autoAwesome, semanticLabel = null, color = iconColor, modifier = Modifier.size(ReferenceQuickActionMetrics.quickActionsBadgeIconSizeDp.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.width(ReferenceQuickActionMetrics.quickActionsLabelGapDp.dp))
            Text("Quick actions", color = iconColor, style = typography.labelLarge)
        }
        com.example.chat.ui.kit.ChatyDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            preferBelow = false,
            containerColor = surfaceColor,
            shadowColor = themeColors.shadow,
            modifier = Modifier
                .widthIn(max = ReferenceQuickActionMetrics.quickActionsMenuMaxWidthDp.dp)
                .heightIn(max = ReferenceQuickActionMetrics.quickActionsMenuMaxHeightDp.dp),
        ) {
            actions.forEach { action ->
                com.example.chat.ui.kit.ChatyDropdownMenuItem(
                    text = { Text(action.label, color = textColor, style = typography.labelLarge) },
                    onClick = { expanded = false; action.onPressed() },
                    enabled = action.enabled,
                    leadingIcon = {
                        Icon(action.icon, contentDescription = null, tint = textColor,
                            modifier = Modifier.size(ReferenceQuickActionMetrics.quickActionsMenuIconSizeDp.dp))
                    },
                )
            }
        }
    }
}

// Previous stacked speed-dial presentation retained inactive for review:
// @Composable
// private fun HomeSpeedDialFab(
//     mainFabShape: androidx.compose.ui.graphics.Shape = CircleShape,
//     actions: List<DialActionLike>,
//     normalColor: Color,
//     iconColor: Color,
//     surfaceColor: Color,
//     textColor: Color,
//     showMetaAiIcon: Boolean,
//     modifier: Modifier = Modifier,
//     leftHandedCompose: Boolean = false,
// ) {
//     var expanded by remember { mutableStateOf(false) }
//     androidx.activity.compose.BackHandler(expanded) {expanded=false}
//     Column(modifier = modifier, horizontalAlignment = Alignment.End) {
//         if (expanded) {
//             actions.forEach { action ->
//                 Row(
//                     verticalAlignment = Alignment.CenterVertically,
//                     modifier = Modifier.padding(bottom = 9.dp).heightIn(min=48.dp)
//                         .then(if (leftHandedCompose) Modifier.align(androidx.compose.ui.AbsoluteAlignment.Left) else Modifier)
//                         .semantics(mergeDescendants=true) {contentDescription=action.label}
//                         .clickable(role=Role.Button) {expanded=false;action.onPressed()},
//                 ) {
//                     Box(
//                         modifier = Modifier
//                             .shadow(3.dp, RoundedCornerShape(14.dp))
//                             .clip(RoundedCornerShape(14.dp))
//                             .background(surfaceColor)
//                             .padding(horizontal = 11.dp, vertical = 8.dp),
//                     ) {
//                         Text(action.label, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
//                     }
//                     Spacer(Modifier.width(9.dp))
//                     Box(
//                         contentAlignment = Alignment.Center,
//                         modifier = Modifier
//                             .size(48.dp)
//                             .clip(CircleShape)
//                             .background(normalColor),
//                             // The whole labeled row now owns this click, rather than the icon alone.
//                             // .clickable { expanded=false;action.onPressed() }
//                     ) {
//                         Icon(action.icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(19.dp))
//                     }
//                 }
//             }
//         }
//         Box(
//             contentAlignment = Alignment.Center,
//             modifier = Modifier
//                 .then(if (leftHandedCompose) Modifier.align(androidx.compose.ui.AbsoluteAlignment.Left) else Modifier)
//                 .shadow(4.5.dp, mainFabShape)
//                 .clip(mainFabShape)
//                 .background(normalColor)
//                 .clickable(role = Role.Button, onClickLabel = if (expanded) "Close quick actions" else "Open quick actions") { expanded = !expanded }
//                 .padding(16.dp),
//         ) {
//             if (expanded) {
//                 com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.close, semanticLabel = "Close quick actions", color = iconColor, modifier = Modifier.size(24.dp))
//             } else {
//                 Box {
//                     com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.chats, semanticLabel = "Open quick actions", color = iconColor, modifier = Modifier.size(24.dp))
//                     if (showMetaAiIcon) {
//                         Box(
//                             contentAlignment = Alignment.Center,
//                             modifier = Modifier
//                                 .align(Alignment.TopEnd)
//                                 .offset(5.dp, (-5).dp)
//                                 .size(16.dp)
//                                 .clip(CircleShape)
//                                 .background(normalColor)
//                                 .border(1.dp, iconColor.copy(alpha = 0.95f), CircleShape),
//                         ) {
//                             com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.autoAwesome, semanticLabel = null, color = iconColor, modifier = Modifier.size(10.dp))
//                         }
//                     }
//                 }
//             }
//         }
//         // This FAB opens a mixed speed dial, not the new-conversation screen.
//         // Keep its purpose visible rather than relying on the chat icon alone.
//         Text(
//             "Quick actions",
//             color = textColor,
//             fontSize = 12.sp,
//             fontWeight = FontWeight.SemiBold,
//             modifier = Modifier
//                 .then(if (leftHandedCompose) Modifier.align(androidx.compose.ui.AbsoluteAlignment.Left) else Modifier)
//                 .padding(top = 6.dp)
//                 .clip(RoundedCornerShape(14.dp))
//                 .background(surfaceColor)
//                 .clickable(role = Role.Button, onClickLabel = if (expanded) "Close quick actions" else "Open quick actions") { expanded = !expanded }
//                 .heightIn(min = 48.dp)
//                 .padding(horizontal = 11.dp, vertical = 8.dp),
//         )
//     }
// }

private data class DialActionLike(
    val id: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val enabled: Boolean = true,
    val onPressed: () -> Unit,
)

/** Exact port of _showFabSecondaryActionsSheet: 'Quick Actions & Tools'
 *  with Theme Studio / Message Unsaved Number / Message Scheduler /
 *  Auto-Reply Rules rows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FabSecondaryActionsSheet(
    colors: ChatyColors,
    onOpenThemeStudio: () -> Unit,
    onOpenDirectNumber: () -> Unit,
    onOpenMessageScheduler: () -> Unit,
    onOpenAutoReplyRules: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(dragHandle = { com.example.chat.ui.kit.ChatySheetGrabber() }, tonalElevation = 0.dp, shape = com.example.chat.ui.kit.ChatyModalShape, onDismissRequest = onDismiss, containerColor = colors.surfaceElevated) {
        Column(
            Modifier
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.autoAwesome, semanticLabel = null, color = colors.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Quick Actions & Tools", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.foreground)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))
            QuickActionRow(Icons.Rounded.Palette, colors.primary, "Theme Studio", "Live colors, gradients & presets", colors) {
                onDismiss(); onOpenThemeStudio()
            }
            QuickActionRow(Icons.Rounded.Dialpad, colors.primary, "Message Unsaved Number", "Direct chat without saving contact", colors) {
                onDismiss(); onOpenDirectNumber()
            }
            QuickActionRow(Icons.Rounded.ScheduleSend, colors.info, "Message Scheduler", "Plan scheduled message deliveries", colors) {
                onDismiss(); onOpenMessageScheduler()
            }
            QuickActionRow(Icons.Rounded.ReplyAll, colors.success, "Auto-Reply Rules", "Keyword automated response bot", colors) {
                onDismiss(); onOpenAutoReplyRules()
            }
        }
    }
}

@Composable
private fun QuickActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    colors: ChatyColors,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = colors.foreground, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = colors.foregroundSecondary, fontSize = 12.sp)
        }
    }
}

/** Exact port of _showDirectNumberDialog: phone/username input, 'Chat'
 *  action builds the synthetic UserProfile (id = input minus '@',
 *  initials first char uppercase, colorHex '#25D366') and opens the
 *  direct conversation. */
@Composable
private fun DirectNumberDialog(
    colors: ChatyColors,
    openDirectConversation: suspend (target: String) -> String,
    onOpenConversation: (String) -> Unit,
    onDismiss: () -> Unit,
    onError: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
        onDismissRequest = onDismiss,
        title = { Text("Message Unsaved Number") },
        text = {
            Column {
                Text(
                    "Enter a phone number or username to initiate a direct conversation without saving to contacts.",
                    color = colors.foregroundSecondary,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Phone or Username") },
                    placeholder = { Text("+1234567890 or @user") },
                    leadingIcon = {
//Icon(Icons.Rounded.PersonSearch, contentDescription = null)
                    com.example.chat.ui.icons.ChatyPurposeIcon(com.example.chat.ui.icons.ChatyIconId.personSearch, Icons.Rounded.PersonSearch, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            ChatyActionButton(
                onClick = {
                    val trimmed = input.trim()
                    if (trimmed.isEmpty()) return@ChatyActionButton
                    onDismiss()
                    scope.launch {
                        try {
                            val conversationId = openDirectConversation(trimmed)
                            onOpenConversation(conversationId)
                        } catch (error: Throwable) {
                            onError((error.message ?: error.toString()))
                        }
                    }
                },
            ) { Text("Chat") }
        },
        dismissButton = { ChatyTextButton(onClick = onDismiss, label = "Cancel") },
    )
}
