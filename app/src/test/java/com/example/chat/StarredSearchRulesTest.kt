package com.example.chat

import com.example.chat.ui.chats.starred.StarredSearchRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StarredSearchRulesTest {
    @Test fun rowKeySeparatesDelimitersAndEmptyIdentifiers() {
        assertFalse(StarredSearchRules.rowKey("a:b", "c") == StarredSearchRules.rowKey("a", "b:c"))
        assertFalse(StarredSearchRules.rowKey("", "ab") == StarredSearchRules.rowKey("a", "b"))
        assertEquals(StarredSearchRules.rowKey("మాయ", "1:2"), StarredSearchRules.rowKey("మాయ", "1:2"))
    }

    @Test fun tokenMatchingIsOrderIndependentAndRequiresEveryToken() {
        val text = StarredSearchRules.normalize("Doctor's appointment tomorrow")
        assertTrue(StarredSearchRules.matches(text, "appointment doctor"))
        assertFalse(StarredSearchRules.matches(text, "appointment dentist"))
    }
    @Test fun emptyQueryMatchesAndWhitespaceIsIgnored() {
        val text = StarredSearchRules.normalize("one two")
        assertTrue(StarredSearchRules.matches(text, ""))
        assertTrue(StarredSearchRules.matches(text, "  TWO\n one  "))
    }
    @Test fun precomposedAndCombiningLatinAccentsMatchUnaccentedText() {
        assertEquals("cafe", StarredSearchRules.normalize("CAFÉ"))
        assertEquals("cafe", StarredSearchRules.normalize("cafe\u0301"))
        assertTrue(StarredSearchRules.matches(StarredSearchRules.normalize("résumé.pdf"), "resume"))
    }
    @Test fun nonLatinVowelSignsAndEmojiArePreserved() {
        assertEquals("మాయ", StarredSearchRules.normalize("మాయ"))
        assertEquals("किताब", StarredSearchRules.normalize("किताब"))
        assertEquals("👨‍👩‍👧‍👦", StarredSearchRules.normalize("👨‍👩‍👧‍👦"))
    }
}
