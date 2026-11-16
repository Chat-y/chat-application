package com.example.chat.ui.settings.notifications

import com.example.chat.domain.models.NotificationToastPreferences

/** One immutable render slice per toast card, independent of other cards. */
internal enum class ToastCardType { online, story, profile, typing }

@androidx.compose.runtime.Immutable
internal data class ToastCardUiState(
    val enabled: Boolean,
    val inBar: Boolean,
    val avatar: Boolean,
    val elevated: Boolean,
    val radius: Double,
    val position: String,
    val ringtone: String,
    val background: Long?,
    val foreground: Long?,
// Previous implementation retained: )
) {
    // Compute display strings once per immutable slice, not inside each row composition.
    val backgroundLabel: String = toastColorSubtitle(background, "Default")
    val foregroundLabel: String = toastColorSubtitle(foreground, "Auto (readable on background)")
    val ringtoneLabel: String = if (ringtone == "Silent") "Silent - no sound will play" else ringtone
}

internal fun toastColorSubtitle(value: Long?, unset: String): String {
    if (value == null) return unset
    val hex = "#" + value.toString(16).uppercase(java.util.Locale.ROOT).padStart(8, '0')
    val name = when (hex) {
        "#FFFFFFFF" -> "White"
        "#FF000000" -> "Black"
        "#FFFF0000" -> "Red"
        "#FF00FF00" -> "Green"
        "#FF0000FF" -> "Blue"
        else -> "Hex " + hex.removePrefix("#").toList().joinToString(" ")
    }
    return "$name ($hex)"
}

internal fun toastCardSlice(type: ToastCardType, prefs: NotificationToastPreferences): ToastCardUiState = when (type) {
    ToastCardType.online -> ToastCardUiState(prefs.onlineToastEnabled, prefs.onlineToastInNotificationBar, prefs.onlineToastWithProfilePicture, prefs.onlineToastElevation, prefs.onlineToastRadius, prefs.onlineToastPosition, prefs.onlineToastRingtone, prefs.onlineToastBgColor, prefs.onlineToastTextColor)
    ToastCardType.story -> ToastCardUiState(prefs.storyToastEnabled, prefs.storyToastInNotificationBar, prefs.storyToastWithProfilePicture, prefs.storyToastElevation, prefs.storyToastRadius, prefs.storyToastPosition, prefs.storyToastRingtone, prefs.storyToastBgColor, prefs.storyToastTextColor)
    ToastCardType.profile -> ToastCardUiState(prefs.profileToastEnabled, prefs.profileToastInNotificationBar, prefs.profileToastWithProfilePicture, prefs.profileToastElevation, prefs.profileToastRadius, prefs.profileToastPosition, prefs.profileToastRingtone, prefs.profileToastBgColor, prefs.profileToastTextColor)
    ToastCardType.typing -> ToastCardUiState(prefs.typingToastEnabled, prefs.typingToastInNotificationBar, prefs.typingToastWithProfilePicture, prefs.typingToastElevation, prefs.typingToastRadius, prefs.typingToastPosition, prefs.typingToastRingtone, prefs.typingToastBgColor, prefs.typingToastTextColor)
}

/** Apply only the selected card to the latest model; keep all other card fields untouched. */
internal fun withToastCardSlice(type: ToastCardType, prefs: NotificationToastPreferences, next: ToastCardUiState): NotificationToastPreferences = when (type) {
    ToastCardType.online -> prefs.copy(
        onlineToastEnabled = next.enabled,
        onlineToastInNotificationBar = next.inBar,
        onlineToastWithProfilePicture = next.avatar,
        onlineToastElevation = next.elevated,
        onlineToastRadius = next.radius,
        onlineToastPosition = next.position,
        onlineToastRingtone = next.ringtone,
        onlineToastBgColor = next.background,
        onlineToastTextColor = next.foreground,
    )
    ToastCardType.story -> prefs.copy(
        storyToastEnabled = next.enabled,
        storyToastInNotificationBar = next.inBar,
        storyToastWithProfilePicture = next.avatar,
        storyToastElevation = next.elevated,
        storyToastRadius = next.radius,
        storyToastPosition = next.position,
        storyToastRingtone = next.ringtone,
        storyToastBgColor = next.background,
        storyToastTextColor = next.foreground,
    )
    ToastCardType.profile -> prefs.copy(
        profileToastEnabled = next.enabled,
        profileToastInNotificationBar = next.inBar,
        profileToastWithProfilePicture = next.avatar,
        profileToastElevation = next.elevated,
        profileToastRadius = next.radius,
        profileToastPosition = next.position,
        profileToastRingtone = next.ringtone,
        profileToastBgColor = next.background,
        profileToastTextColor = next.foreground,
    )
    ToastCardType.typing -> prefs.copy(
        typingToastEnabled = next.enabled,
        typingToastInNotificationBar = next.inBar,
        typingToastWithProfilePicture = next.avatar,
        typingToastElevation = next.elevated,
        typingToastRadius = next.radius,
        typingToastPosition = next.position,
        typingToastRingtone = next.ringtone,
        typingToastBgColor = next.background,
        typingToastTextColor = next.foreground,
    )
}

/** Compare only this card with the existing store defaults; never infer install age/history. */
internal fun toastCardUsesDefaults(type: ToastCardType, card: ToastCardUiState): Boolean =
    card == toastCardSlice(type, NotificationToastPreferences())

/** The accessibility sample is exactly200%, not systemScale*2; normal returns caller density. */
internal fun toastPreviewFontScale(systemScale: Float, largeText: Boolean): Float = if (largeText) 2f else systemScale

/** Undo only appearance fields changed by this edit, and only while they still equal its result.
 * Concurrent/later changes and behavior switches remain untouched.
 */
internal fun undoToastAppearance(before: ToastCardUiState, after: ToastCardUiState, current: ToastCardUiState): ToastCardUiState = current.copy(
    elevated = if (before.elevated != after.elevated && current.elevated == after.elevated) before.elevated else current.elevated,
    radius = if (before.radius != after.radius && current.radius == after.radius) before.radius else current.radius,
    position = if (before.position != after.position && current.position == after.position) before.position else current.position,
    background = if (before.background != after.background && current.background == after.background) before.background else current.background,
    foreground = if (before.foreground != after.foreground && current.foreground == after.foreground) before.foreground else current.foreground,
)

internal fun toastAppearanceChanged(before: ToastCardUiState, after: ToastCardUiState): Boolean =
    before.elevated != after.elevated || before.radius != after.radius || before.position != after.position ||
        before.background != after.background || before.foreground != after.foreground
