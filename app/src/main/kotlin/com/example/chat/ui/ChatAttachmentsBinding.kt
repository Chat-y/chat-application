package com.example.chat.ui

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.chat.ui.kit.ChatyConfirmationDialog as AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.chat.ServiceLocator
import com.example.chat.data.local.BoundedFileReader
import com.example.chat.domain.models.MessageAttachment
import com.example.chat.domain.models.MessageType
import com.example.chat.ui.chats.detail.AttachmentBackendPort
import com.example.chat.ui.chats.detail.ChatAttachmentActionController
import com.example.chat.ui.chats.detail.PickedFile
import com.example.chat.ui.media.CustomFilePickerScreen
import com.example.chat.ui.media.DocumentReaderScreen
import com.example.chat.ui.media.FileSanitizationService
import com.example.chat.ui.media.MediaCategory
import com.example.chat.ui.media.MediaFileDescriptor
import com.example.chat.ui.media.MediaLibraryService
import com.example.chat.ui.media.PdfProcessingService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.NonCancellable

/** Binding of ChatAttachmentActionController (Dart chat_attachment_actions.dart) for ChatDetailHost.
 *  Bound: image/video/document/audio pick + upload (ChatMediaService -> REST /chat/upload when the
 *  api session is authenticated) + send through ApiBackendService.sendMessage with the Dart JSON
 *  envelope, view-once sheet, share-limit and size guards.
 *  Not bound here (explicit gaps): poll dialog/viewer.
 *  document shareMedia routes to the in-app CustomFilePickerScreen (Dart
 *  chat_attachment_actions.dart:92 Navigator.push), not the system picker. */
class ChatAttachmentsBindings(
    val onMedia: (String) -> Unit,
    val onContact: () -> Unit,
    val onLocation: () -> Unit,
    val controller: com.example.chat.ui.chats.detail.ChatAttachmentActionController,
    val onReviewImages: (List<Uri>) -> Unit,
    val onDirectSendImages: (List<Uri>) -> Unit,
    /** Capture sessions for voice/camera video: open at the ORIGINAL record/entry start (null if signed out). */
    val openCaptureSession: ((onCancel: () -> Unit) -> com.example.chat.ui.AttachFlow?) = { null },
    val closeCaptureSession: (com.example.chat.ui.AttachFlow) -> Unit = {},
)

@Composable
fun rememberChatAttachments(conversationId: String, afterSend: () -> Unit, secureChannel: () -> Boolean = { false }): ChatAttachmentsBindings {
    val secureChannelNow by rememberUpdatedState(secureChannel)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = ServiceLocator.apiBackend
    // Conservative lifetime owner: an account switch requires reopening this chat binding.
    // Never rebind an in-flight picker/review/upload to a fresh account snapshot.
    val attachmentAccount = remember(conversationId) { api.authSnapshot() }
    fun requireAttachmentOwner() {
        check(api.authSnapshot() === attachmentAccount && !attachmentAccount.userId.isNullOrBlank() &&
            !attachmentAccount.token.isNullOrBlank()) { "Account changed. Reopen the chat before sending attachments." }
    }
    suspend fun cleanupStaged(files: List<java.io.File>) = withContext(NonCancellable + Dispatchers.IO) {
        files.distinct().forEach { it.delete() }
    }
    // Capture-session scope guard (voice/camera video): sessions open under the current account
    // scope and are atomically cancelled on any account transition.
    val captureRegistry = remember { val h = android.os.Handler(android.os.Looper.getMainLooper()); com.example.chat.ui.GalleryLeaseRegistry { r -> h.post { r() } } }
    androidx.compose.runtime.DisposableEffect(api) {
        val l: (com.example.chat.data.api.ApiAccountScope) -> Unit = { next ->
            captureRegistry.onTransition(next, publishEpoch = { }, cancelWaiters = { })
        }
        api.addAuthScopeListener(l)
        onDispose { api.removeAuthScopeListener(l) }
    }
    var pending by remember { mutableStateOf<CompletableDeferred<List<Uri>>?>(null) }
    var mediaReview by remember { mutableStateOf<Pair<List<com.example.chat.ui.chats.detail.MediaReviewItem>, CompletableDeferred<com.example.chat.ui.chats.detail.ReviewedMedia?>>?>(null) }
    var viewOnceAsk by remember { mutableStateOf<Pair<String, CompletableDeferred<Boolean>>?>(null) }
    val multi = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        pending?.complete(uris); pending = null
    }
    val single = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pending?.complete(listOfNotNull(uri)); pending = null
    }
    var contactPending by remember { mutableStateOf<CompletableDeferred<Uri?>?>(null) }
    var docPickerPending by remember { mutableStateOf<CompletableDeferred<List<MediaFileDescriptor>?>?>(null) }
    // Per-flow exclusion: the picker/review flows below share the single slots above (pending,
    // mediaReview, viewOnceAsk, docPickerPending, contactPending). A second concurrent flow would
    // overwrite an in-flight deferred and orphan or misroute the first flow's picker result, so a
    // new flow is refused while one is running (GalleryLeaseRegistry.begin parity: never overwrite).
    val pickerFlowLock = remember { kotlinx.coroutines.sync.Mutex() }
    // Dart custom_file_picker_screen.dart:33 `final _libraryService = MediaLibraryService();`
    // with path_provider sources (media_library_service.dart:48-51): app documents dir + temp dir.
    val mediaLibrary = remember {
        MediaLibraryService(libraryDirectories = { listOf(context.filesDir, context.cacheDir) })
    }
    // Dart document_reader_screen.dart:29 `final _pdfService = PdfProcessingService();`
    // sanitizer workspace = path_provider temp dir (file_sanitization_service.dart).
    val pdfService = remember {
        PdfProcessingService(FileSanitizationService(tempDirectory = { context.cacheDir }))
    }
    var readerDoc by remember { mutableStateOf<MediaFileDescriptor?>(null) }
    val contactPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        contactPending?.complete(uri); contactPending = null
    }
    fun toast(m: String) = Toast.makeText(context, m, Toast.LENGTH_SHORT).show()

    suspend fun read(uri: Uri, type: String, owned: MutableList<java.io.File>): PickedFile = withContext(Dispatchers.IO) {
        requireAttachmentOwner()
        val cr = context.contentResolver
        var name = "file"
        var declaredSize = -1L
        cr.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val n = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val z = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (n >= 0) name = cursor.getString(n) ?: name
                if (z >= 0 && !cursor.isNull(z)) declaredSize = cursor.getLong(z)
            }
        }
        val mime = cr.getType(uri)
        val max = if (type == "image") 50L * 1024 * 1024 else ServiceLocator.chatMedia.maxBytes(type)
        check(declaredSize <= max) { "This $type exceeds the ${max / (1024 * 1024)} MB storage limit." }
        val target = java.io.File.createTempFile("chaty-outgoing-", ".part", context.cacheDir)
        var finalFile: java.io.File? = null
        try {
            var total = 0L
            cr.openInputStream(uri)?.use { input ->
                target.outputStream().buffered(64 * 1024).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        requireAttachmentOwner()
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        check(count.toLong() <= max - total) { "Selected attachment exceeds its storage limit." }
                        output.write(buffer, 0, count)
                        total += count
                    }
                }
            } ?: error("The selected file is not accessible on this device.")
            check(total > 0L) { "The selected file is empty." }
            requireAttachmentOwner()
            val finalName = name.replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(100).ifBlank { "attachment" }
            val staged = java.io.File(context.cacheDir, "chaty-outgoing-${java.util.UUID.randomUUID()}-$finalName")
            check(target.renameTo(staged)) { "Unable to stage the selected attachment." }
            finalFile = staged
            owned += staged // Store ownership before cancellable dispatcher return can lose the result.
            PickedFile(name = name, path = staged.absolutePath, mimeType = mime)
        } catch (error: Throwable) {
            target.delete(); finalFile?.delete()
            throw error
        }
    }

    /* Previous unbounded/Main metadata implementation retained:
    suspend fun read(uri: Uri, type: String): PickedFile {
        val cr = context.contentResolver
        var name = "file"
        var size = -1L
        cr.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val s = c.getColumnIndex(OpenableColumns.SIZE)
                if (n >= 0) name = c.getString(n) ?: name
                if (s >= 0 && !c.isNull(s)) size = c.getLong(s)
            }
        }
        val max = if (type == "image") 50L * 1024 * 1024 else ServiceLocator.chatMedia.maxBytes(type)
        // Guard before reading the stream: images are compressed after, so allow headroom for them.
        if (size > 0 && size > max) {
            throw IllegalStateException("This $type exceeds the ${max / (1024 * 1024)} MB storage limit.")
        }
        // Copy the provider URI to an app-private temporary file using a bounded buffer.
        // This preserves access after the picker closes without materializing large media in RAM.
        val staged = withContext(Dispatchers.IO) {
            val target = java.io.File.createTempFile("chaty-outgoing-", ".part", context.cacheDir)
            try {
                cr.openInputStream(uri)?.use { input ->
                    target.outputStream().buffered(64 * 1024).use { output ->
                        input.copyTo(output, 64 * 1024)
                    }
                } ?: throw IllegalStateException("The selected file is not accessible on this device.")
                val finalName = name.replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(100).ifBlank { "attachment" }
                val finalFile = java.io.File(context.cacheDir, "chaty-outgoing-${java.util.UUID.randomUUID()}-$finalName")
                check(target.renameTo(finalFile)) { "Unable to stage the selected attachment." }
                finalFile
            } catch (error: Throwable) {
                target.delete()
                throw error
            }
        }
        if (staged.length() <= 0L) {
            staged.delete()
            throw IllegalStateException("The selected file is empty.")
        }
        if (staged.length() > max) {
            staged.delete()
            throw IllegalStateException("This $type exceeds the ${max / (1024 * 1024)} MB storage limit.")
        }
        return PickedFile(name = name, path = staged.absolutePath, mimeType = cr.getType(uri))
    }

    */
    val controller = remember(conversationId, attachmentAccount) {
        // Captured at attachment start (OpenCode 610): retention journaling must
        // use THIS account's key, so an account switch during an upload can never
        // journal into the switched-to account's bucket. Never the ambient account.
        val retainedAccountKey = com.example.chat.data.messaging.ConversationDispatchLocks.accountKey(
            attachmentAccount.userId, attachmentAccount.tenantId, attachmentAccount.baseUrl)
        ChatAttachmentActionController(
            conversationId = conversationId,
            // Track captured finals that outlive their send call (submitted or
            // failed). Registry never deletes; retention policy is an owner decision.
            onLocalFinalRetained = { retainedPath ->
                // audit-05 v1 (replaced in v3, kept inactive per standing rule):
                // record used the ambient account via the registry at record time:
                // com.example.chat.ui.camera.CameraRetainedFinals.record(context, conversationId, retainedPath)
                com.example.chat.ui.camera.CameraRetainedFinals.record(context, retainedAccountKey, conversationId, retainedPath)
            },
            backend = object : AttachmentBackendPort {
                override suspend fun upload(type: String, file: PickedFile, durationSeconds: Int): MessageAttachment {
                    requireAttachmentOwner()
                    // A secure chat never uploads plaintext media; encrypted media
                    // (SecureImageSendService) is Phase 2 and not wired yet.
                    if (secureChannelNow()) {
                        check(com.example.chat.ReleaseFeatures.EXPERIMENTAL_MLS_CHAT_ROUTING &&
                            ServiceLocator.conversationsRepository.isSecureChannel(conversationId)) { "Secure routing is unresolved." }
                        requireAttachmentOwner()
                        var ownedSource: java.io.File? = null
                        var primaryFailure: Throwable? = null
                        try {
                        val source = file.path?.takeIf { !it.startsWith("content:") }?.let { java.io.File(it) } ?: withContext(Dispatchers.IO) {
                            val staged = java.io.File.createTempFile("chaty-outgoing-secure-", ".part", context.cacheDir)
                            ownedSource = staged
                            try {
                                val bytes = file.bytes
                                require(bytes == null || bytes.size.toLong() <= com.example.chat.data.media.SecureAttachmentFileCodec.MAX_CIPHERTEXT)
                                val input = if (bytes != null) java.io.ByteArrayInputStream(bytes)
                                    else context.contentResolver.openInputStream(android.net.Uri.parse(checkNotNull(file.path))) ?: error("Selected attachment is unavailable.")
                                input.use { stream -> staged.outputStream().use { output ->
                                    val buffer = ByteArray(65_536); var total = 0L
                                    while (true) {
                                        coroutineContext.ensureActive(); requireAttachmentOwner()
                                        val count = stream.read(buffer); if (count < 0) break
                                        require(total + count <= com.example.chat.data.media.SecureAttachmentFileCodec.MAX_CIPHERTEXT)
                                        output.write(buffer, 0, count); total += count
                                    }
                                } }
                                staged
                            } catch (failure: Throwable) { if (staged.exists() && !staged.delete()) failure.addSuppressed(java.io.IOException("Private staging cleanup failed.")); throw failure }
                        }
                            return ServiceLocator.secureAttachmentFiles.upload(attachmentAccount, conversationId, source, type,
                                file.name, file.mimeType ?: "application/octet-stream", durationSeconds)
                        } catch (failure: Throwable) { primaryFailure = failure; throw failure }
                        finally {
                            ownedSource?.let {
                                if (it.exists() && !it.delete()) {
                                    val cleanup = java.io.IOException("Private staging cleanup failed.")
                                    if (primaryFailure != null) primaryFailure.addSuppressed(cleanup) else throw cleanup
                                }
                            }
                        }
                    }

                    // Camera captures arrive as a file path (no picker bytes): read them here,
                    // matching Dart ChatMediaService.uploadFile(sourcePath:).
                    val stagedFile = file.path?.let { java.io.File(it) }
                    if (api.isAuthenticated && stagedFile != null) {
                        var uploadTarget = stagedFile
                        var compressedTemp: java.io.File? = null
                        if (type == "image") {
                            val compressed = com.example.chat.ui.media.ImageSendCompressor.compressFile(stagedFile)
                            if (compressed !== stagedFile) {
                                compressedTemp = compressed
                                uploadTarget = compressed
                            }
                        }
                        val scopeNow = attachmentAccount
                        var endUpload: (() -> Unit)? = null
                        try {
                        endUpload = com.example.chat.data.messaging.AttachmentUploadTracker.begin(
                            com.example.chat.data.messaging.ConversationDispatchLocks.accountKey(
                                scopeNow.userId, scopeNow.tenantId, scopeNow.baseUrl),
                            conversationId,
                        )
                            // No flag re-check here: begin() already enforced the
                            // durable flag inside the dispatch lock, and a toggle
                            // is refused while this upload stays registered.
                            // But the ACCOUNT is revalidated after begin's
                            // suspension: tracker registration and upload must
                            // ride the SAME captured identity.
                            check(api.authSnapshot() === scopeNow) {
                                "Account changed while the upload was starting; nothing was uploaded."
                            }
                            val uploaded = api.uploadMediaForScope(scopeNow, uploadTarget) { _, _ -> }
                            val effectiveName = if (type == "image" && compressedTemp != null) {
                                if (file.name.contains('.')) "${file.name.substringBeforeLast('.')}.jpg" else "${file.name}.jpg"
                            } else {
                                file.name
                            }
                            return MessageAttachment(
                                id = java.util.UUID.randomUUID().toString(),
                                type = type,
                                name = effectiveName,
                                size = com.example.chat.data.services.ChatMediaService.formatBytes(uploadTarget.length()),
                                url = uploaded["url"]?.toString() ?: throw IllegalStateException("Upload response did not contain a media URL."),
                                durationSeconds = durationSeconds,
                            )
                        } finally {
                            endUpload?.invoke()
                            compressedTemp?.delete()
                            if (stagedFile.parentFile == context.cacheDir && stagedFile.name.startsWith("chaty-outgoing-")) {
                                stagedFile.delete()
                            }
                        }
                    }
                    // This adapter has no original-owner upload primitive. Do not route a stale
                    // picker through ambient authentication. A larger legacy redesign is NOT DONE.
                    throw IllegalStateException("Choose a file-backed attachment to use the account-scoped upload route.")
                    /* Previous unscoped compatibility upload retained:
                    // The legacy Supabase storage adapter accepts ByteArray only.
                    // Never let a 100–700 MB configured media limit turn into an
                    // equally large heap allocation: keep this compatibility route
                    // to a strict 16 MB buffer. The API route above is file-streamed.
                    val payload = try {
                        file.bytes ?: stagedFile?.let { source ->
                            val bufferLimit = minOf(
                                ServiceLocator.chatMedia.maxBytes(type),
                                BoundedFileReader.MAX_IN_MEMORY_UPLOAD_BYTES,
                            )
                            if (source.length() > bufferLimit) {
                                throw IllegalStateException(
                                    "This upload route buffers at most 16 MB in memory. Use Send Large File where available, or choose a smaller file.",
                                )
                            }
                            withContext(Dispatchers.IO) { BoundedFileReader.read(source, bufferLimit) }
                        } ?: throw IllegalStateException("The selected file is empty.")
                    } catch (error: Throwable) {
                        if (stagedFile?.parentFile == context.cacheDir && stagedFile.name.startsWith("chaty-outgoing-")) {
                            stagedFile.delete()
                        }
                        throw error
                    }
                    val scopeLegacy = attachmentAccount
                    val endLegacyUpload = com.example.chat.data.messaging.AttachmentUploadTracker.begin(
                        com.example.chat.data.messaging.ConversationDispatchLocks.accountKey(
                            scopeLegacy.userId, scopeLegacy.tenantId, scopeLegacy.baseUrl),
                        conversationId,
                    )
                    try {
                        // Same as above: begin()'s durable in-lock check is the
                        // enforcement; the UI lambda is not consulted here. The
                        // account is revalidated after begin's suspension (the
                        // legacy adapter has no scoped variant, so this check
                        // is the binding).
                        check(api.authSnapshot() === scopeLegacy) {
                            "Account changed while the upload was starting; nothing was uploaded."
                        }
                        return ServiceLocator.chatMedia.uploadBytes(
                            conversationId, type, payload, file.name, file.mimeType, durationSeconds,
                        )
                    } finally {
                        endLegacyUpload()
                        if (stagedFile?.parentFile == context.cacheDir && stagedFile.name.startsWith("chaty-outgoing-")) {
                            stagedFile.delete()
                        }
                    }
                    */
                }
                override suspend fun sendMessage(text: String, type: MessageType, attachment: MessageAttachment?, extraMetadata: Map<String, Any?>?) {
                    if (secureChannelNow()) {
                        requireAttachmentOwner()
                        check(com.example.chat.ReleaseFeatures.EXPERIMENTAL_MLS_CHAT_ROUTING &&
                            ServiceLocator.conversationsRepository.isSecureChannel(conversationId)) { "Secure routing is unresolved." }
                        check(attachment?.encryptedFile != null) { "Encrypted media descriptor is required." }
                        check(!com.example.chat.data.local.AttachmentCachePolicy.viewOnceRestricted(extraMetadata.orEmpty())) {
                            "Encrypted view-once media is unavailable until authenticated consume support is ready."
                        }
                        ServiceLocator.conversationsRepository.sendMessage(conversationId, text, type, attachment,
                            extraMetadata = extraMetadata, expectedScope = attachmentAccount)
                        return
                    }

                    requireAttachmentOwner()
                    api.sendMessage(conversationId, text, type, attachment, extraMetadata, expectedScope = attachmentAccount)
                }
                override suspend fun sendStructuredMessage(type: String, body: String, metadata: Map<String, Any?>) {
                    // Conservative lambda also represents UNKNOWN. Never interpret
                    // unknown as permission to send through either transport.
                    if (secureChannelNow()) throw IllegalStateException("Contact/location send blocked until secure routing is verified.")
                    // Location / contact go to backend_python as a normal text message: the body already
                    // carries the maps link or the contact details. (Was a Supabase call that never ran.)
                    requireAttachmentOwner()
                    api.sendMessage(conversationId, body, expectedScope = attachmentAccount)
                }
                // Polls are hidden in this release (ReleaseFeatures.POLLS); these stay on the old backend.
                override suspend fun createPoll(question: String, options: List<String>, allowMultiple: Boolean) {
                    // Polls ride the old server-readable backend; never let one
                    // leak out of an encrypted chat.
                    if (secureChannelNow()) throw IllegalStateException("Polls are not available in an encrypted chat yet.")
                    ServiceLocator.gbFeatures.createPoll(conversationId, question, options, allowMultiple)
                }
                override suspend fun getPoll(messageId: String): Map<String, Any?>? = ServiceLocator.gbFeatures.getPoll(messageId)
                override suspend fun votePoll(messageId: String, optionId: String) {
                    // Same plane as createPoll: server-readable, blocked in an
                    // encrypted chat.
                    if (secureChannelNow()) throw IllegalStateException("Polls are not available in an encrypted chat yet.")
                    ServiceLocator.gbFeatures.votePoll(messageId, optionId)
                }
                override suspend fun reloadConversation() {
                    requireAttachmentOwner()
                    if (secureChannelNow()) ServiceLocator.conversationsRepository.loadMessages(conversationId, attachmentAccount)
                    else api.loadMessages(conversationId)
                }
            },
            toast = { toast(it) },
            imageShareLimit = { ServiceLocator.preferencesController.gbDouble("Img_share_limit", 30.0).toInt() },
            moreDocsSend = { ServiceLocator.preferencesController.gbBool("key_more_docs_send", false) },
        )
    }

    // Dart shareContact (chat_attachment_actions.dart:212): READ_CONTACTS runtime permission
    // (manifest entry added with this patch) decides the canRead path; the system picker stands in
    // for FlutterContacts.native.showPicker. Android has no "limited" contacts access, so the
    // permission result maps to granted/denied only.
    var contactReadPending by remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }
    val contactReadPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        contactReadPending?.complete(granted); contactReadPending = null
    }
    val contactsPort = object : com.example.chat.ui.chats.detail.ContactsPort {
        override suspend fun requestReadAccess(): com.example.chat.ui.chats.detail.ContactReadAccess {
            requireAttachmentOwner()
            if (com.example.chat.data.services.AndroidContactsAccess.hasReadAccess(context)) {
                return com.example.chat.ui.chats.detail.ContactReadAccess.granted
            }
            val d = CompletableDeferred<Boolean>()
            contactReadPending = d
            contactReadPermission.launch(android.Manifest.permission.READ_CONTACTS)
            return if (d.await()) {
                com.example.chat.ui.chats.detail.ContactReadAccess.granted
            } else {
                com.example.chat.ui.chats.detail.ContactReadAccess.denied
            }
        }
        override suspend fun showPicker(canRead: Boolean): com.example.chat.ui.chats.detail.PickedContactRef? {
            val d = CompletableDeferred<Uri?>()
            contactPending = d
            contactPicker.launch(null)
            val uri = d.await() ?: return null
            requireAttachmentOwner()
            val (id, name) = withContext(Dispatchers.IO) { com.example.chat.data.services.AndroidContactsAccess.readPickedContact(context, uri) }
            return com.example.chat.ui.chats.detail.PickedContactRef(id, name)
        }
        override suspend fun fetchDetails(id: String): com.example.chat.ui.chats.detail.ContactDetails? =
            withContext(Dispatchers.IO) { requireAttachmentOwner(); com.example.chat.data.services.AndroidContactsAccess.fetchContactDetails(context, id) }
    }
    // NEW GeoLibre map viewer, loaded only after explicit user action, never during normal chat.
    var locationPickerOpen by remember(conversationId, api.currentUser?.id) { mutableStateOf(false) }
    val liveConversation by androidx.compose.runtime.rememberUpdatedState(conversationId)
    val onLocation: () -> Unit = { locationPickerOpen = true }
    val locationAccount = api.currentUser?.id
    if (locationPickerOpen && locationAccount != null) {
        com.example.chat.ui.chats.location.MapViewerScreen(
            context = context,
            accountId = locationAccount,
            conversationId = conversationId,
            currentScope = { api.currentUser?.id to liveConversation },
            onDismiss = { locationPickerOpen = false },
            onAttributionLink = { uri ->
                // MAP-R5-227: guarded so a missing browser cannot crash the picker.
                // Prior: context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
                runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri)) }
            },
        )
    }
    val onContact: () -> Unit = { scope.launch {
        if (!pickerFlowLock.tryLock()) { toast("Finish the current attachment first."); return@launch }
        // Previous unlocked call: controller.shareContact(contactsPort); afterSend()
        try { controller.shareContact(contactsPort); afterSend() }
        finally { pickerFlowLock.unlock() }
    } }
    val onMedia: (String) -> Unit = { type ->
        scope.launch {
            if (!pickerFlowLock.tryLock()) { toast("Finish the current attachment first."); return@launch }
            val flowStaged = mutableListOf<java.io.File>()
            try {
            requireAttachmentOwner()
            if (type == "image" || type == "video") {
                try {
                    val d = CompletableDeferred<List<Uri>>()
                    pending = d
                    val limit = if (type == "image") ServiceLocator.preferencesController.gbDouble("Img_share_limit", 30.0).toInt().coerceIn(1, 100) else 1
                    if (limit > 1) multi.launch("image/*") else single.launch(if (type == "image") "image/*" else "video/*")
                    val uris = d.await()
                    requireAttachmentOwner()
                    if (uris.isEmpty()) return@launch
                    check(uris.size <= limit) { "Your configured send limit is $limit." }
                    val items = uris.map { uri -> com.example.chat.ui.chats.detail.MediaReviewItem(uri.toString(), read(uri, type, flowStaged), type) }
                    val review = CompletableDeferred<com.example.chat.ui.chats.detail.ReviewedMedia?>()
                    requireAttachmentOwner()
                    mediaReview = items to review
                    val result = review.await() ?: return@launch
                    controller.sendReviewedMedia(result.items.map { it.file }, type, result.caption, result.viewOnce,
                        perItemCaptions = result.items.map { item -> result.captions[item.stableId].orEmpty() },
                        perItemViewOnce = result.items.map { it.stableId in result.viewOnceIds || result.viewOnce })
                    afterSend()
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) { toast(e.message ?: "Unable to prepare attachment") }
                return@launch
            }
            controller.shareMedia(
                type = type,
                pickFiles = { multiple, maxFiles ->
                    if (type == "document") {
                        // Dart shareMedia('document'): Navigator.push -> CustomFilePickerScreen(
                        // initialCategory: document, title: 'Documents & Files'), multi-pick.
                        val d = CompletableDeferred<List<MediaFileDescriptor>?>()
                        docPickerPending = d
                        return@shareMedia d.await()?.map {
                            PickedFile(name = it.name, path = it.path, mimeType = it.mimeType)
                        }
                    }
                    val d = CompletableDeferred<List<Uri>>()
                    pending = d
                    val mime = when (type) { "image" -> "image/*"; "video" -> "video/*"; "audio" -> "audio/*"; else -> "*/*" }
                    if (multiple) multi.launch(mime) else single.launch(mime)
                    val uris = d.await()
                    requireAttachmentOwner()
                    if (uris.isEmpty()) null else {
                        if (uris.size > maxFiles && maxFiles != Int.MAX_VALUE) {
                            throw IllegalStateException("You selected ${uris.size} files; your configured send limit is $maxFiles.")
                        }
                        uris.map { read(it, type, flowStaged) }
                    }
                },
                confirmViewOnce = { t ->
                    val d = CompletableDeferred<Boolean>()
                    viewOnceAsk = t to d
                    d.await()
                },
            )
            afterSend()
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { toast("Could not send attachments. Reopen the chat and try again.") }
            finally { cleanupStaged(flowStaged); pickerFlowLock.unlock() }
        }
    }
    mediaReview?.let { (items, deferred) ->
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { mediaReview = null; deferred.complete(null) },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true),
        ) {
            com.example.chat.ui.chats.detail.MediaReviewScreen(
                items,
                conversationId = conversationId,
                onCancel = { mediaReview = null; deferred.complete(null) },
                onSend = { result -> mediaReview = null; deferred.complete(result) },
            )
        }
    }
    viewOnceAsk?.let { (t, d) ->
        AlertDialog(shape = com.example.chat.ui.kit.ChatyModalShape,
            onDismissRequest = { viewOnceAsk = null; d.complete(false) },
            title = { Text("Send ${if (t == "video") "video" else "photo"}") },
            text = { Text("Choose how it should be delivered.") },
            dismissButton = { TextButton(onClick = { viewOnceAsk = null; d.complete(false) }) { Text("Send normally") } },
            confirmButton = { TextButton(onClick = { viewOnceAsk = null; d.complete(true) }) { Text("View once") } },
        )
    }
    docPickerPending?.let { d ->
        CustomFilePickerScreen(
            libraryService = mediaLibrary,
            allowMultiple = true,
            initialCategory = MediaCategory.document,
            title = "Documents & Files",
            // Dart custom_file_picker_screen.dart:745-756 _openFile: real files push
            // DocumentReaderScreen; demo/missing entries toggle selection inside the picker.
            onOpenDocument = { f -> readerDoc = f },
            onSendAttachments = { files -> docPickerPending = null; d.complete(files) },
            onClose = { docPickerPending = null; d.complete(null) },
        )
    }
    readerDoc?.let { f ->
        DocumentReaderScreen(
            file = f,
            pdfService = pdfService,
            onClose = { readerDoc = null },
        )
    }
    // Gallery-sheet review flow: same staging, review and send path as onMedia("image"),
    // fed by the sheet's own MediaStore grid instead of the system picker.
    val onReviewImages: (List<Uri>) -> Unit = { uris ->
        scope.launch {
            if (!pickerFlowLock.tryLock()) { toast("Finish the current attachment first."); return@launch }
            val flowStaged = mutableListOf<java.io.File>()
            try {
                requireAttachmentOwner()
                if (uris.isEmpty()) return@launch
                val limit = ServiceLocator.preferencesController.gbDouble("Img_share_limit", 30.0).toInt().coerceIn(1, 100)
                check(uris.size <= limit) { "Your configured send limit is $limit." }
                val items = uris.map { uri -> com.example.chat.ui.chats.detail.MediaReviewItem(uri.toString(), read(uri, "image", flowStaged), "image") }
                val review = CompletableDeferred<com.example.chat.ui.chats.detail.ReviewedMedia?>()
                requireAttachmentOwner()
                mediaReview = items to review
                val result = review.await() ?: return@launch
                controller.sendReviewedMedia(result.items.map { it.file }, "image", result.caption, result.viewOnce,
                        perItemCaptions = result.items.map { item -> result.captions[item.stableId].orEmpty() },
                        perItemViewOnce = result.items.map { it.stableId in result.viewOnceIds || result.viewOnce })
                afterSend()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { toast(e.message ?: "Unable to prepare attachment") }
            finally { cleanupStaged(flowStaged); pickerFlowLock.unlock() }
        }
    }
    // Gallery-sheet direct send: stage and send immediately, no review step.
    val onDirectSendImages: (List<Uri>) -> Unit = { uris ->
        scope.launch {
            if (!pickerFlowLock.tryLock()) { toast("Finish the current attachment first."); return@launch }
            val flowStaged = mutableListOf<java.io.File>()
            try {
                requireAttachmentOwner()
                if (uris.isEmpty()) return@launch
                val limit = ServiceLocator.preferencesController.gbDouble("Img_share_limit", 30.0).toInt().coerceIn(1, 100)
                check(uris.size <= limit) { "Your configured send limit is $limit." }
                val files = uris.map { uri -> read(uri, "image", flowStaged) }
                controller.sendReviewedMedia(files, "image", "", false)
                afterSend()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { toast(e.message ?: "Unable to send") }
            finally { cleanupStaged(flowStaged); pickerFlowLock.unlock() }
        }
    }
    val openCaptureSession: (() -> Unit) -> com.example.chat.ui.AttachFlow? = { onCancel ->
        val st = api.authSnapshot()
        if (st.userId.isNullOrBlank() || st.token.isNullOrBlank()) { toast("Sign in to send attachments."); null }
        else captureRegistry.openSession(st, { api.authSnapshot() }, onCancel)
    }
    val closeCaptureSession: (com.example.chat.ui.AttachFlow) -> Unit = { captureRegistry.closeSession(it) }
    return ChatAttachmentsBindings(onMedia, onContact, onLocation, controller, onReviewImages, onDirectSendImages, openCaptureSession, closeCaptureSession)
}

/* Previous implementation fragments retained (net222):

*/

/* Previous implementation fragments retained (net229):
    val controller = remember(conversationId) {
                        val scopeNow = api.authSnapshot()
                        val endUpload = com.example.chat.data.messaging.AttachmentUploadTracker.begin(
                        try {
                            endUpload()
                    val scopeLegacy = api.authSnapshot()
                    api.sendMessage(conversationId, text, type, attachment, extraMetadata)
                    api.sendMessage(conversationId, body)
            val (id, name) = com.example.chat.data.services.AndroidContactsAccess.readPickedContact(context, uri)
            com.example.chat.data.services.AndroidContactsAccess.fetchContactDetails(context, id)
                    val items = uris.map { uri -> com.example.chat.ui.chats.detail.MediaReviewItem(uri.toString(), read(uri, type), type) }
                } catch (e: Exception) { toast(e.message ?: "Unable to prepare attachment") }
                val items = uris.map { uri -> com.example.chat.ui.chats.detail.MediaReviewItem(uri.toString(), read(uri, "image"), "image") }
            } catch (e: Exception) { toast(e.message ?: "Unable to prepare attachment") }
                val files = uris.map { uri -> read(uri, "image") }
            } catch (e: Exception) { toast(e.message ?: "Unable to send") }
*/

/* Previous implementation fragments retained (net230):
                    controller.sendReviewedMedia(result.items.map { it.file }, type, result.caption, result.viewOnce)
                controller.sendReviewedMedia(result.items.map { it.file }, "image", result.caption, result.viewOnce)
*/

// Previous E2EE media wiring retained inactive:
//                     if (secureChannelNow()) throw IllegalStateException("Encrypted media is not wired yet, so media is not sent in an encrypted chat.")
//                     if (secureChannelNow()) throw IllegalStateException("Encrypted media is not wired yet, so media is not sent in an encrypted chat.")
//                     check(!secureChannelNow()) { "Message reload blocked until secure routing is verified." }
//                     api.loadMessages(conversationId)
