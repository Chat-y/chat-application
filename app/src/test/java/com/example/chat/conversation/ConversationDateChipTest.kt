package com.example.chat.ui.chats.detail
import org.junit.Assert.*
import org.junit.Test
class ConversationDateChipTest {
    @Test fun absentFlagPreservesProductionGeometry() { val c=ConversationDateChip.fromMap(emptyMap());assertEquals(12,c.radius);assertEquals(10,c.horizontal);assertEquals(4,c.vertical) }
    @Test fun optInReadsCompact() = assertEquals(ConversationDateChip.Compact,ConversationDateChip.fromMap(mapOf(CONVERSATION_EXTRA_OPTIONS to true,CONVERSATION_DATE_CHIP to "Compact")))
    @Test fun optInReadsPill() = assertEquals(ConversationDateChip.Pill,ConversationDateChip.fromMap(mapOf(CONVERSATION_EXTRA_OPTIONS to true,CONVERSATION_DATE_CHIP to "Pill")))
    @Test fun rollbackRetainsStoredChoiceWithoutApplyingIt() { val v=mapOf<String,Any?>(CONVERSATION_DATE_CHIP to "Pill");assertEquals(ConversationDateChip.Default,ConversationDateChip.fromMap(v));assertEquals("Pill",v[CONVERSATION_DATE_CHIP]) }
    @Test fun malformedOrUnknownDefaults() { assertEquals(ConversationDateChip.Default,ConversationDateChip.fromMap(mapOf(CONVERSATION_EXTRA_OPTIONS to true,CONVERSATION_DATE_CHIP to "Bad")));assertTrue(ConversationDateChip.entries.all { it.radius>=0 && it.horizontal>=0 && it.vertical>=0 }) }
}
