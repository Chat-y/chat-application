package com.example.chat.ui

import com.example.chat.ui.kit.chatyDepth
import com.example.chat.ServiceLocator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.data.services.ChatyEventNotification
import com.example.chat.data.services.ChatyNotificationService
import com.example.chat.ui.core.notifications.ChatyNotificationIconPresets
import com.example.chat.ui.theme.LocalAppColors
import kotlinx.coroutines.delay

/**
 * Port of ui/core/widgets/event_toast_overlay.dart ChatyEventToastOverlay.
 * Renders the latest event notification as a setting-driven toast above the
 * content. [concealWhileLocked] (Dart: lock gate showing AND
 * security.hideLockNotificationContent) replaces the body with dots; titles
 * stay visible.
 *
 * Known differences (compile-only, not pixel-checked): the generic icon is a
 * single chat-bubble glyph instead of the per-preset icon key; the Dynamic
 * Island variant ('dynamic_island_notifications') renders the compact pill
 * only (no expand / recent list); no cutout-aware top padding.
 */
@Composable
fun ChatyEventToastOverlay(
    notificationService: ChatyNotificationService,
    preferences: ChatyPreferencesController,
    concealWhileLocked: Boolean,
    suppressMessageContent: Boolean = false,
    content: @Composable () -> Unit,
) {
    var preferencesTick by remember(preferences) { androidx.compose.runtime.mutableIntStateOf(0) }
    DisposableEffect(preferences) {
        val listener: () -> Unit = { preferencesTick++ }
        preferences.addListener(listener)
        onDispose { preferences.removeListener(listener) }
    }
    @Suppress("UNUSED_EXPRESSION") preferencesTick
    val routeSuppressed = suppressMessageContent || MessageOverlayPrivacy.suppressed
    fun eventEnabled(event: ChatyEventNotification): Boolean = when (event.icon) {
        com.example.chat.data.realtime.EventNotificationIcon.onlinePrediction -> preferences.toasts.onlineToastEnabled
        com.example.chat.data.realtime.EventNotificationIcon.keyboardAlt -> preferences.toasts.typingToastEnabled
        else -> true
    }
    val concealProtected = com.example.chat.ui.settings.security.ProtectedToastPolicy.conceal(
        concealWhileLocked || routeSuppressed, preferences.security.hideLockNotificationContent,
        preferences.security.lockedConversationIds.isNotEmpty(),
    )
    var visible by remember { mutableStateOf<ChatyEventNotification?>(null) }
    var lastId by remember { mutableStateOf<String?>(null) }
    var tick by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    DisposableEffect(notificationService) {
        val l: () -> Unit = { tick++ }
        notificationService.addListener(l)
        onDispose { notificationService.removeListener(l) }
    }
    @Suppress("UNUSED_EXPRESSION") tick
    val latest = notificationService.latest
    if (latest != null && latest.id != lastId) {
        // Dart _onNotificationChanged gate.
        lastId = latest.id
        if (preferences.notification.enableGlobalNotifications &&
// Superseded source retained below per additive-only review rule.
//             !preferences.universal.disableHeadsUpNotification
            !preferences.universal.disableHeadsUpNotification && !routeSuppressed && eventEnabled(latest)
        ) {
            visible = latest
            // Dart _onNotificationChanged: DynamicIslandController.postPayload(chatMessage), masked while locked.
            // No onQuickReply/onMarkAsRead callbacks, exactly like Dart's payload (quick reply is a no-op there).
            ServiceLocator.dynamicIslandController.postPayload(
                com.example.chat.ui.island.DynamicIslandPayload(
                    id = latest.id,
                    type = com.example.chat.ui.island.IslandPayloadType.chatMessage,
                    title = latest.title,
// Superseded source retained below per additive-only review rule.
//                     message = if (concealWhileLocked) "\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022" else latest.body,
                    message = if (concealProtected) "\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022" else latest.body,
                    timestamp = latest.timestamp,
                    accentColor = Color(latest.colorArgb.toInt()),
                    senderInitials = latest.avatarInitials,
                    canQuickReply = true,
                    iconData = com.example.chat.ui.island.IslandIconMapper.map(latest.icon),
                ),
            )
        }
    }
    LaunchedEffect(concealProtected, routeSuppressed) {
        if (concealProtected) {
            visible?.id?.let { ServiceLocator.dynamicIslandController.removePayload(it) }
            visible = null
            ServiceLocator.dynamicIslandController.removePayloadsWhere {
                it.type == com.example.chat.ui.island.IslandPayloadType.chatMessage
            }
        }
    }
    LaunchedEffect(preferencesTick) {
        visible?.takeIf { !eventEnabled(it) || !preferences.notification.enableGlobalNotifications ||
            preferences.universal.disableHeadsUpNotification }?.let {
            ServiceLocator.dynamicIslandController.removePayload(it.id)
            visible = null
        }
    }
    val seconds = preferences.gbInt("event_toast_duration_seconds", 3).coerceIn(2, 8)
    LaunchedEffect(visible?.id) {
        val id = visible?.id ?: return@LaunchedEffect
        delay(seconds * 1000L)
        if (visible?.id == id) visible = null
    }

    Box(Modifier.fillMaxSize()) {
        content()
        val n = visible
        val typedStyle = com.example.chat.domain.notifications.ContactToastStyle.resolve(
            when (n?.icon) {
                com.example.chat.data.realtime.EventNotificationIcon.onlinePrediction -> "online"
                com.example.chat.data.realtime.EventNotificationIcon.keyboardAlt -> "typing"
                else -> "other"
            }, preferences.toasts,
        )
        val position = typedStyle?.position ?: preferences.gbString("event_toast_position", "Top").lowercase()
        val islandMode = preferences.gbBool("dynamic_island_notifications", false)
        val align = when (position) {
            "center" -> Alignment.Center
            "bottom" -> Alignment.BottomCenter
            else -> Alignment.TopCenter
        }
        AnimatedVisibility(
// Superseded source retained below per additive-only review rule.
//             visible = n != null,
            visible = n != null && eventEnabled(n) && !routeSuppressed &&
                preferences.notification.enableGlobalNotifications && !preferences.universal.disableHeadsUpNotification,
            modifier = Modifier.align(if (islandMode) Alignment.TopCenter else align),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            val shown = n ?: return@AnimatedVisibility
            val colors = LocalAppColors.current
// Superseded source retained below per additive-only review rule.
//             val body = if (concealWhileLocked) "••••••" else shown.body
            val body = if (concealProtected) "••••••" else shown.body
            if (islandMode) {
                // Dart _buildDynamicIsland: the real DynamicIslandOverlayHost is mounted below.
            } else {
                val avatarSize = preferences.gbDouble("toast_avatar_size", 40.0).coerceIn(24.0, 56.0).dp
                val corner = (typedStyle?.radius ?: preferences.gbDouble("toast_corner_radius", 18.0).coerceIn(4.0, 32.0)).dp
                val elevation = (if (typedStyle?.elevated == false) 0.0 else preferences.gbDouble("toast_elevation", 10.0).coerceIn(0.0, 24.0)).dp
                // Previous: val bg = (typedStyle?.background ?: preferences.gbColor("event_toast_bg"))?.let { Color(it.toInt()) } ?: colors.surface
                // Presence pill uses theme tokens only. Saved custom colors remain stored unchanged.
                // Previous: (typedStyle?.background ?: preferences.gbColor("event_toast_bg"))?.let { Color(it.toInt()) }
                val bg = if (typedStyle != null) colors.surfaceElevated
                    else preferences.gbColor("event_toast_bg")?.let { Color(it.toInt()) } ?: colors.surface
                val preset = ChatyNotificationIconPresets.forName(preferences.universal.notificationIcon)
                val presetColor = Color(preset.colorArgb.toInt())
                // Previous implementation retained: val textTint = (typedStyle?.text ?: shown.textColorArgb)?.let { Color(it.toInt()) }
                val requestedText = typedStyle?.text ?: shown.textColorArgb
                val scheme = androidx.compose.material3.MaterialTheme.colorScheme
                fun packed(color: Color): Long = color.toArgb().toLong() and 0xFFFFFFFFL
                val themeForegrounds = listOf(packed(scheme.onSurface), packed(scheme.inverseOnSurface))
                val titleTint = Color(com.example.chat.ui.kit.ToastContrastPolicy.readable(packed(bg),
                    requestedText ?: packed(colors.foreground), themeForegrounds))
                val bodyTint = Color(com.example.chat.ui.kit.ToastContrastPolicy.readable(packed(bg),
                    requestedText ?: packed(colors.foregroundSecondary), themeForegrounds))
                val pad = when (position) {
                    "bottom" -> Modifier.padding(start = 14.dp, end = 14.dp, bottom = 92.dp)
                    "center" -> Modifier.padding(horizontal = 20.dp, vertical = 20.dp)
                    else -> Modifier.windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeDrawing).padding(start = 14.dp, end = 14.dp, top = 2.dp)
                }
                Box(pad) {
                    if (typedStyle != null) {
                        PresenceToastPill(
                            event = shown,
                            style = typedStyle,
                            avatarSize = avatarSize,
                            background = bg,
                            // Token foreground, not the legacy event/saved raw tint.
                            foreground = colors.foreground,
                            corner = corner,
                            elevation = elevation,
                            showAvatar = preferences.notification.showSenderAvatar,
                            showName = preferences.notification.showSenderName,
                            showPreview = preferences.notification.showMessagePreview && !concealProtected,
                        )
                    } else {
                    // Original generic event card remains unchanged for non-presence notifications.
                    Surface(
                        shape = RoundedCornerShape(corner),
                        color = bg,
                        // Previous (default black shadow): shadowElevation = elevation,
                        //                        modifier = Modifier.widthIn(max = 430.dp)
                        shadowElevation = 0.dp,
                        modifier = Modifier.chatyDepth(elevation, RoundedCornerShape(corner)).widthIn(max = 430.dp)
                            .border(1.dp, colors.borderSubtle, RoundedCornerShape(corner)),
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            val initials = shown.avatarInitials
                            if (preferences.notification.showSenderAvatar && typedStyle?.showAvatar != false && initials != null) {
                                Box(Modifier.size(avatarSize).clip(CircleShape)
                                    .background(avatarColor(shown)),
                                    contentAlignment = Alignment.Center) {
                                    Text(initials, color = colors.onPrimary, fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold)
                                }
                            } else {
                                Box(Modifier.size(avatarSize).clip(CircleShape)
                                    .background(presetColor.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center) {
                                    com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.chats, semanticLabel = null,
                                        color = presetColor, modifier = Modifier.size(21.dp))
                                }
                            }
                            Spacer(Modifier.width(11.dp))
                            Column {
                                Text(
                                    if (preferences.notification.showSenderName) shown.title else "Chaty",
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.ExtraBold, fontSize = 14.sp,
                                    // Previous implementation retained: color = textTint ?: colors.foreground,
                                    color = titleTint,
                                )
                                if (preferences.notification.showMessagePreview) {
                                    Text(body, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        fontSize = 12.sp,
                                        // Previous implementation retained: color = textTint ?: colors.foregroundSecondary)
                                        color = bodyTint)
                                }
                            }
                        }
                    }
                    } // Generic event card; online/typing use compact pill above.
                }
            }
        }
        // Dart _buildDynamicIsland -> DynamicIslandOverlayHost(controller: locator<DynamicIslandController>()),
        // shown only while heads-up is enabled and the island preference is on.
        if (n != null && eventEnabled(n) && preferences.gbBool("dynamic_island_notifications", false) &&
            preferences.notification.enableGlobalNotifications &&
// Superseded source retained below per additive-only review rule.
//             !preferences.universal.disableHeadsUpNotification
            !preferences.universal.disableHeadsUpNotification && !routeSuppressed
        ) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val config = androidx.compose.ui.platform.LocalConfiguration.current
            val view = androidx.compose.ui.platform.LocalView.current
            val statusTopDp = with(density) { androidx.compose.foundation.layout.WindowInsets.statusBars.getTop(density).toDouble() / density.density }
            val cutouts: List<DoubleArray> = if (android.os.Build.VERSION.SDK_INT >= 28) {
                (view.rootWindowInsets?.displayCutout?.boundingRects ?: emptyList()).map {
                    doubleArrayOf(
                        it.left / density.density.toDouble(), it.top / density.density.toDouble(),
                        it.right / density.density.toDouble(), it.bottom / density.density.toDouble(),
                    )
                }
            } else emptyList()
            com.example.chat.ui.island.DynamicIslandOverlayHost(
                controller = ServiceLocator.dynamicIslandController,
                anchorInput = com.example.chat.ui.island.CutoutAnchorInput(
                    viewPaddingTop = statusTopDp,
                    paddingTop = statusTopDp,
                    screenWidth = config.screenWidthDp.toDouble(),
                    cutoutBounds = cutouts,
                ),
                screenHeight = config.screenHeightDp.toDouble(),
            )
        }
    }
}

private fun avatarColor(n: ChatyEventNotification): Color {
    val fallback = Color(n.colorArgb.toInt())
    val raw = n.avatarColorHex
    if (raw.isNullOrEmpty()) return fallback
    val norm = raw.removePrefix("0x").removePrefix("#")
    val parsed = norm.toLongOrNull(16) ?: return fallback
    return Color((if (norm.length <= 6) 0xFF000000 or parsed else parsed).toInt())
}
