package com.example.chat.ui.settings.navigation

import com.example.chat.core.templates.ChatyNavigationDestinationIds
import org.junit.Assert.*
import org.junit.Test

/** Regression fixtures, source-only and not run. Keep persistence-boundary validation. */
class NavigationReconcileInvariantTest {
    private fun assertValid(primary: List<String>, overflow: List<String>) {
        assertTrue(primary.size in 1..NavigationDestinationsLogic.MAX_PRIMARY)
        assertEquals((primary + overflow).size, (primary + overflow).toSet().size)
        assertEquals(ChatyNavigationDestinationIds.known, (primary + overflow).toSet())
    }

    @Test fun repairsDuplicatesAcrossAndWithinGroups() {
        val (p, o) = NavigationDestinationsLogic.reconcile(listOf("chats", "chats"), listOf("chats", "calls", "calls"))
        assertEquals(listOf("chats"), p)
        assertEquals("calls", o.first())
        assertValid(p, o)
    }

    @Test fun emptyOrUnknownPrimaryGetsOneReachableDestination() {
        val (p, o) = NavigationDestinationsLogic.reconcile(listOf("unknown"), listOf("settings"))
        assertEquals(listOf("settings"), p)
        assertValid(p, o)
    }

    @Test fun extraPrimariesMoveToOverflowWithoutLoss() {
        val (p, o) = NavigationDestinationsLogic.reconcile(ChatyNavigationDestinationIds.all, emptyList())
        assertEquals(ChatyNavigationDestinationIds.all.take(4), p)
        assertEquals(ChatyNavigationDestinationIds.all.drop(4), o)
        assertValid(p, o)
    }

    @Test fun reconciliationIsIdempotent() {
        val once = NavigationDestinationsLogic.reconcile(listOf("chats", "chats"), listOf("settings", "unknown"))
        assertEquals(once, NavigationDestinationsLogic.reconcile(once.first, once.second))
    }
}
