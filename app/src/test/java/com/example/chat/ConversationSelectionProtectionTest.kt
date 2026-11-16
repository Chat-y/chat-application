package com.example.chat

import com.example.chat.domain.models.ChatMessage
import com.example.chat.ui.chats.detail.SelectionAction
import com.example.chat.ui.chats.detail.SelectionActionPolicy
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/** Source fixtures only: not executed during the source-only overnight pass. */
class ConversationSelectionProtectionTest {
    private fun message(id: String = "m", metadata: Map<String, Any?> = emptyMap()) =
        ChatMessage(id, "room", "sender", text = "Readable message", createdAt = Instant.EPOCH, metadata = metadata)

    @Test fun ordinarySelectionRetainsAllFourActions() {
        val expected = setOf(SelectionAction.copy, SelectionAction.forward, SelectionAction.star, SelectionAction.delete)
        assertEquals(expected, SelectionActionPolicy.available(listOf(message())))
        assertEquals(expected, SelectionActionPolicy.available(listOf(message(metadata = mapOf("view_once" to false)))))
    }

    @Test fun protectedAndUnexpectedRepresentationsAllowDeleteOnly() {
        for (flag in listOf(true, "true", "false", "TRUE", 1, 0, -1, 0.0, Double.NaN,
            emptyList<String>(), emptyMap<String, String>())) {
            assertEquals("flag=$flag", setOf(SelectionAction.delete),
                SelectionActionPolicy.available(listOf(message(metadata = mapOf("view_once" to flag)))))
        }
    }

    @Test fun oneProtectedMessageRemovesSensitiveActionsFromMixedSelection() {
        assertEquals(setOf(SelectionAction.delete), SelectionActionPolicy.available(listOf(
            message("normal"), message("protected", mapOf("view_once" to "true")))))
    }

    @Test fun emptySelectionHasNoActions() {
        assertEquals(emptySet<SelectionAction>(), SelectionActionPolicy.available(emptyList()))
    }
}
