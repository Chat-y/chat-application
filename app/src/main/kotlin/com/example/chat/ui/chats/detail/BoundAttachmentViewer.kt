package com.example.chat.ui.chats.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.data.local.*
import com.example.chat.data.media.ResolvedSource
import com.example.chat.domain.models.ChatMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** This entire composition is keyed by session+conversation+message in the host. */
@Composable
internal fun BoundAttachmentViewer(theme: ThemeConfig, message: ChatMessage, binding: AttachmentConsumerBinding,
    restricted: Boolean, bounds: androidx.compose.ui.geometry.Rect?, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf<ResolvedSource?>(null) }
    var edited by remember { mutableStateOf<ExportToken?>(null) }
    var edit by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val stopReader = remember { arrayOfNulls<() -> Unit>(1) }
    val activeState = remember { java.util.concurrent.atomic.AtomicBoolean(true) }
    fun active(): Boolean = activeState.get()
    val attachment = message.attachment ?: return
    // Parent invokes the registered player stop barrier BEFORE releasing the source, regardless of effect order.
    DisposableEffect(Unit) {
        onDispose {
            activeState.set(false)
            val retainedSource = source
            val retainedEdited = edited
            val stop = stopReader[0]
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            // Retry on the main thread. Never drop backing bytes while native teardown is uncertain.
            fun cleanup(attempt: Int) {
                val stopped = runCatching { stop?.invoke() }.isSuccess
                if (!stopped) {
                    if (attempt < 3) handler.postDelayed({ cleanup(attempt + 1) }, 250L)
                    return
                }
                // Independent release operations: failure of one must not skip the other.
                runCatching { releaseConsumerSource(retainedSource) }
                runCatching { retainedEdited?.release() }
            }
            cleanup(0)
        }
    }
    LaunchedEffect(Unit) {
        var pending: ResolvedSource? = null
        try {
            pending = binding.resolve(message, true)
            coroutineContext.ensureActive()
            if (active() && binding.deliver(pending!!) { source = pending }) pending = null
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "Attachment unavailable" }
        finally { releaseConsumerSource(pending) }
    }
    // Owned routes bypass MediaViewerScreen's ordinary external-action busy state.
    var operationBusy by remember { mutableStateOf(false) }
    fun external(purpose: ExportPurpose) {
        if (restricted || !active() || operationBusy || edit) return
        operationBusy = true
        scope.launch {
            var token: ExportToken? = null
            var delivered = false
            try {
                val existing = edited
                val result = if (existing != null && existing.isValid()) ExportResult.Granted(existing)
                    else binding.resolver.exportCopy(binding.ref(message), binding.session, purpose,
                        attachment.name.substringAfterLast('.', "bin"), 10 * 60_000L) { binding.authorizedUrl(message) }
                if (result !is ExportResult.Granted) { error = "Export unavailable or not permitted"; return@launch }
                token = result.token
                coroutineContext.ensureActive()
                check(active() && result.token.isValid())
                val intent = if (purpose == ExportPurpose.SHARE) Intent(Intent.ACTION_SEND).apply {
                    type = context.contentResolver.getType(Uri.parse(result.token.uri)) ?: "application/octet-stream"
                    putExtra(Intent.EXTRA_STREAM, Uri.parse(result.token.uri))
                } else Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(result.token.uri), context.contentResolver.getType(Uri.parse(result.token.uri)))
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                check(binding.deliverIfCurrent {
                    context.startActivity(if (purpose == ExportPurpose.SHARE) Intent.createChooser(intent, "Share attachment") else intent)
                }) { "Session changed" }
                delivered = true
                if (edited === result.token) edited = null // registry timer/logout owns successful external lifetime
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "No application could open this attachment" }
            // Previous: finally { if (!delivered) token?.release() }
            // A reused edited token still belongs to the visible viewer after a failed launch.
            finally {
                if (!delivered && token !== edited) token?.release()
                operationBusy = false
            }
        }
    }
    // Previous gallery-only busy state did not serialize external actions using the same token.
    // var galleryBusy by remember { mutableStateOf(false) }
    fun gallery() {
        // Previous: if (restricted || galleryBusy) return
        if (restricted || operationBusy || edit) return
        var authorized = false
        binding.deliverIfCurrent { authorized = active() }
        if (!authorized) return
        // Previous: galleryBusy = true
        operationBusy = true
        // Barrier above only made a short state decision. No provider/file IO inside its lock.
        scope.launch {
            var token: ExportToken? = null
            try {
                val existing = edited
                val result = if (existing != null && existing.isValid()) ExportResult.Granted(existing)
                    else binding.resolver.exportCopy(binding.ref(message), binding.session, ExportPurpose.SHARE,
                        attachment.name.substringAfterLast('.', "bin"), 10 * 60_000L) { binding.authorizedUrl(message) }
                check(result is ExportResult.Granted) { "Gallery export not permitted." }
                val snapshot = result.token
                token = snapshot
                coroutineContext.ensureActive()
                fun ownerCurrent(): Boolean {
                    if (!active() || !snapshot.isValid()) return false
                    var valid = false
                    binding.deliverIfCurrent { valid = active() && snapshot.isValid() }
                    return valid
                }
                val status = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    saveMediaToGallery(context, snapshot.uri, attachment.name, attachment.type == "video", ::ownerCurrent)
                }
                android.widget.Toast.makeText(context, status, android.widget.Toast.LENGTH_LONG).show()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                android.widget.Toast.makeText(context,
                    "Could not save this attachment. Reopen it from the chat and try again.", android.widget.Toast.LENGTH_LONG).show()
            } finally {
                if (token !== edited) token?.release()
                // Previous: galleryBusy = false
                operationBusy = false
            }
        }
    }
    if (edit) {
        OwnedMediaEditor(binding.resolver, binding.ref(message), binding.session,
            when (attachment.type) { "video" -> EditKind.VIDEO_MP4; "audio" -> EditKind.AUDIO_MP4; else -> EditKind.IMAGE_JPEG },
            10 * 60_000L, ExportPurpose.SHARE, { binding.authorizedUrl(message) },
            onCancel = { edit = false }, onCommitted = { token ->
                var accepted = false
                var replaced: ExportToken? = null
                // Previous callback overwrote the old edited token without releasing it.
                // try { accepted = binding.deliverIfCurrent { edited = token; edit = false } }
                try { accepted = binding.deliverIfCurrent {
                    if (active()) { replaced = edited; edited = token; edit = false }
                } && active() }
                finally {
                    if (!accepted) token.release()
                    else if (replaced !== token) replaced?.release()
                }
            },
        )
        return
    }
    val current = source
    when {
        error != null || current is ResolvedSource.Unavailable -> Surface(Modifier.fillMaxSize()) { Column(Modifier.safeDrawingPadding()) {
            Text(error ?: "Not downloaded for offline use, unavailable or access denied.")
            TextButton(onBack) { Text("Back") }
        } }
        current == null -> Surface(Modifier.fillMaxSize()) { Column(Modifier.safeDrawingPadding()) { CircularProgressIndicator(); TextButton(onBack) { Text("Back") } } }
        else -> MediaViewerScreen(theme,
            if (edited != null) "${attachment.name} (edited)" else attachment.name,
            attachment.type, if (edited != null) "Edited copy" else attachment.size,
            storagePath = edited?.uri ?: current.consumerUri() ?: "verified-bytes",
            createSignedUrl = { path, _ -> path }, onBack = onBack, sourceBounds = bounds,
            restrictedViewOnce = restricted,
            providedModel = edited?.uri ?: current.consumerModel(), ownedActions = true,
            ownedActionBusy = operationBusy,
            registerStopReader = { stopReader[0] = it },
            deliverPlayback = binding.deliverIfCurrent,
            onOwnedSaveToGallery = ::gallery,
            // Previous edit callback had no shared operation gate.
            // onOwnedEdit = { if (runCatching { stopReader[0]?.invoke() }.isSuccess) edit = true
            //     else error = "Could not stop this attachment for editing. Close it and try again." },
            onOwnedEdit = { if (!restricted && active() && !operationBusy && !edit) { if (runCatching { stopReader[0]?.invoke() }.isSuccess) edit = true
                else error = "Could not stop this attachment for editing. Close it and try again." } },
            onOwnedShare = { external(ExportPurpose.SHARE) }, onOwnedOpen = { external(ExportPurpose.OPEN) },
        )
    }
}

/* Previous implementation fragments retained (net227):

*/

// Previous source retained for source-only media polish:
//         else -> MediaViewerScreen(theme, attachment.name, attachment.type, attachment.size,

// Previous cleanup source retained:
//     var active by remember { mutableStateOf(true) }
//     DisposableEffect(Unit) { onDispose { active = false; stopReader[0]?.invoke(); releaseConsumerSource(source); edited?.release() } }
//             if (active && binding.deliver(pending!!) { source = pending }) pending = null
//         if (restricted || !active || operationBusy || edit) return
//                 check(active && result.token.isValid())
//         binding.deliverIfCurrent { authorized = active }
//                     if (!active || !snapshot.isValid()) return false
//                     binding.deliverIfCurrent { valid = active && snapshot.isValid() }
//                     if (active) { replaced = edited; edited = token; edit = false }
//                 } && active }
//             // onOwnedEdit = { stopReader[0]?.invoke(); edit = true },
//             onOwnedEdit = { if (!restricted && active && !operationBusy && !edit) { stopReader[0]?.invoke(); edit = true } },
