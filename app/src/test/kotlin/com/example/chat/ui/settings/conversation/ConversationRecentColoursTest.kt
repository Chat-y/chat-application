package com.example.chat.ui.settings.conversation

import org.junit.Assert.assertEquals
import org.junit.Test

/** In-memory recent-palette cases, source only and unrun. Values are synthetic ids. */
class ConversationRecentColoursTest {
    @Test fun round1FirstAppliedColourBecomesTheFirstRecent() = assertEquals(listOf(1L),
        withRecentConversationColour(emptyList(), 1L))
    @Test fun round2OnlySixUniqueValuesAreRetained() = assertEquals(listOf(7L, 6L, 5L, 4L, 3L, 2L),
        withRecentConversationColour(listOf(6L, 5L, 4L, 3L, 2L, 1L), 7L))
    @Test fun round3ReusingAColourMovesItToTheFront() = assertEquals(listOf(2L, 3L, 1L),
        withRecentConversationColour(listOf(3L, 2L, 1L), 2L))
    @Test fun round4InputListIsNotModified() {
        val original = listOf(2L, 1L)
        withRecentConversationColour(original, 3L)
        assertEquals(listOf(2L, 1L), original)
    }
    @Test fun round5AZeroValueIsNotDroppedOrRewritten() = assertEquals(listOf(0L, 1L),
        withRecentConversationColour(listOf(1L), 0L))
}
