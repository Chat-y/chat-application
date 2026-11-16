package com.example.chat

import com.example.chat.ui.camera.cameraVideoDurationLimitMillis
import com.example.chat.ui.camera.cameraVideoRemainingSeconds
import org.junit.Assert.*
import org.junit.Test

/** Source fixtures NOT RUN; native finalize and actual pixels remain unverified. */
class CameraVideoDurationPolicyTest {
    @Test fun chatAndStoryHaveExplicitNativeBudgets() {
        assertEquals(300_000L, cameraVideoDurationLimitMillis(false)); assertEquals(60_000L, cameraVideoDurationLimitMillis(true))
    }
    @Test fun remainingIsClampedAndPauseUsesSameElapsedInput() {
        assertEquals(60, cameraVideoRemainingSeconds(0, 60_000)); assertEquals(1, cameraVideoRemainingSeconds(59, 60_000))
        assertEquals(0, cameraVideoRemainingSeconds(61, 60_000)); assertEquals(60, cameraVideoRemainingSeconds(-1, 60_000))
    }
}
