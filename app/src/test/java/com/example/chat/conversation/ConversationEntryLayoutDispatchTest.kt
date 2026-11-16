package com.example.chat.core.theme
import org.junit.Assert.*
import org.junit.Test
/** Pure selector source cases. Actual ChatEntryBar has an exhaustive enum when; no rendering claim. */
class ConversationEntryLayoutDispatchTest {
    @Test fun everySavedIdSelectsDistinctNonDefaultOrDeclaredAlias() {
        ConversationEntryStyles.options.forEach { option ->
            val target = ConversationEntryStyles.rendererLayout(option.id)
            val expected = ConversationEntryStyles.layoutAliases[option.id] ?: option.id
            assertEquals(option.id,expected,target.name)
            if (option.id != "stock") assertNotEquals(ConversationEntryLayout.stock,target)
        }
    }
    @Test fun allTargetsAreCoveredByOptions() = assertEquals(ConversationEntryLayout.entries.toSet(),ConversationEntryStyles.options.map { ConversationEntryStyles.rendererLayout(it.id) }.toSet())
    @Test fun existingDuplicateLayoutsAreDeclaredOnly() = assertEquals(mapOf("wanh" to "aran_v2","rc_google" to "yowa"),ConversationEntryStyles.layoutAliases)
    @Test fun legacyAndStableIdsShareRenderer() { ConversationEntryStyles.options.forEach { assertEquals(it.id,ConversationEntryStyles.rendererLayout(it.id),ConversationEntryStyles.rendererLayout(it.legacyName)) } }
    @Test fun unknownRemainsStock() = assertEquals(ConversationEntryLayout.stock,ConversationEntryStyles.rendererLayout("unknown future style"))
}
