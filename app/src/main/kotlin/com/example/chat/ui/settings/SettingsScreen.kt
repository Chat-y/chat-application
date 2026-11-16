package com.example.chat.ui.settings

import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesomeMosaic
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RestorePage
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.CurrencyRupee
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Dock
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.HistoryEdu
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.ReplyAll
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.ShieldMoon
import androidx.compose.material.icons.rounded.SpaceDashboard
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Palette
import com.example.chat.ui.kit.ChatyConfirmationDialog as AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.core.theme.ThemeController
import com.example.chat.ui.kit.AppContextMenu
import com.example.chat.ui.kit.ChatyAboutDialog
import com.example.chat.ui.kit.ChatyLicenseEntry
import com.example.chat.ui.kit.ContextMenuItem
import com.example.chat.ui.kit.ContextMenuSection
import com.example.chat.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

/** Screens SettingsScreen (and the search index) can open. The owner maps each to its nav route. */
enum class SettingsScreenDestination {
    PrivacyCenter, SecurityCenter, ThemeEditor, TemplatesSettings, StarredMessages, HomeScreenSettings,
    ConversationSettings, MessageManagement, NavigationEffects, NotificationSettings, SystemPermissions,
    ProPricing, LinkedDevicesOnly, AccountSettings, NewChatGroupMode, NewChatBroadcastMode, UniversalScreen,
    UniversalAppearance,
}

/** Dart SettingsSearchResult with the destination Widget replaced by [destination]. */
data class SettingsSearchEntry(
    val title: String,
    val category: String,
    val description: String,
    val icon: ImageVector,
    val destination: SettingsScreenDestination,
    val keywords: List<String> = emptyList(),
)

/** The fields SettingsScreen reads from dataStore.currentUser. */
data class SettingsProfileUser(val displayName: String, val about: String, val avatarInitials: String, val username: String)

/** Port of _buildSearchIndex (18 entries, same order and copy; keywords added for synonym search). */
fun buildSettingsSearchIndex(): List<SettingsSearchEntry> {
    // SET-R2-005: synonym keywords so 'lock', 'theme', 'sound' etc. match
    val kw = mapOf(
        "Freeze Last Seen" to listOf("online", "status", "timestamp"),
        "Hide Last Seen Audience" to listOf("online", "visibility", "who can see"),
        "Read Receipts (Blue Ticks)" to listOf("seen", "ticks", "read"),
        "Anti-Delete Messages" to listOf("deleted", "revoke", "retain"),
        "Chaty App Lock" to listOf("lock", "pin", "biometric", "fingerprint", "pattern", "password"),
        "Chaty Themes" to listOf("theme", "dark", "light", "color", "colour"),
        "Global Template Studio" to listOf("template", "layout", "style"),
        "Starred Messages" to listOf("star", "saved", "favorite", "bookmark"),
        "Home UI Style" to listOf("home", "layout", "compact", "classic"),
        "Instagram Stories Bar" to listOf("stories", "status", "avatars"),
        "Ghost Mode" to listOf("stealth", "hide", "private"),
        "Bubble Shapes & Corner Radius" to listOf("bubble", "chat", "shape", "font"),
        "Quick Contact Sidebar" to listOf("sidebar", "contacts", "switcher"),
        "Auto-Reply Rules" to listOf("auto reply", "bot", "automation"),
        "Message Scheduler" to listOf("schedule", "later", "timer"),
        "Interactive Click Particles" to listOf("particles", "effects", "touch", "stars", "hearts"),
        "Notification Sounds & LED" to listOf("sound", "tone", "ringtone", "vibration", "led"),
        "System Permissions & Hardware" to listOf("camera", "microphone", "storage", "battery", "permission"),
    )
    fun e(t: String, c: String, d: String, i: ImageVector, dest: SettingsScreenDestination) = SettingsSearchEntry(t, c, d, i, dest, kw[t].orEmpty())
    return listOf(
        e("Freeze Last Seen", "Privacy", "Freeze last visible timestamp", Icons.Rounded.AcUnit, SettingsScreenDestination.PrivacyCenter),
        e("Hide Last Seen Audience", "Privacy", "Restrict last seen visibility to Everyone, Contacts, Nobody", Icons.Rounded.VisibilityOff, SettingsScreenDestination.PrivacyCenter),
        e("Read Receipts (Blue Ticks)", "Privacy", "Enable or disable read receipt indicators", Icons.Rounded.DoneAll, SettingsScreenDestination.PrivacyCenter),
        e("Anti-Delete Messages", "Privacy", "Retain deleted messages marked \"Deleted by sender\"", Icons.Rounded.DeleteForever, SettingsScreenDestination.PrivacyCenter),
        e("Chaty App Lock", "Security", "Biometric, PIN, Pattern & Password protection", Icons.Rounded.Lock, SettingsScreenDestination.SecurityCenter),
        e("Chaty Themes", "Themes", "13+ dark & light theme presets and token editor", Icons.Rounded.Palette, SettingsScreenDestination.ThemeEditor),
        e("Global Template Studio", "Themes & Appearance", "Apply app-wide templates and independently customize navigation, home, chat list, bubbles, composer, updates, profile and calls", Icons.Rounded.DashboardCustomize, SettingsScreenDestination.TemplatesSettings),
        e("Starred Messages", "Messages", "Search, open and remove saved messages across conversations", Icons.Rounded.StarOutline, SettingsScreenDestination.StarredMessages),
        e("Home UI Style", "Home Screen", "Default, Classic, Compact, Expressive, Stories First", Icons.Rounded.SpaceDashboard, SettingsScreenDestination.HomeScreenSettings),
        e("Instagram Stories Bar", "Home Screen", "Horizontal story avatars above chats", Icons.Rounded.HistoryEdu, SettingsScreenDestination.HomeScreenSettings),
        e("Ghost Mode", "Home Screen", "One-tap total stealth privacy bundle", Icons.Rounded.ShieldMoon, SettingsScreenDestination.HomeScreenSettings),
        e("Bubble Shapes & Corner Radius", "Conversation", "Rounded, Tail, Squircle, Compact bubble shapes", Icons.Rounded.ChatBubbleOutline, SettingsScreenDestination.ConversationSettings),
        e("Quick Contact Sidebar", "Conversation", "Docked contact switcher panel", Icons.Rounded.Dock, SettingsScreenDestination.ConversationSettings),
        e("Auto-Reply Rules", "Message Management", "Keyword automated response rules", Icons.Rounded.ReplyAll, SettingsScreenDestination.MessageManagement),
        e("Message Scheduler", "Message Management", "Schedule messages for auto execution", Icons.Rounded.Schedule, SettingsScreenDestination.MessageManagement),
        e("Interactive Click Particles", "Navigation Effects", "Tap particle splash symbols and speed", Icons.Rounded.AutoAwesome, SettingsScreenDestination.NavigationEffects),
        e("Notification Sounds & LED", "Notifications", "Conversation tones, LED colors, and heads-up alerts", Icons.Rounded.NotificationsActive, SettingsScreenDestination.NotificationSettings),
        e("System Permissions & Hardware", "Permissions", "Camera, mic, storage, notification access", Icons.Rounded.Security, SettingsScreenDestination.SystemPermissions),
    )
}

/** SET-R2-011: drop bidi-control and zero-width characters that can spoof names. */
@Composable
private fun SettingsSectionHeader(color: Color, title: String) {
    Text(
        title, Modifier.padding(start = 16.dp, top = 4.dp).semantics { heading() },
        fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = color.copy(alpha = 0.8f), letterSpacing = 0.9.sp,
    )
}

@Composable
private fun SettingsTile(icon: ImageVector, tint: Color, title: String, subtitle: String, onTap: () -> Unit) =
    ChatySettingsTile(title = title, icon = icon, iconColor = tint, subtitle = subtitle, onTap = onTap)

private fun stripSpoofChars(s: String): String =
    s.filterNot { it in '\u200B'..'\u200F' || it in '\u202A'..'\u202E' || it in '\u2066'..'\u2069' || it == '\uFEFF' }

private enum class SettingsSheet { EditProfile, Reset, ConfirmResetAll, ConfirmResetPrivacy, ConfirmResetTheme, Export, About }

/**
 * Port of lib/features/settings/settings_screen.dart. Navigation goes through [onNavigate]; the search
 * UI (SettingsSearchDelegate, another worker) through [onOpenSearch]. Dart read dataStore.currentUser:
 * [user] / [onSaveProfile] / [onLogout] stand in for dataStore.updateProfile / dataStore.logout +
 * pushAndRemoveUntil(WelcomeScreen). Uncompiled; no pixel check.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferences: ChatyPreferencesController,
    themeController: ThemeController,
    user: SettingsProfileUser,
    onSaveProfile: (displayName: String, about: String) -> Unit,
    onNavigate: (SettingsScreenDestination) -> Unit,
    onOpenSearch: (List<SettingsSearchEntry>) -> Unit,
    onLogout: () -> Unit,
    onBack: (() -> Unit)?,
    licenses: List<ChatyLicenseEntry> = ChatyLicenseData.entries,
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var themeTick by remember { mutableStateOf(0) }
    DisposableEffect(themeController) {
        val l: () -> Unit = { themeTick++ }
        themeController.addListener(l)
        onDispose { themeController.removeListener(l) }
    }
    @Suppress("UNUSED_VARIABLE") val tick = themeTick
    val theme = themeController.globalTheme
    var sheet by remember { mutableStateOf<SettingsSheet?>(null) }
    // SET-R1-032: static index, built once per composition instead of per search tap
    val searchIndex = remember { buildSettingsSearchIndex() }
    var resetTyped by remember { mutableStateOf("") }
    val appContext = androidx.compose.ui.platform.LocalContext.current
    // SET-R1-060: real version instead of a hardcoded string
    val appVersionText = remember {
        try {
            val info = if (android.os.Build.VERSION.SDK_INT >= 33) appContext.packageManager.getPackageInfo(appContext.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            else @Suppress("DEPRECATION") appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            val code = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
            // SET-R2-018: flag debuggable builds for support
            val debug = if ((appContext.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) "-debug" else ""
            "${info.versionName ?: "Unknown"}$debug (Build $code)"
        } catch (e: Exception) { "Unknown" }
    }
    val accent = Color(theme.accentColor)
    val primaryText = Color(theme.primaryTextColor)
    val secondaryText = Color(theme.secondaryTextColor)

    fun toast(msg: String) { scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(msg) } }

    fun nav(d: SettingsScreenDestination): () -> Unit = { onNavigate(d) }

    Box(Modifier.fillMaxSize().background(Color(theme.backgroundColor))) {
        Column(
            Modifier.fillMaxSize().semantics { paneTitle = "Settings" }.safeDrawingPadding().verticalScroll(androidx.compose.runtime.saveable.rememberSaveable(saver = androidx.compose.foundation.ScrollState.Saver) { androidx.compose.foundation.ScrollState(0) })
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                // SET-R1-001: only show Back when there is somewhere to go back to.
                if (onBack != null) IconButton(onClick = { onBack.invoke() }) { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.back, semanticLabel = "Back", modifier = Modifier.size(22.dp), color = primaryText) } else Spacer(Modifier.size(48.dp))
                Row {
                    IconButton(onClick = { onOpenSearch(searchIndex) }) { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.search, semanticLabel = "Search settings", modifier = Modifier.size(22.dp), color = primaryText) }
                    IconButton(onClick = { sheet = SettingsSheet.Export }) { Icon(Icons.Rounded.IosShare, "Export chats", Modifier.size(22.dp), tint = primaryText) }
                    IconButton(onClick = { sheet = SettingsSheet.EditProfile }) { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.edit, semanticLabel = "Edit profile", modifier = Modifier.size(22.dp), color = primaryText) }
                }
            }
            Spacer(Modifier.height(10.dp))

            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    Modifier
                        .minimumInteractiveComponentSize()
                        .background(colors.surfaceSecondary, RoundedCornerShape(18.dp))
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(18.dp))
                        .clickable(role = androidx.compose.ui.semantics.Role.Button) { sheet = SettingsSheet.EditProfile }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Mood, null, Modifier.size(16.dp), tint = accent)
                    Spacer(Modifier.width(6.dp))
                    Text(if (user.about.isNotEmpty()) user.about else "Current mood", color = primaryText, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier.size(86.dp)
                        // SET-R1-057: tinted shadow dropped (was .shadow(8.dp, CircleShape, ambientColor = accent.copy(alpha = 0.25f), spotColor = accent.copy(alpha = 0.25f))); ring 2dp, subtler
                        .border(2.dp, accent.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(80.dp).background(colors.surfaceSecondary, CircleShape), contentAlignment = Alignment.Center) {
                        Text(
                            if (user.avatarInitials.isNotEmpty()) user.avatarInitials
                            else if (user.displayName.isNotEmpty()) user.displayName.substring(0, 1).uppercase() else "U",
                            fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = primaryText,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.minimumInteractiveComponentSize().clickable { sheet = SettingsSheet.EditProfile }, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (user.displayName.isNotEmpty()) user.displayName
                        else if (user.username.isNotEmpty()) user.username else "User",
                        color = primaryText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(22.dp), tint = secondaryText)
                }
                Spacer(Modifier.height(3.dp))
                if (user.username.isNotEmpty()) Text("@${user.username}", color = secondaryText, fontSize = 13.5.sp)
            }
            Spacer(Modifier.height(24.dp))

            ChatySettingsCard {
                Column {
                    SettingsTile(com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.rupee), colors.primary, "Payments", "Chaty Pro plans, UPI & billing", nav(SettingsScreenDestination.ProPricing))
                    ChatySettingsRowDivider()
                    SettingsTile(Icons.Outlined.Diamond, colors.warning, "Subscriptions", "Explore premium benefits", nav(SettingsScreenDestination.ProPricing))
                    ChatySettingsRowDivider()
                    SettingsTile(Icons.Rounded.Devices, colors.info, "Linked devices", "Use Chaty on web and desktop devices", nav(SettingsScreenDestination.LinkedDevicesOnly))
                    ChatySettingsRowDivider()
                    SettingsTile(Icons.Outlined.Person, colors.success, "Account", "Profile photo, username & details", nav(SettingsScreenDestination.AccountSettings))
                    ChatySettingsRowDivider()
                    SettingsTile(Icons.Outlined.Lock, colors.primary, "Privacy", "Blocked accounts, disappearing messages", nav(SettingsScreenDestination.PrivacyCenter))
                    ChatySettingsRowDivider()
                    SettingsTile(Icons.Outlined.People, colors.accent, "Lists", "Manage people and contact groups", nav(SettingsScreenDestination.NewChatGroupMode))
                    ChatySettingsRowDivider()
                    SettingsTile(Icons.Outlined.ChatBubbleOutline, colors.success, "Chats", "Chat history, backup", nav(SettingsScreenDestination.ConversationSettings))
                    ChatySettingsRowDivider()
                    SettingsTile(Icons.Outlined.Palette, colors.warning, "Appearance", "Chat theme, app icon, app theme", nav(SettingsScreenDestination.UniversalScreen))
                    ChatySettingsRowDivider()
                    SettingsTile(Icons.Rounded.Cast, colors.primary, "Broadcasts", "Create and manage broadcast recipient lists", nav(SettingsScreenDestination.NewChatBroadcastMode))
                }
            }
            Spacer(Modifier.height(18.dp))

            SettingsSectionHeader(secondaryText, "PRIVACY & SECURITY")
            Spacer(Modifier.height(8.dp))
            ChatySettingsCard {
                Column {
                    SettingsTile(Icons.Outlined.Shield, colors.info, "Privacy Center", "Freeze last seen, anti-delete, read receipts & stealth", nav(SettingsScreenDestination.PrivacyCenter))
                    ChatySettingsRowDivider()
                    SettingsTile(Icons.Outlined.Lock, colors.primary, "Security Center", "Chaty App Lock (Biometric/PIN/Pattern), devices & audit", nav(SettingsScreenDestination.SecurityCenter))
                    ChatySettingsRowDivider()
                    SettingsTile(Icons.Outlined.VpnKey, colors.success, "System Permissions & Hardware", "Camera, mic, storage, background refresh & battery", nav(SettingsScreenDestination.SystemPermissions))
                }
            }
            Spacer(Modifier.height(18.dp))

            SettingsSectionHeader(secondaryText, "THEMES & APPEARANCE")
            Spacer(Modifier.height(8.dp))
            ChatySettingsCard {
                Column {
                    SettingsTile(Icons.Outlined.Palette, colors.accent, "Themes & Color Engine", "13+ dark & light presets, custom tokens, font scaling", nav(SettingsScreenDestination.ThemeEditor))
                    ChatySettingsRowDivider()
                    SettingsTile(Icons.Rounded.DashboardCustomize, colors.primary, "Global Template Studio", "Apply a complete app template or mix component styles across navigation, chats, composer, updates, profile and calls", nav(SettingsScreenDestination.TemplatesSettings))
                    ChatySettingsRowDivider()
                    SettingsTile(Icons.Outlined.AutoAwesomeMosaic, colors.primary, "Universal Appearance", "App icon changer, custom emojis, typography styles", nav(SettingsScreenDestination.UniversalAppearance))
                    ChatySettingsRowDivider()
                    SettingsTile(Icons.Outlined.TouchApp, colors.warning, "Navigation & Touch Effects", "Interactive click particles (Stars/Hearts/Bubbles), Sound FX", nav(SettingsScreenDestination.NavigationEffects))
                }
            }
            Spacer(Modifier.height(18.dp))

            SettingsSectionHeader(secondaryText, "CHATS & NOTIFICATIONS")
            Spacer(Modifier.height(8.dp))
            ChatySettingsCard {
                Column {
                    SettingsTile(Icons.Outlined.SpaceDashboard, colors.info, "Home Screen UI Layout", "Classic, compact, expressive, stories-first styles", nav(SettingsScreenDestination.HomeScreenSettings))
                    ChatySettingsRowDivider()
                    SettingsTile(Icons.Outlined.ChatBubbleOutline, colors.success, "Conversation Customization", "Bubble shapes, corner radius, tick styles, contact sidebar", nav(SettingsScreenDestination.ConversationSettings))
                    ChatySettingsRowDivider()
                    SettingsTile(Icons.Outlined.Star, colors.primary, "Starred Messages", "Search, open and remove saved messages across conversations", nav(SettingsScreenDestination.StarredMessages))
                    ChatySettingsRowDivider()
                    SettingsTile(Icons.Outlined.Notifications, colors.warning, "Notification & Sound Studio", "Conversation tones, LED colors, heads-up popups, vibration", nav(SettingsScreenDestination.NotificationSettings))
                    ChatySettingsRowDivider()
                    SettingsTile(Icons.Outlined.SmartToy, colors.success, "Message Automation & Scheduler", "Auto-reply bot with keyword rules, message scheduler", nav(SettingsScreenDestination.MessageManagement))
                }
            }
            Spacer(Modifier.height(18.dp))

            SettingsSectionHeader(secondaryText, "DATA & SYSTEM")
            Spacer(Modifier.height(8.dp))
            ChatySettingsCard {
                Column {
                    SettingsTile(Icons.Outlined.Backup, colors.info, "Backup & Export Data", "Export encrypted chats JSON, backup verification") { sheet = SettingsSheet.Export }
                    ChatySettingsRowDivider()
                    SettingsTile(Icons.Rounded.RestartAlt, colors.error, "Reset & Diagnostics", "Reset privacy settings, themes, or restore factory defaults") { sheet = SettingsSheet.Reset }
                    ChatySettingsRowDivider()
                    SettingsTile(Icons.Outlined.Info, colors.foregroundSecondary, "About Chaty", "Version, build information and licenses") { sheet = SettingsSheet.About }
                }
            }
            Spacer(Modifier.height(24.dp))

            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().height(48.dp).semantics { contentDescription = "Log out of Chaty. Asks for confirmation." },
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.2.dp, colors.error),
            ) {
                com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.logout, semanticLabel = null, modifier = Modifier.size(18.dp), color = colors.error)
                Spacer(Modifier.width(8.dp))
                Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.settings_main_log_out_of_chaty), color = colors.error, fontWeight = FontWeight.Bold, fontSize = 14.5.sp) // was: "Log Out of Chaty"
            }
            Spacer(Modifier.height(90.dp))
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    when (sheet) {
        SettingsSheet.EditProfile -> {
            var name by remember { mutableStateOf(user.displayName) }
            var about by remember { mutableStateOf(user.about) }
            ModalBottomSheet(dragHandle = { com.example.chat.ui.kit.ChatySheetGrabber() }, tonalElevation = 0.dp,
                onDismissRequest = { sheet = null },
                containerColor = colors.surface,
                shape = com.example.chat.ui.kit.ChatyModalShape,
            ) {
                Column(Modifier.fillMaxWidth().semantics { paneTitle = "Edit profile details" }.imePadding().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp)) {
                    Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.settings_main_edit_profile_details), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.foreground) // was: "Edit Profile Details"
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(name, { if (it.length <= 50) name = it }, Modifier.fillMaxWidth(), label = { Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.settings_main_display_name)) }, // was: "Display Name"
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words, imeAction = androidx.compose.ui.text.input.ImeAction.Next),
                        isError = stripSpoofChars(name).isBlank(), supportingText = { Text(if (stripSpoofChars(name).isBlank()) "Name can't be empty" else "${name.length}/50") }, singleLine = true)
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(about, { if (it.length <= 140) about = it }, Modifier.fillMaxWidth(), label = { Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.settings_main_bio_about_status)) }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done), supportingText = { Text("${about.length}/140") }) // was: "Bio / About Status"
                    Spacer(Modifier.height(20.dp))
                    Button(
                        // Prior validation used raw name before spoof-control removal.
                        onClick = { val cleanedName = stripSpoofChars(name).trim(); if (cleanedName.isNotEmpty()) { onSaveProfile(cleanedName, stripSpoofChars(about).trim()); sheet = null } },
                        enabled = stripSpoofChars(name).isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                    ) { Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.settings_main_save_profile), fontWeight = FontWeight.Bold) } // was: "Save Profile"
                }
            }
        }
        SettingsSheet.Reset -> AppContextMenu(
            onDismiss = { sheet = null },
            title = "Reset & Preferences",
            subtitle = "Restore settings or presets",
            sections = listOf(
                ContextMenuSection(
                    items = listOf(
                        ContextMenuItem("Reset Theme to Default B&W", icon = Icons.Rounded.Restore, subtitle = "Restore default monochrome theme", onTap = {
                            sheet = SettingsSheet.ConfirmResetTheme
                        }),
                        ContextMenuItem("Reset Privacy Options", icon = Icons.Outlined.Shield, subtitle = "Reset last seen and stealth toggles", onTap = {
                            sheet = SettingsSheet.ConfirmResetPrivacy
                        }),
                    ),
                ),
                ContextMenuSection(
                    title = "Danger Zone",
                    items = listOf(
                        ContextMenuItem("Reset ALL Preferences", icon = Icons.Rounded.CleaningServices, subtitle = "Restore factory defaults", isDestructive = true, onTap = {
                            // SET-R1-004: destructive, so ask first.
                            sheet = SettingsSheet.ConfirmResetAll
                        }),
                    ),
                ),
            ),
        )
        SettingsSheet.ConfirmResetTheme -> AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
            onDismissRequest = { sheet = null },
            title = { Text("Reset theme?") },
            text = { Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.settings_main_the_theme_returns_to_the_default_monochr)) }, // was: "The theme returns to the default monochrome look."
            dismissButton = { TextButton(onClick = { sheet = null }) { Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.settings_main_cancel)) } }, // was: "Cancel"
            confirmButton = { TextButton(onClick = { themeController.resetToDefaults(); sheet = null; toast("Theme reset to default.") }) { Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.settings_main_reset), color = colors.error) } }, // was: "Reset"
        )
        SettingsSheet.ConfirmResetAll -> AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
            onDismissRequest = { sheet = null; resetTyped = "" },
            title = { Text("Reset all preferences?") },
            text = {
                Column {
                    Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.settings_main_this_restores_every_setting_and_the_them)) // was: "This restores every setting and the theme to factory defaults. This cannot be undone. Type RESET to confirm."
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(resetTyped, { resetTyped = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.settings_main_type_reset)) }) // was: "Type RESET"
                }
            },
            dismissButton = { TextButton(onClick = { sheet = null; resetTyped = "" }) { Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.settings_main_cancel)) } }, // was: "Cancel"
            confirmButton = {
                TextButton(enabled = resetTyped.trim() == "RESET", onClick = {
                    preferences.resetAll(); themeController.resetToDefaults(); sheet = null; resetTyped = ""
                    toast("All preferences reset to factory defaults.")
                }) { Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.settings_main_reset), color = colors.error) } // was: "Reset"
            },
        )
        SettingsSheet.ConfirmResetPrivacy -> AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
            onDismissRequest = { sheet = null },
            title = { Text("Reset privacy options?") },
            text = { Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.settings_main_last_seen_read_receipts_and_stealth_opti)) }, // was: "Last seen, read receipts and stealth options will return to their defaults."
            dismissButton = { TextButton(onClick = { sheet = null }) { Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.settings_main_cancel)) } }, // was: "Cancel"
            confirmButton = { TextButton(onClick = { preferences.resetPrivacy(); sheet = null; toast("Privacy settings reset.") }) { Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.settings_main_reset), color = colors.error) } }, // was: "Reset"
        )
        SettingsSheet.Export -> AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
            onDismissRequest = { sheet = null },
            title = { Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.settings_main_backup_export)) }, // was: "Backup & Export"
            text = { Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.settings_main_chat_export_is_coming_soon_nothing_has_b)) }, // was: "Chat export is coming soon. Nothing has been exported or backed up by this screen yet."
            dismissButton = { TextButton(onClick = { sheet = null }) { Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.settings_main_close)) } }, // was: "Close"
            // SET-R1-003: no false success. Export is disabled until it exists.
            confirmButton = { Button(onClick = {}, enabled = false) { Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.settings_main_export_json_coming_soon)) } }, // was: "Export JSON (coming soon)"
        )
        SettingsSheet.About -> ChatyAboutDialog(
            applicationName = "Chaty",
            applicationVersion = appVersionText,
            applicationLegalese = "\u00A9 2026 LOGY BYTE. All rights reserved.",
            onDismiss = { sheet = null },
            licenses = licenses,
        ) {
            Spacer(Modifier.height(12.dp))
            Text(androidx.compose.ui.res.stringResource(com.example.chat.R.string.settings_main_deeply_customizable_instant_messaging_ap)) // was: "Deeply customizable instant messaging application. Standard chats use HTTPS transport encryption and are not end-to-end encrypted in this version."
        }
        null -> Unit
    }
}

// Superseded source retained inactive:
//                         isError = name.isBlank(), supportingText = { Text(if (name.isBlank()) "Name can't be empty" else "${name.length}/50") }, singleLine = true)
//                         onClick = { onSaveProfile(stripSpoofChars(name).trim(), stripSpoofChars(about).trim()); sheet = null },
//                         enabled = name.isNotBlank(),
