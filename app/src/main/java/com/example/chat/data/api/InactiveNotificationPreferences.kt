package com.example.chat.data.api

/** Absent means a fresh account's documented defaults. Present-but-malformed never grants disclosure. */
object InactiveNotificationPreferences {
    data class Snapshot(val enabled: Boolean, val protectedIds: Set<String>)
    fun decode(root: Map<String, Any?>?): Snapshot? {
        if (root == null) return Snapshot(true, emptySet())
        val notification = root["notification"] as? Map<*, *> ?: return null
        val enabled = notification["enableGlobalNotifications"] as? Boolean ?: return null
        val security = root["security"] as? Map<*, *> ?: return null
        val protected = mutableSetOf<String>()
        for (key in listOf("lockedConversationIds", "hiddenConversationIds")) {
            val values = security[key] as? List<*> ?: return null
            if (values.any { it !is String }) return null
            protected.addAll(values.filterIsInstance<String>())
        }
        return Snapshot(enabled, protected)
    }
}
