package com.example.chat.ui.calls

import org.junit.Assert.*
import org.junit.Test

/** Pure source fixtures only, not run. */
class CallQuietHoursTest {
    @Test fun overnightRangeHasExclusiveEnd() {
        val value = CallQuietHours(true, 1320, 420)
        assertTrue(value.contains(1320))
        assertTrue(value.contains(60))
        assertFalse(value.contains(420))
        assertFalse(value.contains(1000))
        assertFalse(value.copy(enabled = false).contains(60))
    }
    @Test fun sameDayRangeAndMalformedDataFailClosed() {
        val value = CallQuietHours(true, 60, 120)
        assertTrue(value.contains(60))
        assertFalse(value.contains(120))
        assertFalse(CallQuietHours.parse("true,60,60").enabled)
        assertFalse(CallQuietHours.parse("true,9999,0").enabled)
        assertEquals(value, CallQuietHours.parse(value.encode()))
    }
}
