package com.example.chat.ui.chats.detail

/** Filename-only namespace. JSON schema unchanged. Never include credentials or raw IDs in paths.
 * Length framing prevents ambiguous account/route/chat concatenation. Anonymous drafts disabled. */
internal fun reviewDraftScopeKey(userId: String?, tenantId: String, baseUrl: String, conversationId: String): String? {
    if (userId.isNullOrBlank() || conversationId.isBlank()) return null
    val framed = listOf(userId, tenantId, baseUrl, conversationId).joinToString("") { "${it.length}:$it" }
    return java.security.MessageDigest.getInstance("SHA-256").digest(framed.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
