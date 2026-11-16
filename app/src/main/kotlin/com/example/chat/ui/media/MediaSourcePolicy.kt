package com.example.chat.ui.media

import java.io.File
import java.net.URI
import java.util.Locale

/** MV-R2-020: viewer source allowlist. Never treats arbitrary device paths as attachments. */
internal fun isAllowedViewerSource(source: String, appDirectories: List<File>): Boolean = runCatching {
    if (source.isBlank()) return false
    // Gallery/derivative callers also pass raw absolute filenames. Spaces, '#' and '?'
    // are literal filename characters here, not URI syntax. Still enforce canonical roots.
    if (File(source).isAbsolute) return isViewerFileWithinRoots(File(source), appDirectories)
    val uri = URI(source)
    when (uri.scheme?.lowercase(Locale.ROOT)) {
        "https" -> !uri.host.isNullOrBlank() && uri.rawUserInfo == null
        "content" -> !uri.rawAuthority.isNullOrBlank() && uri.rawUserInfo == null
        "file", null -> {
            if (uri.scheme != null && !uri.rawAuthority.isNullOrEmpty()) return false
            val file = if (uri.scheme == null) File(source) else File(uri)
            if (!file.isAbsolute) return false
            // Previous inline canonical containment is shared with raw absolute paths.
            isViewerFileWithinRoots(file, appDirectories)
        }
        else -> false
    }
}.getOrDefault(false)

/** MV-R4-022: initial load is not a retry; only three explicit retries are permitted. */
internal fun viewerRetryDelayMillis(retryNumber: Int): Long? = when (retryNumber) {
    0 -> 0L
    1 -> 1_000L
    2 -> 2_000L
    3 -> 4_000L
    else -> null
}

private fun isViewerFileWithinRoots(file: File, appDirectories: List<File>): Boolean {
    val canonical = file.canonicalFile
    return appDirectories.any { directory ->
        val root = directory.canonicalFile
        canonical.path.startsWith(root.path + File.separator) && canonical != root
    }
}
