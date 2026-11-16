package com.example.chat.ui.settings.conversation

import com.example.chat.core.theme.ConversationEntryStyles
import com.example.chat.core.theme.ThemePresets
import com.example.chat.domain.models.ConversationPreferences
import org.junit.Assert.*
import org.junit.Test

/** Pending-notice and state-adapter source cases. Not executed. */
class EntryStyleViewModelTest {
    private class Harness {
        var latest = ConversationPreferences(makeTextSelectable = true)
        val model = EntryStyleViewModel({ latest }, { latest = it }, {}, {})
    }
    @Test fun round1SelectUsesAStableIdAndEmitsOnePendingNotice() {
        val h = Harness(); val option = ConversationEntryStyles.options[1]
        h.model.onEvent(EntryStyleEvent.SelectStyle(option.id))
        assertEquals(option.id, h.latest.entryStyle)
        assertEquals(option.id, h.model.uiEvent.value!!.appliedId)
        assertEquals(EntryStyleUiState.from(h.latest), h.model.uiState.value)
    }
    @Test fun round2EachColorChangesOnlyItsTarget() {
        EntryColorTarget.entries.forEach { target ->
            val h = Harness(); val before = h.latest; val color = ThemePresets.paper.accentColor
            h.model.onEvent(EntryStyleEvent.PickColor(target, color))
            assertEquals(target.write(before, color), h.latest)
        }
    }
    @Test fun round3ResetPreservesStyleAndUnrelatedValues() {
        val h = Harness(); val style = ConversationEntryStyles.options[1].id
        h.latest = h.latest.copy(entryStyle = style, uiButtonsColor = ThemePresets.paper.accentColor)
        h.model.onEvent(EntryStyleEvent.Reset)
        assertEquals(ConversationPreferences(entryStyle = style, makeTextSelectable = true), h.latest)
    }
    @Test fun round4UndoIsGuardedAgainstANewerSelection() {
        val h = Harness(); val first = ConversationEntryStyles.options[1].id; val second = ConversationEntryStyles.options[2].id
        h.model.onEvent(EntryStyleEvent.SelectStyle(first)); val oldId = h.model.uiEvent.value!!.id
        h.model.onEvent(EntryStyleEvent.SelectStyle(second)); val newId = h.model.uiEvent.value!!.id
        h.model.onEvent(EntryStyleEvent.Undo(oldId)); assertEquals(second, h.latest.entryStyle)
        h.model.onEvent(EntryStyleEvent.Undo(newId)); assertEquals(first, h.latest.entryStyle)
        assertNull(h.model.uiEvent.value)
    }
    @Test fun round5AcknowledgmentDoesNotClearANewerNotice() {
        val h = Harness()
        h.model.onEvent(EntryStyleEvent.SelectStyle(ConversationEntryStyles.options[1].id)); val first = h.model.uiEvent.value!!.id
        h.model.onEvent(EntryStyleEvent.SelectStyle(ConversationEntryStyles.options[2].id)); val second = h.model.uiEvent.value!!.id
        h.model.acknowledge(first); assertEquals(second, h.model.uiEvent.value!!.id)
        h.model.acknowledge(second); assertNull(h.model.uiEvent.value)
    }
}
