package com.example.chat

import com.example.chat.ui.camera.*
import org.junit.Assert.*
import org.junit.Test

/** CAM-R4-034 helper cases only, NOT RUN. Not Android keyboard/TalkBack/pixels proof. */
class PaperCornerAccessibilityStepTest {
    @Test fun movesOnlyRequestedCornerByOnePercent() {
        for (i in 0..3) {
            val moved = stepPaperCorner(PaperQuad.inset, i, 1, 0)
            for (j in 0..3) {
                assertEquals(PaperQuad.inset.points[j].x + if (i == j) .01f else 0f, moved.points[j].x, .00001f)
                assertEquals(PaperQuad.inset.points[j].y, moved.points[j].y, .00001f)
            }
        }
    }
    @Test fun movementClampsInsideImage() {
        val upper = PaperQuad.inset.moved(0, PaperPoint(0f, 0f))
        assertEquals(PaperPoint(0f, 0f), stepPaperCorner(upper, 0, -1, -1).topLeft)
        val lower = PaperQuad.inset.moved(2, PaperPoint(1f, 1f))
        assertEquals(PaperPoint(1f, 1f), stepPaperCorner(lower, 2, 1, 1).bottomRight)
    }
    @Test fun oppositeStepsRestorePositionWithinFloatPrecision() {
        val moved = stepPaperCorner(stepPaperCorner(PaperQuad.inset, 1, 0, 1), 1, 0, -1)
        assertEquals(PaperQuad.inset.topRight.y, moved.topRight.y, .00001f)
    }
    @Test fun zeroStepPreservesAllPoints() { assertEquals(PaperQuad.inset, stepPaperCorner(PaperQuad.inset, 0, 0, 0)) }
    @Test(expected = IllegalArgumentException::class) fun rejectsUnknownCorner() { stepPaperCorner(PaperQuad.inset, 4, 0, 0) }
    @Test(expected = IllegalArgumentException::class) fun rejectsUnexpectedStride() { stepPaperCorner(PaperQuad.inset, 0, 2, 0) }
}
