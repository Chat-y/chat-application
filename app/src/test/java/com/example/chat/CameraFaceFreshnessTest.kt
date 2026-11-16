package com.example.chat

import com.example.chat.ui.camera.isCameraFaceSolveFresh
import org.junit.Assert.*
import org.junit.Test

/** Source fixtures only, NOT RUN under the overnight no-build instruction. */
class CameraFaceFreshnessTest {
    @Test fun exactBudgetIsAcceptedButOlderSolveIsNot() {
        assertTrue(isCameraFaceSolveFresh(1500L, 1000L))
        assertFalse(isCameraFaceSolveFresh(1501L, 1000L))
    }
    @Test fun missingFutureOrInvalidSolveIsNotFresh() {
        assertFalse(isCameraFaceSolveFresh(1000L, null))
        assertFalse(isCameraFaceSolveFresh(1000L, 1001L))
        assertFalse(isCameraFaceSolveFresh(1000L, -1L))
    }
}
