package com.example.chat.ui.settings.conversation

import com.example.chat.core.theme.ConversationEntryStyles
import org.junit.Assert.*
import org.junit.Test

/** Conservative recommendation shortlist, not popularity data. Source-only, unrun. */
class ConversationEntryRecommendationsTest {
    @Test fun defaultStaysFirst() { assertEquals("stock", conversationRecommendedEntryKeys.first()) }
    @Test fun shortlistHasThreeDistinctStyles() { assertEquals(3, conversationRecommendedEntryKeys.distinct().size) }
    @Test fun everyRecommendedIdExists() { assertTrue(conversationRecommendedEntryKeys.all { id -> ConversationEntryStyles.options.any { it.id == id } }) }
    @Test fun recommendationsLeadTheEntryQuickPicks() {
        assertEquals(conversationRecommendedEntryKeys, conversationQuickPicks(ConversationEntryStyles.options, { it.id }, conversationRecommendedEntryKeys, 3).map { it.id })
    }
    @Test fun shortlistKeepsDistinctExistingLayoutNames() {
        assertEquals(3, conversationRecommendedEntryKeys.map { ConversationEntryStyles.layoutName(it) }.distinct().size)
    }
}
