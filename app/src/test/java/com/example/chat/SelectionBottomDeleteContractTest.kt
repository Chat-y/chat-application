package com.example.chat

import org.junit.Assert.*
import org.junit.Test

/** Source fixtures only: no UI or deletion was executed. */
class SelectionBottomDeleteContractTest {
    private fun source(): String {
        val root = if (java.io.File("app/src/main").exists()) java.io.File("app/src/main") else java.io.File("src/main")
        return java.io.File(root, "kotlin/com/example/chat/ui/chats/ChatDetailScreen.kt").readText()
            .lineSequence().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")
    }
    @Test fun selectModeKeepsDeleteBesideTheExistingBottomActions() {
        val bar = source().substringAfter("private fun SelectionActions(").substringBefore("private fun SelectionActionButton(")
        assertTrue(bar.contains("SelectionAction.forward in actions"))
        assertTrue(bar.contains("SelectionAction.copy in actions"))
        assertTrue(bar.contains("SelectionAction.delete in actions"))
        assertTrue(bar.contains("SelectionAction.star in actions"))
        assertTrue(bar.contains("onDelete)"))
    }
    @Test fun bottomDeleteUsesExistingGuardedDeleteForMeAndSelectionDisablesSwipeReply() {
        val source = source()
        val callback = source.substringAfter("                    onDelete = {").substringBefore("            } else {")
        assertTrue(callback.contains("performSelectionAction(com.example.chat.ui.chats.detail.SelectionAction.delete)"))
        assertTrue(callback.contains("onDeleteMessage(it.id, false)"))
        assertTrue(callback.contains("selectedIds = emptySet()"))
        assertTrue(source.contains("onSwipeReply = if (isSelectionMode || row.messages.size > 1) null"))
    }
}
