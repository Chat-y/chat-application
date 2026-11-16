package com.example.chat.ui.media

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Private bounded snapshot for editors or explicit external-open/share actions. Never overwrites input. */
internal suspend fun stageMediaSource(context: Context, source: String, prefix: String): File {
    var pending: File? = null
    try {
        return withContext(Dispatchers.IO) {
            val directory = if (prefix == "chaty_shared_") File(context.cacheDir, "chaty_shared_exports").apply {
                check(isDirectory || mkdirs()) { "Export storage unavailable" }
                val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
                listFiles()?.filter { it.isFile && it.lastModified() < cutoff }?.forEach { it.delete() }
            } else context.cacheDir
            val file = File.createTempFile(prefix, ".bin", directory)
            pending = file
            openMediaInput(context, source).use { input -> file.outputStream().use { output ->
                require(copyMediaBounded(input, output, FileSanitizationService.MAX_SAFE_FILE_SIZE.toLong()) > 0) { "Empty attachment" }
            } }
            file
        }
    } catch (failure: Throwable) { pending?.delete(); throw failure }
}

internal fun openMediaInput(context: Context, source: String): InputStream {
    val uri = Uri.parse(source)
    return when (uri.scheme?.lowercase(java.util.Locale.ROOT)) {
        "http", "https" -> {
            val connection = URL(source).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.connect()
                require(connection.responseCode in 200..299) { "Attachment download failed" }
                require(connection.contentLengthLong <= FileSanitizationService.MAX_SAFE_FILE_SIZE.toLong()) { "Attachment exceeds file-size limit" }
                object : FilterInputStream(connection.inputStream) {
                    override fun close() { try { super.close() } finally { connection.disconnect() } }
                }
            } catch (failure: Throwable) { connection.disconnect(); throw failure }
        }
        "content" -> context.contentResolver.openInputStream(uri) ?: error("Attachment source is unavailable")
        "file" -> File(requireNotNull(uri.path)).inputStream()
        null -> File(source).inputStream()
        else -> error("Unsupported attachment source")
    }
}
