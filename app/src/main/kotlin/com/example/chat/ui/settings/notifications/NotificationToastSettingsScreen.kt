package com.example.chat.ui.settings.notifications

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.rounded.AccountBox
import androidx.compose.material.icons.rounded.FormatColorText
import androidx.compose.material.icons.rounded.FormatPaint
import androidx.compose.material.icons.rounded.HistoryEdu
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ToggleOn
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material.icons.rounded.Warning
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.ui.kit.ChatyBackButton
import com.example.chat.ui.kit.GbCardContainer
import com.example.chat.ui.kit.GbLivePreviewCard
import com.example.chat.ui.kit.GbSettingRow
import com.example.chat.ui.kit.GbSliderTile
import com.example.chat.ui.kit.GbSwitchTile
import com.example.chat.ui.kit.rememberGbPickers
import com.example.chat.ui.theme.LocalThemeConfig

/** Neutral placeholder person used by all toast previews (never the device owner's real name). */
private const val SAMPLE_NAME = "Aarav"

// Hoisted option lists: created once, not per recomposition (NTF-R1-029).
private val TOAST_POSITIONS = listOf("Top of the screen", "Center of screen", "Bottom of screen")
private val TOAST_RINGTONES = listOf("Default Chaty Tone", "Soft Ping", "Pop Bubble", "Chime Bell", "Silent")

private const val TOAST_RADIUS_MIN = 0.0
private const val TOAST_RADIUS_MAX = 30.0

/** NTF-R1-008/NTF-R1-046: when no custom text color is set, derive a readable one from the background. */
private fun readableTextFor(bg: Color): Color {
    val l = 0.2126f * bg.red + 0.7152f * bg.green + 0.0722f * bg.blue
    return if (l > 0.45f) Color(0xFF11161D) else Color.White
}

private fun formatColorHex(value: Long): String = "#" + value.toString(16).uppercase().padStart(8, '0')

/**
 * Dart _buildToastPreview. Previews are deliberately always dark-styled (NTF-R3-035 documents this as
 * a pinned product decision): the fixed default background shows what the floating toast chrome looks
 * like regardless of app theme; custom colors override it. UNCOMPILED (Compose).
 */
@Composable
private fun ToastPreview(text: String, hasAvatar: Boolean, hasElevation: Boolean, radius: Double, bgColor: Long?, textColor: Long?, defaultBg: Color) {
    val bg = bgColor?.let { Color(it) } ?: defaultBg
    // NTF-R1-008: unset text color follows the background so previews never show unreadable combos.
    val fg = textColor?.let { Color(it) } ?: readableTextFor(bg)
    // NTF-R1-005/NTF-R4-005: clamp at read so an out-of-range stored value never disagrees with the slider.
    val shape = RoundedCornerShape(radius.coerceIn(TOAST_RADIUS_MIN, TOAST_RADIUS_MAX).toFloat().dp)
    Row(
        Modifier.let { if (hasElevation) it.shadow(8.dp, shape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f)) else it }
            .background(bg, shape).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasAvatar) {
            Box(Modifier.size(24.dp).background(Color.White.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.profile, semanticLabel = null, color = Color.White, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = fg, fontWeight = FontWeight.W600, fontSize = 13.sp, maxLines = 1)
    }
}

/** NTF-R1-013: system notification permission check (framework-only, no androidx.core dependency). */
private fun systemNotificationsAllowed(context: android.content.Context): Boolean {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) return true
    val nm = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager ?: return true
    return nm.areNotificationsEnabled()
}

/**
 * Port of NotificationToastSettingsScreen. Dart field aliases map to the Kotlin real names
 * (e.g. onlineWithProfilePic -> onlineToastWithProfilePicture). Capability matrix (NTF-R1-009):
 * ringtone applies to the Online card only; the notification-bar toggle applies to Online and Story;
 * radius, position, background/text color and elevation are per-type on every card.
 * Previews use a neutral sample name, never the device owner's (NTF-R1-007).
 * UNCOMPILED (Compose).
 */
@Composable
fun NotificationToastSettingsScreen(preferences: ChatyPreferencesController, onBack: () -> Unit) {
    val config = LocalThemeConfig.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    DisposableEffect(preferences) {
        val l: () -> Unit = { version += 1 }
        preferences.addListener(l)
        onDispose { preferences.removeListener(l) }
    }
    val p = preferences.toasts
    val pickers = rememberGbPickers()
    // NTF-R1-017: force alpha FF on picked colors so a translucent pick cannot break readability.
    fun pickColor(title: String, current: Long?, apply: (Long) -> Unit) =
        pickers.showColor(title, current ?: config.accentColor) { apply(it or 0xFF000000L) }
    fun showPositionModal(current: String, onSelected: (String) -> Unit) = pickers.showRadio(
        "Position of Toast", TOAST_POSITIONS, current, onSelected = onSelected,
    )
    fun showRingtoneModal(current: String, onSelected: (String) -> Unit) = pickers.showRadio(
        "Ringtone", TOAST_RINGTONES, current, onSelected = onSelected,
    )
    fun ringtoneSubtitle(value: String) = if (value == "Silent") "Silent - no sound will play" else value
    fun colorSubtitle(value: Long?, unset: String) = value?.let { formatColorHex(it) } ?: unset

    // NTF-R1-013: recheck the system permission whenever the screen recomposes.
    val notificationsAllowed = remember(version) { systemNotificationsAllowed(context) }

    // NTF-R1-003/NTF-R1-025: radius sliders keep a local drag value and commit ~450ms after the last
    // movement instead of writing to disk on every drag event; previews read the drag value so they
    // stay live while dragging (NTF-R4-026).
    var onlineRadiusDrag by remember { mutableStateOf<Float?>(null) }
    var storyRadiusDrag by remember { mutableStateOf<Float?>(null) }
    var profileRadiusDrag by remember { mutableStateOf<Float?>(null) }
    var typingRadiusDrag by remember { mutableStateOf<Float?>(null) }
    val onlineRadius = (onlineRadiusDrag?.toDouble() ?: p.onlineRadius).coerceIn(TOAST_RADIUS_MIN, TOAST_RADIUS_MAX)
    val storyRadius = (storyRadiusDrag?.toDouble() ?: p.storyRadius).coerceIn(TOAST_RADIUS_MIN, TOAST_RADIUS_MAX)
    val profileRadius = (profileRadiusDrag?.toDouble() ?: p.profileRadius).coerceIn(TOAST_RADIUS_MIN, TOAST_RADIUS_MAX)
    val typingRadius = (typingRadiusDrag?.toDouble() ?: p.typingRadius).coerceIn(TOAST_RADIUS_MIN, TOAST_RADIUS_MAX)
    LaunchedEffect(onlineRadiusDrag) { val d = onlineRadiusDrag ?: return@LaunchedEffect; kotlinx.coroutines.delay(450); preferences.updateToasts(preferences.toasts.copy(onlineToastRadius = d.toDouble())); onlineRadiusDrag = null }
    LaunchedEffect(storyRadiusDrag) { val d = storyRadiusDrag ?: return@LaunchedEffect; kotlinx.coroutines.delay(450); preferences.updateToasts(preferences.toasts.copy(storyToastRadius = d.toDouble())); storyRadiusDrag = null }
    LaunchedEffect(profileRadiusDrag) { val d = profileRadiusDrag ?: return@LaunchedEffect; kotlinx.coroutines.delay(450); preferences.updateToasts(preferences.toasts.copy(profileToastRadius = d.toDouble())); profileRadiusDrag = null }
    LaunchedEffect(typingRadiusDrag) { val d = typingRadiusDrag ?: return@LaunchedEffect; kotlinx.coroutines.delay(450); preferences.updateToasts(preferences.toasts.copy(typingToastRadius = d.toDouble())); typingRadiusDrag = null }

    // NTF-R1-011: keep scroll position across rotation/process death.
    val scrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
    var confirmReset by remember { mutableStateOf(false) }

    val allTypesEnabled = p.onlineToastEnabled && p.viewedStoryToast && p.profileToast && p.typingToast

    Column(Modifier.fillMaxSize().background(Color(config.backgroundColor)).semantics { paneTitle = "Toast notifications" }) {
        Row(
            Modifier.fillMaxWidth().background(Color(config.surfaceColor))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.padding(8.dp)) { ChatyBackButton(onPressed = onBack) }
            // NTF-R1-045/R3-045: distinct name (not Android's own "Notifications"); NTF-R1-044: fixed size,
            // no manual fontScale multiplication; NTF-R1-038: heading semantics.
            Text("Toast Notifications", Modifier.semantics { heading() }, color = Color(config.primaryTextColor), fontSize = 22.sp, fontWeight = FontWeight.W700)
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scrollState).padding(horizontal = 14.dp, vertical = 12.dp)) {
            if (!notificationsAllowed) {
                // NTF-R1-013: nothing on this screen fires when the system permission is off - say so and deep-link.
                GbCardContainer {
                    GbSettingRow(
                        icon = Icons.Rounded.Warning, title = "Android notifications are off", subtitle = "Toast notifications can't appear while notifications are off for Chaty. Tap to open system settings.", hasSubScreen = true,
                        onTap = {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                            context.startActivity(intent)
                        },
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
            GbCardContainer(headerText = "Activity toasts") {
                // NTF-R1-018: one master switch writing all four type toggles in a single update.
                GbSwitchTile(
                    icon = Icons.Rounded.ToggleOn, title = "All activity toasts", subtitle = "Online, story, profile and typing toasts. Toasts may also appear on your lock screen and notification shade.",
                    value = allTypesEnabled,
                    onChanged = { on ->
                        val t = preferences.toasts
                        preferences.updateToasts(t.copy(onlineToastEnabled = on, storyToastEnabled = on, profileToastEnabled = on, typingToastEnabled = on))
                    },
                )
            }
            if (!allTypesEnabled && !p.onlineToastEnabled && !p.viewedStoryToast && !p.profileToast && !p.typingToast) {
                // NTF-R4-006: say it in one glance when the whole feature is off.
                Spacer(Modifier.height(8.dp))
                Text("All toast types are off", color = Color(config.secondaryTextColor), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 6.dp))
            }
            Spacer(Modifier.height(16.dp))
            GbCardContainer(headerText = "Contact Online Toast") {
                Column {
                    GbLivePreviewCard(height = 70.dp) {
                        Box(Modifier.fillMaxWidth().fillMaxHeight().semantics(mergeDescendants = true) { contentDescription = "Preview: contact online toast" }, contentAlignment = Alignment.Center) {
                            ToastPreview("$SAMPLE_NAME Online", p.onlineWithProfilePic, p.onlineElevation, onlineRadius, p.onlineBgColor, p.onlineTextColor, Color(0xFF1E2630))
                        }
                    }
                    // NTF-R1-001: positive phrasing - the switch reads the stored enabled flag directly.
                    GbSwitchTile(
                        icon = Icons.Rounded.NotificationsNone, title = "Contact online toast", subtitle = "Show a toast when a contact comes online", value = p.onlineToastEnabled,
                        onChanged = { preferences.updateToasts(preferences.toasts.copy(onlineToastEnabled = it)) },
                    )
                    GbSwitchTile(
                        icon = Icons.Rounded.NotificationsNone, title = "Notification in the Notification Bar", subtitle = "Show a notification on the phone's status bar", value = p.onlineInNotificationBar,
                        onChanged = { preferences.updateToasts(preferences.toasts.copy(onlineToastInNotificationBar = it)) },
                    )
                    GbSwitchTile(
                        icon = Icons.Outlined.AccountCircle, title = "Notifications with profile picture", subtitle = "Show avatar next to toast message", value = p.onlineWithProfilePic,
                        onChanged = { preferences.updateToasts(preferences.toasts.copy(onlineToastWithProfilePicture = it)) },
                    )
                    GbSwitchTile(
                        icon = com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.layers), title = "Elevation", subtitle = "Show a shadow under the toast", value = p.onlineElevation,
                        onChanged = { preferences.updateToasts(preferences.toasts.copy(onlineToastElevation = it)) },
                    )
                    GbSliderTile(
                        title = "Radius of toast notifications", subtitle = "Rounded corners radius · ${onlineRadius.toInt()}dp", value = onlineRadius.toFloat(), min = 0f, max = 30f, divisions = 15,
                        onChanged = { onlineRadiusDrag = it },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.MusicNote, title = "Ringtone", subtitle = ringtoneSubtitle(p.onlineRingtone), hasSubScreen = true,
                        onTap = { showRingtoneModal(p.onlineRingtone) { v -> preferences.updateToasts(preferences.toasts.copy(onlineToastRingtone = v)) } },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.VerticalAlignTop, title = "Position of Toast", subtitle = p.onlinePosition, hasSubScreen = true,
                        onTap = { showPositionModal(p.onlinePosition) { v -> preferences.updateToasts(preferences.toasts.copy(onlineToastPosition = v)) } },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.FormatPaint, title = "Toast Background Color", subtitle = colorSubtitle(p.onlineBgColor, "Default"), colorValue = p.onlineBgColor,
                        onTap = { pickColor("Toast Background Color", p.onlineBgColor) { c -> preferences.updateToasts(preferences.toasts.copy(onlineToastBgColor = c)) } },
                        onColorTap = { pickColor("Toast Background Color", p.onlineBgColor) { c -> preferences.updateToasts(preferences.toasts.copy(onlineToastBgColor = c)) } },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.FormatColorText, title = "Toast Text Color", subtitle = colorSubtitle(p.onlineTextColor, "Auto (readable on background)"), colorValue = p.onlineTextColor,
                        onTap = { pickColor("Toast Text Color", p.onlineTextColor) { c -> preferences.updateToasts(preferences.toasts.copy(onlineToastTextColor = c)) } },
                        onColorTap = { pickColor("Toast Text Color", p.onlineTextColor) { c -> preferences.updateToasts(preferences.toasts.copy(onlineToastTextColor = c)) } },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            GbCardContainer(headerText = "Viewed Story Toast") {
                Column {
                    GbLivePreviewCard(height = 70.dp) {
                        Box(Modifier.fillMaxWidth().fillMaxHeight().semantics(mergeDescendants = true) { contentDescription = "Preview: story view toast" }, contentAlignment = Alignment.Center) {
                            ToastPreview("$SAMPLE_NAME Status", p.storyWithProfilePic, p.storyElevation, storyRadius, p.storyBgColor, p.storyTextColor, Color(0xFF1E2630))
                        }
                    }
                    GbSwitchTile(
                        icon = Icons.Rounded.HistoryEdu, title = "Story view toast", subtitle = "Show a toast when someone views your status. Contacts aren't told their views trigger this toast.", value = p.viewedStoryToast,
                        onChanged = { preferences.updateToasts(preferences.toasts.copy(storyToastEnabled = it)) },
                    )
                    GbSwitchTile(
                        icon = Icons.Rounded.NotificationsNone, title = "Notification in the Notification Bar", subtitle = "Show in status bar", value = p.storyInNotificationBar,
                        onChanged = { preferences.updateToasts(preferences.toasts.copy(storyToastInNotificationBar = it)) },
                    )
                    GbSwitchTile(
                        icon = Icons.Outlined.AccountCircle, title = "Notifications with profile picture", subtitle = "Show avatar next to status toast", value = p.storyWithProfilePic,
                        onChanged = { preferences.updateToasts(preferences.toasts.copy(storyToastWithProfilePicture = it)) },
                    )
                    GbSwitchTile(
                        icon = com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.layers), title = "Elevation", subtitle = "Show a shadow under the toast", value = p.storyElevation,
                        onChanged = { preferences.updateToasts(preferences.toasts.copy(storyToastElevation = it)) },
                    )
                    GbSliderTile(
                        title = "Radius of toast notifications", subtitle = "Rounded corners radius · ${storyRadius.toInt()}dp", value = storyRadius.toFloat(), min = 0f, max = 30f, divisions = 15,
                        onChanged = { storyRadiusDrag = it },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.VerticalAlignTop, title = "Position of Toast", subtitle = p.storyPosition, hasSubScreen = true,
                        onTap = { showPositionModal(p.storyPosition) { v -> preferences.updateToasts(preferences.toasts.copy(storyToastPosition = v)) } },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.FormatPaint, title = "Toast Background Color", subtitle = colorSubtitle(p.storyBgColor, "Default"), colorValue = p.storyBgColor,
                        onTap = { pickColor("Story Toast Background Color", p.storyBgColor) { c -> preferences.updateToasts(preferences.toasts.copy(storyToastBgColor = c)) } },
                        onColorTap = { pickColor("Story Toast Background Color", p.storyBgColor) { c -> preferences.updateToasts(preferences.toasts.copy(storyToastBgColor = c)) } },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.FormatColorText, title = "Toast Text Color", subtitle = colorSubtitle(p.storyTextColor, "Auto (readable on background)"), colorValue = p.storyTextColor,
                        onTap = { pickColor("Story Toast Text Color", p.storyTextColor) { c -> preferences.updateToasts(preferences.toasts.copy(storyToastTextColor = c)) } },
                        onColorTap = { pickColor("Story Toast Text Color", p.storyTextColor) { c -> preferences.updateToasts(preferences.toasts.copy(storyToastTextColor = c)) } },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            GbCardContainer(headerText = "Profile Toast") {
                Column {
                    GbLivePreviewCard(height = 70.dp) {
                        Box(Modifier.fillMaxWidth().fillMaxHeight().semantics(mergeDescendants = true) { contentDescription = "Preview: profile view toast" }, contentAlignment = Alignment.Center) {
                            ToastPreview("$SAMPLE_NAME Profile", p.profileWithProfilePic, p.profileElevation, profileRadius, p.profileBgColor, p.profileTextColor, Color(0xFF1E2630))
                        }
                    }
                    GbSwitchTile(
                        icon = Icons.Rounded.AccountBox, title = "Profile view toast", subtitle = "Show a toast when someone views your profile. Contacts aren't told their views trigger this toast.", value = p.profileToast,
                        onChanged = { preferences.updateToasts(preferences.toasts.copy(profileToastEnabled = it)) },
                    )
                    GbSwitchTile(
                        icon = Icons.Outlined.AccountCircle, title = "Notifications with profile picture", subtitle = "Show avatar", value = p.profileWithProfilePic,
                        onChanged = { preferences.updateToasts(preferences.toasts.copy(profileToastWithProfilePicture = it)) },
                    )
                    GbSwitchTile(
                        icon = com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.layers), title = "Elevation", subtitle = "Show a shadow under the toast", value = p.profileElevation,
                        onChanged = { preferences.updateToasts(preferences.toasts.copy(profileToastElevation = it)) },
                    )
                    GbSliderTile(
                        title = "Radius of toast notifications", subtitle = "Rounded corners radius · ${profileRadius.toInt()}dp", value = profileRadius.toFloat(), min = 0f, max = 30f, divisions = 15,
                        onChanged = { profileRadiusDrag = it },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.VerticalAlignTop, title = "Position of Toast", subtitle = p.profilePosition, hasSubScreen = true,
                        onTap = { showPositionModal(p.profilePosition) { v -> preferences.updateToasts(preferences.toasts.copy(profileToastPosition = v)) } },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.FormatPaint, title = "Toast Background Color", subtitle = colorSubtitle(p.profileBgColor, "Default"), colorValue = p.profileBgColor,
                        onTap = { pickColor("Profile Toast Background Color", p.profileBgColor) { c -> preferences.updateToasts(preferences.toasts.copy(profileToastBgColor = c)) } },
                        onColorTap = { pickColor("Profile Toast Background Color", p.profileBgColor) { c -> preferences.updateToasts(preferences.toasts.copy(profileToastBgColor = c)) } },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.FormatColorText, title = "Toast Text Color", subtitle = colorSubtitle(p.profileTextColor, "Auto (readable on background)"), colorValue = p.profileTextColor,
                        onTap = { pickColor("Profile Toast Text Color", p.profileTextColor) { c -> preferences.updateToasts(preferences.toasts.copy(profileToastTextColor = c)) } },
                        onColorTap = { pickColor("Profile Toast Text Color", p.profileTextColor) { c -> preferences.updateToasts(preferences.toasts.copy(profileToastTextColor = c)) } },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            GbCardContainer(headerText = "Typing Toast") {
                Column {
                    GbLivePreviewCard(height = 70.dp) {
                        Box(Modifier.fillMaxWidth().fillMaxHeight().semantics(mergeDescendants = true) { contentDescription = "Preview: typing toast" }, contentAlignment = Alignment.Center) {
                            ToastPreview("$SAMPLE_NAME Typing...", p.typingWithProfilePic, p.typingElevation, typingRadius, p.typingBgColor, p.typingTextColor, Color(0xFF1E2630))
                        }
                    }
                    GbSwitchTile(
                        icon = Icons.Rounded.Keyboard, title = "Typing toast", subtitle = "Show a toast when someone starts typing to you. Contacts aren't told typing triggers this toast.", value = p.typingToast,
                        onChanged = { preferences.updateToasts(preferences.toasts.copy(typingToastEnabled = it)) },
                    )
                    GbSwitchTile(
                        icon = Icons.Outlined.AccountCircle, title = "Notifications with profile picture", subtitle = "Show avatar next to typing alert", value = p.typingWithProfilePic,
                        onChanged = { preferences.updateToasts(preferences.toasts.copy(typingToastWithProfilePicture = it)) },
                    )
                    GbSwitchTile(
                        icon = com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.layers), title = "Elevation", subtitle = "Show a shadow under the toast", value = p.typingElevation,
                        onChanged = { preferences.updateToasts(preferences.toasts.copy(typingToastElevation = it)) },
                    )
                    GbSliderTile(
                        title = "Radius of toast notifications", subtitle = "Rounded corners radius · ${typingRadius.toInt()}dp", value = typingRadius.toFloat(), min = 0f, max = 30f, divisions = 15,
                        onChanged = { typingRadiusDrag = it },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.VerticalAlignTop, title = "Position of Toast", subtitle = p.typingPosition, hasSubScreen = true,
                        onTap = { showPositionModal(p.typingPosition) { v -> preferences.updateToasts(preferences.toasts.copy(typingToastPosition = v)) } },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.FormatPaint, title = "Toast Background Color", subtitle = colorSubtitle(p.typingBgColor, "Default"), colorValue = p.typingBgColor,
                        onTap = { pickColor("Typing Toast Background Color", p.typingBgColor) { c -> preferences.updateToasts(preferences.toasts.copy(typingToastBgColor = c)) } },
                        onColorTap = { pickColor("Typing Toast Background Color", p.typingBgColor) { c -> preferences.updateToasts(preferences.toasts.copy(typingToastBgColor = c)) } },
                    )
                    GbSettingRow(
                        icon = Icons.Rounded.FormatColorText, title = "Toast Text Color", subtitle = colorSubtitle(p.typingTextColor, "Auto (readable on background)"), colorValue = p.typingTextColor,
                        onTap = { pickColor("Typing Toast Text Color", p.typingTextColor) { c -> preferences.updateToasts(preferences.toasts.copy(typingToastTextColor = c)) } },
                        onColorTap = { pickColor("Typing Toast Text Color", p.typingTextColor) { c -> preferences.updateToasts(preferences.toasts.copy(typingToastTextColor = c)) } },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            // NTF-R1-060: restore the whole toasts group to its defaults (constructible defaults model).
            GbCardContainer {
                GbSettingRow(
                    icon = Icons.Rounded.Refresh, title = "Reset toast settings", subtitle = "Restore the default toast appearance and behavior for all four toast types", hasSubScreen = false,
                    onTap = { confirmReset = true },
                )
            }
            // NTF-R3-044: keep the last card clear of the system gesture bar.
            Spacer(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)).height(24.dp))
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset toast settings?") },
            text = { Text("This restores the default appearance and behavior for all four toast types.") },
            confirmButton = {
                TextButton(onClick = {
                    preferences.updateToasts(com.example.chat.domain.models.NotificationToastPreferences())
                    confirmReset = false
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
    pickers.Host()
}
