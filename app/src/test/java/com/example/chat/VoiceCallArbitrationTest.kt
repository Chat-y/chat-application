package com.example.chat

import com.example.chat.core.audio.CommunicationMediaOwner
import com.example.chat.core.audio.DefaultCommunicationMediaArbiter
import com.example.chat.domain.models.MessageAttachment
import com.example.chat.ui.chats.detail.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceCallArbitrationTest {
    private class Harness {
        val arbiter = DefaultCommunicationMediaArbiter()
        var now = 0L
        var resumes = 0
        var captures = false
        var failPause = false
        var failStart = false
        var failStop = false
        var failDispose = false
        var sends = 0
        var uploads = 0
        val deleted = mutableListOf<String>()
        private var pathSequence = 0
        val note = VoiceNoteController("synthetic", object : VoiceRecorderPort {
            override suspend fun hasPermission() = true
            override suspend fun start(config: VoiceRecordConfig, path: String) { captures = true; check(!failStart) }
            override suspend fun pause() { check(!failPause); captures = false }
            override suspend fun resume() { resumes++; captures = true }
            override suspend fun stop(): String? { check(!failStop); captures = false; return null }
            override suspend fun currentAmplitudeDbfs() = -20.0
            override suspend fun dispose() { check(!failDispose); captures = false }
        }, object : VoiceStagingPort {
            override fun newStagingPath(epochMs: Long) = "synthetic_${pathSequence++}.m4a"
            override suspend fun deleteQuietly(path: String) { deleted += path }
        }, uploadAudio = { _, _, _ ->
            uploads++; MessageAttachment("synthetic", "audio", "test", "1 KB", "offline")
        }, sendAudioMessage = { sends++ }, now = { now }, communicationArbiter = arbiter)
    }
    @Test fun subsecondCallSuspensionRetainsDraftAndRequiresExplicitResume() = runTest {
        val h = Harness()
        val session = VoiceSessionController(h.note, backgroundScope)
        session.beginVoice(false); runCurrent(); h.now = 300
        val call = h.arbiter.acquireCall(CommunicationMediaOwner()); runCurrent()
        assertTrue(call.mediaSuspended); assertFalse(h.captures)
        assertTrue(h.note.callInterrupted); assertTrue(session.state.interruptedForCall)
        session.onRelease(); runCurrent()
        assertTrue(h.deleted.isEmpty()); assertEquals(0, h.uploads); assertEquals(0, h.sends)
        session.togglePause(); runCurrent()
        assertTrue(h.note.isPaused); assertTrue(h.deleted.isEmpty()); assertEquals(0, h.resumes)
        call.release(true); runCurrent()
        assertFalse(h.captures); assertEquals(0, h.resumes)
        session.togglePause(); runCurrent()
        assertTrue(h.captures); assertEquals(1, h.resumes)
        assertFalse(h.note.callInterrupted)
        session.cancelVoice(); runCurrent()
    }
    @Test fun failedNativePauseRetainsBlockAndDraftUntilExplicitRecovery() = runTest {
        val h = Harness(); h.note.start(); h.now = 200; h.failPause = true
        val call = h.arbiter.acquireCall(CommunicationMediaOwner())
        assertFalse(call.mediaSuspended); assertTrue(h.deleted.isEmpty()); assertTrue(h.captures)
        call.release(true)
        assertNull(h.arbiter.acquirePlayback(CommunicationMediaOwner()) {})
        h.failPause = false
        assertTrue(call.retryMediaSuspension()); assertFalse(h.captures)
        assertTrue(h.deleted.isEmpty()); assertEquals(0, h.sends); assertEquals(0, h.uploads)
        call.release(true); h.note.cancel()
    }
    @Test fun failedResumeDeleteRetainsReviewUntilNativeStopCanBeRetried() = runTest {
        val h = Harness(); h.note.start(); h.now = 2000
        val clip = h.note.pauseForReview()!!
        assertTrue(h.deleted.isEmpty())
        h.failStart = true; h.failStop = true
        assertTrue(runCatching { h.note.resumeSegment() }.isFailure)
        assertTrue(h.captures)
        assertTrue(runCatching { h.note.discardReview() }.isFailure)
        assertTrue(h.note.hasSegments); assertTrue(h.deleted.isEmpty())
        assertFalse(h.note.sendClip(clip)); assertEquals(0, h.sends)
        h.failStop = false
        h.note.discardReview()
        assertFalse(h.captures); assertFalse(h.note.hasSegments)
        assertTrue(clip.path in h.deleted)
        assertTrue("synthetic_1.m4a" in h.deleted)
    }

    @Test fun failedUnmountedDisposeCanBeRetriedThroughCallRecoveryWithoutDeletingLiveFiles() = runTest {
        val h = Harness(); h.note.start(); h.failDispose = true
        h.note.markDisposing()
        assertTrue(runCatching { h.note.dispose() }.isFailure)
        assertTrue(h.captures); assertTrue(h.deleted.isEmpty())
        val call = h.arbiter.acquireCall(CommunicationMediaOwner())
        assertFalse(call.mediaSuspended)
        h.failDispose = false
        assertTrue(call.retryMediaSuspension()); assertFalse(h.captures)
        assertTrue(h.deleted.isNotEmpty())
        call.release(true)
    }

    @Test fun invalidatedSendDiscardsReviewDespiteUiBusyFlag() = runTest {
        val h = Harness(); val session = VoiceSessionController(h.note, backgroundScope)
        session.beginVoice(true); runCurrent(); h.now = 2000
        session.beginReview(); runCurrent(); assertNotNull(session.state.review)
        h.note.invalidate(); session.sendReview(); runCurrent()
        assertNull(session.state.review); assertFalse(session.state.busy)
        assertTrue(h.deleted.isNotEmpty()); assertEquals(0, h.uploads); assertEquals(0, h.sends)
    }

}
