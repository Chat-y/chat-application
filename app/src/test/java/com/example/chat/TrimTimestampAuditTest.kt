package com.example.chat.ui.chats.detail

import org.junit.Assert.assertEquals
import org.junit.Test

/** Source-only regression fixtures. Not run. */
class TrimTimestampAuditTest {
    @Test fun zeroStaysZero() { assertEquals(0L, trimTimestampUs(0)) }
    @Test fun ordinaryClipKeepsExactMicroseconds() { assertEquals(1234000L, trimTimestampUs(1234)) }
    @Test fun largestExactValueDoesNotOverflow() {
        val boundary = Long.MAX_VALUE / 1000L
        assertEquals(boundary * 1000L, trimTimestampUs(boundary))
    }
    @Test fun muteWholeClipSentinelStaysPositive() { assertEquals(Long.MAX_VALUE, trimTimestampUs(Long.MAX_VALUE / 4)) }
    @Test fun beyondBoundarySaturates() { assertEquals(Long.MAX_VALUE, trimTimestampUs(Long.MAX_VALUE / 1000L + 1)) }
    @Test(expected = IllegalArgumentException::class) fun negativeTimestampIsRejected() { trimTimestampUs(-1) }
}
