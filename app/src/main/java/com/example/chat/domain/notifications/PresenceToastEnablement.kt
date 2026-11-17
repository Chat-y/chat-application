package com.example.chat.domain.notifications

/** Typed toast fields own saved choices. Legacy fields are read only when a typed field is absent. */
object PresenceToastEnablement {
    data class Flags(val online: Boolean, val typing: Boolean)
    private fun bool(value: Any?): Boolean? = when (value) {
        is Boolean -> value
        is String -> when (value.lowercase()) { "true" -> true; "false" -> false; else -> null }
        is Number -> value.toDouble() != 0.0
        else -> null
    }
    fun restore(typed: Map<*, *>?, notification: Map<*, *>?, legacy: Map<*, *>?): Flags {
        fun resolve(typedKey: String, notificationKey: String, legacyKey: String, default: Boolean): Boolean {
            bool(typed?.get(typedKey))?.let { return it }
            val n = bool(notification?.get(notificationKey))
            val g = bool(legacy?.get(legacyKey))
            // Preserve the historical OR rule ONLY while importing data with no typed choice.
            return if (n != null || g != null) (n == true || g == true) else default
        }
        return Flags(resolve("onlineToastEnabled", "notifyContactOnline", "abu_saleh_toast_online", false),
            resolve("typingToastEnabled", "notifyTypingStarted", "abu_saleh_toast_typing", false))
    }
    /** A false master cannot be bypassed by legacy true or per-contact Always. */
    fun allows(master: Boolean, globallyEnabled: Boolean, headsUpDisabled: Boolean): Boolean =
        master && globallyEnabled && !headsUpDisabled
}
