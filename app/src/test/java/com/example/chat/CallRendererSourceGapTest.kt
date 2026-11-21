package com.example.chat

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.*
import org.junit.Test

/** NOT RUN. Structural fixture, not EGL ownership, provider-swap or camera-mirror pixel proof. */
class CallRendererSourceGapTest {
    @Test fun rendererKeysCaptureOwnerAndMirrorUpdatesOutsideFactory() {
        val source = listOf(java.io.File("src/main/kotlin/com/example/chat/ui/calls/OngoingCallScreen.kt"),
            java.io.File("app/src/main/kotlin/com/example/chat/ui/calls/OngoingCallScreen.kt"))
            .first { it.isFile }.readText()
            .lineSequence().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")
// Previous implementation retained:         assertTrue(source.contains("key(session.callId, RendererProviderIdentity(videoSurfaces))"))
// Previous implementation retained:         assertTrue(source.contains("key(callId, RendererProviderIdentity(videoSurfaces))"))
        assertTrue(source.contains("key(session.callId, RendererProviderIdentity(videoSurfaces),"))
        assertTrue(source.contains("RendererProviderIdentity(videoSurfaces.remoteRendererBindingIdentity)"))
        assertTrue(source.contains("key(callId, RendererProviderIdentity(videoSurfaces),"))
        assertTrue(source.contains("RendererProviderIdentity(videoSurfaces.localRendererBindingIdentity)"))
        assertTrue(source.contains("update = { renderer -> renderer.setMirror(mirror) }"))
        assertTrue(source.contains("onRelease = { renderer -> owner.detachRenderer(renderer) }"))
        assertTrue(source.contains("provider === other.provider"))
        assertFalse(source.contains("height(80.dp)"))
        assertFalse(source.contains("height(140.dp)"))
    }
    @Test fun nativeDetachUsesOriginalTrackAndStillAttemptsSurfaceRelease() {
        val source = listOf(java.io.File("src/main/java/com/example/chat/data/services/WebRtcCallMediaController.kt"),
            java.io.File("app/src/main/java/com/example/chat/data/services/WebRtcCallMediaController.kt"))
            .first { it.isFile }.readText()
            .lineSequence().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")
        val detach = source.substringAfter("override fun detachRenderer(").substringBefore("override suspend fun dispose()")
        assertTrue(detach.contains("rendererBindings.remove(renderer)"))
        assertTrue(detach.contains("originalTrack?.removeSink(renderer)"))
        assertTrue(detach.contains("cleanup.attempt { renderer.release() }"))
        assertFalse(detach.contains("localVideoTrackForRender?.removeSink"))
        assertFalse(detach.contains("remoteVideoTrackForRender?.removeSink"))
    }

}
