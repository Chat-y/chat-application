package com.example.chat.ui.chats.detail
import org.junit.Assert.*
import org.junit.Test
class ConversationPerChatColorsTest {
    @Test fun absentAndDisabledNeverOverrideCurrentTheme() { assertEquals(ConversationPerChatColors(),ConversationPerChatColors.fromMap(emptyMap(),"account/chat").effective());val saved=ConversationPerChatColors(header=123L);assertNull(saved.effective().header);assertEquals(123L,saved.header) }
    @Test fun readUsesExactAccountAndChatKeyOnly() { val v=mapOf<String,Any?>(CONVERSATION_PER_CHAT_COLORS to mapOf("a/chat" to mapOf("enabled" to true,"header" to 0xff123456L)));assertNotNull(ConversationPerChatColors.fromMap(v,"a/chat").effective().header);assertNull(ConversationPerChatColors.fromMap(v,"b/chat").effective().header);assertNull(ConversationPerChatColors.fromMap(v,null).header) }
    @Test fun invalidNumbersNeverBecomeColors() { val own=mapOf("enabled" to true,"header" to Double.NaN,"link" to 1.5,"name" to Long.MAX_VALUE);val v=mapOf<String,Any?>(CONVERSATION_PER_CHAT_COLORS to mapOf("a/chat" to own));assertEquals(ConversationPerChatColors(enabled=true),ConversationPerChatColors.fromMap(v,"a/chat")) }
    @Test fun updatePreservesOtherChatsAndUnknownFields() { val root=mapOf("other/chat" to mapOf("header" to 12L),"a/chat" to mapOf("future" to "kept"));val v=mapOf<String,Any?>(CONVERSATION_PER_CHAT_COLORS to root);val next=updatedConversationPerChatColors(v,"a/chat",ConversationPerChatColors(enabled=true,header=123L));assertEquals(root["other/chat"],next["other/chat"]);assertEquals("kept",(next["a/chat"] as Map<*,*>)["future"]) }
    @Test fun disablingRetainsStoredRawChoicesForRollback() { val stored=ConversationPerChatColors(true,123L,456L,789L);val rolled=stored.copy(enabled=false);assertEquals(123L,rolled.header);assertNull(rolled.effective().header);assertEquals(123L,rolled.copy(enabled=true).header) }
    @Test fun scopedThemeBaseStillReceivesExplicitLocalOverridesOnly() {
        val scoped=BubbleColorOverrides()
        val own=ConversationPerChatColors(enabled=true,link=123L,name=456L)
        val result=own.applyTo(scoped)
        assertEquals(123L,result.hyperlinksColor);assertEquals(456L,result.groupParticipantNameColor)
        assertNull(result.rightBubbleColor);assertFalse(result.varyCustomParticipantNames)
        assertEquals(scoped,own.copy(enabled=false).applyTo(scoped))
    }
}
