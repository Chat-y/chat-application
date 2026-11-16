package com.example.chat

import com.example.chat.core.preferences.PreferenceHistoryEntry
import com.example.chat.ui.settings.privacy.privacyRecentHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Actual screen projection, uncompiled/unrun; no controller/persistence mutation. */
class PrivacyHistoryProjectionProposalTest {
    private fun entry(title: String, time: Long, key: String = "privacy") =
        PreferenceHistoryEntry(key, title, null, null, time)

    @Test fun newestFirstControllerHistoryIsNotReversed() {
        val input = listOf(entry("newest", 300), entry("middle", 200), entry("oldest", 100))
        assertEquals(listOf("newest", "middle", "oldest"), privacyRecentHistory(input).map { it.title })
    }
    @Test fun filterKeepsOnlyPrivacyWithoutChangingOrderOrInput() {
        val input = listOf(entry("newest", 300), entry("other", 250, "home"), entry("oldest", 100))
        assertEquals(listOf("newest", "oldest"), privacyRecentHistory(input).map { it.title })
        assertEquals(3, input.size)
        assertTrue(privacyRecentHistory(emptyList()).isEmpty())
    }
    @Test fun capKeepsFiftyNewestRatherThanFiftyOldest() {
        val input = (60 downTo 1).map { entry("event$it", it.toLong()) }
        val output = privacyRecentHistory(input)
        assertEquals(50, output.size)
        assertEquals("event60", output.first().title)
        assertEquals("event11", output.last().title)
    }
}
