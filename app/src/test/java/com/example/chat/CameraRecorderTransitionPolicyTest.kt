package com.example.chat

import com.example.chat.ui.camera.*
import org.junit.Assert.*
import org.junit.Test

/** Source cases NOT RUN; native acknowledgement/Mutex sequencing requires Android tests. */
class CameraRecorderTransitionPolicyTest {
    @Test fun pauseAndResumeRequireAcknowledgedOppositeStates() {
        for (phase in CameraRecorderPhase.entries) {
            assertEquals(phase == CameraRecorderPhase.Recording, canPauseCameraRecorder(phase))
            assertEquals(phase == CameraRecorderPhase.Paused, canResumeCameraRecorder(phase))
        }
    }
    @Test fun stopSupersedesPendingCommandsButNotIdleOrRepeatedStop() {
        for (phase in CameraRecorderPhase.entries) {
            assertEquals(phase !in setOf(CameraRecorderPhase.Idle, CameraRecorderPhase.Stopping), canStopCameraRecorder(phase))
        }
    }
}
