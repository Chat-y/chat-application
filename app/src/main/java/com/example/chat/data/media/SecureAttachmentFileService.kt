package com.example.chat.data.media

import com.example.chat.data.api.ApiAccountScope
import com.example.chat.data.api.ApiEncryptedAttachmentClient
import com.example.chat.data.local.AttachmentRef
import com.example.chat.domain.models.ChatMessage
import com.example.chat.domain.models.MessageAttachment
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Generic image/document/video/audio encrypted file lane. No REST media upload,
 * no filename/key/MIME HTTP disclosure, and no unauthenticated render fallback.
 * View-once remains blocked until its authenticated consume protocol is ready.
 */
class SecureAttachmentFileService(
    private val transport: ApiEncryptedAttachmentClient,
    private val isCurrent: (ApiAccountScope) -> Boolean,
    private val directory: (String) -> File,
    private val currentNamespace: (ApiAccountScope) -> String,
    private val assertActive: suspend (String) -> Unit,
    private val ensureConversation: suspend (String) -> Unit,
    private val isOnline: () -> Boolean = { true },
    private val localCipherLimit: () -> Long = { 1024L * 1024 * 1024 },
) {
    private fun syncDirectory(dir: File) {
        val fd = android.system.Os.open(dir.absolutePath, android.system.OsConstants.O_RDONLY, 0)
        try { android.system.Os.fsync(fd) } finally { android.system.Os.close(fd) }
    }

    private val ownerGates = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.sync.Mutex>()
    private val retired = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private suspend fun <T> withOwner(owner: ApiAccountScope, block: suspend () -> T): T {
        val namespace = currentNamespace(owner)
        val gate = ownerGates.getOrPut(namespace) { kotlinx.coroutines.sync.Mutex() }
        gate.lock()
        try {
            check(namespace !in retired); assertActive(namespace)
            return block()
        } finally { gate.unlock() }
    }
    suspend fun purgeNamespace(namespace: String) {
        retired.add(namespace)
        val gate = ownerGates.getOrPut(namespace) { kotlinx.coroutines.sync.Mutex() }; gate.lock()
        try { val dir = directory(namespace); check(!dir.exists() || dir.deleteRecursively()) { "Secure media owner cleanup failed." } }
        finally { gate.unlock() }
    }

    suspend fun upload(owner: ApiAccountScope, conversationId: String, source: File, type: String,
                       name: String, mime: String, seconds: Int): MessageAttachment = withOwner(owner) { uploadActive(owner, conversationId, source, type, name, mime, seconds) }

    private suspend fun uploadActive(owner: ApiAccountScope, conversationId: String, source: File, type: String,
                       name: String, mime: String, seconds: Int): MessageAttachment = withContext(Dispatchers.IO) {
        check(type in setOf("image", "document", "video", "audio") && seconds >= 0)
        fun guard() { check(isCurrent(owner)) { "Account changed; media operation stopped." } }
        guard(); val ns = currentNamespace(owner); assertActive(ns)
        ensureConversation(conversationId); guard(); assertActive(ns)
        val dir = directory(ns); check(dir.isDirectory || dir.mkdirs())
        val preparedCipher = SecureAttachmentFileCodec.seal(source, dir, conversationId, name, mime, ::guard)
        var uploadFailure: Throwable? = null
        var retainedPath: File? = null
        var handedOff = false
        try {
            assertActive(ns)
            require(preparedCipher.file.length() <= localCipherLimit().coerceAtLeast(0)) { "Local encrypted media cache cannot retain this file. Increase its limit before sending." }
            val uploaded = transport.forScope(owner, isCurrent).upload(conversationId, preparedCipher.file, SecureAttachmentFileCodec.MAX_CIPHERTEXT)
            guard(); assertActive(ns)
            require(uploaded.attachmentId.matches(Regex("[A-Za-z0-9_-]{1,128}")))
            val descriptor = preparedCipher.descriptor.copy(attachmentId = uploaded.attachmentId)
            // Sender keeps bounded opaque ciphertext before publishing the descriptor.
            // Recipients may immediately confirm and delete the server blob.
            val retained = File(dir, "sent-${uploaded.attachmentId}.blob")
            check(!retained.exists() && preparedCipher.file.renameTo(retained)) { "Sender encrypted media could not be retained." }
            retainedPath = retained
            syncDirectory(dir)
            val cap = localCipherLimit().coerceAtLeast(0)
            var total = dir.listFiles()?.filter { it.name.startsWith("sent-") && it.name.endsWith(".blob") }?.sumOf { it.length() } ?: 0L
            for (old in dir.listFiles().orEmpty().filter { it != retained && it.name.startsWith("sent-") && it.name.endsWith(".blob") }.sortedBy { it.lastModified() }) {
                if (total <= cap) break
                val size = old.length(); check(old.delete()) { "Sender encrypted cache eviction failed." }; total -= size
            }
            check(total <= cap) { "Encrypted local cache limit is smaller than this attachment." }
            guard(); assertActive(ns); syncDirectory(dir)
            handedOff = true
            MessageAttachment(uploaded.attachmentId, type, descriptor.manifest.displayName,
                com.example.chat.data.services.ChatMediaService.formatBytes(descriptor.manifest.sizeBytes),
                url = null, durationSeconds = seconds, encryptedFile = descriptor.toMap())
        } catch (failure: Throwable) { uploadFailure = failure; throw failure }
        finally {
            preparedCipher.descriptor.destroy()
            if (!handedOff) retainedPath?.let { retained ->
                try { check(!retained.exists() || retained.delete()); syncDirectory(dir) }
                catch (cleanup: Exception) { if (uploadFailure != null) uploadFailure.addSuppressed(cleanup) else throw cleanup }
            }
            if (preparedCipher.file.exists() && !preparedCipher.file.delete()) {
                val cleanup = java.io.IOException("Encrypted upload temporary cleanup failed.")
                if (uploadFailure != null) uploadFailure.addSuppressed(cleanup) else throw cleanup
            }
        }
    }

    /** [lookup] must recover the trusted decrypted repository row, not caller metadata.
     * [ingest] writes through the existing account-bound encrypted cache and checks policy.
     * Confirm is after fsync, authenticated read-back and a live cache pin.
     * This is ephemeral local storage, not backup or recovery: explicit clear,
     * device loss or later eviction may permanently remove the last accessible
     * copy once all recipients acknowledge the transient server blob.
     */
    suspend fun fetch(owner: ApiAccountScope, ref: AttachmentRef, stamp: String,
                      lookup: (AttachmentRef) -> ChatMessage?,
                      ingest: suspend (AttachmentRef, String, File) -> String?,
                      pinStored: (AttachmentRef) -> com.example.chat.data.local.MediaRetentionPin?): Boolean = withOwner(owner) {
        fetchActive(owner, ref, stamp, lookup, ingest, pinStored)
    }

    private suspend fun fetchActive(owner: ApiAccountScope, ref: AttachmentRef, stamp: String,
                      lookup: (AttachmentRef) -> ChatMessage?,
                      ingest: suspend (AttachmentRef, String, File) -> String?,
                      pinStored: (AttachmentRef) -> com.example.chat.data.local.MediaRetentionPin?): Boolean = withContext(Dispatchers.IO) {
        fun live(): ChatMessage {
            check(isCurrent(owner)) { "Account changed; download stopped." }
            val m = checkNotNull(lookup(ref)) { "Encrypted media message is unavailable." }
            check(m.conversationId == ref.conversationId && m.id == ref.messageId && m.attachment?.id == ref.attachmentId &&
                !m.isDeletedForMe && !m.isDeletedForEveryone && !m.isRevokedBySender &&
                !com.example.chat.data.local.AttachmentCachePolicy.viewOnceRestricted(m.metadata)) { "Media is no longer authorized." }
            return m
        }
        val ns = currentNamespace(owner); assertActive(ns)
        val original = live().attachment?.encryptedFile ?: error("Missing encrypted descriptor.")
        fun guard() { check(live().attachment?.encryptedFile == original) { "Encrypted media descriptor changed." } }
        val descriptor = SecureAttachmentFileCodec.parse(original, ref.conversationId, ref.attachmentId)
        var ciphertext: File? = null; var plain: File? = null; var retainedSender = false
        var fetchFailure: Throwable? = null
        try {
            val dir = directory(ns); check(dir.isDirectory || dir.mkdirs())
            val scoped = transport.forScope(owner, isCurrent)
            val senderCopy = File(dir, "sent-${ref.attachmentId}.blob")
            retainedSender = senderCopy.isFile
            ciphertext = if (retainedSender) senderCopy else {
                check(isOnline()) { "Encrypted media is not downloaded; reconnect to fetch it." }
                scoped.download(ref.attachmentId, dir, SecureAttachmentFileCodec.cipherBytes(descriptor.manifest))
            }
            plain = SecureAttachmentFileCodec.open(checkNotNull(ciphertext), dir, descriptor, ::guard)
            guard(); assertActive(ns)
            check(ingest(ref, stamp, checkNotNull(plain)) == null) { "Authenticated media could not be stored for this account." }
            val pin = checkNotNull(pinStored(ref)) { "Stored encrypted media could not be retained and verified." }
            try {
                guard(); assertActive(ns); currentCoroutineContext().ensureActive()
                // No confirmation on failed storage/read-back/pin. Server copy stays.
                if (!retainedSender) scoped.confirmDelivered(ref.attachmentId) {
                    guard(); check(pin.isLive()) { "Private media storage was cleared during receipt confirmation." }
                }
                true
            } finally { pin.close() }
        } catch (failure: Throwable) { fetchFailure = failure; throw failure }
        finally {
            descriptor.destroy()
            val failures = listOfNotNull(plain, ciphertext.takeIf { !retainedSender }).filter { it.exists() && !it.delete() }
            if (failures.isNotEmpty()) {
                val cleanup = java.io.IOException("Encrypted media temporary cleanup failed.")
                if (fetchFailure != null) fetchFailure.addSuppressed(cleanup) else throw cleanup
            }
        }
    }
}
