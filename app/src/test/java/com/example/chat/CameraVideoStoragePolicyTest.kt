package com.example.chat

import com.example.chat.ui.camera.hasCameraVideoStorage
import org.junit.Assert.*
import org.junit.Test

/** Source fixtures NOT RUN. StatFs/native start/lifecycle paths need Android tests. */
class CameraVideoStoragePolicyTest {
    @Test fun storageThresholdIncludesExactlyFiveHundredMiB() {
        assertTrue(hasCameraVideoStorage(500L * 1024 * 1024))
        assertTrue(hasCameraVideoStorage(Long.MAX_VALUE))
        assertFalse(hasCameraVideoStorage(500L * 1024 * 1024 - 1))
    }
    @Test fun unavailableAndNegativeSpaceRefused() {
        assertFalse(hasCameraVideoStorage(0)); assertFalse(hasCameraVideoStorage(-1))
    }
}
