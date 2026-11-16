package com.example.chat.ui.chats.starred

import org.junit.Assert.*
import org.junit.Test

class StarredScrollRulesTest {
    @Test fun hiddenWithoutLaidOutRowsOrAtTop() {
        assertFalse(StarredScrollRules.showTop(0, 8))
        assertFalse(StarredScrollRules.showTop(15, 0))
    }
    @Test fun twoScreensBoundary() {
        assertFalse(StarredScrollRules.showTop(15, 8))
        assertTrue(StarredScrollRules.showTop(16, 8))
    }
    @Test fun extremeValuesDoNotOverflow() {
        assertFalse(StarredScrollRules.showTop(Int.MAX_VALUE, Int.MAX_VALUE))
        assertTrue(StarredScrollRules.showTop(Int.MAX_VALUE, 1))
    }
}
