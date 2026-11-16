package com.example.chat.ui.settings.conversation

import com.example.chat.core.theme.ThemePresets
import com.example.chat.domain.models.ConversationPreferences
import org.junit.Assert.*
import org.junit.Test

/** Row grouping must not change the existing visibility/color behavior. Source cases, unrun. */
class ConversationHeaderStatusGroupingTest {
    private val color = ThemePresets.paper.accentColor
    @Test fun hidingStatusKeepsItsBackground() {
        val before = ConversationPreferences(contactStatusBgColor = color)
        assertEquals(before.copy(disableContactStatus = true), reduceActionBar(before, ActionBarEvent.ToggleStatus(true)))
    }
    @Test fun restoringStatusKeepsItsTextColor() {
        val before = ConversationPreferences(disableContactStatus = true, contactStatusTextColor = color)
        assertEquals(before.copy(disableContactStatus = false), reduceActionBar(before, ActionBarEvent.ToggleStatus(false)))
    }
    @Test fun resetStatusBackgroundKeepsVisibility() {
        val before = ConversationPreferences(disableContactStatus = true, contactStatusBgColor = color)
        assertEquals(before.copy(contactStatusBgColor = null), reduceActionBar(before, ActionBarEvent.PickColor(ConversationHeaderColorTarget.StatusBackground, null)))
    }
    @Test fun statusTextChangeKeepsHeaderBackground() {
        val before = ConversationPreferences(actionBarColor = color)
        assertEquals(before.copy(contactStatusTextColor = color), reduceActionBar(before, ActionBarEvent.PickColor(ConversationHeaderColorTarget.StatusText, color)))
    }
    @Test fun visibilityAndColorEventsPreserveBothChanges() {
        val hidden = reduceActionBar(ConversationPreferences(), ActionBarEvent.ToggleStatus(true))
        val colored = reduceActionBar(hidden, ActionBarEvent.PickColor(ConversationHeaderColorTarget.StatusBackground, color))
        assertTrue(colored.disableContactStatus); assertEquals(color, colored.contactStatusBgColor)
    }
}
