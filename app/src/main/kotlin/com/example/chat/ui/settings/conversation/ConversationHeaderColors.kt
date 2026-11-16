package com.example.chat.ui.settings.conversation

/**
 * Header preference contract (all keys belong to ConversationPreferences):
 * actionBarColor: null by default, follows the theme surface; custom value colours the header.
 * contactStatusBgColor: null by default, follows the secondary surface in settings.
 * contactStatusTextColor: null by default, follows secondary text in settings.
 * hideProfilePicture: false by default, hides the header avatar when true.
 * hideContactName: false by default, hides the header name when true.
 * hideCallButton: false by default, hides both header call shortcuts when true.
 * disableContactStatus: false by default, hides the presence/status line when true.
 * This UI resolver validates width and makes valid overrides opaque on read; it never persists them.
 */
internal data class ConversationHeaderColors(val bar: Long, val statusBackground: Long, val statusText: Long)

/** Explicit custom values win; a null override follows the current theme on each read. */
internal fun resolveConversationHeaderColors(
    barOverride: Long?, statusBackgroundOverride: Long?, statusTextOverride: Long?,
    themeSurface: Long, themeSecondarySurface: Long, themeSecondaryText: Long,
): ConversationHeaderColors = ConversationHeaderColors(
    ConversationColorPreference.fromStored(opaqueConversationStoredColor(barOverride)).resolve(themeSurface),
    // Previous: barOverride ?: themeSurface,
    ConversationColorPreference.fromStored(opaqueConversationStoredColor(statusBackgroundOverride)).resolve(themeSecondarySurface),
    // Previous: statusBackgroundOverride ?: themeSecondarySurface,
    ConversationColorPreference.fromStored(opaqueConversationStoredColor(statusTextOverride)).resolve(themeSecondaryText),
    // Previous: statusTextOverride ?: themeSecondaryText,
)
