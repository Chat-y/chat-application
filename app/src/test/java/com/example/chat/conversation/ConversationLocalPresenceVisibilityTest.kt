package com.example.chat.ui.settings.conversation

import com.example.chat.core.theme.ThemePresets
import com.example.chat.domain.models.ConversationPreferences
import org.junit.Assert.*
import org.junit.Test

/** Local viewer display only. Existing Host passes !disableContactStatus to the live header. Unrun. */
class ConversationLocalPresenceVisibilityTest {
    @Test fun defaultKeepsLineVisible() { assertFalse(ConversationPreferences().disableContactStatus) }
    @Test fun explicitHideChangesOnlyExistingLocalFlag() {
        val before = ConversationPreferences(makeTextSelectable = true, hideContactName = true,
            contactStatusTextColor = ThemePresets.paper.primaryTextColor)
        assertEquals(before.copy(disableContactStatus = true), reduceActionBar(before, ActionBarEvent.ToggleStatus(true)))
    }
    @Test fun explicitShowRestoresVisibleLine() {
        val before = ConversationPreferences(disableContactStatus = true, hideCallButton = true)
        assertEquals(before.copy(disableContactStatus = false), reduceActionBar(before, ActionBarEvent.ToggleStatus(false)))
    }
    @Test fun hidePreservesCustomStatusColors() {
        val before = ConversationPreferences(contactStatusTextColor = ThemePresets.paper.primaryTextColor,
            contactStatusBgColor = ThemePresets.paper.surfaceColor)
        val after = reduceActionBar(before, ActionBarEvent.ToggleStatus(true))
        assertEquals(before.contactStatusTextColor, after.contactStatusTextColor)
        assertEquals(before.contactStatusBgColor, after.contactStatusBgColor)
    }
    @Test fun snapshotReflectsExternalLocalFlag() {
        assertTrue(ActionBarUiState.from(ConversationPreferences(disableContactStatus = true)).disableContactStatus)
        assertFalse(ActionBarUiState.from(ConversationPreferences()).disableContactStatus)
    }
}
