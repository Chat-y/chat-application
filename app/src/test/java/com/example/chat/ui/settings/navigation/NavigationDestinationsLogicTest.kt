package com.example.chat.ui.settings.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** SET-R4-004: pins reconcile / move guards / reorder of NavigationDestinationsLogic. */
class NavigationDestinationsLogicTest {

    @Test
    fun reconcileAppendsMissingKnownDestinationsToOverflow() {
        val (p, o) = NavigationDestinationsLogic.reconcile(listOf("chats", "groups"), listOf("updates"))
        assertEquals(listOf("chats", "groups"), p)
        assertEquals(listOf("updates", "tasks", "calls", "settings", "desktop"), o)
    }

    @Test
    fun reconcileDropsUnknownIds() {
        val (p, o) = NavigationDestinationsLogic.reconcile(listOf("chats", "nope"), listOf("alsoNope"))
        assertFalse(p.contains("nope"))
        assertFalse(o.contains("alsoNope"))
        assertTrue(p.contains("chats"))
    }

    @Test
    fun cannotMoveLastPrimaryToOverflow() {
        val p = mutableListOf("chats")
        val o = mutableListOf("groups")
        NavigationDestinationsLogic.moveToOverflow(p, o, "chats")
        assertEquals(listOf("chats"), p)
        assertEquals(listOf("groups"), o)
    }

    @Test
    fun cannotMoveToPrimaryAtMax() {
        val p = mutableListOf("chats", "groups", "updates", "tasks")
        val o = mutableListOf("calls")
        NavigationDestinationsLogic.moveToPrimary(p, o, "calls")
        assertEquals(NavigationDestinationsLogic.MAX_PRIMARY, p.size)
        assertEquals(listOf("calls"), o)
    }

    @Test
    fun moveToPrimaryAndBackWorks() {
        val p = mutableListOf("chats", "groups")
        val o = mutableListOf("calls")
        NavigationDestinationsLogic.moveToPrimary(p, o, "calls")
        assertEquals(listOf("chats", "groups", "calls"), p)
        NavigationDestinationsLogic.moveToOverflow(p, o, "groups")
        assertEquals(listOf("chats", "calls"), p)
        assertEquals(listOf("groups"), o)
    }

    @Test
    fun reorderMovesByDeltaAndRespectsBounds() {
        val l = mutableListOf("a", "b", "c", "d")
        NavigationDestinationsLogic.reorder(l, 0, 2)
        assertEquals(listOf("b", "c", "a", "d"), l)
        NavigationDestinationsLogic.reorder(l, 2, -2)
        assertEquals(listOf("a", "b", "c", "d"), l)
        NavigationDestinationsLogic.reorder(l, 0, -1)
        NavigationDestinationsLogic.reorder(l, 3, 1)
        assertEquals(listOf("a", "b", "c", "d"), l)
    }

    @Test
    fun desktopRowIsHidden() {
        assertFalse(NavigationDestinationsLogic.isRowVisible("desktop"))
        assertTrue(NavigationDestinationsLogic.isRowVisible("chats"))
    }
}
