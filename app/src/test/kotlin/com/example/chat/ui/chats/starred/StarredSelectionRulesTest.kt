package com.example.chat.ui.chats.starred

import org.junit.Assert.*
import org.junit.Test

class StarredSelectionRulesTest {
    @Test fun preservesNewestOrderAndFiltersBeforeProjection() {
        assertEquals(listOf(30, 10), StarredSelectionRules.select(listOf(3, 2, 1), { it != 2 }, { it * 10 }, false, null, {}))
    }
    @Test fun oldestReversesCapturedNewestOrder() {
        assertEquals(listOf(1, 2, 3), StarredSelectionRules.select(listOf(3, 2, 1), { true }, { it }, true, null, {}))
    }
    @Test fun comparatorOrderTakesPrecedenceAndKeepsDuplicates() {
        assertEquals(listOf(1, 1, 2, 3), StarredSelectionRules.select(listOf(3, 1, 2, 1), { true }, { it }, true, naturalOrder<Int>(), {}))
    }
    @Test fun emptyInputStillChecksCancellation() {
        var checks = 0
        assertTrue(StarredSelectionRules.select(emptyList<Int>(), { true }, { it }, false, null, { checks++ }).isEmpty())
        assertTrue(checks > 0)
    }
    @Test(expected = java.util.concurrent.CancellationException::class)
    fun cancellationStopsSelection() {
        StarredSelectionRules.select(listOf(3, 2, 1), { true }, { it }, false, null, { throw java.util.concurrent.CancellationException() })
    }
}
