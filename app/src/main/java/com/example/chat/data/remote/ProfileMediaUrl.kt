package com.example.chat.data.remote

import java.net.URI
import java.util.Locale

/** Resolve server-relative media references without turning them into device file paths.
 * Does not fetch, rewrite query strings, or claim that the resulting image is reachable. */
object ProfileMediaUrl {
    fun form(raw: String?): String {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty() || value.equals("null", ignoreCase = true)) return "missing"
        return try {
            val uri = URI(value)
            when {
                uri.scheme != null -> if (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) "absolute-http" else "non-http"
                value.startsWith("//") -> "network-relative"
                value.startsWith("/") -> "root-relative"
                else -> "path-relative"
            }
        } catch (_: Exception) { "malformed" }
    }

    fun resolve(raw: String?, apiBase: String): String? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", true) } ?: return null
        return try {
            val supplied = URI(value)
            // A bare query/fragment is not a media reference.
            if (supplied.scheme == null && supplied.rawAuthority == null && supplied.rawPath.isNullOrBlank()) return null
            val resolved = if (supplied.isAbsolute) supplied else {
                val base = URI(apiBase.trim().trimEnd('/') + "/")
                if (base.scheme?.lowercase(Locale.ROOT) !in setOf("http", "https") || base.host.isNullOrBlank() || base.rawUserInfo != null) return null
                base.resolve(supplied)
            }
            if (resolved.scheme?.lowercase(Locale.ROOT) !in setOf("http", "https") || resolved.host.isNullOrBlank() || resolved.rawUserInfo != null) return null
            if (resolved.rawPath.isNullOrBlank() || resolved.rawPath == "/") return null
            resolved.toASCIIString()
        } catch (_: Exception) { null }
    }
}
