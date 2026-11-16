package com.example.chat

import com.example.chat.ui.chats.detail.clipLabelMillis
import com.example.chat.ui.chats.detail.formatClipTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** MR-R5-037 / MR-R5-038: table-driven pinning of trim labels. formatClipTime is a pure
 *  function of milliseconds (single clock source), so these cases are deterministic
 *  without any fake clock. */
class MediaReviewTrimFormatTest {

    @Test fun `clip time table`() {
        val cases = mapOf(
            // MR-R4-019 prior tenths expectation: 0L to "0:00.0",
            0L to "0:00.00",
            // MR-R4-019 prior tenths expectation: 100L to "0:00.1",
            100L to "0:00.10",
            // MR-R4-019 prior tenths expectation: 999L to "0:00.9",
            999L to "0:00.99",
            // MR-R4-019 prior tenths expectation: 1000L to "0:01.0",
            1000L to "0:01.00",
            // MR-R4-019 prior tenths expectation: 59999L to "0:59.9",
            59999L to "0:59.99",
            // MR-R5-038 explicit requested clock boundaries, NOT RUN.
            59900L to "0:59.90",
            60100L to "1:00.10",
            // MR-R4-019 prior tenths expectation: 60000L to "1:00.0",
            60000L to "1:00.00",
            // MR-R4-019 prior tenths expectation: 61050L to "1:01.0",
            61050L to "1:01.05",
            // MR-R4-019 prior tenths expectation: 3599999L to "59:59.9",
            3599999L to "59:59.99",
            // MR-R4-019 prior tenths expectation: 3600000L to "60:00.0",
            3600000L to "60:00.00",
        )
        for ((ms, expected) in cases) assertEquals("ms=$ms", expected, formatClipTime(ms))
    }

    @Test fun `negative input clamps to zero`() {
        // MR-R4-019 prior tenths expectation: assertEquals("0:00.0", formatClipTime(-5000L))
        assertEquals("0:00.00", formatClipTime(-5000L))
    }

    @Test fun `output is stable across repeated calls`() {
        assertEquals(formatClipTime(83754L), formatClipTime(83754L))
    }
    // MR-R4-019 new centisecond and display-rounding cases, NOT RUN.
    @Test fun `centiseconds retain two places`() {
        assertEquals("0:00.01", formatClipTime(10L))
        assertEquals("0:12.34", formatClipTime(12349L))
        assertEquals("1:00.00", formatClipTime(60000L))
    }
    @Test fun `display boundary rounds whole milliseconds`() {
        assertEquals(126L, clipLabelMillis(1004L, 0.125f))
        assertEquals(0L, clipLabelMillis(1004L, 0f))
        assertEquals(1004L, clipLabelMillis(1004L, 1f))
    }
}
