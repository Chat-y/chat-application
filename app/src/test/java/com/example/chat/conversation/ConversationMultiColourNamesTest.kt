package com.example.chat.ui.chats.detail
import com.example.chat.core.theme.ThemePresets
import org.junit.Assert.*
import org.junit.Test
class ConversationMultiColourNamesTest {
    @Test fun defaultOnPreservesOldPolicy() = assertTrue(conversationMultiColourNamesEnabled(emptyMap()))
    @Test fun explicitOffOnlyAppliesBehindMaster() { val v=mapOf<String,Any?>(CONVERSATION_MULTI_COLOUR_NAMES to false);assertTrue(conversationMultiColourNamesEnabled(v));assertFalse(conversationMultiColourNamesEnabled(v + (CONVERSATION_EXTRA_OPTIONS to true))) }
    @Test fun uniformOverrideStaysAuthoritativeByDefault() { val t=ThemePresets.monochromeLight;assertEquals(123L,resolveConversationParticipantNameColor(t,t.surfaceColor,"sender",123L,false,true)) }
    @Test fun offStopsSeedVariation() { val t=ThemePresets.monochromeLight;assertEquals(123L,resolveConversationParticipantNameColor(t,t.surfaceColor,"sender",123L,true,true,false)) }
    @Test fun offWithoutOverrideUsesThemeAccent() { val t=ThemePresets.monochromeDark;assertEquals(t.accentColor,resolveConversationParticipantNameColor(t,t.surfaceColor,"sender",null,false,true,false)) }
}
