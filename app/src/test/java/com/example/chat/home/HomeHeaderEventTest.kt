package com.example.chat.home

import com.example.chat.ui.navigation.*
import com.example.chat.ui.chats.home.HomeViewState
import org.junit.Assert.*
import org.junit.Test

/** Unrun source fixtures. No UI, coroutine, native call or external account is exercised. */
class HomeHeaderEventTest {
    @Test fun absentReceiverDoesNotQueueAnActionForLaterReplay() {
        val controller = HomeHeaderController()
        controller.emit("chats", HeaderEvent(HeaderAction.Delete))
        assertTrue(controller.entry("chats").events.isEmpty())
    }
    @Test fun disposalClearsPendingActionsAndCapabilities() {
        val controller = HomeHeaderController()
        val entry = controller.entry("chats")
        entry.attach()
        controller.emit("chats", HeaderEvent(HeaderAction.Delete))
        val event = entry.events.single()
        entry.detach()
        entry.attach()
        assertFalse(entry.accepts(event))
        assertTrue(entry.events.isEmpty())
        assertNull(entry.model)
    }
    @Test fun selectionOrViewChangeInvalidatesSelectionAction() {
        val controller = HomeHeaderController()
        val entry = controller.entry("chats")
        entry.attach()
        entry.selectedIds = setOf("a")
        entry.selectionScope = HomeViewState() to ""
        controller.emit("chats", HeaderEvent(HeaderAction.Delete))
        val event = entry.events.single()
        entry.selectedIds = setOf("b")
        assertFalse(entry.accepts(event))
        controller.emit("chats", HeaderEvent(HeaderAction.SelectAll))
        val select = entry.events.last()
        entry.selectionScope = HomeViewState(filter = "Unread") to ""
        assertFalse(entry.accepts(select))
    }
    @Test fun identicalClicksHaveDistinctAcknowledgements() {
        val controller = HomeHeaderController()
        val entry = controller.entry("chats")
        entry.attach()
        repeat(2) { controller.emit("chats", HeaderEvent(HeaderAction.ToggleSearch)) }
        assertNotEquals(entry.events[0].sequence, entry.events[1].sequence)
    }
    @Test fun failedDispatchResetsReceiverAndFencesOldQueue() {
        val controller = HomeHeaderController()
        val entry = controller.entry("chats")
        entry.attach()
        controller.emit("chats", HeaderEvent(HeaderAction.ToggleSearch))
        val failed = entry.events.single()
        entry.recoverReceiverAfterFailure()
        assertTrue(entry.receiverReady)
        assertTrue(entry.events.isEmpty())
        assertFalse(entry.accepts(failed))
        controller.emit("chats", HeaderEvent(HeaderAction.OpenOverflowMenu))
        assertTrue(entry.accepts(entry.events.single()))
    }
    @Test fun returningToSameSelectedIdsDoesNotReviveOldOperation() {
        val controller = HomeHeaderController()
        val entry = controller.entry("chats")
        entry.attach()
        entry.selectedIds = setOf("a")
        controller.emit("chats", HeaderEvent(HeaderAction.Delete))
        val old = entry.events.single()
        entry.selectedIds = setOf("b")
        entry.selectedIds = setOf("a")
        assertFalse(entry.accepts(old))
    }
    @Test fun queryScopeChangeFencesSelectionButNotPlainSearchEditing() {
        val controller = HomeHeaderController()
        val entry = controller.entry("chats")
        entry.attach()
        entry.selectionScope = HomeViewState() to "first"
        controller.emit("chats", HeaderEvent(HeaderAction.ToggleMute))
        controller.emit("chats", HeaderEvent(HeaderAction.ChangeQuery, text = "second"))
        val selectedAction = entry.events[0]
        val queryAction = entry.events[1]
        entry.selectionScope = HomeViewState() to "second"
        assertFalse(entry.accepts(selectedAction))
        assertTrue(entry.accepts(queryAction))
    }
    @Test fun accountControllersAndDestinationEntriesDoNotShareQueueOrModalState() {
        val firstAccount = HomeHeaderController()
        val secondAccount = HomeHeaderController()
        val chats = firstAccount.entry("chats")
        chats.attach()
        chats.showDeleteConfirm = true
        firstAccount.emit("chats", HeaderEvent(HeaderAction.NavNewCall))
        assertFalse(firstAccount.entry("groups").showDeleteConfirm)
        assertTrue(firstAccount.entry("groups").events.isEmpty())
        assertFalse(secondAccount.entry("chats").showDeleteConfirm)
        assertTrue(secondAccount.entry("chats").events.isEmpty())
        assertFalse(secondAccount.entry("chats").receiverReady)
    }
    @Test fun detachedCallAndCameraHeaderActionsCannotReplayAfterRemount() {
        val controller = HomeHeaderController()
        val entry = controller.entry("chats")
        entry.attach()
        controller.emit("chats", HeaderEvent(HeaderAction.NavNewCall))
        controller.emit("chats", HeaderEvent(HeaderAction.NavOpenCameraEffects))
        val previous = entry.events.toList()
        entry.detach()
        entry.attach()
        assertTrue(previous.all { !entry.accepts(it) })
        assertTrue(entry.events.isEmpty())
    }
}
