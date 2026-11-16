package com.example.chat

import com.example.chat.ui.camera.shouldWarnCameraLowBattery
import org.junit.Assert.*
import org.junit.Test

/** Source fixtures NOT RUN; system battery and once-per-session delivery need Android tests. */
class CameraLowBatteryPolicyTest {
    @Test fun validCapacityBelowTenPercentWarns() {
        assertTrue(shouldWarnCameraLowBattery(0)); assertTrue(shouldWarnCameraLowBattery(9))
        assertFalse(shouldWarnCameraLowBattery(10)); assertFalse(shouldWarnCameraLowBattery(100))
    }
    @Test fun unknownAndOutOfRangeReadingsDoNotInventLowBattery() {
        assertFalse(shouldWarnCameraLowBattery(Int.MIN_VALUE)); assertFalse(shouldWarnCameraLowBattery(-1)); assertFalse(shouldWarnCameraLowBattery(101))
    }
}
