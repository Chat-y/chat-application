package com.example.chat.ui.icons

import com.example.chat.ui.settings.universal.iconPackDisplayOrder
import com.example.chat.ui.settings.universal.iconPackDisplayLabel
import org.junit.Assert.*
import org.junit.Test

class AppliedIconPackDisplayContractTest {
//    @Test fun appliedPackIsDefaultFirstWithoutChangingStoredIdentity() {
//        for (theme in ChatyIconThemeId.entries) {
//            val order = iconPackDisplayOrder(theme)
//            assertEquals(theme, order.first())
//            assertEquals(ChatyIconThemeId.entries.toSet(), order.toSet())
//            assertEquals(ChatyIconThemeId.entries.size, order.size)
//            assertEquals("Default", iconPackDisplayLabel(theme, theme))
//            assertEquals(theme, ChatyIconThemeId.fromString(theme.title))
//            for (other in order.drop(1)) assertEquals(other.title, iconPackDisplayLabel(other, theme))
//        }
//    }
    @Test fun factoryDefaultPackIsFirstAndOnlyItsLabelChanges() {
        for (applied in ChatyIconThemeId.entries) {
            val order = iconPackDisplayOrder(applied)
            assertEquals(ChatyIconThemeId.hugeiconsFree, order.first())
            assertEquals(ChatyIconThemeId.entries.toSet(), order.toSet())
            assertEquals(ChatyIconThemeId.entries.size, order.size)
            assertEquals("Default", iconPackDisplayLabel(ChatyIconThemeId.hugeiconsFree, applied))
            for (other in order.drop(1)) assertEquals(other.title, iconPackDisplayLabel(other, applied))
            for (theme in order) assertEquals(theme, ChatyIconThemeId.fromString(theme.title))
        }
    }
}
