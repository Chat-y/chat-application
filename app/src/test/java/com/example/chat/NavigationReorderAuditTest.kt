package com.example.chat

import com.example.chat.ui.settings.navigation.NavigationDestinationsLogic
import org.junit.Assert.assertEquals
import org.junit.Test

/** Source-only fixtures. Not run. */
class NavigationReorderAuditTest {
    @Test fun negativeOriginDoesNotRemove() {
        val values = mutableListOf("a", "b")
        NavigationDestinationsLogic.reorder(values, -1, 1)
        assertEquals(listOf("a", "b"), values)
    }
    @Test fun pastEndOriginDoesNotRemove() {
        val values = mutableListOf("a", "b")
        NavigationDestinationsLogic.reorder(values, 2, -1)
        assertEquals(listOf("a", "b"), values)
    }
    @Test fun ordinaryMoveStillInserts() {
        val values = mutableListOf("a", "b", "c")
        NavigationDestinationsLogic.reorder(values, 0, 2)
        assertEquals(listOf("b", "c", "a"), values)
    }
}
