package com.example.chat

import com.example.chat.ui.chats.detail.formatClipTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** MR-R5-037 / MR-R5-038: table-driven pinning of trim labels. formatClipTime is a pure
 *  function of milliseconds (single clock source), so these cases are deterministic
 *  without any fake clock. */
class MediaReviewTrimFormatTest {

    @Test fun `clip time table`() {
        val cases = mapOf(
            0L to "0:00.0",
            100L to "0:00.1",
            999L to "0:00.9",
            1000L to "0:01.0",
            59999L to "0:59.9",
            60000L to "1:00.0",
            61050L to "1:01.0",
            3599999L to "59:59.9",
            3600000L to "60:00.0",
        )
        for ((ms, expected) in cases) assertEquals("ms=$ms", expected, formatClipTime(ms))
    }

    @Test fun `negative input clamps to zero`() {
        assertEquals("0:00.0", formatClipTime(-5000L))
    }

    @Test fun `output is stable across repeated calls`() {
        assertEquals(formatClipTime(83754L), formatClipTime(83754L))
    }
}
