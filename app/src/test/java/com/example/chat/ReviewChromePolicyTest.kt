package com.example.chat

import com.example.chat.ui.chats.detail.reviewShouldAutoHideChrome
import org.junit.Assert.*
import org.junit.Test

/** Source-only cases, NOT RUN. */
class ReviewChromePolicyTest {
    @Test fun playingVideoCanHide() { assertTrue(reviewShouldAutoHideChrome(true, true, true, false)) }
    @Test fun pausedVideoStaysVisible() { assertFalse(reviewShouldAutoHideChrome(true, false, true, false)) }
    @Test fun imageNeverHides() { assertFalse(reviewShouldAutoHideChrome(false, true, true, false)) }
    @Test fun reducedMotionKeepsChrome() { assertFalse(reviewShouldAutoHideChrome(true, true, true, true)) }
    @Test fun alreadyHiddenDoesNotStartAnotherTimer() { assertFalse(reviewShouldAutoHideChrome(true, true, false, false)) }
}
