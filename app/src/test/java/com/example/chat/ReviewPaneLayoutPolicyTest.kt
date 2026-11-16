package com.example.chat

import com.example.chat.ui.chats.detail.reviewUsesTwoPanes
import org.junit.Assert.*
import org.junit.Test

/** MR-R3-037 source policy fixtures, NOT RUN. Not narrow landscape/font/IME/pixel proof. */
class ReviewPaneLayoutPolicyTest {
    @Test fun narrowPortraitKeepsStack() { assertFalse(reviewUsesTwoPanes(599.9f, false)) }
    @Test fun exactBreakpointUsesRail() { assertTrue(reviewUsesTwoPanes(600f, false)) }
    @Test fun widePortraitUsesRail() { assertTrue(reviewUsesTwoPanes(840f, false)) }
    @Test fun narrowLandscapeUsesRail() { assertTrue(reviewUsesTwoPanes(568f, true)) }
    @Test fun wideLandscapeUsesRail() { assertTrue(reviewUsesTwoPanes(900f, true)) }
    @Test fun orientationReturnCanRestoreStack() {
        assertTrue(reviewUsesTwoPanes(568f, true))
        assertFalse(reviewUsesTwoPanes(568f, false))
    }
    @Test fun invalidWidthsDoNotChooseRail() {
        for (landscape in listOf(false, true)) {
            assertFalse(reviewUsesTwoPanes(Float.NaN, landscape))
            assertFalse(reviewUsesTwoPanes(Float.POSITIVE_INFINITY, landscape))
            assertFalse(reviewUsesTwoPanes(0f, landscape))
            assertFalse(reviewUsesTwoPanes(-1f, landscape))
        }
    }
}
