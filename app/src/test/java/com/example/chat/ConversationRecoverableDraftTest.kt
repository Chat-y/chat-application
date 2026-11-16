package com.example.chat

import com.example.chat.core.messages.ConversationRecoverableDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Source fixtures only. Not compiled or executed in the source-only overnight pass. */
class ConversationRecoverableDraftTest {
    @Test fun ordinaryRecoveryKeepsExactTextWithoutAnEditTarget() {
        val draft = ConversationRecoverableDraft("  unsent text\n")
        assertEquals("  unsent text\n", draft.text)
        assertEquals(ConversationRecoverableDraft.Kind.message, draft.kind)
        assertNull(draft.editMessageId)
    }
    @Test fun editRecoveryRetainsOriginalTargetAndExactDraft() {
        val draft = ConversationRecoverableDraft("  edit  ", ConversationRecoverableDraft.Kind.edit, "original-message")
        assertEquals("original-message", draft.editMessageId)
        assertEquals("  edit  ", draft.text)
    }
    @Test fun viewOnceRecoveryIsExplicitlyDistinctFromRegularSend() {
        val draft = ConversationRecoverableDraft("private", ConversationRecoverableDraft.Kind.viewOnce)
        assertEquals(ConversationRecoverableDraft.Kind.viewOnce, draft.kind)
        assertNull(draft.editMessageId)
    }
    @Test(expected = IllegalArgumentException::class)
    fun editRecoveryCannotLoseItsTarget() {
        ConversationRecoverableDraft("edit", ConversationRecoverableDraft.Kind.edit)
    }
    @Test(expected = IllegalArgumentException::class)
    fun regularRecoveryCannotBecomeAnImplicitEdit() {
        ConversationRecoverableDraft("message", editMessageId = "unexpected-target")
    }
}
