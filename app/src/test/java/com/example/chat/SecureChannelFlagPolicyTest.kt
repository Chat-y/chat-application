package com.example.chat

import com.example.chat.data.messaging.ConversationsRepository
import com.example.chat.data.messaging.SnapshotCacheStore
import com.example.chat.data.messaging.SupabaseRpc
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Policy regressions only. No real keystore, crypto, network or device proof. */
class SecureChannelFlagPolicyTest {
    private class Store : SnapshotCacheStore {
        val values = mutableMapOf<String, Any?>()
        var failRead = false
        var failWrite = false
        var failNextWrites = 0
        override suspend fun readJson(userId: String, scope: String): Any? = values[userId]
        override suspend fun writeJson(userId: String, scope: String, value: Any?) { values[userId] = value }
        override suspend fun readJsonStrict(userId: String, scope: String): Any? {
            check(!failRead) { "bad storage" }; return values[userId]
        }
        override suspend fun writeJsonStrict(userId: String, scope: String, value: Any?) {
            if (failNextWrites > 0) { failNextWrites--; error("temporary write failure") }
            // Previous store fixture write retained: check(!failWrite) { "disk full" }; values[userId] = value
            check(!failWrite) { "disk full" }; values[userId] = value
        }
    }
    private class Fixture {
        var scope: Any = Any()
        var user = "account-a"
        var holdCalls = 0
        var releaseCalls = 0
        val store = Store()
        val repo = ConversationsRepository(
            rpc = object : SupabaseRpc {
                override suspend fun rpc(function: String, params: Map<String, Any?>): Any? = error("No network expected")
            }, experimentalSecureRoutingEnabled = { true }, restSendsInFlight = { false },
            holdRestSends = { _, _ -> holdCalls++; 0 },
            releaseRestSends = { _, _ -> releaseCalls++ },
            mls = { null }, currentUserId = { user }, snapshotCache = store,
            scopeSnapshot = { scope }, withScope = { expected, action ->
                if (expected !== scope) false else { action(); true }
            },
        )
    }
    private suspend fun fails(action: suspend () -> Unit) {
        var failed = false
        try { action() } catch (_: IllegalStateException) { failed = true }
        assertTrue("Must reject instead of assuming plaintext", failed)
    }
    @Test fun unreadableStorageDoesNotResolveOff() = runBlocking {
        val f = Fixture(); f.store.failRead = true
        fails { f.repo.isSecureChannel("chat") }
        f.store.failRead = false
        assertFalse(f.repo.isSecureChannel("chat"))
    }
    @Test fun malformedStorageDoesNotResolveOff() = runBlocking {
        val f = Fixture(); f.store.values[f.user] = mapOf("chat" to true)
        fails { f.repo.isSecureChannel("chat") }
    }
    /* Previous write-failure fixture retained inactive. Secure-to-regular now
       rejects before any write, so exercise a second enable for write failure.
    @Test fun failedWriteKeepsPriorFlag() = runBlocking {
        val f = Fixture(); f.repo.setSecureChannel("chat", true)
        f.store.failWrite = true
        fails { f.repo.setSecureChannel("chat", false) }
        assertTrue(f.repo.isSecureChannel("chat"))
        assertEquals(listOf("chat"), f.store.values[f.user])
    }
    */
    @Test fun failedWriteKeepsPriorFlag() = runBlocking {
        val f = Fixture(); f.repo.setSecureChannel("chat", true)
        f.store.failWrite = true
        fails { f.repo.setSecureChannel("second", true) }
        assertTrue(f.repo.isSecureChannel("chat"))
        assertFalse(f.repo.isSecureChannel("second"))
        assertEquals(listOf("chat"), f.store.values[f.user])
    }
    @Test fun encryptedRoutingCannotReturnToRegularEvenWithHealthyStorage() = runBlocking {
        val f = Fixture(); f.repo.setSecureChannel("chat", true)
        fails { f.repo.setSecureChannel("chat", false) }
        assertTrue(f.repo.isSecureChannel("chat"))
        assertEquals(listOf("chat"), f.store.values[f.user])
    }
    @Test fun neverEncryptedConversationCanRemainRegular() = runBlocking {
        val f = Fixture(); f.repo.setSecureChannel("regular", false)
        assertFalse(f.repo.isSecureChannel("regular"))
    }
    @Test fun accountIdentityChangeReloadsEvenWithoutReset() = runBlocking {
        val f = Fixture(); f.repo.setSecureChannel("chat", true)
        f.user = "account-b"; f.scope = Any()
        assertFalse(f.repo.isSecureChannel("chat"))
        f.user = "account-a"; f.scope = Any()
        assertTrue(f.repo.isSecureChannel("chat"))
    }
    @Test fun separateChatsKeepIndependentFlags() = runBlocking {
        val f = Fixture(); f.repo.setSecureChannel("a", true); f.repo.setSecureChannel("b", true)
        // Previous downgrade fixture retained inactive:
        // f.repo.setSecureChannel("a", false)
        // assertFalse(f.repo.isSecureChannel("a")); assertTrue(f.repo.isSecureChannel("b"))
        fails { f.repo.setSecureChannel("a", false) }
        assertTrue(f.repo.isSecureChannel("a")); assertTrue(f.repo.isSecureChannel("b"))
    }

    @Test fun failedRepeatEnableNeverReleasesExistingRestQuarantine() = runBlocking {
        val f = Fixture(); f.repo.setSecureChannel("chat", true)
        // The attempted flag write fails once; restoring the prior true flag succeeds.
        f.store.failNextWrites = 1
        fails { f.repo.setSecureChannel("chat", true) }
        assertTrue(f.repo.isSecureChannel("chat"))
        assertEquals(2, f.holdCalls)
        assertEquals(0, f.releaseCalls)
    }
}
