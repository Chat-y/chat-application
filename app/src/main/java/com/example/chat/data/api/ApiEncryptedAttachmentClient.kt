package com.example.chat.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** Client for the backend's encrypted attachment transient queue
 *  (the /e2ee/attachments endpoints). The file bytes handed to [upload] must already be
 *  the client-side ciphertext - this client never sees plaintext or keys.
 *  Download returns the raw ciphertext bytes; [confirmDelivered] tells the
 *  server the blob can be deleted (deleted after delivery, by design). */
class ApiEncryptedAttachmentClient(
    private val baseUrl: () -> String,
    private val tenantId: () -> String,
    private val token: suspend () -> String?,
    private val client: OkHttpClient = com.example.chat.data.api.ChatyOkHttp.shared,
) {
    data class UploadResult(val attachmentId: String, val sizeBytes: Long)

    /** Explicit scope binding is required by the new file-based encrypted-media lane.
     * Existing byte-array entry points remain unchanged for compatibility. */
    fun forScope(owner: ApiAccountScope, isCurrent: (ApiAccountScope) -> Boolean): ScopedFiles {
        check(!owner.userId.isNullOrBlank() && !owner.token.isNullOrBlank()) { "Authentication required." }
        val frozen = ApiEncryptedAttachmentClient(
            baseUrl = { owner.baseUrl }, tenantId = { owner.tenantId }, token = { owner.token },
            client = client.newBuilder().followRedirects(false).followSslRedirects(false)
                .retryOnConnectionFailure(false).callTimeout(120, java.util.concurrent.TimeUnit.SECONDS).build(),
        )
        return ScopedFiles(frozen, requireOwner = { check(isCurrent(owner)) { "Account changed; encrypted media operation stopped." } })
    }

    /** Only ciphertext files belong here. Filename/MIME/caption/key stay inside the MLS descriptor.
     * This class is a transport prerequisite, not an encryption or authenticity assertion. */
    class ScopedFiles internal constructor(
        private val frozen: ApiEncryptedAttachmentClient,
        private val requireOwner: () -> Unit,
        private val deleteTemporary: (File) -> Boolean = { it.delete() },
    ) {
        private fun cleanup(file: File, failure: Throwable) {
            try {
                if (file.exists() && !deleteTemporary(file)) {
                    failure.addSuppressed(IOException("Ciphertext temporary-file cleanup failed; private cache sweep required."))
                }
            } catch (cleanupFailure: Exception) {
                failure.addSuppressed(cleanupFailure)
            }
        }
        suspend fun upload(conversationId: String, ciphertext: File, maxBytes: Long): UploadResult =
            withContext(Dispatchers.IO) {
                requireOwner()
                require(conversationId.isNotBlank() && maxBytes > 0) { "Invalid attachment request." }
                val size = ciphertext.length()
                require(ciphertext.isFile && size > 0 && size <= maxBytes) { "Ciphertext file is missing or exceeds the limit." }
                val operationContext = currentCoroutineContext()
                // A bounded body checks the captured owner and cancellation on every chunk.
                // It never follows a later source-path replacement once the stream is open.
                val fileBody = object : okhttp3.RequestBody() {
                    override fun contentType() = "application/octet-stream".toMediaType()
                    override fun contentLength() = size
                    override fun writeTo(sink: okio.BufferedSink) {
                        operationContext.ensureActive(); requireOwner()
                        java.io.FileInputStream(ciphertext).use { input ->
                            val buffer = ByteArray(65_536)
                            var remaining = size
                            while (remaining > 0) {
                                operationContext.ensureActive(); requireOwner()
                                val n = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                                if (n < 0) throw IOException("Ciphertext source was truncated.")
                                if (n == 0) continue
                                operationContext.ensureActive(); requireOwner()
                                sink.write(buffer, 0, n); remaining -= n
                            }
                            check(input.read() == -1) { "Ciphertext source grew during upload." }
                        }
                        operationContext.ensureActive(); requireOwner()
                    }
                }
                val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("conversation_id", conversationId)
                    .addFormDataPart("file", "blob.bin", fileBody)
                    .build()
                suspend fun execute(prefix: String): UploadResult? {
                    requireOwner()
                    val request = frozen.requestBuilder("${frozen.base()}$prefix/e2ee/attachments/upload").post(body).build()
                    frozen.client.newCall(request).execute().use { response ->
                        requireOwner()
                        if (response.code == 404 && prefix.isEmpty()) return null
                        // Only a small JSON envelope is expected, never buffer an unbounded error body.
                        val text = response.body?.charStream()?.use { reader ->
                            val chars = CharArray(65_537)
                            var count = 0
                            while (count < chars.size) {
                                val n = reader.read(chars, count, chars.size - count)
                                if (n < 0) break
                                if (n == 0) continue
                                count += n
                            }
                            check(count <= 65_536) { "Attachment response is too large." }
                            String(chars, 0, count)
                        }.orEmpty()
                        return frozen.parseUpload(response.code, text)
                    }
                }
                val result = execute("") ?: execute("/api/v1") ?: error("Attachment upload failed.")
                requireOwner()
                check(ciphertext.length() == size && result.sizeBytes == size) { "Ciphertext size changed or was not confirmed." }
                result
            }

        /** Returns a new ciphertext-only temp file. The caller owns deletion.
         * No existing destination can be replaced. Failure/stale owner deletes the partial file. */
        suspend fun download(attachmentId: String, directory: File, maxBytes: Long): File {
            requireOwner()
            require(maxBytes > 0 && directory.isDirectory) { "Invalid ciphertext destination." }
            require(attachmentId.matches(Regex("[A-Za-z0-9_-]{1,128}"))) { "Invalid attachment identity." }
            // Ownership lives outside withContext so prompt cancellation at its return
            // is caught here rather than orphaning a completed temporary file.
            val output = File.createTempFile("chaty-ciphertext-", ".part", directory)
            try {
                withContext(Dispatchers.IO) {
                requireOwner()
                currentCoroutineContext().ensureActive()
                require(maxBytes > 0 && directory.isDirectory) { "Invalid ciphertext destination." }
                require(attachmentId.matches(Regex("[A-Za-z0-9_-]{1,128}"))) { "Invalid attachment identity." }
                    suspend fun execute(prefix: String): Boolean {
                        requireOwner()
                        val request = frozen.requestBuilder("${frozen.base()}$prefix/e2ee/attachments/$attachmentId").get().build()
                        frozen.client.newCall(request).execute().use { response ->
                            requireOwner()
                            if (response.code == 404 && prefix.isEmpty()) return false
                            if (response.code !in 200..299) throw IOException("Attachment download failed (HTTP ${response.code})")
                            val body = response.body ?: throw IOException("Empty attachment response.")
                            val expected = body.contentLength()
                            check(expected <= maxBytes) { "Ciphertext exceeds the download limit." }
                            body.byteStream().use { input ->
                                java.io.FileOutputStream(output).use { sink ->
                                    val buffer = ByteArray(65_536)
                                    var total = 0L
                                    while (true) {
                                        currentCoroutineContext().ensureActive(); requireOwner()
                                        val n = input.read(buffer)
                                        if (n < 0) break
                                        if (n == 0) continue
                                        check(total <= maxBytes - n) { "Ciphertext exceeds the download limit." }
                                        currentCoroutineContext().ensureActive(); requireOwner()
                                        sink.write(buffer, 0, n); total += n
                                    }
                                    check(total > 0 && (expected < 0 || total == expected)) { "Truncated ciphertext response." }
                                    sink.fd.sync()
                                }
                            }
                            return true
                        }
                    }
                    if (!execute("")) check(execute("/api/v1")) { "Attachment unavailable." }
                    requireOwner()
                    currentCoroutineContext().ensureActive()
                }
                currentCoroutineContext().ensureActive(); requireOwner()
                return output
            } catch (failure: Throwable) {
                cleanup(output, failure)
                throw failure
            }
        }

        /** Call only after authenticated decrypt + durable recipient storage, never just download. */
        suspend fun confirmDelivered(attachmentId: String, requireReceipt: () -> Unit = {}) {
            requireOwner(); requireReceipt()
            require(attachmentId.matches(Regex("[A-Za-z0-9_-]{1,128}"))) { "Invalid attachment identity." }
            frozen.confirmDelivered(attachmentId) { requireOwner(); requireReceipt() }
            requireOwner(); requireReceipt()
        }
    }

    private suspend fun requestBuilder(url: String): Request.Builder {
        val builder = Request.Builder()
            .url(url)
            .addHeader("x-tenant-id", tenantId())
        token()?.let { builder.addHeader("Authorization", "Bearer $it") }
        return builder
    }

    private fun base() = baseUrl().trimEnd('/')

    /** Uploads [ciphertext] for [conversationId]; returns the attachment id.
     *  Throws with the server's detail on 413 (file too large) or 429
     *  (per-user pending quota full). */
    suspend fun upload(conversationId: String, ciphertext: ByteArray): UploadResult =
        withContext(Dispatchers.IO) {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("conversation_id", conversationId)
                .addFormDataPart(
                    "file", "blob.bin",
                    ciphertext.toRequestBody("application/octet-stream".toMediaType()),
                )
                .build()
            val request = requestBuilder("${base()}/e2ee/attachments/upload").post(body).build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (response.code == 404) {
                    // dual-mount fallback
                    val retry = requestBuilder("${base()}/api/v1/e2ee/attachments/upload").post(body).build()
                    client.newCall(retry).execute().use { r2 ->
                        return@withContext parseUpload(r2.code, r2.body?.string().orEmpty())
                    }
                }
                parseUpload(response.code, text)
            }
        }

    private fun parseUpload(code: Int, text: String): UploadResult {
        if (code !in 200..299) {
            val detail = runCatching {
                val parsed = JSONObject(text)
                if (parsed.has("detail")) parsed.optString("detail")
                else parsed.optJSONObject("error")?.optString("message").orEmpty()
            }.getOrDefault("")
            throw IOException(if (detail.isNotEmpty()) detail else "Attachment upload failed (HTTP $code)")
        }
        val data = JSONObject(text).getJSONObject("data")
        return UploadResult(
            attachmentId = data.getString("attachment_id"),
            sizeBytes = data.getLong("size_bytes"),
        )
    }

    /** Downloads the ciphertext. Throws FileNotFoundException when the blob
     *  was already delivered and deleted. */
    suspend fun download(attachmentId: String): ByteArray = withContext(Dispatchers.IO) {
        val request = requestBuilder("${base()}/e2ee/attachments/$attachmentId").get().build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) {
                val retry = requestBuilder("${base()}/api/v1/e2ee/attachments/$attachmentId").get().build()
                client.newCall(retry).execute().use { r2 ->
                    if (r2.code == 404) throw java.io.FileNotFoundException("Attachment delivered or expired.")
                    if (r2.code !in 200..299) throw IOException("Attachment download failed (HTTP ${r2.code})")
                    return@withContext r2.body!!.bytes()
                }
            }
            if (response.code == 404) throw java.io.FileNotFoundException("Attachment delivered or expired.")
            if (response.code !in 200..299) throw IOException("Attachment download failed (HTTP ${response.code})")
            response.body!!.bytes()
        }
    }

    /** Confirms receipt; the server deletes the blob once every other
     *  participant has confirmed. Idempotent. */
    suspend fun confirmDelivered(attachmentId: String, requireReceipt: () -> Unit = {}): Unit = withContext(Dispatchers.IO) {
        requireReceipt()
        val request = requestBuilder("${base()}/e2ee/attachments/$attachmentId/delivered")
            .post("".toRequestBody(null))
            .build()
        requireReceipt()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) {
                requireReceipt()
                val retry = requestBuilder("${base()}/api/v1/e2ee/attachments/$attachmentId/delivered")
                    .post("".toRequestBody(null)).build()
                client.newCall(retry).execute().use { r2 ->
                    if (r2.code !in 200..299 && r2.code != 404) {
                        throw IOException("Delivery confirmation failed (HTTP ${r2.code})")
                    }
                    return@withContext
                }
            }
            if (response.code !in 200..299 && response.code != 404) {
                throw IOException("Delivery confirmation failed (HTTP ${response.code})")
            }
        }
    }
}

// Previous implementation retained inactive (v5 review):
//         suspend fun confirmDelivered(attachmentId: String) {
//             requireOwner()
//             frozen.confirmDelivered(attachmentId)
//             requireOwner()
//     suspend fun confirmDelivered(attachmentId: String): Unit = withContext(Dispatchers.IO) {
