package com.example.chat.ui.settings.conversation

import org.junit.Assert.*
import org.junit.Test

/** Boundary policy source cases; device/layout acceptance remains unverified. Unrun. */
class ConversationAdaptiveSettingsBodyTest {
    @Test fun round1PhoneUsesOnePane() { assertFalse(conversationUsesTwoPanes(360f)) }
    @Test fun round2JustBelowBreakpointUsesOnePane() { assertFalse(conversationUsesTwoPanes(839.99f)) }
    @Test fun round3ExactBreakpointUsesTwoPanes() { assertTrue(conversationUsesTwoPanes(840f)) }
    @Test fun round4WideWindowUsesTwoPanes() { assertTrue(conversationUsesTwoPanes(1200f)) }
    @Test fun round5InvalidWidthNeverUsesTwoPanes() { assertFalse(conversationUsesTwoPanes(Float.NaN)); assertFalse(conversationUsesTwoPanes(Float.POSITIVE_INFINITY)) }
}
