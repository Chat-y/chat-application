package com.example.chat

import com.example.chat.data.api.ApiAccountScope
import com.example.chat.data.api.ApiEncryptedAttachmentClient
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import okio.buffer
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

/** UNRUN transport fixtures. Ciphertext-only IO does not prove encryption or MLS/device delivery. */
class ScopedEncryptedAttachmentFilesTest {
    private fun client(server: MockWebServer) = ApiEncryptedAttachmentClient(
        baseUrl = { error("Ambient base forbidden") }, tenantId = { error("Ambient tenant forbidden") },
        token = { error("Ambient token forbidden") }, client = OkHttpClient(),
    )
    private fun owner(server: MockWebServer) = ApiAccountScope("a", "token-a", "tenant-a", server.url("/").toString(), "", 1)
    private suspend fun rejected(action: suspend () -> Unit) {
        var failed = false
        try { action() } catch (_: Exception) { failed = true }
        assertTrue("Must fail closed", failed)
    }
    @Test fun frozenOwnerStreamsFileAndUsesOnlyOpaqueFilename() = runBlocking {
        val server = MockWebServer(); server.start()
        val dir = java.nio.file.Files.createTempDirectory("cipher-io").toFile()
        try {
            val source = File(dir, "private-filename.pdf").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            server.enqueue(MockResponse().setBody("""{"data":{"attachment_id":"blob-id","size_bytes":3}}"""))
            val scoped = client(server).forScope(owner(server)) { true }
            assertEquals("blob-id", scoped.upload("room", source, 10).attachmentId)
            val request = server.takeRequest(1, TimeUnit.SECONDS)!!
            assertEquals("Bearer token-a", request.getHeader("Authorization"))
            assertEquals("tenant-a", request.getHeader("x-tenant-id"))
            val body = request.body.readUtf8()
            assertTrue(body.contains("blob.bin")); assertFalse(body.contains("private-filename.pdf"))
        } finally { server.shutdown(); dir.deleteRecursively() }
    }
    @Test fun staleOwnerAndOversizeSourceSendNothing() = runBlocking {
        val server = MockWebServer(); server.start()
        val dir = java.nio.file.Files.createTempDirectory("cipher-io").toFile()
        try {
            val source = File(dir, "cipher").apply { writeBytes(ByteArray(11)) }
            rejected { client(server).forScope(owner(server)) { false }.upload("room", source, 100); Unit }
            rejected { client(server).forScope(owner(server)) { true }.upload("room", source, 10); Unit }
            assertEquals(0, server.requestCount)
        } finally { server.shutdown(); dir.deleteRecursively() }
    }
    @Test fun oversizedUnknownLengthDownloadDeletesPartialAndNeverAcknowledges() = runBlocking {
        val server = MockWebServer(); server.start()
        val dir = java.nio.file.Files.createTempDirectory("cipher-io").toFile()
        try {
            server.enqueue(MockResponse().setChunkedBody("12345678901", 2))
            rejected { client(server).forScope(owner(server)) { true }.download("blob-id", dir, 10); Unit }
            assertEquals(0, dir.listFiles()!!.size)
            assertEquals(1, server.requestCount)
        } finally { server.shutdown(); dir.deleteRecursively() }
    }
    @Test fun downloadSuccessReturnsCiphertextWithoutDeliveryAck() = runBlocking {
        val server = MockWebServer(); server.start()
        val dir = java.nio.file.Files.createTempDirectory("cipher-io").toFile()
        try {
            server.enqueue(MockResponse().setBody("opaque"))
            val result = client(server).forScope(owner(server)) { true }.download("blob-id", dir, 20)
            assertEquals("opaque", result.readText()); assertEquals(1, server.requestCount)
        } finally { server.shutdown(); dir.deleteRecursively() }
    }
    @Test fun redirectIsRejectedRatherThanForwardingTenantOrCiphertext() = runBlocking {
        val server = MockWebServer(); val other = MockWebServer(); server.start(); other.start()
        val dir = java.nio.file.Files.createTempDirectory("cipher-io").toFile()
        try {
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", other.url("/steal")))
            rejected { client(server).forScope(owner(server)) { true }.download("blob-id", dir, 10); Unit }
            assertEquals(0, other.requestCount); assertEquals(0, dir.listFiles()!!.size)
        } finally { server.shutdown(); other.shutdown(); dir.deleteRecursively() }
    }

    @Test fun growingUploadAndMidstreamOwnerSwitchAreRejected() = runBlocking {
        for (grow in listOf(true, false)) {
            val dir = java.nio.file.Files.createTempDirectory("cipher-body").toFile()
            try {
                val source = File(dir, "cipher").apply { writeBytes(ByteArray(200_000)) }
                val current = java.util.concurrent.atomic.AtomicBoolean(true)
                val changed = java.util.concurrent.atomic.AtomicBoolean(false)
                val bodyRejected = java.util.concurrent.atomic.AtomicBoolean(false)
                val http = OkHttpClient.Builder().addInterceptor { chain ->
                    val target = object : okio.ForwardingSink(okio.Buffer()) {
                        override fun write(buffer: okio.Buffer, byteCount: Long) {
                            super.write(buffer, byteCount)
                            if (changed.compareAndSet(false, true)) {
                                if (grow) source.appendBytes(byteArrayOf(1)) else current.set(false)
                            }
                        }
                    }
                    val sink = target.buffer()
                    try { chain.request().body!!.writeTo(sink); sink.flush() }
                    catch (failure: Exception) { bodyRejected.set(true); throw failure }
                    // A broken body that completes must yield success, making rejected{} fail.
                    okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                        .code(200).message("OK").body(okhttp3.ResponseBody.create(null,
                            """{"data":{"attachment_id":"blob","size_bytes":200000}}""")).build()
                }.build()
                val owner = ApiAccountScope("a", "token", "tenant", "https://example.invalid", "", 1)
                val client = ApiEncryptedAttachmentClient({ owner.baseUrl }, { owner.tenantId }, { owner.token }, http)
                rejected { client.forScope(owner) { current.get() }.upload("room", source, 300_000); Unit }
                assertTrue(changed.get())
                assertTrue("The bounded body itself must reject mutation/stale owner", bodyRejected.get())
            } finally { dir.deleteRecursively() }
        }
    }
    @Test fun cancellationAtDownloadCompletionCleansOwnedTemporary() = runBlocking {
        val dir = java.nio.file.Files.createTempDirectory("cipher-cancel").toFile()
        try {
            lateinit var operation: kotlinx.coroutines.Job
            val http = OkHttpClient.Builder().addInterceptor { chain ->
                val buffer = okio.Buffer().writeUtf8("opaque")
                val body = object : okhttp3.ResponseBody() {
                    override fun contentType(): okhttp3.MediaType? = null
                    override fun contentLength() = 6L
                    override fun source(): okio.BufferedSource = object : okio.ForwardingSource(buffer) {
                        override fun read(sink: okio.Buffer, byteCount: Long): Long {
                            val n = super.read(sink, byteCount)
                            if (n < 0) operation.cancel()
                            return n
                        }
                    }.buffer()
                }
                okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200).message("OK").body(body).build()
            }.build()
            val owner = ApiAccountScope("a", "token", "tenant", "https://example.invalid", "", 1)
            val client = ApiEncryptedAttachmentClient({ owner.baseUrl }, { owner.tenantId }, { owner.token }, http)
            operation = launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                client.forScope(owner) { true }.download("blob", dir, 100)
            }
            operation.start(); operation.join()
            assertTrue(operation.isCancelled)
            assertEquals(0, dir.listFiles()!!.size)
        } finally { dir.deleteRecursively() }
    }
    @Test fun cleanupFailureIsReportedOnOriginalFailure() = runBlocking {
        val dir = java.nio.file.Files.createTempDirectory("cipher-cleanup").toFile()
        try {
            val http = OkHttpClient.Builder().addInterceptor { chain ->
                okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(500).message("fail").body(okhttp3.ResponseBody.create(null, "error")).build()
            }.build()
            val client = ApiEncryptedAttachmentClient({ "https://example.invalid" }, { "tenant" }, { "token" }, http)
            val files = ApiEncryptedAttachmentClient.ScopedFiles(client, {}, deleteTemporary = { false })
            var failure: Throwable? = null
            try { files.download("blob", dir, 100) } catch (caught: Exception) { failure = caught }
            assertNotNull(failure)
            val caught = checkNotNull(failure)
            assertEquals(1, caught.suppressed.size)
            assertTrue(caught.suppressed[0].message!!.contains("cleanup failed"))
        } finally { dir.deleteRecursively() }
    }
}
