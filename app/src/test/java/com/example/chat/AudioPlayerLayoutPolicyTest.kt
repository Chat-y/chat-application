package com.example.chat

import com.example.chat.ui.media.audioDiscSizeDp
import com.example.chat.ui.media.audioDiscRotationMillis
import org.junit.Assert.assertEquals
import org.junit.Test

/** Source-only cases, NOT RUN. */
class AudioPlayerLayoutPolicyTest {
    @Test fun minimumCoverUses180Dp() { assertEquals(180, audioDiscSizeDp(280, 640)) }
    @Test fun lastNarrowWidthUses180Dp() { assertEquals(180, audioDiscSizeDp(339, 640)) }
    @Test fun breakpointWidthKeeps240Dp() { assertEquals(240, audioDiscSizeDp(340, 640)) }
    @Test fun shortLandscapeKeepsExisting144Dp() { assertEquals(144, audioDiscSizeDp(840, 400)) }
    @Test fun shortNarrowKeepsExisting144Dp() { assertEquals(144, audioDiscSizeDp(280, 599)) }
    @Test fun height600UsesWidthRule() { assertEquals(180, audioDiscSizeDp(280, 600)) }
    @Test fun normalRateKeepsFourteenSeconds() { assertEquals(14000, audioDiscRotationMillis(1.0)) }
    @Test fun slowerRateTakesLonger() { assertEquals(18666, audioDiscRotationMillis(0.75)) }
    @Test fun quarterFasterRate() { assertEquals(11200, audioDiscRotationMillis(1.25)) }
    @Test fun halfFasterRate() { assertEquals(9333, audioDiscRotationMillis(1.5)) }
    @Test fun doubleRateTakesSevenSeconds() { assertEquals(7000, audioDiscRotationMillis(2.0)) }
    @Test fun zeroRateFallsBack() { assertEquals(14000, audioDiscRotationMillis(0.0)) }
    @Test fun negativeRateFallsBack() { assertEquals(14000, audioDiscRotationMillis(-1.0)) }
    @Test fun invalidRateFallsBack() {
        assertEquals(14000, audioDiscRotationMillis(Double.NaN))
        assertEquals(14000, audioDiscRotationMillis(Double.POSITIVE_INFINITY))
    }
}
