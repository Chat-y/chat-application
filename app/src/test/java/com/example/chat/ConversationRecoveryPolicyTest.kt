package com.example.chat

import com.example.chat.core.messages.ConversationArrivalPolicy
import com.example.chat.core.messages.ConversationDraftRecovery
import org.junit.Assert.*
import org.junit.Test

class ConversationRecoveryPolicyTest {
    @Test fun unchangedBlankDraftCanRecover() {
        assertEquals(ConversationDraftRecovery.Decision.restore, ConversationDraftRecovery.decide(true, 1, 1, ""))
    }
    @Test fun changedDraftIsNeverOverwrittenEvenWhenBlankAgain() {
        assertEquals(ConversationDraftRecovery.Decision.keepSeparately, ConversationDraftRecovery.decide(true, 1, 2, "new text"))
        assertEquals(ConversationDraftRecovery.Decision.keepSeparately, ConversationDraftRecovery.decide(true, 1, 3, ""))
    }
    @Test fun switchedOwnerNeverReceivesOldDraft() {
        assertEquals(ConversationDraftRecovery.Decision.ignore, ConversationDraftRecovery.decide(false, 1, 1, ""))
    }
    @Test fun prependedHistoryIsNotANewArrival() {
        assertEquals(emptyList<String>(), ConversationArrivalPolicy.appendedIds(setOf("b", "c"), listOf("a", "b", "c")))
    }
    @Test fun appendAndEqualSizeReplacementCountByIdentity() {
        assertEquals(listOf("d"), ConversationArrivalPolicy.appendedIds(setOf("b", "c"), listOf("b", "c", "d")))
        assertEquals(listOf("d"), ConversationArrivalPolicy.appendedIds(setOf("b", "c"), listOf("c", "d")))
    }
    @Test fun unknownReplacementAndInitialHydrationDoNotJump() {
        assertEquals(emptyList<String>(), ConversationArrivalPolicy.appendedIds(setOf("b", "c"), listOf("d", "e")))
        assertEquals(emptyList<String>(), ConversationArrivalPolicy.appendedIds(emptySet(), listOf("b", "c")))
    }
    @Test fun duplicateOrBlankRowsCannotInflateBadge() {
        assertEquals(listOf("d"), ConversationArrivalPolicy.appendedIds(setOf("b", "c"), listOf("c", "d", "d", "")))
    }
}
