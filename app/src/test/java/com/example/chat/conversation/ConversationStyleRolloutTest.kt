package com.example.chat.ui.settings.conversation
import com.example.chat.core.theme.ConversationEntryStyles
import org.junit.Assert.*
import org.junit.Test
class ConversationStyleRolloutTest {
    @Test fun bothFlagsDefaultOff() { assertFalse(conversationStyleRollout(emptyMap(),CONVERSATION_BUBBLE_ROLLOUT));assertFalse(conversationStyleRollout(emptyMap(),CONVERSATION_ENTRY_ROLLOUT)) }
    @Test fun flagsAreIndependent() { val v=mapOf<String,Any?>(CONVERSATION_ENTRY_ROLLOUT to true);assertTrue(conversationStyleRollout(v,CONVERSATION_ENTRY_ROLLOUT));assertFalse(conversationStyleRollout(v,CONVERSATION_BUBBLE_ROLLOUT)) }
    @Test fun offSavesLegacyNameOnlyAfterExplicitSelection() = assertEquals("Reference 2",conversationEntrySavedValue("reference_two",false))
    @Test fun onSavesStableId() = assertEquals("reference_two",conversationEntrySavedValue("Reference 2",true))
    @Test fun rollbackDoesNotRewriteAnySavedStyleAndReadersStillWork() { val raw="reference_two";val flags=mapOf<String,Any?>(CONVERSATION_ENTRY_ROLLOUT to false);assertFalse(conversationStyleRollout(flags,CONVERSATION_ENTRY_ROLLOUT));assertEquals("Reference 2",ConversationEntryStyles.layoutName(raw));assertEquals("reference_two",raw) }
    @Test fun everyLegacyWriterPreservesStyleEvenForCollidingLabels() {
        com.example.chat.core.theme.DeliveryIconStyle.entries.forEach { assertEquals(it,com.example.chat.core.theme.deliveryIconStyleFromString(conversationLegacyTickValue(it))) }
        com.example.chat.core.theme.BubbleStyleId.entries.forEach { assertEquals(it,com.example.chat.core.theme.bubbleStyleIdFromString(conversationLegacyBubbleValue(it))) }
    }
}
