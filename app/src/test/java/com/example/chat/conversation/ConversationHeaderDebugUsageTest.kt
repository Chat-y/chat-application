package com.example.chat.ui.settings.conversation

import com.example.chat.domain.models.ConversationPreferences
import org.junit.Assert.*
import org.junit.Test

/** Debug session counters contain event categories only. Source-only, unrun. */
class ConversationHeaderDebugUsageTest {
    @Test fun repeatsIncrementOnlyOneFixedKey() {
        val once = recordHeaderDebugUsage(emptyMap(), ActionBarEvent.ToggleCall(true))
        assertEquals(mapOf("calls" to 2), recordHeaderDebugUsage(once, ActionBarEvent.ToggleCall(false)))
    }
    @Test fun colorValueIsNeverInCounterPayload() {
        val map = recordHeaderDebugUsage(emptyMap(), ActionBarEvent.PickColor(ConversationHeaderColorTarget.Bar, null))
        assertEquals(mapOf("colour_Bar" to 1), map)
    }
    @Test fun releaseDefaultDoesNotCollect() {
        var latest = ConversationPreferences()
        val model = ActionBarViewModel({ latest }, { latest = it }, {}, {})
        model.onEvent(ActionBarEvent.ToggleAvatar(true)); assertTrue(model.debugUsage.value.isEmpty())
    }
    @Test fun debugAdapterRecordsSuccessfulEventsOnlyInMemory() {
        var latest = ConversationPreferences()
        val model = ActionBarViewModel({ latest }, { latest = it }, {}, {}, enableDebugUsage = true)
        model.onEvent(ActionBarEvent.Reset); assertEquals(mapOf("reset" to 1), model.debugUsage.value)
    }
    @Test fun saturationDoesNotOverflow() {
        assertEquals(Int.MAX_VALUE, recordHeaderDebugUsage(mapOf("name" to Int.MAX_VALUE), ActionBarEvent.ToggleName(true))["name"])
    }
}
