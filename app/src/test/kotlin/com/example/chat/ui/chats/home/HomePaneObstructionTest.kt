package com.example.chat.ui.chats.home

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure scalar layout decisions only, not rendering or physical fold support. */
class HomePaneObstructionTest {
    @Test fun overlappingInteriorsCannotWinAsSnapCandidates() {
        val hinges = listOf(Rect(.3f, 0f, .6f, 1f), Rect(.4f, 0f, .7f, 1f))
        assertEquals(.7f, clampDivider(.55f, hinges), 0f)
    }

    @Test fun equalDistanceSnapUsesTheLowerBoundary() {
        // Binary-exact fractions make the tie independent of decimal rounding.
        val hinge = Rect(.375f, 0f, .625f, 1f)
        assertEquals(.375f, clampDivider(.5f, listOf(hinge)), 0f)
        assertEquals(.375f, clampDivider(.375f, listOf(hinge)), 0f)
        assertEquals(.625f, clampDivider(.625f, listOf(hinge)), 0f)
    }

    @Test fun malformedAndNonVerticalObstructionsAreIgnored() {
        val invalid = listOf(
            Rect(Float.NaN, 0f, .6f, 1f),
            Rect(.4f, 0f, Float.POSITIVE_INFINITY, 1f),
            Rect(-.1f, 0f, .6f, 1f),
            Rect(.4f, 0f, 1.1f, 1f),
            Rect(.4f, -.1f, .6f, 1f),
            Rect(.4f, 0f, .6f, 1.1f),
            Rect(.6f, 0f, .4f, 1f),
            Rect(.4f, 0f, .4f, 1f),
            Rect(.1f, .4f, .9f, .6f),
            Rect(.4f, .4f, .6f, .6f),
        )
        invalid.forEach { assertEquals(.5f, clampDivider(.5f, listOf(it)), 0f) }
    }

    @Test fun fullyObstructedLayoutFallsBackWithoutHidingSelectedDetail() {
        val hinges = listOf(Rect(.1f, 0f, .9f, 1f))
        val state = HomePaneState(dividerFraction = .5f)
        // clampDivider alone returns the bounded request when no candidate exists.
        assertEquals(.5f, clampDivider(.5f, hinges), 0f)
        val detail = state.layout(1001f, detailOpen = true, hingeBounds = hinges)
        assertFalse(detail.isSplit)
        assertFalse(detail.showList)
        assertTrue(detail.showDetail)
        val list = state.layout(1001f, detailOpen = false, hingeBounds = hinges)
        assertTrue(list.showList)
        assertFalse(list.showDetail)
    }

    @Test fun splitRequiresUsableWidthIncludingDividerAndFitsBothMinimums() {
        val state = HomePaneState(dividerFraction = .2f)
        assertFalse(state.layout(720f, detailOpen = true).isSplit)
        val exact = state.layout(721f, detailOpen = true)
        assertTrue(exact.isSplit)
        assertEquals(.5f, exact.dividerFraction, 0f)
        val wider = state.layout(1001f, detailOpen = true)
        assertTrue(wider.isSplit)
        assertEquals(.36f, wider.dividerFraction, .00001f)
    }

    @Test fun obstructionOutsideFittedRangeForcesSinglePane() {
        // Usable width is 800; both 360 minimums constrain the divider to .45..55.
        val layout = HomePaneState(.5f).layout(
            801f,
            detailOpen = true,
            hingeBounds = listOf(Rect(.4f, 0f, .6f, 1f)),
        )
        assertFalse(layout.isSplit)
        assertTrue(layout.showDetail)
        assertFalse(layout.showList)
    }
}
