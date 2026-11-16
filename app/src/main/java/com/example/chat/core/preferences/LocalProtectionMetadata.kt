package com.example.chat.core.preferences

/** Conversation protection membership belongs to this device, not the settings-sync service.
 * Keep all unrelated and future settings fields intact. These transforms do not mutate inputs. */
object LocalProtectionMetadata {
    private val localKeys = setOf("lockedConversationIds", "hiddenConversationIds")

    fun isPresent(snapshot: Map<String, Any?>): Boolean =
        (snapshot[PreferenceKeys.security] as? Map<*, *>)?.keys?.any { it in localKeys } == true

    fun forRemote(snapshot: Map<String, Any?>): Map<String, Any?> {
        val security = snapshot[PreferenceKeys.security] as? Map<*, *> ?: return snapshot
        return snapshot + (PreferenceKeys.security to security.filterKeys { it !in localKeys })
    }

    fun keepLocal(remoteMerged: Map<String, Any?>, local: Map<String, Any?>): Map<String, Any?> {
        val remoteSecurity = remoteMerged[PreferenceKeys.security] as? Map<*, *> ?: emptyMap<Any?, Any?>()
        val localSecurity = local[PreferenceKeys.security] as? Map<*, *> ?: emptyMap<Any?, Any?>()
        val protected = localKeys.associateWith { key -> localSecurity[key] ?: emptyList<String>() }
        return remoteMerged + (PreferenceKeys.security to (remoteSecurity.filterKeys { it !in localKeys } + protected))
    }
}
