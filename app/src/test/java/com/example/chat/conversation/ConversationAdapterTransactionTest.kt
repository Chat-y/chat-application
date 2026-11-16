package com.example.chat.ui.settings.conversation

import androidx.compose.ui.graphics.Color
import com.example.chat.core.theme.ThemePresets
import com.example.chat.core.theme.ConversationEntryStyles
import com.example.chat.domain.models.ConversationPreferences
import org.junit.Assert.*
import org.junit.Test

/** Transaction-contract source cases. Read snapshot deliberately stale; commit owns latest. Unrun. */
class ConversationAdapterTransactionTest {
    private val stale = ConversationPreferences()
    private var latest = ConversationPreferences(makeTextSelectable = true)
    private val transaction: ((ConversationPreferences) -> ConversationPreferences) -> Unit = { transform -> latest = transform(latest) }
    private val forbiddenWrite: (ConversationPreferences) -> Unit = { error("Transaction adapter bypassed") }
    @Test fun round1HeaderEventUsesTransactionLatest() {
        val model = ActionBarViewModel({ stale }, forbiddenWrite, {}, {}, transaction)
        model.onEvent(ActionBarEvent.ToggleCall(true))
        assertTrue(latest.makeTextSelectable); assertTrue(latest.hideCallButton)
    }
    @Test fun round2ExtrasEventUsesTransactionLatest() {
        val model = MoreOptionsViewModel({ stale }, forbiddenWrite, {}, {}, transaction)
        model.onEvent(MoreOptionsEvent.ColorChanged(ConversationExtraColor.hyperlinksColor, ThemePresets.paper.accentColor))
        assertTrue(latest.makeTextSelectable); assertEquals(ThemePresets.paper.accentColor, latest.hyperlinksColor)
    }
    @Test fun round3EntrySelectionAndUndoReadInsideTransaction() {
        val first = ConversationEntryStyles.options[1].id
        latest = latest.copy(entryStyle = first)
        val model = EntryStyleViewModel({ stale }, forbiddenWrite, {}, {}, transaction)
        model.onEvent(EntryStyleEvent.SelectStyle(ConversationEntryStyles.options[2].id))
        model.onEvent(EntryStyleEvent.Undo(model.uiEvent.value!!.id))
        assertEquals(first, latest.entryStyle); assertTrue(latest.makeTextSelectable)
    }
    @Test fun round4BubbleEditUsesTransactionLatest() {
        val theme = ThemePresets.paper
        val model = BubbleAndTicksViewModel({ stale }, forbiddenWrite, {}, {},
            BubblePreviewTheme(theme, Color(theme.surfaceColor), Color(theme.cardColor)), transaction)
        model.edit { it.copy(removeReadMore = true) }
        assertTrue(latest.makeTextSelectable); assertTrue(latest.removeReadMore)
    }
    @Test fun round5TwoInterleavedHeaderChangesRetainBoth() {
        val model = ActionBarViewModel({ stale }, forbiddenWrite, {}, {}, transaction)
        model.onEvent(ActionBarEvent.ToggleAvatar(true))
        latest = latest.copy(hideContactName = true)
        model.onEvent(ActionBarEvent.ToggleCall(true))
        assertTrue(latest.hideProfilePicture); assertTrue(latest.hideContactName); assertTrue(latest.hideCallButton)
    }
}
