package com.example.chat.ui.media

import java.io.File
import kotlinx.coroutines.ensureActive

/** Exact port of lib/features/media/services/file_sanitization_service.dart.
 *
 *  path_provider's getTemporaryDirectory is supplied as the [tempDirectory]
 *  seam (Android: context.cacheDir).
 */
class FileSanitizationService(
    private val tempDirectory: () -> File,
) {
    companion object {
        const val MAX_SAFE_FILE_SIZE = 100 * 1024 * 1024 // 100 MB
        const val MAX_ARCHIVE_ENTRIES = 5000
    }

    fun validateSafePath(targetDirectory: String, requestedFilePath: String): Boolean {
        val canonicalTarget = File(targetDirectory).canonicalPath
        val canonicalFile = File(requestedFilePath).canonicalPath
        return canonicalFile == canonicalTarget || canonicalFile.startsWith(canonicalTarget.trimEnd(File.separatorChar) + File.separator)
    }

    fun sanitizeFileName(inputName: String): String {
        val sanitized = inputName
            .replace(Regex("[\\\\/:*?\"<>|\u0000-\u001F]"), "_")
            .replace(Regex("[\u202A-\u202E\u2066-\u2069]"), "_")
            .trim().trimEnd('.')
        if (sanitized.isEmpty() || sanitized == "." || sanitized == "..") return "unnamed_file"
        if (sanitized.length <= 120) return sanitized
        val extension = sanitized.substringAfterLast('.', "").takeIf { it.isNotEmpty() && it.length <= 16 }?.let { ".$it" }.orEmpty()
        val stem = if (extension.isNotEmpty()) sanitized.dropLast(extension.length) else sanitized
        var end = (120 - extension.length).coerceAtMost(stem.length)
        if (end > 0 && stem[end - 1].isHighSurrogate()) end--
        return stem.take(end) + extension
    }

    suspend fun getScopedTempDirectory(): File {
        val mediaTemp = File(tempDirectory(), "chaty_media")
        if (!mediaTemp.exists()) {
            check(mediaTemp.mkdirs()) { "Could not create temporary media directory" }
        }
        return mediaTemp
    }

    /** DOC-R4-201: dry-run estimate for the cleanup confirmation (same scope and age rules). */
    suspend fun estimateExpiredTempBytes(maxAgeMs: Long = 24 * 60 * 60 * 1000): Long =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            require(maxAgeMs >= 0)
            val dir = getScopedTempDirectory()
            val now = System.currentTimeMillis()
            dir.listFiles()?.filter { it.isFile && it.canonicalFile.parentFile == dir.canonicalFile && now - it.lastModified() > maxAgeMs }
                ?.sumOf { it.length() } ?: 0L
        }

    data class CleanupReport(val deleted: Int, val skipped: Int, val failed: Int, val freedBytes: Long = 0)

    suspend fun cleanExpiredTempFiles(maxAgeMs: Long = 24 * 60 * 60 * 1000): CleanupReport {
        require(maxAgeMs >= 0)
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            var deleted = 0; var skipped = 0; var failed = 0
            var freedBytes = 0L
            try {
                val dir = getScopedTempDirectory()
                val now = System.currentTimeMillis()
                val files = dir.listFiles()
                if (files == null) failed++ else files.forEach { entity ->
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    if (entity.isFile && entity.canonicalFile.parentFile == dir.canonicalFile && now - entity.lastModified() > maxAgeMs) {
                        val size = entity.length()
                        if (entity.delete()) { deleted++; freedBytes += size } else failed++
                    } else skipped++
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { failed++ }
            CleanupReport(deleted, skipped, failed, freedBytes)
        }
    }
}
