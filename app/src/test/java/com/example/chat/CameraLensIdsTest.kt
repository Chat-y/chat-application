package com.example.chat

import com.example.chat.ui.camera.requireUniqueCameraLensIds
import org.junit.Test

/** Source-only fixtures. NOT RUN under the overnight no-build instruction. */
class CameraLensIdsTest {
    @Test fun uniqueAndEmptyIdsPass() {
        requireUniqueCameraLensIds(emptyList())
        requireUniqueCameraLensIds(listOf("none", "lens_one", "lens_two"))
    }
    @Test(expected = IllegalArgumentException::class)
    fun duplicateIdsFail() {
        requireUniqueCameraLensIds(listOf("none", "lens_one", "lens_one"))
    }
}
