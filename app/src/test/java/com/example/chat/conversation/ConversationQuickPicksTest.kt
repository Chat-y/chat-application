package com.example.chat.ui.settings.conversation

import org.junit.Assert.*
import org.junit.Test

/** Quick-pick ordering policy source cases. Unrun. */
class ConversationQuickPicksTest {
    private val values = (0..12).map { "style-$it" }
    @Test fun firstUseKeepsExistingRegistryOrder() { assertEquals(values.take(8), conversationQuickPicks(values, { it }, emptyList(), 8)) }
    @Test fun recentChoiceLeadsTheShortList() { assertEquals("style-12", conversationQuickPicks(values, { it }, listOf("style-12"), 8).first()) }
    @Test fun repeatedRecentsDoNotDuplicateCards() { assertEquals(8, conversationQuickPicks(values, { it }, listOf("style-1", "style-1"), 8).distinct().size) }
    @Test fun removedStylesAreIgnored() { assertFalse(conversationQuickPicks(values, { it }, listOf("missing"), 8).contains("missing")) }
    @Test fun zeroOrNegativeCountKeepsGalleryOnly() { assertTrue(conversationQuickPicks(values, { it }, emptyList(), 0).isEmpty()); assertTrue(conversationQuickPicks(values, { it }, emptyList(), -1).isEmpty()) }
}
