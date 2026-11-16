package com.example.chat.ui.settings.conversation

import com.example.chat.core.theme.ThemePresets
import com.example.chat.domain.models.ConversationPreferences
import org.junit.Assert.*
import org.junit.Test

/** Five review-round source cases. Not executed. */
class MoreOptionsViewModelTest {
    private val color = ThemePresets.paper.accentColor
    @Test fun round1EveryColorHasOneTargetedEvent() {
        val before = ConversationPreferences(makeTextSelectable = true)
        ConversationExtraColor.entries.forEach { target ->
            assertEquals(target.write(before, color), reduceMoreOptions(before, MoreOptionsEvent.ColorChanged(target, color)))
        }
    }
    @Test fun round2NullResetPreservesOtherColors() {
        val before = ConversationPreferences(emojiHeaderColor = color, hyperlinksColor = color)
        assertEquals(before.copy(emojiHeaderColor = null), reduceMoreOptions(before,
            MoreOptionsEvent.ColorChanged(ConversationExtraColor.emojiHeaderColor, null)))
    }
    @Test fun round3ResetAllKeepsUnrelatedPreferences() {
        val before = ConversationPreferences(hideCallButton = true, voicePlaybackSpeed = 1.5,
            emojiHeaderColor = color, voiceNotePlayButtonColor = color)
        assertEquals(before.copy(emojiHeaderColor = null, voiceNotePlayButtonColor = null),
            reduceMoreOptions(before, MoreOptionsEvent.ResetColors))
    }
    @Test fun round4BatchChangesOnlySelectedTargets() {
        val before = ConversationPreferences(hyperlinksColor = color)
        val after = reduceMoreOptions(before, MoreOptionsEvent.ColorsChanged(mapOf(
            ConversationExtraColor.emojiHeaderColor to color, ConversationExtraColor.emojiPickerBgColor to color)))
        assertEquals(before.copy(emojiHeaderColor = color, emojiPickerBgColor = color), after)
    }
    @Test fun round5AdapterReadsTheFreshSnapshotAndNotifiesOneState() {
        var latest = ConversationPreferences()
        var listener: (() -> Unit)? = null
        val model = MoreOptionsViewModel({ latest }, { latest = it }, { listener = it }, {})
        model.onEvent(MoreOptionsEvent.ColorChanged(ConversationExtraColor.hyperlinksColor, color))
        latest = latest.copy(hideCallButton = true)
        listener!!.invoke()
        model.onEvent(MoreOptionsEvent.ColorChanged(ConversationExtraColor.emojiHeaderColor, color))
        assertTrue(latest.hideCallButton)
        assertEquals(color, latest.hyperlinksColor)
        assertEquals(MoreOptionsUiState.from(latest), model.uiState.value)
    }
}
