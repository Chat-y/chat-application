package com.example.chat.data.messaging

import com.example.chat.core.crypto.MlsEncryptor
import com.example.chat.core.crypto.MlsEncryptedPayload
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Synthetic fixtures only, authored but not run under the no-build gate. */
class ImmutableMlsRevisionsTest {
    private val id = "11111111-1111-1111-1111-111111111111"
    private val user = "22222222-2222-2222-2222-222222222222"
    private class Cache : SnapshotCacheStore {
        val entries = mutableMapOf<String, Any?>()
        override suspend fun readJson(userId: String, scope: String) = error("Strict only")
        override suspend fun writeJson(userId: String, scope: String, value: Any?) { error("Strict only") }
        override suspend fun listRevisionPayloadScopes(userId: String) = entries.filter { (key, value) ->
            key.startsWith("$userId:") && value != null && (key.removePrefix("$userId:").startsWith("own_revision_") || key.removePrefix("$userId:").startsWith("revision_pending_"))
        }.keys.map { it.removePrefix("$userId:") }
        override suspend fun readJsonStrict(userId: String, scope: String) = entries["$userId:$scope"]
        override suspend fun writeJsonStrict(userId: String, scope: String, value: Any?) { entries["$userId:$scope"] = value }
    }
    private class Crypto : MlsEncryptor {
        override val currentDeviceId = "device"
        var encryptions = 0; var decryptions = 0; var rejectRetry = false
        override suspend fun encryptPayload(conversationId: String, payload: Map<String, Any?>): MlsEncryptedPayload {
            encryptions++; return MlsEncryptedPayload("group", 7, "cipher-$encryptions")
        }
        override suspend fun encryptEditedPayload(conversationId: String, payload: Map<String, Any?>) = encryptPayload(conversationId, payload)
        override suspend fun assertStoredCipherReady(conversationId: String, cipher: MlsEncryptedPayload) { check(!rejectRetry) }
        override suspend fun decryptPayload(conversationId: String, ciphertext: String): Map<String, Any?> { decryptions++; error("Own history must not decrypt") }
    }
    private inner class Backend : SupabaseRpc {
        val revisions = mutableListOf<Map<String, Any?>>()
        var timer: Any? = null
        var timerPresent = true
        var acknowledgementTimer: Any? = null
        var missingMessage = false
        var lostResponse = false
        var lastPost: Map<String, Any?>? = null
        override suspend fun rpc(function: String, params: Map<String, Any?>): Any? = when (function) {
            "get_mls_message_revisions" -> {
                check(!missingMessage) { "Synthetic404" }
                mapOf("message_id" to id, "base_revision_seq" to 0, "current_revision_seq" to revisions.size, "revisions" to revisions.toList()) +
                    if (timerPresent) mapOf("disappear_seconds" to timer) else emptyMap()
            }
            "edit_mls_message_v1" -> {
                lastPost = params
                val revision = ImmutableMlsRevisions.Revision(revisions.size + 1, "", revisions.lastOrNull()?.get("revision_hash") as? String,
                    user, params["p_sender_device_id"] as String, params["p_group_id"] as String, params["p_epoch"] as Int,
                    params["p_ciphertext"] as String, params["p_client_revision_id"] as String)
                revisions.add(mapOf("revision_seq" to revision.seq, "revision_hash" to ImmutableMlsRevisions.contentHash(id, revision),
                    "prev_revision_hash" to revision.previous, "editor_user_id" to user, "editor_device_id" to revision.device,
                    "mls_group_id" to revision.group, "mls_epoch" to revision.epoch, "ciphertext" to revision.ciphertext,
                    "client_revision_id" to revision.client, "hash_version" to "chaty-edit-v1"))
                if (lostResponse) error("Synthetic response loss after acceptance")
                mapOf("edited" to true, "disappear_seconds" to acknowledgementTimer)
            }
            else -> error("Unexpected RPC")
        }
    }
    @Test fun compactUnicodeHashMatchesIndependentVector() {
        val revision = ImmutableMlsRevisions.Revision(1, "", null, user, "device-漢字", "group", 7, "cipher\"\\\n", null)
        assertEquals("156717da36145649218d2c24fc2f28737f39f0de0dfb916642d979851987ff2b", ImmutableMlsRevisions.contentHash(id, revision))
    }
    @Test fun lostEditResponseRecoversBeforeEpochRetryGateWithoutNewCiphertext() = runBlocking {
        val cache = Cache(); val backend = Backend(); val crypto = Crypto()
        val client = ImmutableMlsRevisions(backend, cache, "owner", user, crypto, {})
        backend.lostResponse = true
        try { client.edit("room", id, "edited", emptyMap()); fail() } catch (_: IllegalStateException) { }
        assertEquals(1, crypto.encryptions)
        crypto.rejectRetry = true // v2 permits accepted recovery after epoch/cutoff.
        client.edit("room", id, "edited", emptyMap())
        assertEquals(1, crypto.encryptions); assertEquals(1, backend.revisions.size)
        val revision = backend.revisions.single()
        val projection = client.project("room", id, user, 1, revision["revision_hash"] as String, "cipher-1", "device", "group", 7)
        assertEquals("edited", projection.first["text"]); assertEquals(0, crypto.decryptions)
        assertTrue(backend.lastPost!!.containsKey("p_client_revision_id")); assertEquals(0, backend.lastPost!!["p_base_revision_seq"])
    }
    @Test fun tamperedChainOrRelabeledMessageNeverProjectsOwnText() = runBlocking {
        val cache = Cache(); val backend = Backend(); val crypto = Crypto()
        val client = ImmutableMlsRevisions(backend, cache, "owner", user, crypto, {})
        client.edit("room", id, "edited", emptyMap())
        val revision = backend.revisions.single()
        var rejected = false
        try { client.project("other-room", id, user, 1, revision["revision_hash"] as String, "cipher-1", "device", "group", 7) }
        catch (_: IllegalStateException) { rejected = true }
        assertTrue(rejected); assertEquals(0, crypto.decryptions)
        backend.revisions[0] = revision + ("ciphertext" to "tampered")
        rejected = false
        try { client.chain(id) } catch (_: IllegalStateException) { rejected = true }
        assertTrue(rejected)
    }
    @Test fun deleteRetiresAndErasesAuthoredRevisionPayloads() = runBlocking {
        val cache = Cache(); val backend = Backend(); val crypto = Crypto()
        val client = ImmutableMlsRevisions(backend, cache, "owner", user, crypto, {})
        client.edit("room", id, "edited", emptyMap())
        ImmutableMlsRevisions.forget(cache, "owner", id)
        assertTrue(cache.entries.filterKeys { it.contains("own_revision_") }.values.all { it == null })
        var rejected = false
        try { client.edit("room", id, "again", emptyMap()) } catch (_: IllegalStateException) { rejected = true }
        assertTrue(rejected); assertEquals(1, crypto.encryptions)
    }
    @Test fun timerOnMissingInvalidFailClosedBeforeEncryption() = runBlocking {
        for ((present, timer) in listOf(true to 60, false to null, true to "off", true to 0, true to -1, true to 1.5)) {
            val cache = Cache(); val backend = Backend(); val crypto = Crypto()
            backend.timerPresent = present; backend.timer = timer
            val client = ImmutableMlsRevisions(backend, cache, "owner", user, crypto, {})
            var blocked = false
            try { client.edit("room", id, "edited", emptyMap()) } catch (_: IllegalStateException) { blocked = true }
            assertTrue(blocked); assertEquals(0, crypto.encryptions); assertNull(backend.lastPost)
        }
    }
    @Test fun acknowledgementTimerChangedErasesPersistedOperationWithoutNewKey() = runBlocking {
        val cache = Cache(); val backend = Backend(); val crypto = Crypto()
        backend.acknowledgementTimer = 60
        val client = ImmutableMlsRevisions(backend, cache, "owner", user, crypto, {})
        var blocked = false
        try { client.edit("room", id, "edited", emptyMap()) } catch (_: IllegalStateException) { blocked = true }
        assertTrue(blocked); assertEquals(1, backend.revisions.size); assertEquals(1, crypto.encryptions)
        assertTrue(cache.entries.filterKeys { it.contains("own_revision_") || it.contains("revision_pending_") }.values.all { it == null })
    }
    @Test fun timerEnabledLaterMissingAndEmptyChainsEraseAuthoredOrphans() = runBlocking {
        for (mode in listOf("timer", "missing", "empty", "unknown")) {
            val cache = Cache(); val backend = Backend(); val crypto = Crypto()
            val client = ImmutableMlsRevisions(backend, cache, "owner", user, crypto, {})
            client.edit("room", id, "edited", emptyMap())
            cache.entries["owner:own_revision_orphan"] = mapOf("message_id" to id, "room_id" to "room", "payload" to mapOf("text" to "orphan"))
            when (mode) { "timer" -> backend.timer = 60; "missing" -> backend.missingMessage = true
                "empty" -> backend.revisions.clear(); "unknown" -> backend.timerPresent = false }
            ImmutableMlsRevisions.sweepRetention(cache, "owner", backend, {})
            assertTrue(cache.entries.filterKeys { it.contains("own_revision_") || it.contains("revision_pending_") }.values.all { it == null })
            assertEquals(1, crypto.encryptions)
        }
    }
    @Test fun emptyChainAfterPriorEditCannotBeResurrectedAsOriginal() = runBlocking {
        val cache = Cache(); val backend = Backend(); val crypto = Crypto()
        val client = ImmutableMlsRevisions(backend, cache, "owner", user, crypto, {})
        client.edit("room", id, "edited", emptyMap()); backend.revisions.clear()
        var blocked = false
        try { client.edit("room", id, "again", emptyMap()) } catch (_: IllegalStateException) { blocked = true }
        assertTrue(blocked); assertEquals(1, crypto.encryptions)
        assertTrue(cache.entries.filterKeys { it.contains("own_revision_") || it.contains("revision_pending_") }.values.all { it == null })
    }

}

// Row51 first-delta originals retained after P1 retention correction.
//             "get_mls_message_revisions" -> mapOf("message_id" to id, "base_revision_seq" to 0, "current_revision_seq" to revisions.size, "revisions" to revisions.toList())
//                 mapOf("edited" to true)
