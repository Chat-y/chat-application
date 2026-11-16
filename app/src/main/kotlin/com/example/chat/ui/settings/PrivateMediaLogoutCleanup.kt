package com.example.chat.ui.settings

/** Narrow logout cleanup: invoke the existing owners of disposable decrypted/export temps
 * and incomplete downloads independently. Never enumerate/delete originals, encrypted media
 * entries, user-selected sources, Gallery/MediaStore files or another account's durable store.
 * A successful invocation is not a completed async purge or secure-erasure guarantee. */
internal data class PrivateMediaLogoutCleanupResult(val transientReturned: Boolean, val downloadsReturned: Boolean)

internal fun cleanupPrivateMediaOnLogout(
    purgeTransientTemps: () -> Unit,
    purgeIncompleteDownloads: () -> Unit,
): PrivateMediaLogoutCleanupResult {
    val transient = runCatching { purgeTransientTemps() }.isSuccess
    // Previously an exception in the first call prevented this second owner from being invoked.
    val downloads = runCatching { purgeIncompleteDownloads() }.isSuccess
    return PrivateMediaLogoutCleanupResult(transient, downloads)
}
