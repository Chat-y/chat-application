package com.example.chat

import com.example.chat.data.services.*
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

/** NOT RUN. Existing source/pure-policy checks, not Android service/notification visibility proof. */
class CallForegroundSourceContractGapTest {
    private fun source(relative: String): String = Files.readString(
        listOf(Path.of("src/main/$relative"), Path.of("app/src/main/$relative")).first { Files.isRegularFile(it) })
    private fun session(state: CallSessionState, outgoing: Boolean) = ChatyCallSession(
        "synthetic", "peer", "Fixture", isVideo = false, isOutgoing = outgoing,
        state = state, startedAt = Instant.EPOCH)
    @Test fun activeStatesKeepForegroundPolicyButIncomingRingingDoesNotStartCaptureService() {
        assertFalse(CallForegroundPolicy.shouldRunFor(session(CallSessionState.incoming, false)))
        assertTrue(CallForegroundPolicy.shouldRunFor(session(CallSessionState.ringing, true)))
        for (state in listOf(CallSessionState.connecting, CallSessionState.connected, CallSessionState.reconnecting))
            assertTrue(CallForegroundPolicy.shouldRunFor(session(state, false)))
        assertFalse(CallForegroundPolicy.shouldRunFor(session(CallSessionState.ended, true)))
    }
    @Test fun manifestAndServiceKeepMicrophoneCameraDeclarationsAndOngoingStatusIcon() {
        val manifest = source("AndroidManifest.xml")
        assertTrue(Regex("android:name=\"com\\.example\\.chat\\.data\\.services\\.CallForegroundService\"[\\s\\S]*?android:foregroundServiceType=\"microphone\\|camera\\|mediaProjection\"").containsMatchIn(manifest))
        val service = source("java/com/example/chat/data/services/CallForegroundService.kt")
        assertTrue(service.contains("ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE"))
        assertTrue(service.contains("ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA"))
        assertTrue(service.contains("startForeground(SERVICE_ID, notification, types)"))
        assertTrue(service.contains(".setOngoing(true)"))
        assertTrue(service.contains(".setSmallIcon("))
    }
}
