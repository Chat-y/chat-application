package com.example.chat

import com.example.chat.ui.chats.newchat.NewChatRules
import org.junit.Assert.*
import org.junit.Test

/** Pure local review eligibility only, not an Android restoration or backend permission test. */
class NewChatDraftSelectionTest {
    @Test fun onlyKnownOtherIdentitiesCanBeReviewed() {
        assertTrue(NewChatRules.validSelection(linkedSetOf("a", "b"), "self", setOf("a", "b", "c")))
        assertFalse(NewChatRules.validSelection(setOf("missing"), "self", setOf("a")))
    }
    @Test fun blankSelfAndEmptySelectionsAreRejected() {
        assertFalse(NewChatRules.validSelection(emptySet(), "self", setOf("a")))
        assertFalse(NewChatRules.validSelection(setOf("self"), "self", setOf("self")))
        assertFalse(NewChatRules.validSelection(setOf("a"), null, setOf("a")))
        assertFalse(NewChatRules.validSelection(setOf(""), "self", setOf("")))
    }
    @Test fun unresolvedSavedIdentityRemainsInSelectionButCannotBeSubmitted() {
        val restored = linkedSetOf("a", "missing")
        assertFalse(NewChatRules.validSelection(restored, "self", setOf("a")))
        assertEquals(listOf("a", "missing"), restored.toList())
        assertTrue(NewChatRules.validSelection(restored, "self", setOf("a", "missing")))
    }
}
