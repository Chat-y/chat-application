package com.example.chat

import com.example.chat.core.preferences.*
import com.example.chat.domain.models.NotificationToastPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

/** Real controller with fake local persistence. Not compiled/run. */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationToastControllerWritesTest {
    private class Store : PreferencesBlobStore {
        var fail = false
        val blobs = mutableMapOf<String?, Map<String, Any?>>()
        override suspend fun load(userId: String?) = blobs[userId].orEmpty()
        override suspend fun hasScoped(userId: String) = true
        override suspend fun save(userId: String?, data: Map<String, Any?>) {
            if (fail) error("fixture write failed")
            blobs[userId] = data
        }
        override suspend fun clearGlobal() {}
        override suspend fun storedUserId(): String? = null
        override suspend fun setStoredUserId(userId: String?) {}
    }
    private val auth = object : PreferencesAuthSource {
        override fun currentUserId() = "toast-test"
        override fun addListener(listener: (String?) -> Unit) {}
        override fun removeListener(listener: (String?) -> Unit) {}
    }
    private val lock = object : LockCredentialStore {
        override suspend fun hasCredential(method: String) = false
        override suspend fun setCredential(method: String, secret: String, pinLength: Int?) {}
    }
    @Test fun positiveToggleAndElevationPersistThroughController() = runTest {
        val store = Store()
        val controller = ChatyPreferencesController(store, null, lock, auth, backgroundScope)
        runCurrent()
        assertTrue(controller.preferencesReady)
        val before = controller.toasts
        controller.updateToasts(before.copy(onlineToastEnabled = false, onlineToastElevation = false))
        runCurrent()
        assertFalse(controller.toasts.onlineToastEnabled)
        assertFalse(controller.toasts.onlineToastElevation)
        assertEquals(before.storyToastEnabled, controller.toasts.storyToastEnabled)
        val group = store.blobs.getValue("toast-test")[PreferenceKeys.toasts] as Map<*, *>
        assertEquals(false, group["onlineToastEnabled"])
        assertEquals(false, group["onlineToastElevation"])
        assertEquals(before.typingToastElevation, group["typingToastElevation"])
    }
    @Test fun failureIsObservableAndRetryPersistsLatestEditNotOldSnapshot() = runTest {
        val store = Store()
        val controller = ChatyPreferencesController(store, null, lock, auth, backgroundScope)
        runCurrent()
        store.fail = true
        controller.updateToasts(controller.toasts.copy(onlineToastEnabled = false))
        runCurrent(); advanceTimeBy(100); runCurrent(); advanceTimeBy(200); runCurrent()
        assertTrue(controller.saveState.value is SaveState.Failed)
        controller.updateToasts(controller.toasts.copy(typingToastElevation = false))
        runCurrent(); advanceTimeBy(100); runCurrent(); advanceTimeBy(200); runCurrent()
        store.fail = false
        controller.retrySave(); runCurrent()
        assertTrue(controller.saveState.value is SaveState.Saved)
        val group = store.blobs.getValue("toast-test")[PreferenceKeys.toasts] as Map<*, *>
        assertEquals(false, group["onlineToastEnabled"])
        assertEquals(false, group["typingToastElevation"])
    }
}
