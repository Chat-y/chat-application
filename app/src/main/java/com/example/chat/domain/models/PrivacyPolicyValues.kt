package com.example.chat.domain.models

import java.util.Locale

/** Stable values with a legacy preference-label adapter for mixed-version settings stores. */
enum class PrivacyAudienceValue(val wire: String, val preference: String) {
    EVERYONE("everyone", "Everyone"), CONTACTS("contacts", "My Contacts"),
    CONTACTS_EXCEPT("contacts_except", "My Contacts Except..."), NOBODY("nobody", "Nobody");
    companion object {
        fun parse(value: String?): PrivacyAudienceValue? {
            val key = value?.trim()?.replace("…", "...")?.lowercase(Locale.ROOT) ?: return null
            return entries.firstOrNull { it.wire == key || it.preference.lowercase(Locale.ROOT) == key }
        }
    }
}

enum class PrivacyOnlineValue(val wire: String, val preference: String) {
    EVERYONE("everyone", "Everyone"), SAME_AS_LAST_SEEN("same_as_last_seen", "Same as Last Seen");
    companion object {
        fun parse(value: String?): PrivacyOnlineValue? {
            val key = value?.trim()?.lowercase(Locale.ROOT) ?: return null
            return entries.firstOrNull { it.wire == key || it.preference.lowercase(Locale.ROOT) == key }
        }
    }
}
