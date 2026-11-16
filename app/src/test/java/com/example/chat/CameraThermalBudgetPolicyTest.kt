package com.example.chat

import com.example.chat.ui.camera.shouldPauseCameraSegmentationForThermal
import org.junit.Assert.*
import org.junit.Test

/** Source fixtures NOT RUN. Thermal service/native stream behavior needs device tests. */
class CameraThermalBudgetPolicyTest {
    @Test fun noneLightAndModerateKeepSegmentation() {
        for (status in listOf(-1, 0, 1, 2)) assertFalse(shouldPauseCameraSegmentationForThermal(status))
    }
    @Test fun severeAndHigherPauseExpensiveStream() {
        for (status in listOf(3, 4, 5, 6)) assertTrue(shouldPauseCameraSegmentationForThermal(status))
    }
}
