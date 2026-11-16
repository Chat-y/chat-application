package com.example.chat.ui.settings.conversation

import com.example.chat.core.theme.ThemePresets
import com.example.chat.domain.models.ConversationPreferences
import org.junit.Assert.*
import org.junit.Test

/** Export projection only. No document-picker/file-write execution. Unrun. */
class ConversationHeaderLookExportTest {
    @Test fun conservativeExportOmitsAllVisibilityChoices() {
        assertEquals(setOf("actionBarColor","contactStatusBgColor","contactStatusTextColor"),conversationHeaderLookValues(ConversationPreferences(hideContactName = true),false).keys)
    }
    @Test fun explicitOptInContainsExactlySevenOriginalValues() { assertEquals(7,conversationHeaderLookValues(ConversationPreferences(),true).size) }
    @Test fun colorsKeepNullThemeFollowingRatherThanResolvedTheme() {
        assertNull(conversationHeaderLookValues(ConversationPreferences(),false)["actionBarColor"])
    }
    @Test fun customColorAndVisibilityArePreservedWhenRequested() {
        val values = conversationHeaderLookValues(ConversationPreferences(actionBarColor = ThemePresets.paper.accentColor,hideContactName = true),true)
        assertEquals(ThemePresets.paper.accentColor,values["actionBarColor"]);assertEquals(true,values["hideContactName"])
    }
    @Test fun unrelatedPreferencesNeverEnterFile() {
        val before = ConversationPreferences()
        assertEquals(conversationHeaderLookValues(before,true),conversationHeaderLookValues(before.copy(entryStyle = "private sample",makeTextSelectable = false),true))
    }
}
