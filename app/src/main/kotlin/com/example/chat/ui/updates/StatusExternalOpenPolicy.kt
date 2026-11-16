package com.example.chat.ui.updates

import java.net.URI
import java.time.Instant

/** Local safety check only. URL possession never proves remote authorization. */
internal object StatusExternalOpenPolicy {
    fun canOpen(url: String, expiresAt: Instant, now: Instant = Instant.now()): Boolean {
        if (!expiresAt.isAfter(now)) return false
        val uri = try { URI(url) } catch (_: Exception) { return false }
        return uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() && uri.rawUserInfo == null && !uri.isOpaque
    }
}
