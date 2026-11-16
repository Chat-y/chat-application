package com.example.chat.ui.chats.detail

import org.junit.Assert.*
import org.junit.Test

/** Source-only opt-in/motion policy. Not animation or rendered acceptance. Unrun. */
class ConversationHeaderMarqueeTest {
    @Test fun defaultNeverStartsMotion() { assertFalse(ConversationHeaderAppearance.fromMap(emptyMap()).statusMarqueeOnTap) }
    @Test fun disabledOptionIgnoresTap() { assertFalse(conversationHeaderMarqueeActive(false,true,true)) }
    @Test fun enablingOptionDoesNotStartUntilTap() { assertFalse(conversationHeaderMarqueeActive(true,false,true)) }
    @Test fun tapStartsOnlyWhenAllConditionsPermit() { assertTrue(conversationHeaderMarqueeActive(true,true,true)) }
    @Test fun systemReduceMotionStopsEvenRequestedMarquee() { assertFalse(conversationHeaderMarqueeActive(true,true,false)) }
}
