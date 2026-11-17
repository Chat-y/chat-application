package com.example.chat.data.media

import com.example.chat.data.transfer.NativeP2pTransferProtocol as Chunks
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** File envelope for transient attachment storage. Only opaque frames go to HTTP;
 * all descriptor fields, including the random per-file key, travel inside MLS.
 * Reuses the existing authenticated chunk format, not an alternate plaintext lane.
 * Every frame is bounded, ordered, authenticated, and checked against final digest.
 */
object SecureAttachmentFileCodec {
    const val FORMAT = "chaty-attachment-chunks-v1"
    const val MAX_CIPHERTEXT = 100L * 1024 * 1024
    private const val MAGIC = 0x43414631
    private const val CHUNK_BYTES = 64 * 1024
    private const val FRAME_OVERHEAD = 8 + 12 + 16

    data class Descriptor(
        val conversationId: String,
        val attachmentId: String,
        val secret: ByteArray,
        val manifest: Chunks.Manifest,
    ) {
        fun toMap(): Map<String, Any?> = mapOf(
            "format" to FORMAT, "conversation_id" to conversationId,
            "attachment_id" to attachmentId, "key_b64" to enc(secret),
            "transfer_id" to manifest.transferId, "name" to manifest.displayName,
            "mime" to manifest.mimeType, "plain_bytes" to manifest.sizeBytes,
            "chunk_bytes" to manifest.chunkBytes, "sha256" to manifest.sha256Hex,
            "nonce_prefix_b64" to enc(manifest.noncePrefix),
        )
        fun destroy() { secret.fill(0) }
    }
    data class SealedFile(val file: File, val descriptor: Descriptor)
    private fun enc(v: ByteArray) = Base64.getEncoder().encodeToString(v)
    private fun dec(v: String, size: Int): ByteArray {
        require(v.length <= 128)
        return Base64.getDecoder().decode(v).also { require(it.size == size) }
    }
    fun cipherBytes(m: Chunks.Manifest): Long = 4 + m.sizeBytes + m.chunkCount * (4 + FRAME_OVERHEAD)

    fun parse(raw: Map<String, Any?>, conversationId: String, attachmentId: String): Descriptor {
        require(raw["format"] == FORMAT && raw["conversation_id"] == conversationId && raw["attachment_id"] == attachmentId)
        fun str(key: String, max: Int) = (raw[key] as? String)?.also { require(it.length in 1..max) } ?: error("Invalid media descriptor.")
        fun num(key: String): Long {
            val n = raw[key] as? Number ?: error("Invalid media geometry.")
            val value = n.toLong()
            require(n.toDouble().isFinite() && n.toDouble() == value.toDouble())
            return value
        }
        val id = str("transfer_id", 36)
        require(java.util.UUID.fromString(id).toString() == id)
        val bytes = num("plain_bytes"); val chunk = num("chunk_bytes")
        require(bytes in 1..MAX_CIPHERTEXT && chunk == CHUNK_BYTES.toLong())
        val digest = str("sha256", 64); require(digest.matches(Regex("[0-9a-f]{64}")))
        val name = str("name", 180); require(!name.contains('/') && !name.contains('\\') && name.none { it.code < 32 })
        val m = Chunks.Manifest(id, name, str("mime", 180), bytes, CHUNK_BYTES, digest,
            dec(str("nonce_prefix_b64", 128), 4), Long.MAX_VALUE)
        require(cipherBytes(m) <= MAX_CIPHERTEXT)
        return Descriptor(conversationId, attachmentId, dec(str("key_b64", 128), 32), m)
    }

    suspend fun seal(source: File, directory: File, conversationId: String, name: String, mime: String,
                     requireOwner: () -> Unit): SealedFile {
        requireOwner(); require(conversationId.isNotBlank() && directory.isDirectory)
        val invite = Chunks.createInvite()
        var output: File? = null
        try {
            FileInputStream(source).channel.use { input ->
                val size = input.size()
                require(size in 1..MAX_CIPHERTEXT) { "Attachment is empty or exceeds the encrypted file limit." }
                val count = (size + CHUNK_BYTES - 1) / CHUNK_BYTES
                require(4 + size + count * (4 + FRAME_OVERHEAD) <= MAX_CIPHERTEXT)
                // Cancellable bounded hash pass; do not run the old 100GB transfer helper.
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = java.nio.ByteBuffer.allocate(CHUNK_BYTES)
                var at = 0L
                try {
                    while (at < size) {
                        currentCoroutineContext().ensureActive(); requireOwner()
                        buffer.clear(); buffer.limit(minOf(CHUNK_BYTES.toLong(), size - at).toInt())
                        val n = input.read(buffer, at); check(n > 0) { "Attachment changed while hashing." }
                        digest.update(buffer.array(), 0, n); at += n
                    }
                } finally { buffer.array().fill(0) }
                val safeName = name.map { if (it == '/' || it == '\\' || it.code < 32) '_' else it }.joinToString("").take(180).ifBlank { "attachment" }
                val safeMime = mime.lowercase().take(180).takeIf { it.matches(Regex("[a-z0-9.+_-]+/[a-z0-9.+_-]+")) } ?: "application/octet-stream"
                val m = Chunks.Manifest(invite.transferId, safeName, safeMime, size, CHUNK_BYTES,
                    digest.digest().joinToString("") { "%02x".format(it) }, ByteArray(4).also(java.security.SecureRandom()::nextBytes), Long.MAX_VALUE)
                require(cipherBytes(m) <= MAX_CIPHERTEXT) { "Encrypted attachment exceeds the server file limit." }
                val target = File.createTempFile("chaty-sealed-", ".part", directory); output = target
                FileOutputStream(target).use { stream ->
                    val out = DataOutputStream(stream); out.writeInt(MAGIC)
                    for (index in 0 until m.chunkCount) {
                        currentCoroutineContext().ensureActive(); requireOwner()
                        val frame = Chunks.encryptChunk(input, m, invite.secret, index)
                        try {
                            out.writeInt(FRAME_OVERHEAD + frame.ciphertextAndTag.size - 16)
                            out.writeLong(index); out.write(frame.nonce); out.write(frame.ciphertextAndTag)
                        } finally { frame.ciphertextAndTag.fill(0); frame.nonce.fill(0) }
                    }
                    out.flush(); stream.fd.sync()
                }
                currentCoroutineContext().ensureActive(); requireOwner()
                check(target.length() == cipherBytes(m))
                return SealedFile(target, Descriptor(conversationId, "", invite.secret, m))
            }
        } catch (failure: Throwable) {
            invite.secret.fill(0)
            output?.let { if (it.exists() && !it.delete()) failure.addSuppressed(java.io.IOException("Ciphertext temporary cleanup failed.")) }
            throw failure
        }
    }

    /** Destination never leaves this method until every tag and final digest passes.
     * The caller must store it in the encrypted account cache, then delete it. */
    suspend fun open(ciphertext: File, directory: File, descriptor: Descriptor, requireOwner: () -> Unit): File {
        requireOwner(); val m = descriptor.manifest
        require(ciphertext.length() == cipherBytes(m) && directory.isDirectory)
        val plain = File.createTempFile("chaty-verified-", ".part", directory)
        try {
            DataInputStream(FileInputStream(ciphertext)).use { input ->
                check(input.readInt() == MAGIC) { "Unsupported encrypted attachment version." }
                for (index in 0 until m.chunkCount) {
                    currentCoroutineContext().ensureActive(); requireOwner()
                    val size = minOf(m.chunkBytes.toLong(), m.sizeBytes - index * m.chunkBytes).toInt()
                    require(input.readInt() == size + FRAME_OVERHEAD)
                    require(input.readLong() == index) { "Encrypted media frames reordered or duplicated." }
                    val nonce = ByteArray(12); input.readFully(nonce)
                    val cipher = ByteArray(size + 16); input.readFully(cipher)
                    try { Chunks.writeChunk(plain, m, descriptor.secret, Chunks.EncryptedChunk(index, nonce, cipher)) }
                    finally { nonce.fill(0); cipher.fill(0) }
                }
                check(input.read() == -1) { "Trailing encrypted attachment data." }
            }
            currentCoroutineContext().ensureActive(); requireOwner()
            check(plain.length() == m.sizeBytes)
            val digest = MessageDigest.getInstance("SHA-256")
            val verifyBuffer = ByteArray(CHUNK_BYTES)
            try {
                FileInputStream(plain).use { stream ->
                    while (true) {
                        currentCoroutineContext().ensureActive(); requireOwner()
                        val n = stream.read(verifyBuffer); if (n < 0) break
                        digest.update(verifyBuffer, 0, n)
                    }
                }
                check(MessageDigest.isEqual(digest.digest(), m.sha256Hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray())) { "Encrypted attachment digest mismatch." }
            } finally { verifyBuffer.fill(0) }
            Chunks.syncFile(plain)
            currentCoroutineContext().ensureActive(); requireOwner()
            return plain
        } catch (failure: Throwable) {
            if (plain.exists() && !plain.delete()) failure.addSuppressed(java.io.IOException("Verified-media temporary cleanup failed."))
            throw failure
        }
    }
}
