package com.example.chat

import com.example.chat.core.preferences.*
import com.example.chat.domain.notifications.PresenceToastEnablement
import com.example.chat.domain.models.NotificationToastPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

/** Authored fixtures only. No Gradle/test execution or runtime delivery claim. */
@OptIn(ExperimentalCoroutinesApi::class)
class PresenceToastEnablementTest {
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

    @Test fun missingDefaultsAreOffAndExplicitSavedTrueSurvives() {
        val p = NotificationToastPreferences.fromMap(emptyMap())
        assertFalse(p.onlineToastEnabled); assertFalse(p.typingToastEnabled)
        assertFalse(p.storyToastEnabled); assertFalse(p.profileToastEnabled)
        assertEquals(PresenceToastEnablement.Flags(false, false), PresenceToastEnablement.restore(null, null, null))
        val saved = NotificationToastPreferences.fromMap(mapOf("onlineToastEnabled" to true,
            "typingToastEnabled" to true, "storyToastEnabled" to true, "profileToastEnabled" to true))
        assertTrue(saved.onlineToastEnabled && saved.typingToastEnabled && saved.storyToastEnabled && saved.profileToastEnabled)
    }
    @Test fun typedFalseBeatsLegacyTrueAndMissingFieldsImportLegacyIndependently() {
        val n = mapOf("notifyContactOnline" to true, "notifyTypingStarted" to false)
        val gb = mapOf("abu_saleh_toast_online" to true, "abu_saleh_toast_typing" to "true")
        assertEquals(PresenceToastEnablement.Flags(false, true),
            PresenceToastEnablement.restore(mapOf("onlineToastEnabled" to false), n, gb))
        assertEquals(PresenceToastEnablement.Flags(true, false), PresenceToastEnablement.restore(
            mapOf("typingToastEnabled" to false), n, gb))
    }
    @Test fun globalAndHeadsUpDisableWinForBothConsumers() {
        assertFalse(PresenceToastEnablement.allows(false, true, false))
        assertFalse(PresenceToastEnablement.allows(true, false, false))
        assertFalse(PresenceToastEnablement.allows(true, true, true))
        assertTrue(PresenceToastEnablement.allows(true, true, false))
    }
    @Test fun allWritersBridgeWithoutAppearanceLossAndPersistTogether() = runTest {
        val store = Store()
        val c = ChatyPreferencesController(store, null, lock, auth, backgroundScope)
        runCurrent()
        assertFalse(c.toasts.onlineToastEnabled); assertFalse(c.toasts.typingToastEnabled)
        c.updateToasts(c.toasts.copy(onlineToastEnabled = true, typingToastEnabled = true, onlineToastRadius = 22.0))
        runCurrent()
        assertTrue(c.notification.notifyContactOnline && c.notification.notifyTypingStarted)
        assertTrue(c.gbBool("abu_saleh_toast_online")); assertTrue(c.gbBool("abu_saleh_toast_typing"))
        c.updateNotification(c.notification.copy(notifyContactOnline = false))
        runCurrent()
        assertFalse(c.toasts.onlineToastEnabled); assertTrue(c.toasts.typingToastEnabled)
        assertEquals(22.0, c.toasts.onlineToastRadius, 0.0)
        c.updateGbFeature("abu_saleh_toast_typing", false)
        runCurrent()
        assertFalse(c.toasts.typingToastEnabled); assertFalse(c.notification.notifyTypingStarted)
        val blob = store.blobs.getValue("toast-test")
        assertEquals(false, (blob[PreferenceKeys.toasts] as Map<*, *>)["typingToastEnabled"])
        assertEquals(false, (blob[PreferenceKeys.notification] as Map<*, *>)["notifyTypingStarted"])
    }
    @Test fun resetAllClearsAllFourMastersButNormalWritesPreserveStoryProfile() = runTest {
        val c = ChatyPreferencesController(Store(), null, lock, auth, backgroundScope)
        runCurrent()
        c.updateToasts(c.toasts.copy(onlineToastEnabled = true, typingToastEnabled = true,
            storyToastEnabled = true, profileToastEnabled = true))
        c.updateNotification(c.notification.copy(notifyContactOnline = false))
        assertTrue(c.toasts.storyToastEnabled && c.toasts.profileToastEnabled)
        c.resetAll(); runCurrent()
        assertFalse(c.toasts.onlineToastEnabled || c.toasts.typingToastEnabled || c.toasts.storyToastEnabled || c.toasts.profileToastEnabled)
    }
    @Test fun savedTypedFalseIsNotOverriddenAndLegacyOnlyExplicitTrueMigrates() = runTest {
        val store = Store()
        store.blobs["toast-test"] = mapOf(
            PreferenceKeys.toasts to mapOf("onlineToastEnabled" to false),
            PreferenceKeys.notification to mapOf("notifyContactOnline" to true, "notifyTypingStarted" to true))
        val c = ChatyPreferencesController(store, null, lock, auth, backgroundScope)
        runCurrent()
        assertFalse(c.toasts.onlineToastEnabled); assertFalse(c.notification.notifyContactOnline)
        assertFalse(c.gbBool("abu_saleh_toast_online"))
        assertTrue(c.toasts.typingToastEnabled); assertTrue(c.notification.notifyTypingStarted)
    }
}
