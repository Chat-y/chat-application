package com.example.chat.ui.media

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** P2P owned callback: claim a verified private receive path, snapshot, sniff bytes, then grant.
 * No extension-derived MIME and no grant to the receive original. Maximum snapshot is100MB.
 * The caller must recheck viewer/account identity immediately before handoff. */
internal suspend fun launchOwnedP2pExternal(
    context: Context, source: File, share: Boolean, mayDeliver: () -> Boolean,
) {
    var export: File? = null
    var handedOff = false
    var lease: AutoCloseable? = null
    try {
        // Store ownership before returning across dispatchers, where cancellation may discard results.
        val claimed = withContext(Dispatchers.IO) {
            val file = source.canonicalFile
            val receive = File(context.filesDir, "p2p-received").canonicalFile
            require(file.isFile && file.canRead() && file.parentFile == receive) { "Attachment unavailable" }
            lease = MediaPlaybackFileLeases.acquire(listOf(file.path))
            file
        }
        require(mayDeliver()) { "Attachment session changed" }
        export = stageMediaSource(context, claimed.toURI().toString(), "chaty_shared_")
        val file = export!!
        val mime = withContext(Dispatchers.IO) {
            // The .bin snapshot intentionally removes suffix fallback from the detector.
            FileTypeDetector.classifyFile({ file.inputStream().use { input ->
                val header = ByteArray(FileTypeDetector.HEADER_BYTES_TO_READ)
                var count = 0
                while (count < header.size) {
                    val read = input.read(header, count, header.size - count)
                    if (read <= 0) break
                    count += read
                }
                header.copyOf(count)
            } }, file.name).mimeType
        }
        coroutineContext.ensureActive()
        require(mayDeliver()) { "Attachment session changed" }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(if (share) Intent.ACTION_SEND else Intent.ACTION_VIEW).apply {
            if (share) { type = mime; putExtra(Intent.EXTRA_STREAM, uri) } else setDataAndType(uri, mime)
            clipData = ClipData.newRawUri("Attachment", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, if (share) "Share attachment" else "Open attachment"))
        handedOff = true
    } finally {
        lease?.close()
        if (!handedOff) export?.delete()
    }
}
