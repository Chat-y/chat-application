package com.example.chat

import com.example.chat.ui.media.audioUseLandscapePanes
import com.example.chat.ui.media.audioLandscapeDiscSizeDp
import org.junit.Assert.*
import org.junit.Test

/** Source-only cases, NOT RUN. */
class AudioPlayerLandscapePolicyTest {
    @Test fun landscapeWidePaneUsesTwoPanes() { assertTrue(audioUseLandscapePanes(true, 800f, 320f)) }
    @Test fun portraitNeverForcesTwoPanes() { assertFalse(audioUseLandscapePanes(false, 800f, 320f)) }
    @Test fun tallSplitWindowFallsBack() { assertFalse(audioUseLandscapePanes(true, 320f, 600f)) }
    @Test fun squareFallsBack() { assertFalse(audioUseLandscapePanes(true, 320f, 320f)) }
    @Test fun invalidPaneFallsBack() {
        assertFalse(audioUseLandscapePanes(true, Float.POSITIVE_INFINITY, 320f))
        assertFalse(audioUseLandscapePanes(true, 320f, 0f))
    }
    @Test fun discKeepsPreferredWhenItFits() { assertEquals(144f, audioLandscapeDiscSizeDp(320f, 240f, 144f), 0f) }
    @Test fun discShrinksToPaneWidth() { assertEquals(128f, audioLandscapeDiscSizeDp(160f, 240f, 240f), 0f) }
    @Test fun discShrinksToPaneHeight() { assertEquals(88f, audioLandscapeDiscSizeDp(320f, 120f, 240f), 0f) }
    @Test fun zeroSpaceCannotProduceNegativeSize() { assertEquals(0f, audioLandscapeDiscSizeDp(20f, 10f, 144f), 0f) }
}
