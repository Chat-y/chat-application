package com.example.chat

import com.example.chat.ui.search.SearchResultWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchResultWindowTest {
    @Test fun preservesRankAndFirstDuplicateWithinRawWindow() {
        val result = SearchResultWindow.first(listOf("a", "b", "a", "c"), limit = 3) { it }
        assertEquals(listOf("a", "b"), result.items)
        assertTrue(result.truncated)
    }
    @Test fun exactBoundAndEmptyResponseAreNotTruncated() {
        assertFalse(SearchResultWindow.first(listOf(1, 2), limit = 2) { it }.truncated)
        assertEquals(emptyList<Int>(), SearchResultWindow.first(emptyList<Int>()) { it }.items)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsNonpositiveBounds() {
        SearchResultWindow.first(listOf(1), limit = 0) { it }
    }
}
