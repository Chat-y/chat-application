package com.example.chat

import com.example.chat.core.preferences.*
import com.example.chat.domain.models.SecurityPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocalProtectionSyncIntegrationTest {
    private class Store : PreferencesBlobStore {
        var blob: Map<String,Any?> = emptyMap()
        override suspend fun load(userId: String?) = blob
        override suspend fun hasScoped(userId: String) = true
        override suspend fun save(userId: String?, data: Map<String,Any?>) { blob = data }
        override suspend fun clearGlobal() { error("must not wipe") }
        override suspend fun storedUserId(): String? = "owner"
        override suspend fun setStoredUserId(userId: String?) { }
    }
    private class Auth : PreferencesAuthSource {
        override fun currentUserId() = "owner"
        override fun addListener(listener: (String?) -> Unit) { }
        override fun removeListener(listener: (String?) -> Unit) { }
    }
    private class Lock : LockCredentialStore {
        override suspend fun hasCredential(method: String) = false
        override suspend fun setCredential(method: String, secret: String, pinLength: Int?) { error("not enrollment") }
    }
    private class Remote : RemoteSettingsStore {
        var row: RemoteSettingsRow? = null
        val pushes = mutableListOf<Map<String,Any?>>()
        override suspend fun fetch(userId: String) = row
        override suspend fun push(userId: String, snapshot: Map<String,Any?>) { pushes.add(snapshot) }
    }
    @Test fun localMembershipPersistsButIsNeverPushedAndRemoteCannotReplaceIt() = runTest {
        val store = Store(); val remote = Remote()
        val controller = ChatyPreferencesController(store, remote, Lock(), Auth(), this, now = { 10_000L }, remoteSyncDebounceMs = 0)
        runCurrent()
        controller.updateSecurity(SecurityPreferences(lockedConversationIds = listOf("local-lock"), hiddenConversationIds = listOf("local-hidden")))
        runCurrent()
        assertTrue(remote.pushes.isNotEmpty())
        assertTrue(remote.pushes.none(LocalProtectionMetadata::isPresent))
        assertTrue(LocalProtectionMetadata.isPresent(store.blob))
        remote.row = RemoteSettingsRow(mapOf("schemaVersion" to 2, "security" to mapOf("lockedConversationIds" to listOf("foreign-lock"), "hiddenConversationIds" to listOf("foreign-hidden"), "lockMethod" to "Password")), null)
        controller.syncFromRemote(); runCurrent()
        assertEquals(listOf("local-lock"), controller.security.lockedConversationIds)
        assertEquals(listOf("local-hidden"), controller.security.hiddenConversationIds)
        assertTrue(remote.pushes.none(LocalProtectionMetadata::isPresent))
        controller.dispose()
    }
    @Test fun oldRemoteMembershipIsPurgedWithoutImportingItOnNewDevice() = runTest {
        val store = Store(); val remote = Remote()
        remote.row = RemoteSettingsRow(mapOf("schemaVersion" to 2, "security" to mapOf("lockedConversationIds" to listOf("foreign-lock"))), null)
        val controller = ChatyPreferencesController(store, remote, Lock(), Auth(), this, now = { 10_000L }, remoteSyncDebounceMs = 0)
        runCurrent(); controller.syncFromRemote(); runCurrent()
        assertTrue(controller.security.lockedConversationIds.isEmpty())
        assertTrue(controller.security.hiddenConversationIds.isEmpty())
        assertTrue(remote.pushes.isNotEmpty()); assertTrue(remote.pushes.none(LocalProtectionMetadata::isPresent))
        controller.dispose()
    }
}
