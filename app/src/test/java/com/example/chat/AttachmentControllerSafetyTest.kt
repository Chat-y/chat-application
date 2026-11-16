package com.example.chat

import com.example.chat.domain.models.MessageAttachment
import com.example.chat.domain.models.MessageType
import com.example.chat.ui.chats.detail.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** Source fixtures only; not executed by the patch task. */
class AttachmentControllerSafetyTest {
    private class Backend : AttachmentBackendPort {
        var uploads = 0
        var sends = 0
        var afterSend: suspend () -> Unit = {}
        var uploadFailure: Exception? = null
        override suspend fun upload(type: String, file: PickedFile, durationSeconds: Int): MessageAttachment {
            uploadFailure?.let { throw it }
            uploads++
            return MessageAttachment(id = file.name, type = type, name = file.name, size = "1 KB", url = "/media/test")
        }
        override suspend fun sendMessage(text: String, type: MessageType, attachment: MessageAttachment?, extraMetadata: Map<String, Any?>?) {
            sends++; afterSend()
        }
        override suspend fun sendStructuredMessage(type: String, body: String, metadata: Map<String, Any?>) {}
        override suspend fun createPoll(question: String, options: List<String>, allowMultiple: Boolean) {}
        override suspend fun getPoll(messageId: String): Map<String, Any?>? = null
        override suspend fun votePoll(messageId: String, optionId: String) {}
        override suspend fun reloadConversation() {}
    }
    @Test fun mediaCancellationPropagatesWithoutToast() = runBlocking {
        val b = Backend().apply { uploadFailure = CancellationException("private") }
        val notices = mutableListOf<String>()
        try {
            ChatAttachmentActionController("c", b, { notices.add(it) }).shareMedia("audio", { _, _ -> listOf(PickedFile("a")) }, { false })
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {}
        assertTrue(notices.isEmpty())
    }
    @Test fun providerErrorsDoNotLeakMessage() = runBlocking {
        val b = Backend().apply { uploadFailure = IllegalStateException("/private/secret-provider-path") }
        val notices = mutableListOf<String>()
        ChatAttachmentActionController("c", b, { notices.add(it) }).sendRecordedVoiceNote("voice", 1)
        assertEquals(listOf("Unable to send voice note. Please try again."), notices)
    }
    @Test fun videoObserverCannotTurnSubmissionIntoFailure() = runBlocking {
        val b = Backend()
        val notices = mutableListOf<String>()
        val submitted = ChatAttachmentActionController("c", b, { notices.add(it) }).sendCapturedVideo("v.mp4", 1000L, "video/mp4",
            isStillCurrent = { true }, onSubmitted = { throw IllegalStateException("observer") })
        assertTrue(submitted)
        assertEquals(1, b.sends)
        assertTrue(notices.none { it.contains("Could not send") })
    }
    @Test fun cancelledNonCooperativeSendCannotStartSecondUpload() = runBlocking {
        val b = Backend().apply { afterSend = { currentCoroutineContext().cancel() } }
        val child = launch {
            ChatAttachmentActionController("c", b, {}).sendReviewedMedia(listOf(PickedFile("a"), PickedFile("b")), "image", "", false)
        }
        child.join()
        assertTrue(child.isCancelled)
        assertEquals(1, b.uploads)
        assertEquals(1, b.sends)
    }
    @Test fun invalidatedReviewGuardStopsNextUpload() = runBlocking {
        var current = true
        val b = Backend().apply { afterSend = { current = false } }
        ChatAttachmentActionController("c", b, {}).sendReviewedMedia(listOf(PickedFile("a"), PickedFile("b")), "image", "", false,
            isStillCurrent = { current })
        assertEquals(1, b.uploads)
    }
    @Test fun pollCountsCannotWrapAndMalformedIdsCannotVote() {
        val poll = PollState.from(mapOf("total_votes" to -2L, "options" to listOf(
            mapOf("id" to "", "votes" to 10), mapOf("id" to "a", "votes" to -3),
            mapOf("id" to "a", "votes" to 50), mapOf("id" to "b", "votes" to Long.MAX_VALUE))))
        assertEquals(listOf("a", "b"), poll.options.map { it.id })
        assertEquals(listOf(0, Int.MAX_VALUE), poll.options.map { it.votes })
        assertEquals(0, poll.totalVotes)
        assertEquals(0.0, poll.options.first().progress(-1), 0.0)
    }
}
