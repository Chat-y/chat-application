package com.example.chat

import com.example.chat.domain.models.NotificationToastPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source tests for current model contract, not a claim of Android notification delivery. */
class NotificationToastDefaultsGapTest {
    @Test fun freshDefaultsArePinnedPerType() {
        val p = NotificationToastPreferences()
        // Previous fresh default was true. User requested all event masters off.
        assertFalse(p.onlineToastEnabled)
        assertFalse(p.storyToastEnabled)
        assertFalse(p.profileToastEnabled)
        assertFalse(p.typingToastEnabled)
        listOf("online", "story", "profile", "typing").forEach { type ->
            val m = p.toMap()
            assertEquals(8.0, m[type + "ToastRadius"])
            assertEquals("Default", m[type + "ToastRingtone"])
            assertEquals("Top of the screen", m[type + "ToastPosition"])
            assertEquals(true, m[type + "ToastElevation"])
            assertNull(m[type + "ToastBgColor"])
            assertNull(m[type + "ToastTextColor"])
        }
    }
    @Test fun onlineEnabledUsesPositiveStorageSemantics() {
        val off = NotificationToastPreferences().copy(onlineToastEnabled = false)
        assertFalse(off.onlineToastEnabled)
        assertTrue(off.disableContactOnlineToast)
        assertEquals(false, off.toMap()["onlineToastEnabled"])
    }
    @Test fun emptyStorageUsesSameModelDefaults() {
        assertEquals(NotificationToastPreferences(), NotificationToastPreferences.fromMap(emptyMap()))
    }
    @Test fun independentAppearanceFieldsRoundTripWithoutCrossTypeBleed() {
        val p = NotificationToastPreferences(onlineToastRadius = 4.0, storyToastRadius = 12.0,
            profileToastRadius = 20.0, typingToastRadius = 28.0,
            onlineToastPosition = "Bottom of screen", typingToastPosition = "Center of screen")
        assertEquals(p, NotificationToastPreferences.fromMap(p.toMap()))
        assertEquals(4.0, p.onlineRadius, 0.0)
        assertEquals(28.0, p.typingRadius, 0.0)
    }
}
