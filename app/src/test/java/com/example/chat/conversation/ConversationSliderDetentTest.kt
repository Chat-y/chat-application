package com.example.chat.ui.settings.conversation

import org.junit.Assert.*
import org.junit.Test

/** Source-only review cases for haptic detent boundaries. Unrun. */
class ConversationSliderDetentTest {
    @Test fun round1WholeValuesStayOnTheirDetent() { (12..26).forEach { assertEquals(it, conversationTextDetent(it.toFloat())) } }
    @Test fun round2MovementWithinADetentKeepsItsKey() { assertEquals(conversationTextDetent(16.1f), conversationTextDetent(16.4f)) }
    @Test fun round3CrossingABoundaryChangesTheKey() { assertNotEquals(conversationTextDetent(16.4f), conversationTextDetent(16.6f)) }
    @Test fun round4OutsideValuesClampToTheSliderRange() { assertEquals(12, conversationTextDetent(1f)); assertEquals(26, conversationTextDetent(40f)) }
    @Test fun round5InvalidValuesHaveNoHapticKey() { assertNull(conversationTextDetent(Float.NaN)); assertNull(conversationTextDetent(Float.POSITIVE_INFINITY)) }
}
