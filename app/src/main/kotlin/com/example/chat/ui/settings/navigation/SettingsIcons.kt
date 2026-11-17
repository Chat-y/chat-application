package com.example.chat.ui.settings.navigation

import androidx.compose.material.icons.Icons
// SET-R2-028: keep the old wildcard imports inactive; resolve only referenced symbols.
// import androidx.compose.material.icons.filled.*
// import androidx.compose.material.icons.outlined.*
// import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Preview
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.ViewSidebar
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Hd
import androidx.compose.material.icons.rounded.HistoryToggleOff
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.MarkChatRead
import androidx.compose.material.icons.rounded.OnlinePrediction
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Reply
import androidx.compose.material.icons.rounded.RingVolume
import androidx.compose.material.icons.rounded.ScheduleSend
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.ui.graphics.vector.ImageVector

/** Resolves the Dart `Icons.<name>` identifiers stored in SettingsRegistry (SettingsDefinition.iconName). */
object SettingsIcons {
    fun resolve(name: String?): ImageVector? = when (name) {
        "ac_unit_rounded" -> Icons.Rounded.AcUnit
        "admin_panel_settings_outlined" -> Icons.Outlined.AdminPanelSettings
        "alternate_email_rounded" -> Icons.Rounded.AlternateEmail
        "animation_rounded" -> Icons.Rounded.Animation
        "apps_rounded" -> Icons.Rounded.Apps
        "auto_fix_high_rounded" -> Icons.Rounded.AutoFixHigh
        "auto_stories_outlined" -> Icons.Outlined.AutoStories
        "badge_outlined" -> Icons.Outlined.Badge
        "call_outlined" -> Icons.Outlined.Call
        "chat_bubble_outline_rounded" -> Icons.Rounded.ChatBubbleOutline
        "dashboard_customize_rounded" -> Icons.Rounded.DashboardCustomize
        "dashboard_outlined" -> Icons.Outlined.Dashboard
        "delete_forever_rounded" -> Icons.Rounded.DeleteForever
        "devices_rounded" -> Icons.Rounded.Devices
        "done_all_rounded" -> Icons.Rounded.DoneAll
        "edit_note_rounded" -> Icons.Rounded.EditNote
        "emoji_emotions_outlined" -> Icons.Outlined.EmojiEmotions
        "favorite_outline_rounded" -> Icons.Rounded.FavoriteBorder
        "fingerprint_rounded" -> Icons.Rounded.Fingerprint
        "groups_outlined" -> Icons.Outlined.Groups
        "hd_rounded" -> Icons.Rounded.Hd
        "history_toggle_off_rounded" -> Icons.Rounded.HistoryToggleOff
        "home_outlined" -> Icons.Outlined.Home
        "info_outline_rounded" -> Icons.Outlined.Info
        "lock_outline_rounded" -> Icons.Outlined.Lock
        "login_rounded" -> Icons.Rounded.Login
        "mark_chat_read_rounded" -> Icons.Rounded.MarkChatRead
        "notifications_active_outlined" -> Icons.Outlined.NotificationsActive
        "notifications_outlined" -> Icons.Outlined.Notifications
        "online_prediction_rounded" -> Icons.Rounded.OnlinePrediction
        "palette_outlined" -> Icons.Outlined.Palette
        "person_outline_rounded" -> Icons.Outlined.Person
        "preview_outlined" -> /* Previous stock: Icons.Outlined.Preview */ com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.appearancePreview)
        "qr_code_scanner_rounded" -> Icons.Rounded.QrCodeScanner
        "reply_rounded" -> Icons.Rounded.Reply
        "ring_volume_rounded" -> Icons.Rounded.RingVolume
        "schedule_send_rounded" -> Icons.Rounded.ScheduleSend
        "screenshot_outlined" -> Icons.Outlined.Screenshot
        "storage_rounded" -> Icons.Rounded.Storage
        "text_fields_rounded" -> Icons.Rounded.TextFields
        "timer_outlined" -> Icons.Outlined.Timer
        "view_sidebar_outlined" -> Icons.Outlined.ViewSidebar
        "visibility_off_outlined" -> Icons.Outlined.VisibilityOff
        "visibility_off_rounded" -> Icons.Rounded.VisibilityOff
        "visibility_outlined" -> Icons.Outlined.Visibility
        "wallpaper_rounded" -> Icons.Rounded.Wallpaper
        else -> null
    }

    /** SET-R2-003: never render a registry entry without an icon. */
    fun resolveOrDefault(name: String?): ImageVector =
        resolve(name) ?: run {
            android.util.Log.w("SettingsIcons", "unmapped icon name: $name")
            Icons.Outlined.Settings
        }
}
