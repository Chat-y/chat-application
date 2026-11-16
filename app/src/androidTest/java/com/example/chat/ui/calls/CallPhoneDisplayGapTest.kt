package com.example.chat.ui.calls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Android formatting source tests, NOT RUN; no contact lookup/call/network. */
class CallPhoneDisplayGapTest {
    @Test fun internationalFormattingPreservesDialableDigits() {
        val raw = "+14155552671"
        val display = callPhoneDisplay(raw, "US")
        assertEquals(raw.filter(Char::isDigit), display.filter(Char::isDigit))
        assertTrue(display.startsWith("+"))
    }
    @Test fun emptyAndUnsupportedTextAreSafe() {
        assertEquals("", callPhoneDisplay("  ", "US"))
        assertEquals("not-a-number", callPhoneDisplay(" not-a-number ", "US"))
    }
}
