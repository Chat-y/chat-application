package com.example.chat.ui.profile

/**
 * Shared error-to-copy mapper for the profile flows (edit screen + scoped editor).
 * Known failures get friendly, actionable copy; short messages that were written for
 * users pass through; anything that looks like backend or stack text falls back to
 * [fallback] so internals (hosts, class names, SQL, status codes) never reach the UI.
 */
internal object ProfileErrorMessages {
    private val internalMarkers = listOf(
        "exception", "http", "://", "sql", "stack", "java.", "kotlin.", "okhttp", "socket", "error:",
    )
    private val statusCode = Regex("\\b[1-5][0-9]{2}\\b")

    fun friendly(error: Throwable, fallback: String, isSave: Boolean = false): String {
        val raw = error.message.orEmpty().trim()
        val lower = raw.lowercase()
        return when {
            error is java.net.UnknownHostException ||
                error is java.net.ConnectException ||
                error is java.net.SocketTimeoutException ||
                lower.contains("unable to resolve host") || lower.contains("timeout") || lower.contains("timed out") ->
                "You're offline or the connection is weak. Check your connection and try again."
            lower.contains("401") || lower.contains("unauthor") || lower.contains("session expired") ->
                "Your session has expired. Please sign in again."
            lower.contains("413") || lower.contains("payload too large") || lower.contains("too large") ->
                "That photo is too large. Choose a smaller one."
            lower.contains("415") || lower.contains("unsupported media") || lower.contains("unsupported") ->
                "That file type isn't supported. Try a JPEG or PNG."
            isSave && (lower.contains("409") || lower.contains("already taken") || lower.contains("already exists")) ->
                "That username is already taken. Try another one."
            raw.isNotEmpty() && raw.length <= 100 &&
                internalMarkers.none { lower.contains(it) } && !statusCode.containsMatchIn(raw) -> raw
            else -> fallback
        }
    }
}
