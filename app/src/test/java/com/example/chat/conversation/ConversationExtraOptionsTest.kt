package com.example.chat.ui.chats.detail

import org.junit.Assert.*
import org.junit.Test

/** New extras gate only, no preference deletion or baseline color reset. Source-only, unrun. */
class ConversationExtraOptionsTest {
    @Test fun masterDefaultsOff() { assertFalse(conversationExtraOptionsEnabled(emptyMap())) }
    @Test fun explicitMasterEnablesOnlyGateNotVariedNames() {
        val values = mapOf(CONVERSATION_EXTRA_OPTIONS to true)
        assertTrue(conversationExtraOptionsEnabled(values)); assertFalse(conversationVaryCustomNamesEnabled(values))
    }
    @Test fun variedNamesRequiresBothExplicitFlags() {
        assertFalse(conversationVaryCustomNamesEnabled(mapOf(CONVERSATION_VARY_CUSTOM_NAMES to true)))
        assertTrue(conversationVaryCustomNamesEnabled(mapOf(CONVERSATION_EXTRA_OPTIONS to true,CONVERSATION_VARY_CUSTOM_NAMES to true)))
    }
    @Test fun malformedFlagsDoNotOptIn() {
        assertFalse(conversationExtraOptionsEnabled(mapOf(CONVERSATION_EXTRA_OPTIONS to "true")))
        assertFalse(conversationVaryCustomNamesEnabled(mapOf(CONVERSATION_EXTRA_OPTIONS to true,CONVERSATION_VARY_CUSTOM_NAMES to "true")))
    }
    @Test fun disablingGateDoesNotDeleteStoredChoices() {
        val values = mapOf<String,Any?>(CONVERSATION_EXTRA_OPTIONS to true,CONVERSATION_VARY_CUSTOM_NAMES to true,"legacy" to "kept")
        val rolledBack = values + (CONVERSATION_EXTRA_OPTIONS to false)
        assertFalse(conversationVaryCustomNamesEnabled(rolledBack));assertEquals(true,rolledBack[CONVERSATION_VARY_CUSTOM_NAMES]);assertEquals("kept",rolledBack["legacy"])
    }
}
