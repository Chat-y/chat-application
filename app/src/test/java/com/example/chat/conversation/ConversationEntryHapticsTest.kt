package com.example.chat.ui.settings.conversation

import org.junit.Assert.*
import org.junit.Test

/** Opt-in decision source cases; no device haptic execution. Unrun. */
class ConversationEntryHapticsTest {
    @Test fun defaultOffNeverBuzzes() { assertFalse(shouldPlayEntrySelectionHaptic(false, "stock", "simple")) }
    @Test fun optedInChangeBuzzes() { assertTrue(shouldPlayEntrySelectionHaptic(true, "stock", "simple")) }
    @Test fun repeatedStyleDoesNotBuzz() { assertFalse(shouldPlayEntrySelectionHaptic(true, "simple", "simple")) }
    @Test fun unknownStyleDoesNotBuzz() { assertFalse(shouldPlayEntrySelectionHaptic(true, "stock", "unknown")) }
    @Test fun restoringClassicCanBuzzWhenOptedIn() { assertTrue(shouldPlayEntrySelectionHaptic(true, "simple", "stock")) }
}
