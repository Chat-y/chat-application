package com.example.chat

import com.example.chat.data.remote.MediaStorageBackend
import com.example.chat.data.services.ProfileMediaService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Authored fixtures only; deliberately not run under source-only restrictions. */
class ProfileMediaCompressionOrderTest {
    private class Storage : MediaStorageBackend {
        var uploadedBytes = 0
        override suspend fun upload(bucket: String, path: String, bytes: ByteArray, contentType: String,
            onProgress: ((Long, Long) -> Unit)?) { uploadedBytes = bytes.size }
        override fun getPublicUrl(bucket: String, path: String) = "https://cdn.invalid/result.jpg"
        override suspend fun createSignedUrl(bucket: String, path: String, expiresInSeconds: Int) = getPublicUrl(bucket, path)
        override suspend fun remove(bucket: String, paths: List<String>) = Unit
    }
    @Test fun compressesLargeRawCropBeforeApplyingPayloadLimits() = runBlocking {
        val storage = Storage()
        val service = ProfileMediaService(storage, { "owner" }) { _, _, _ -> byteArrayOf(1, 2, 3) }
        assertEquals("https://cdn.invalid/result.jpg", service.uploadBanner(ByteArray(6 * 1024 * 1024 + 1)))
        assertEquals(3, storage.uploadedBytes)
    }
    @Test fun stillRejectsFinalPayloadAboveExistingTwoMegabyteCap() = runBlocking {
        val storage = Storage()
        val service = ProfileMediaService(storage, { "owner" }) { _, _, _ -> ByteArray(2 * 1024 * 1024 + 1) }
        try { service.uploadAvatar(byteArrayOf(1)); fail("Expected payload rejection") }
        catch (_: IllegalStateException) { assertEquals(0, storage.uploadedBytes) }
    }
}
