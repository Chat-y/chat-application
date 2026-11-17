package com.example.chat

import com.example.chat.data.services.VoiceCaptureBoundary
import com.example.chat.domain.models.MessageAttachment
import com.example.chat.ui.chats.detail.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** Source regression tests only: synthetic capture, clock and finalization, never microphone/network. */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceInstantStopTest {
    private class Recorder : VoiceRecorderPort {
        var current = VoiceCaptureBoundary()
        var stopRequests = 0
        var finalizations = 0
        var failure: Exception? = null
        val finalized = CompletableDeferred<Unit>()
        override suspend fun hasPermission() = true
        override suspend fun start(config: VoiceRecordConfig, path: String) {
            current = VoiceCaptureBoundary().also { it.start() }
        }
        override fun captureStopRequest(): (() -> Unit) {
            val generation = current
            return { stopRequests++; generation.requestStop() }
        }
        override suspend fun stop(): String? {
            finalizations++; finalized.await(); failure?.let { throw it }
            return "synthetic.m4a"
        }
        override suspend fun currentAmplitudeDbfs() = -20.0
        override suspend fun dispose() {}
    }
    private class Harness {
        var now = 0L
        var uploads = 0
        var sends = 0
        val recorder = Recorder()
        val errors = mutableListOf<String>()
        val voice = VoiceNoteController("synthetic", recorder, object : VoiceStagingPort {
            override fun newStagingPath(epochMs: Long) = "synthetic.m4a"
            override suspend fun deleteQuietly(path: String) {}
        }, uploadAudio = { _, _, _ ->
            check(recorder.finalized.isCompleted)
            uploads++; MessageAttachment("a", "audio", "test", "1 KB")
        }, sendAudioMessage = { sends++ }, now = { now },
            communicationArbiter = com.example.chat.core.audio.DefaultCommunicationMediaArbiter())
    }

    @Test fun sendRequestsCutoffBeforeDispatchAndAwaitsFinalizationBeforeUpload() = runTest {
        val h = Harness()
        val session = VoiceSessionController(h.voice, backgroundScope, showError = { h.errors += it })
        session.beginVoice(locked = true); runCurrent(); h.now = 2000
        session.finishVoice()
        // No scheduler drain yet: stop is already reserved, UI is busy, no file was uploaded.
        assertFalse(h.recorder.current.canStartRead()); assertTrue(session.state.busy)
        assertEquals(1, h.recorder.stopRequests); assertEquals(0, h.recorder.finalizations)
        h.now = 9000; assertEquals(2000L, h.voice.elapsedMs)
        session.finishVoice(); assertEquals(1, h.recorder.stopRequests)
        runCurrent(); assertEquals(1, h.recorder.finalizations); assertEquals(0, h.uploads)
        h.recorder.finalized.complete(Unit); runCurrent()
        assertEquals(1, h.uploads); assertEquals(1, h.sends); assertTrue(h.errors.isEmpty())
    }

    @Test fun failedFinalizationSurfacesErrorAndNeverUploadsOrSends() = runTest {
        val h = Harness()
        val session = VoiceSessionController(h.voice, backgroundScope, showError = { h.errors += it })
        session.beginVoice(locked = true); runCurrent(); h.now = 2000
        h.recorder.failure = IllegalStateException("Synthetic encoder failure")
        session.finishVoice(); runCurrent(); h.recorder.finalized.complete(Unit); runCurrent()
        assertEquals(0, h.uploads); assertEquals(0, h.sends)
        assertTrue(h.errors.single().contains("Synthetic encoder failure"))
    }

    @Test fun invalidOutputWithReleasedHardwareDoesNotPermanentlyReserveCapture() = runTest {
        val h = Harness()
        h.voice.start(); h.now = 2000
        h.recorder.failure = VoiceFinalizationException(IllegalStateException("Invalid AAC"))
        h.recorder.finalized.complete(Unit)
        assertTrue(runCatching { h.voice.stopAndSend() }.exceptionOrNull() is VoiceFinalizationException)
        assertFalse(h.voice.isRecording)
        h.recorder.failure = null
        h.voice.start() // Must reacquire hardware after failed output was discarded.
        assertTrue(h.voice.isRecording)
        h.voice.cancel()
        assertEquals(0, h.uploads); assertEquals(0, h.sends)
    }

    @Test fun delayedStopRequestCannotStopAReplacementGeneration() = runTest {
        val recorder = Recorder(); recorder.start(VoiceRecordConfig(), "first")
        val old = recorder.current; val request = recorder.captureStopRequest()
        recorder.start(VoiceRecordConfig(), "replacement"); request()
        assertFalse(old.canStartRead()); assertTrue(recorder.current.canStartRead())
    }

    @Test fun sendRetainsTheAdmittedPcmTailButNeverStartsAnotherRead() {
        val boundary = VoiceCaptureBoundary(); boundary.start()
        assertTrue(boundary.tryBeginRead()) // Atomic admission, not a stale canStartRead result.
        boundary.requestStop()
        assertFalse(boundary.tryBeginRead())
        boundary.endRead()
        assertFalse(boundary.tryBeginRead()) // Even after old read returns, no post-stop admission.
        assertFalse(boundary.canStartRead())
        assertTrue(boundary.keepCompletedRead(0, 0, paused = false))
        // A pause/resume read belongs to the old segment and must still be rejected.
        assertFalse(boundary.keepCompletedRead(0, 1, paused = false))
        assertFalse(boundary.keepCompletedRead(0, 0, paused = true))
    }
}
