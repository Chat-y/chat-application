package com.example.chat.ui.settings.conversation

import com.example.chat.domain.models.ConversationPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Explicit default/custom UI-boundary and saved-target tests. Source only, unrun. */
class ConversationColorPreferenceTest {
    @Test fun round1NullIsTypedThemeDefault() = assertEquals(
        ConversationColorPreference.ThemeDefault, ConversationColorPreference.fromStored(null))
    @Test fun round2CustomRoundTripsWithoutChangingAlpha() {
        listOf(0L, 1L, 0x12345678L, 0xFFFFFFFFL).forEach {
            assertEquals(it, ConversationColorPreference.fromStored(it).toStored())
        }
    }
    @Test fun round3DefaultFollowsTheCurrentTheme() {
        val preference = ConversationColorPreference.fromStored(null)
        assertEquals(1L, preference.resolve(1L))
        assertEquals(2L, preference.resolve(2L))
    }
    @Test fun round4SavedPickerKeysResolveWithoutCapturingCallbacks() {
        ConversationHeaderColorTarget.entries.forEach { assertEquals(it, ConversationHeaderColorTarget.fromSavedKey(it.name)) }
        assertNull(ConversationHeaderColorTarget.fromSavedKey("unknown"))
    }
    @Test fun round5ResetTargetWritesOnlyOneField() {
        val before = ConversationPreferences(actionBarColor = 1L, contactStatusBgColor = 2L)
        assertEquals(before.copy(actionBarColor = null), ConversationHeaderColorTarget.Bar.write(before, null))
        assertNull(ConversationColorPreference.ThemeDefault.toStored())
    }
}
