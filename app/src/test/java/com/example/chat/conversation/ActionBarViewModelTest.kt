package com.example.chat.ui.settings.conversation

import com.example.chat.core.theme.ThemePresets
import com.example.chat.domain.models.ConversationPreferences
import org.junit.Assert.*
import org.junit.Test

/** Reducer/adapter source tests. No Android UI or controller implementation needed. Unrun. */
class ActionBarViewModelTest {
    @Test fun round1CallEventPreservesTheNameAndUnrelatedPreferences() {
        val before = ConversationPreferences(hideContactName = true, makeTextSelectable = true)
        assertEquals(before.copy(hideCallButton = true), reduceActionBar(before, ActionBarEvent.ToggleCall(true)))
    }
    @Test fun round2ResetPreservesNonHeaderValues() {
        val before = ConversationPreferences(hideProfilePicture = true, hideContactName = true,
            hideCallButton = true, disableContactStatus = true, actionBarColor = ThemePresets.paper.accentColor,
            makeTextSelectable = true)
        val after = reduceActionBar(before, ActionBarEvent.Reset)
        assertEquals(ConversationPreferences(makeTextSelectable = true), after)
    }
    @Test fun round3ResetOneColorKeepsTheOthers() {
        val before = ConversationPreferences(actionBarColor = ThemePresets.paper.accentColor,
            contactStatusTextColor = ThemePresets.paper.primaryTextColor)
        assertEquals(before.copy(actionBarColor = null), reduceActionBar(before,
            ActionBarEvent.PickColor(ConversationHeaderColorTarget.Bar, null)))
    }
    @Test fun round4EventsReadTheNewestSnapshot() {
        var latest = ConversationPreferences()
        val model = ActionBarViewModel({ latest }, { latest = it }, {}, {})
        model.onEvent(ActionBarEvent.ToggleAvatar(true))
        latest = latest.copy(makeTextSelectable = true)
        model.onEvent(ActionBarEvent.ToggleName(true))
        assertTrue(latest.hideProfilePicture)
        assertTrue(latest.hideContactName)
        assertTrue(latest.makeTextSelectable)
        assertEquals(ActionBarUiState.from(latest), model.uiState.value)
    }
    @Test fun round5ExternalChangesRefreshOneState() {
        var latest = ConversationPreferences()
        var listener: (() -> Unit)? = null
        val model = ActionBarViewModel({ latest }, { latest = it }, { listener = it }, {})
        latest = latest.copy(hideCallButton = true, disableContactStatus = true)
        listener!!.invoke()
        assertEquals(ActionBarUiState.from(latest), model.uiState.value)
    }
}
