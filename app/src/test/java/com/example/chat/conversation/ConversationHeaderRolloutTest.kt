package com.example.chat.ui.chats.detail

import org.junit.Assert.*
import org.junit.Test

/** Existing GB map only. Flag stops effects without deleting saved choices. Source-only, unrun. */
class ConversationHeaderRolloutTest {
    @Test fun flagDefaultsOff() { assertFalse(ConversationHeaderAppearance.fromMap(emptyMap()).optionsEnabled) }
    @Test fun disabledFlagKeepsAllEffectsAtOriginalDefaults() {
        val stored = ConversationHeaderAppearance(nameWeight = "regular",centeredTitle = true,avatarSizeDp = 44,
            roundedSquareAvatar = true,autoContrast = true,divider = true,statusMarqueeOnTap = true,callsInOverflow = true,compactLandscape = true)
        assertEquals(ConversationHeaderAppearance(),stored.effective())
        assertEquals("regular",stored.nameWeight)
    }
    @Test fun flagEnablesOnlyExplicitStoredChoices() {
        val stored = ConversationHeaderAppearance(optionsEnabled = true,nameWeight = "regular")
        assertEquals(stored,stored.effective())
    }
    @Test fun turningFlagOffDoesNotRemoveLegacyOrNewKeys() {
        val values = mapOf<String,Any?>("legacy_key" to "kept",ConversationHeaderAppearance.NAME_WEIGHT to "medium",ConversationHeaderAppearance.OPTIONS_ENABLED to true)
        val rolledBack = values + (ConversationHeaderAppearance.OPTIONS_ENABLED to false)
        assertEquals("kept",rolledBack["legacy_key"])
        assertEquals("medium",ConversationHeaderAppearance.fromMap(rolledBack).nameWeight)
        assertEquals(ConversationHeaderAppearance(),ConversationHeaderAppearance.fromMap(rolledBack).effective())
    }
    @Test fun malformedFlagDoesNotOptIn() {
        assertFalse(ConversationHeaderAppearance.fromMap(mapOf(ConversationHeaderAppearance.OPTIONS_ENABLED to "true")).optionsEnabled)
    }
}
