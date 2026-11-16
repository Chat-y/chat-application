package com.example.chat.ui.settings

import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Repository-level owner of the Hide-media marker state (HM3-50, HM3-19): one place that knows
 * the marker folder layout, writes markers idempotently and atomically, asks the system media
 * scanner to re-index, and keeps a local audit trail of setting changes.
 *
 * filesDir is injected so the logic is JVM-unit-testable (HM3-20); Android-only work (the media
 * scan and debug logging) arrives as callbacks.
 *
 * Known limits (HM2-38, HM3-44): markers live in app-private storage; private chat files already
 * stay inside Chaty. A .nomedia marker is a hint to gallery scanners, not a security boundary:
 * existing exported copies, camera captures and cloud backups are unchanged, and gallery apps
 * that ignore .nomedia can still show files (HM1-15, HM1-32).
 */
class HideMediaRepository(
    private val filesDir: File,
    private val cacheDir: File? = null,
    private val debug: Boolean = false,
    private val scanMedia: (File) -> Unit = {},
    private val debugLog: (String) -> Unit = {},
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    enum class MediaKind(val folderName: String) { PHOTOS("Photos"), VIDEOS("Videos"), GIFS("GIFs") }

    data class MarkerState(val photos: Boolean, val videos: Boolean, val gifs: Boolean) {
        val anyOn: Boolean get() = photos || videos || gifs
        val onCount: Int get() = listOf(photos, videos, gifs).count { it }
    }

    val mediaRoot: File get() = File(filesDir, MediaVisibilityMarkers.MEDIA_DIR_NAME)
    private val auditLog: File get() = File(filesDir, MediaVisibilityMarkers.AUDIT_FILE_NAME)
    private val bannerFlag: File get() = File(filesDir, MediaVisibilityMarkers.BANNER_FLAG_FILE_NAME)
    private val loggedOnce = mutableSetOf<String>()

    fun markerFile(kind: MediaKind): File =
        File(File(mediaRoot, kind.folderName), MediaVisibilityMarkers.MARKER_FILE_NAME)

    private fun stateValue(state: MarkerState, kind: MediaKind): Boolean = when (kind) {
        MediaKind.PHOTOS -> state.photos
        MediaKind.VIDEOS -> state.videos
        MediaKind.GIFS -> state.gifs
    }

    /** True when on-disk markers already match the preferences; sync can skip the write (HM3-26). */
    fun isInSync(state: MarkerState): Boolean {
        if (File(mediaRoot, MediaVisibilityMarkers.MARKER_FILE_NAME).exists()) return false
        for (kind in MediaKind.values()) {
            if (markerFile(kind).exists() != stateValue(state, kind)) return false
        }
        return true
    }

    /**
     * Idempotent marker write (HM3-25) with low-storage and read-only handling (HM3-27, HM3-28),
     * followed by one batched media-scanner pass per type folder so the system gallery
     * re-indexes existing files (HM1-09, HM3-15). Scan completion is confirmed through the
     * caller-provided callback's own completion hook (HM3-16).
     */
    fun sync(state: MarkerState): Result<Unit> = runCatching {
        val startedAt = now()
        if (!mediaRoot.isDirectory) {
            val free = filesDir.usableSpace
            if (free in 1 until MIN_FREE_BYTES) throw IOException("Low storage for media markers")
        }
        MediaVisibilityMarkers.sync(mediaRoot, state.photos, state.videos, state.gifs)
        for (kind in MediaKind.values()) {
            runCatching { scanMedia(File(mediaRoot, kind.folderName)) }
        }
        // Debug-only timing record for slow folders (HM3-29).
        if (debug) debugLog("hide-media marker sync took ${now() - startedAt} ms")
    }

    /** Debug-only assertion: marker state must equal the three preferences after a change (HM3-45). */
    fun assertMarkersMatch(state: MarkerState) {
        if (debug) check(isInSync(state)) { "Hide-media marker drift after write: $state" }
    }

    /**
     * Debug drift report used on screen entry; null when consistent (HM3-49). Also flags media
     * files in the cache dir, which galleries can index when the cache is external (HM3-48).
     */
    fun driftReport(state: MarkerState): String? {
        val notes = mutableListOf<String>()
        if (!isInSync(state)) notes += "markers out of sync with preferences ($state)"
        val cache = cacheDir
        if (cache != null && cache.isDirectory &&
            cache.walkTopDown().take(MAX_SCAN_FILES).any { it.isFile && it.extension.lowercase(Locale.US) in MEDIA_EXTENSIONS }
        ) {
            notes += "media files present in cache dir"
        }
        return if (notes.isEmpty()) null else notes.joinToString("; ")
    }

    /** Rate-limited debug log: each distinct message is logged once (HM5-04). */
    fun logDebugOnce(message: String) {
        if (debug && loggedOnce.add(message)) debugLog(message)
    }

    /** Local audit trail of setting changes (HM3-13) and write failures (HM5-09). Local count
     *  only; nothing is uploaded (HM5-08). */
    fun audit(event: String) = appendAudit("ok", event)

    fun auditFailure(event: String) = appendAudit("failed", event)

    private fun appendAudit(result: String, event: String) {
        runCatching {
            val stamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date(now()))
            auditLog.appendText("$stamp $result $event\n")
        }.onFailure { logDebugOnce("hide-media audit write failed") }
    }

    /** Count of media files currently inside the private chat media folders (HM1-30). */
    fun affectedFileCount(): Int {
        var count = 0
        for (kind in MediaKind.values()) {
            val folder = File(mediaRoot, kind.folderName)
            if (!folder.isDirectory) continue
            count += folder.walkTopDown().take(MAX_SCAN_FILES)
                .count { it.isFile && it.name != MediaVisibilityMarkers.MARKER_FILE_NAME }
        }
        return count
    }

    /** First-visit explainer banner flag (HM1-59). */
    fun isBannerSeen(): Boolean = bannerFlag.exists()

    fun markBannerSeen() {
        runCatching { if (!bannerFlag.exists()) bannerFlag.createNewFile() }
    }

    private companion object {
        const val MIN_FREE_BYTES = 1024L * 1024L
        const val MAX_SCAN_FILES = 10_000
        val MEDIA_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "mp4", "webm", "mov", "mp3", "aac", "m4a", "opus")
    }
}
