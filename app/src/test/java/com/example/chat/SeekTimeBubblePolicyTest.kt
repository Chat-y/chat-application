package com.example.chat

import com.example.chat.ui.media.seekBubbleWindowOffset
import org.junit.Assert.assertEquals
import org.junit.Test

/** Source-only cases, NOT RUN. */
class SeekTimeBubblePolicyTest {
    @Test fun centeredAboveMeasuredThumb() { assertEquals(82 to 96, seekBubbleWindowOffset(100, 150, 4, 400, 800, 40, 30, 24)) }
    @Test fun startClampsWithinWindow() { assertEquals(0 to 96, seekBubbleWindowOffset(0, 150, 4, 400, 800, 40, 30, 24)) }
    @Test fun endClampsWithinWindow() { assertEquals(360 to 96, seekBubbleWindowOffset(398, 150, 4, 400, 800, 40, 30, 24)) }
    @Test fun topClampsWhenGapCannotFit() { assertEquals(82 to 0, seekBubbleWindowOffset(100, 20, 4, 400, 800, 40, 30, 24)) }
    @Test fun shortWindowClampsBottom() { assertEquals(82 to 50, seekBubbleWindowOffset(100, 500, 4, 400, 80, 40, 30, 24)) }
    @Test fun oversizedBubbleUsesOrigin() { assertEquals(0 to 0, seekBubbleWindowOffset(100, 150, 4, 20, 10, 40, 30, 24)) }
    @Test fun largeCoordinatesDoNotOverflow() { assertEquals(360 to 770, seekBubbleWindowOffset(Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, 400, 800, 40, 30, 24)) }
}
