package com.example.chat.ui.chats.detail

import org.junit.Assert.*
import org.junit.Test

/** Source policy cases only. No call execution or rendered-layout acceptance. Unrun. */
class ConversationHeaderLayoutOptionsTest {
    @Test fun callOverflowDefaultsOff() { assertFalse(ConversationHeaderAppearance.fromMap(emptyMap()).callsInOverflow) }
    @Test fun visibleCallsMoveOnlyAfterOptIn() { assertTrue(conversationHeaderCallsInOverflow(true,true)) }
    @Test fun defaultKeepsCallsOutsideMenu() { assertFalse(conversationHeaderCallsInOverflow(true,false)) }
    @Test fun hiddenCallsAreNotResurrectedByMenuOption() { assertFalse(conversationHeaderCallsInOverflow(false,true)) }
    @Test fun malformedMenuOptionDoesNotOptIn() { assertFalse(ConversationHeaderAppearance.fromMap(mapOf(ConversationHeaderAppearance.CALLS_IN_OVERFLOW to "true")).callsInOverflow) }
    @Test fun compactDefaultsOff() { assertFalse(ConversationHeaderAppearance.fromMap(emptyMap()).compactLandscape) }
    @Test fun portraitAlwaysKeepsEightDpPadding() {
        assertEquals(8,conversationHeaderVerticalPadding(false,false)); assertEquals(8,conversationHeaderVerticalPadding(true,false))
    }
    @Test fun defaultLandscapeKeepsEightDpPadding() { assertEquals(8,conversationHeaderVerticalPadding(false,true)) }
    @Test fun optInLandscapeUsesZeroPaddingNotFixedHeight() { assertEquals(0,conversationHeaderVerticalPadding(true,true)) }
    @Test fun malformedCompactOptionDoesNotOptIn() { assertFalse(ConversationHeaderAppearance.fromMap(mapOf(ConversationHeaderAppearance.COMPACT_LANDSCAPE to 1)).compactLandscape) }
}
