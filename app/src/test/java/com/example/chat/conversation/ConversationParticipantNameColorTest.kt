package com.example.chat.ui.chats.detail

import com.example.chat.core.theme.ThemePresets
import org.junit.Assert.*
import org.junit.Test

/** Source color-only policy. Existing participant identity/hash policy is unchanged. Unrun. */
class ConversationParticipantNameColorTest {
    private val theme = ThemePresets.paper
    @Test fun defaultOffKeepsUniformExplicitCustomColor() {
        val seed = ThemePresets.cobalt.accentColor
        assertEquals(seed,resolveConversationParticipantNameColor(theme,theme.incomingBubbleColor,"sender-1",seed,false,true))
        assertEquals(seed,resolveConversationParticipantNameColor(theme,theme.incomingBubbleColor,"sender-2",seed,false,true))
    }
    @Test fun defaultOffKeepsExistingThemeSurfaceFallback() {
        assertEquals(ParticipantColorPolicy.colorFor("sender-1",theme.accentColor,theme.surfaceColor),
            resolveConversationParticipantNameColor(theme,theme.incomingBubbleColor,"sender-1",null,false,true))
    }
    @Test fun explicitOptInUsesChosenSeedAgainstActualBubble() {
        val seed = ThemePresets.cobalt.accentColor
        assertEquals(ParticipantColorPolicy.colorFor("sender-1",seed,theme.incomingBubbleColor),
            resolveConversationParticipantNameColor(theme,theme.incomingBubbleColor,"sender-1",seed,true,true))
    }
    @Test fun missingSeedUsesCurrentThemeWhenOptedIn() {
        assertEquals(ParticipantColorPolicy.colorFor("sender-1",theme.accentColor,theme.incomingBubbleColor),
            resolveConversationParticipantNameColor(theme,theme.incomingBubbleColor,"sender-1",null,true,true))
    }
    @Test fun messagesWithoutGroupSenderLabelKeepExistingColor() {
        assertEquals(theme.accentColor,resolveConversationParticipantNameColor(theme,theme.outgoingBubbleColor,"me",null,true,false))
        assertEquals(theme.primaryTextColor,resolveConversationParticipantNameColor(theme,theme.outgoingBubbleColor,"me",theme.primaryTextColor,true,false))
    }
}
