package com.example.chat

import com.example.chat.core.theme.ConversationEntryStyles
import com.example.chat.core.theme.BubbleStyleId
import com.example.chat.core.theme.DeliveryIconStyle
import com.example.chat.core.theme.bubbleStyleIdFromString
import com.example.chat.core.theme.deliveryIconStyleFromString
import org.junit.Assert.*
import org.junit.Test

class ConversationStyleRegistryTest {
    @Test fun stableIdsAreUniqueAndAllLegacyNamesStillResolve() {
        val options = ConversationEntryStyles.options
        assertEquals(31, options.size)
        assertEquals(options.size, options.map { it.id }.toSet().size)
        options.forEach { option ->
            assertEquals(option, ConversationEntryStyles.resolve(option.id))
            assertEquals(option, ConversationEntryStyles.resolve(option.legacyName))
            assertEquals(option.legacyName, ConversationEntryStyles.layoutName(option.id))
        }
    }
    @Test fun unknownAndOldStockFallBackToStock() {
        for (value in listOf("", "not-installed", "Stock (Chaty Default)")) {
            assertEquals("Stock", ConversationEntryStyles.layoutName(value))
        }
    }
    @Test fun bubbleAndTickStableIdsRoundTrip() {
        BubbleStyleId.entries.forEach { assertEquals(it, bubbleStyleIdFromString(it.name)) }
        DeliveryIconStyle.entries.forEach { assertEquals(it, deliveryIconStyleFromString(it.name)) }
    }
}
