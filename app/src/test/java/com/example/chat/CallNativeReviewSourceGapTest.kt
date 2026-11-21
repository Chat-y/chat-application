package com.example.chat

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.*
import org.junit.Test

/** NOT RUN. Structural review pins only, not typecheck, native scheduling, sink or camera proof. */
class CallNativeReviewSourceGapTest {
    private fun source(file: String): String = listOf(java.io.File("src/main/java/com/example/chat/data/services/$file.kt"),
        java.io.File("app/src/main/java/com/example/chat/data/services/$file.kt"))
        .first { it.isFile }.readText()
        .lineSequence().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")
    @Test fun nativeGenerationAndTerminalEglAreSeparateFromBlockingReleaseMonitor() {
        val body = source("WebRtcCallMediaController")
        assertTrue(body.contains("cameraFacingFront = isFrontCamera"))
        assertTrue(body.contains("cameraFacingFront = enumerator.isFrontFacing(deviceName)"))
        assertTrue(body.contains("withTimeoutOrNull(2_500L)"))
        assertTrue(body.contains("if (!cont.isActive) return"))
        assertTrue(body.contains("synchronized(this) { nativeGeneration++ }"))
        assertTrue(body.contains("peerConnection !== peer"))
        assertFalse(body.contains("@Synchronized\n    private fun disposeInternal"))
        assertTrue(body.contains("if (terminallyDisposed) releaseTerminalRenderContext()"))
        assertTrue(body.contains("egl?.release()"))
    }
    @Test fun terminalDetachSchedulesCleanupBeforeOptionalEffectsAndSinkCommitsAfterSuccess() {
        val body = source("CallSignalingService")
        val detach = body.substringAfter("private fun detachMediaTransport()").substringBefore("private suspend fun disposeMediaTransport")
        assertTrue(detach.indexOf("media = null") < detach.indexOf("closeVideoUpgrade()"))
        assertTrue(detach.indexOf("scope.launch(kotlinx.coroutines.NonCancellable)") < detach.indexOf("closeVideoUpgrade()"))
        val record = body.substringAfter("private fun logCallRecordOnce(").substringBefore("private suspend fun flushRemoteCandidates")
        assertTrue(record.indexOf("callRecordSink(") < record.indexOf("loggedCallIds.add(session.callId)"))
        assertTrue(record.contains("loggedCallIds.contains(session.callId)"))
    }
    @Test fun terminalSignalingDeclarationsRemainActiveAndUnique() {
        val body = source("CallSignalingService")
        for (name in listOf("endCallSession", "declineCall", "markTransportFailed", "logCallRecordOnce", "detachMediaTransport")) {
            val declaration = Regex("(?m)^\\s*(?:private )?(?:suspend )?fun " + name + "\\(")
            assertEquals(name, 1, declaration.findAll(body).count())
        }
        assertTrue(body.contains("private suspend fun endCallSession(session: ChatyCallSession, expected: Any?, reason: String)"))
    }
    @Test fun independentForegroundLossCleanupIsTerminal() {
        val body = source("WebRtcCallMediaController")
        val fallback = body.substringAfter("if (foregroundLease === readyForeground) {").substringBefore("catch (_: Exception)")
        assertTrue(fallback.contains("terminallyDisposed = true"))
        assertTrue(fallback.indexOf("terminallyDisposed = true") < fallback.indexOf("disposeOwned()"))
    }

}
