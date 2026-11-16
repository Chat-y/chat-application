package com.example.chat.ui.settings.privacy

import com.example.chat.domain.models.PrivacyPreferences

internal data class PrivacyDebugRow(val key: String, val local: String, val cachedServer: String)

/** Explicit value allowlist: never include IDs, names, timestamps or arbitrary server strings.
 * Produces UI data only, never writes logs/files or makes a request. */
internal fun privacyDebugRows(local: PrivacyPreferences, calls: String?, lastSeen: String?, online: String?): List<PrivacyDebugRow> {
    fun audience(value: String?): String = when (value) {
        "Everyone", "everyone" -> "everyone"
        "My Contacts", "contacts" -> "contacts"
        "My Contacts Except...", "contacts_except" -> "contacts_except"
        "Nobody", "nobody" -> "nobody"
        "Same as Last Seen", "same_as_last_seen" -> "same_as_last_seen"
        else -> "Unavailable"
    }
    val policies = listOf(
        PrivacyDebugRow("calls", audience(local.whoCanCallMe), audience(calls)),
        PrivacyDebugRow("lastSeen", audience(local.hideLastSeenAudience), audience(lastSeen)),
        PrivacyDebugRow("online", audience(local.hideOnlineAudience), audience(online)),
    )
    val flags = local.toMap().mapNotNull { (key, value) ->
        (value as? Boolean)?.let { PrivacyDebugRow(key, if (it) "On" else "Off", "Local flag") }
    }
    return policies + flags
}
