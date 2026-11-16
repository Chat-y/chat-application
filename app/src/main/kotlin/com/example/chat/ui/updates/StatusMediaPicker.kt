package com.example.chat.ui.updates

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * FilePicker.pickFiles(type: image | video | audio | any) from StatusService.pickAndPublish.
 * Returns the picked file's bytes + name + MIME for StatusService.publishMediaBytes (which owns the
 * Dart validation: audio flag, empty file, size limit, upload, insert). Null when the picker is cancelled.
 */
@Composable
fun rememberStatusMediaPicker(): suspend (String) -> PickedStatusMedia? {
    val context = LocalContext.current
    var waiter by remember { mutableStateOf<CompletableDeferred<Uri?>?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        waiter?.complete(uri)
        waiter = null
    }
    DisposableEffect(Unit) {
        onDispose { waiter?.cancel(); waiter = null }
    }
    return remember(launcher, context) {
        val pick: suspend (String) -> PickedStatusMedia? = { mediaType: String ->
            check(waiter == null) { "A media picker is already open." }
            val d = CompletableDeferred<Uri?>()
            waiter = d
            val mimes = when (mediaType) {
                "image" -> arrayOf("image/*")
                "video" -> arrayOf("video/*")
                "audio" -> arrayOf("audio/*")
                else -> arrayOf("*/*")
            }
            try {
                launcher.launch(mimes)
                val uri = d.await()
                if (uri == null) null else readPicked(context, uri)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (waiter === d) waiter = null
                throw error
            } finally {
                // Keep a cancelled picker reserved until its callback returns, so a late
                // ActivityResult cannot accidentally complete a different request.
                if (!d.isCompleted) d.cancel()
            }
        }
        pick
    }
}

// Dart StatusService.storageLimitMb (50): no effective limit can exceed it, so nothing larger is read into memory.
private const val MAX_PICK_BYTES = com.example.chat.data.services.StatusService.STORAGE_LIMIT_MB * 1024L * 1024L

private suspend fun readPicked(context: Context, uri: Uri): PickedStatusMedia = withContext(Dispatchers.IO) {
    val coroutineContext = currentCoroutineContext()
    coroutineContext.ensureActive()
    var name = "file"
    var size = -1L
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val s = c.getColumnIndex(OpenableColumns.SIZE)
            if (n >= 0) name = c.getString(n) ?: name
            if (s >= 0 && !c.isNull(s)) size = c.getLong(s)
        }
    }
    coroutineContext.ensureActive()
    if (size > MAX_PICK_BYTES) throw IllegalStateException("Selected media exceeds the ${com.example.chat.data.services.StatusService.STORAGE_LIMIT_MB} MB storage limit.")
    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
        // This seam still materializes bytes; peak-memory/stream-upload design remains separate work.
        coroutineContext.ensureActive()
        StatusMediaReadRules.read(input, MAX_PICK_BYTES) { coroutineContext.ensureActive() }
    } ?: throw IllegalStateException("The selected file is not accessible on this device.")
    coroutineContext.ensureActive()
    val mimeType = context.contentResolver.getType(uri)
    coroutineContext.ensureActive()
    PickedStatusMedia(bytes = bytes, displayName = name, mimeType = mimeType)
}
