package com.example.chat.ui.chats.detail

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageInteractionAnchorPolicyTest {

    @Test
    fun computePlacement_sufficientHeadroom_placesRailAboveAndMenuBelow() {
        // Viewport: 400dp x 800dp. Density = 2.0 (so 1dp = 2px)
        // Bubble at y = 300dp..360dp (px: 600..720)
        val bubble = Rect(left = 100f, top = 600f, right = 600f, bottom = 720f)
        val placement = MessageInteractionAnchorPolicy.computePlacement(
            bubbleBounds = bubble,
            viewportWidthDp = 400.dp,
            viewportHeightDp = 800.dp,
            density = 2.0f,
            isOutgoing = true,
        )

        // bubbleTopDp = 300.dp. Space above = 300 - 8 = 292dp >= 48 + 8 = 56dp.
        // Rail should be above: 300 - 48 - 8 = 244.dp
        assertEquals(244.dp, placement.reactionRailTopDp)
        // Menu should be below bubble: 360 + 8 = 368.dp
        assertEquals(368.dp, placement.menuTopDp)
        assertTrue(placement.menuMaxHeightDp > 200.dp)
    }

    @Test
    fun computePlacement_tightHeadroom_placesRailAndMenuBelow() {
        // Viewport: 400dp x 800dp. Bubble near top: top = 20dp (px: 40), bottom = 80dp (px: 160)
        val bubble = Rect(left = 100f, top = 40f, right = 600f, bottom = 160f)
        val placement = MessageInteractionAnchorPolicy.computePlacement(
            bubbleBounds = bubble,
            viewportWidthDp = 400.dp,
            viewportHeightDp = 800.dp,
            density = 2.0f,
            isOutgoing = false,
        )

        // Space above = 20 - 8 = 12dp < 56dp. Both rail and menu should be placed below bubble.
        // Rail at bottom of bubble + 8dp = 80 + 8 = 88.dp
        assertEquals(88.dp, placement.reactionRailTopDp)
        // Menu at railBottom + 8dp = 88 + 48 + 8 = 144.dp
        assertEquals(144.dp, placement.menuTopDp)
    }

    @Test
    fun computePlacement_clamping_respectsViewportBoundaries() {
        val bubble = Rect(left = 0f, top = 0f, right = 400f, bottom = 200f)
        val placement = MessageInteractionAnchorPolicy.computePlacement(
            bubbleBounds = bubble,
            viewportWidthDp = 320.dp,
            viewportHeightDp = 400.dp,
            density = 1.0f,
            isOutgoing = true,
        )

        assertTrue(placement.reactionRailLeftDp >= 8.dp)
        assertTrue(placement.menuLeftDp >= 8.dp)
        assertTrue(placement.menuMaxHeightDp >= 100.dp)
    }

    // Authored overnight fixtures only. Not compiled/run under the source-only restriction.
    @Test fun tinyViewportNeverGetsAnUnboundedMinimumOrWidth() {
        for ((width, height) in listOf(220f to 160f, 100f to 60f, 8f to 8f, 0f to 0f)) {
            for (outgoing in listOf(false, true)) {
                val p = MessageInteractionAnchorPolicy.computePlacement(
                    Rect(-500f, -500f, 1000f, 1000f), width.dp, height.dp, 1f, outgoing)
                assertTrue(p.menuTopDp.value >= 0f)
                assertTrue(p.menuLeftDp.value >= 0f)
                assertTrue(p.menuMaxHeightDp.value >= 0f)
                assertTrue(p.menuMaxWidthDp.value >= 0f)
                assertTrue(p.menuTopDp.value + p.menuMaxHeightDp.value <= height)
                assertTrue(p.menuLeftDp.value + p.menuMaxWidthDp.value <= width)
                assertTrue(p.reactionRailTopDp.value + p.reactionRailMaxHeightDp.value <= height)
                assertTrue(p.reactionRailLeftDp.value + p.reactionRailMaxWidthDp.value <= width)
            }
        }
    }

    @Test fun invalidDensityOrAnchorUsesBoundedFallback() {
        for (density in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            val p = MessageInteractionAnchorPolicy.computePlacement(
                Rect(Float.NaN, 0f, 10f, 20f), 220.dp, 160.dp, density, false)
            assertTrue(p.menuTopDp.value.isFinite())
            assertTrue(p.menuMaxHeightDp.value.isFinite())
            assertTrue(p.menuTopDp.value + p.menuMaxHeightDp.value <= 160f)
            assertTrue(p.menuLeftDp.value + p.menuMaxWidthDp.value <= 220f)
        }
    }

    @Test fun invalidViewportCannotInventScreenSpace() {
        val p = MessageInteractionAnchorPolicy.computePlacement(null,
            Float.NaN.dp, (-10).dp, Float.NaN, false)
        assertEquals(0.dp, p.menuMaxWidthDp)
        assertEquals(0.dp, p.menuMaxHeightDp)
    }

    @Test fun lastBubbleKeepsUsefulScrollableActionViewport() {
        val p = MessageInteractionAnchorPolicy.computePlacement(
            Rect(150f, 650f, 390f, 760f), 400.dp, 800.dp, 1f, true, railHeightDp = 56.dp)
        assertTrue(p.menuMaxHeightDp >= 416.dp)
        assertTrue(p.menuTopDp + p.menuMaxHeightDp < p.reactionRailTopDp)
        assertTrue(p.menuTopDp + p.menuMaxHeightDp <= 800.dp)
    }

    @Test fun shortImeAndLandscapeNeverOverlapReactionRail() {
        for (height in listOf(60f, 160f, 240f, 400f, 800f)) {
            for (top in listOf(0f, 20f, 150f, 650f)) {
                val p = MessageInteractionAnchorPolicy.computePlacement(
                    Rect(100f, top, 300f, top + 50f), 400.dp, height.dp, 1f, true, railHeightDp = 56.dp)
                val menuEnd = p.menuTopDp + p.menuMaxHeightDp
                val railEnd = p.reactionRailTopDp + p.reactionRailMaxHeightDp
                assertTrue(p.menuMaxHeightDp.value == 0f || menuEnd <= p.reactionRailTopDp || p.menuTopDp >= railEnd)
                assertTrue(menuEnd.value <= height)
            }
        }
    }
}
