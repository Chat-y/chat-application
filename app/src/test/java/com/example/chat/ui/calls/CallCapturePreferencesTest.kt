package com.example.chat.ui.calls

import org.junit.Assert.*
import org.junit.Test

/** Uncompiled, unrun source fixtures; not native camera evidence. */
class CallCapturePreferencesTest {
    @Test fun defaultsPreserveCurrentCaptureBehavior() {
        val value = CallCapturePreferences()
        assertTrue(value.frontCamera)
        assertTrue(value.mirrorFrontPreview)
        assertTrue(value.noiseSuppression)
        assertTrue(value.echoCancellation)
        assertEquals(CallVideoQuality.AUTO, value.videoQuality)
    }
    @Test fun optionUpdatesOnlyItsOwnField() {
        val original = CallCapturePreferences()
        for (option in CallCaptureOption.entries) {
            val changed = option.update(original, false)
            assertFalse(option.selected(changed))
            for (other in CallCaptureOption.entries.filter { it != option }) assertTrue(other.selected(changed))
        }
    }
    @Test fun videoLimitsDoNotUpscaleNetworkProfile() {
        assertEquals(640 to 360, CallVideoQuality.SD_480.limit(640, 360))
        val limited = CallVideoQuality.SD_480.limit(1280, 720)
        assertTrue(limited.first <= 854 && limited.second <= 480)
        assertEquals(0, limited.first % 2)
        assertEquals(0, limited.second % 2)
        assertEquals(CallVideoQuality.AUTO, CallVideoQuality.parse("future-quality"))
    }
}
