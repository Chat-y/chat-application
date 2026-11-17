package com.example.chat

import com.example.chat.data.e2ee.*
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Authored UNRUN; no native identity attestation is claimed. */
class DeviceKeyTrustStoreTest {
    private class Store : SecureKeyValueStore {
        val values = mutableMapOf<String, String>()
        override suspend fun read(key: String) = values[key]
        override suspend fun write(key: String, value: String) { values[key] = value }
        override suspend fun delete(key: String) { values.remove(key) }
    }
    private fun device(byte: Byte, id: String = "device") = MlsDeviceDescriptor("user", id, "user:$id", Base64.getEncoder().encodeToString(ByteArray(32) { byte }))
    @Test fun firstSeenIsLabelledNotVerifiedAndChangedKeyNeedsExactApproval() = runBlocking {
        val storage = Store(); val pins = DeviceKeyTrustStore(storage)
        pins.inspect("ns", listOf(device(1)))
        assertEquals("first-seen", pins.observed.value.single().label)
        var rejected = false
        try { pins.inspect("ns", listOf(device(2))) } catch (_: IllegalStateException) { rejected = true }
        assertTrue(rejected)
        val review = pins.reviews.value.single()
        assertNotNull(review.previous)
        pins.approve(review)
        pins.inspect("ns", listOf(device(2)))
        assertTrue(pins.reviews.value.isEmpty())
        assertEquals("explicitly-approved", pins.observed.value.single().label)
    }
    @Test fun laterDeviceRequiresApprovalAndNamespacePinsStaySeparate() = runBlocking {
        val storage = Store(); val pins = DeviceKeyTrustStore(storage)
        pins.inspect("a", listOf(device(1)))
        var rejected = false
        try { pins.inspect("a", listOf(device(1), device(2, "new"))) } catch (_: IllegalStateException) { rejected = true }
        assertTrue(rejected)
        pins.inspect("b", listOf(device(2)))
        assertEquals("first-seen", pins.observed.value.first { it.namespace == "b" }.label)
    }
    @Test fun nativeMemberMustMatchPinnedSigningKey() = runBlocking {
        val pins = DeviceKeyTrustStore(Store())
        pins.inspect("a", listOf(device(1)))
        pins.assertMember("a", "user:device", ByteArray(32) { 1 })
        var rejected = false
        try { pins.assertMember("a", "user:device", ByteArray(32) { 2 }) } catch (_: IllegalStateException) { rejected = true }
        assertTrue(rejected)
    }
}
