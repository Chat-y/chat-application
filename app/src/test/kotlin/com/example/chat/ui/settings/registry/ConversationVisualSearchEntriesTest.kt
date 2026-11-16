package com.example.chat.ui.settings.registry

import com.example.chat.domain.models.ConversationPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source-only registry projection and hook cases. Unrun. */
class ConversationVisualSearchEntriesTest {
    private val entries get() = conversationVisualSearchClusters.flatMap { it.settings }
    @Test fun round1ExactlySixteenUniqueOptionsArePrepared() {
        assertEquals(16, entries.size)
        assertEquals(16, entries.map { it.id }.distinct().size)
    }
    @Test fun round2AllEntriesReuseTheKnownHubRoute() {
        assertTrue(entries.all { it.canonicalRoute == "/settings/conversation" })
    }
    @Test fun round3HeaderHasSevenAndExtrasHasNineOptions() = assertEquals(listOf(7, 9),
        conversationVisualSearchClusters.map { it.settings.size })
    @Test fun round4EveryKeyExistsInTheCurrentModel() {
        val model = ConversationPreferences().toMap()
        entries.forEach { assertTrue(it.id.removePrefix("conv_visual_") in model) }
    }
    @Test fun round5DefaultsMatchTheCurrentModel() {
        val model = ConversationPreferences().toMap()
        entries.forEach { assertEquals(it.title, model[it.id.removePrefix("conv_visual_")], it.defaultValue) }
    }
    @Test fun registeredHeaderAndExtrasIdsArePresentExactlyOnce() {
        val all = SettingsRegistry.allSettings
        entries.forEach { item -> assertEquals(1,all.count { it.id == item.id }) }
        assertTrue(SettingsRegistry.validateInvariants())
    }
}
