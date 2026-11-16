package com.example.chat

import com.example.chat.ui.chats.detail.viewerDecodeExtent
import com.example.chat.ui.chats.detail.viewerZoomPercent
import com.example.chat.ui.chats.detail.viewerDoubleTapTransform
import com.example.chat.ui.chats.detail.viewerZoomTransform
import com.example.chat.ui.chats.detail.ViewerZoomTransform
import org.junit.Assert.assertEquals
import org.junit.Test

/** MV-R5-038: eight numeric cases, source-only, NOT RUN. */
class ViewerZoomPolicyTest {
    private fun check(expected: ViewerZoomTransform, actual: ViewerZoomTransform) {
        assertEquals(expected.scale, actual.scale, 0.0001f)
        assertEquals(expected.x, actual.x, 0.0001f)
        assertEquals(expected.y, actual.y, 0.0001f)
    }
    @Test fun identity() { check(ViewerZoomTransform(1f, 0f, 0f), viewerZoomTransform(1f,0f,0f,50f,40f,0f,0f,1f,100f,80f)) }
    @Test fun centeredDouble() { check(ViewerZoomTransform(2f, -50f, -40f), viewerZoomTransform(1f,0f,0f,50f,40f,0f,0f,2f,100f,80f)) }
    @Test fun offCenterDouble() { check(ViewerZoomTransform(2f, -25f, -60f), viewerZoomTransform(1f,0f,0f,25f,60f,0f,0f,2f,100f,80f)) }
    @Test fun repeatedCentroidFixed() { check(ViewerZoomTransform(4f, -150f, -120f), viewerZoomTransform(2f,-50f,-40f,50f,40f,0f,0f,2f,100f,80f)) }
    @Test fun positivePanClampsAtOrigin() { check(ViewerZoomTransform(2f, 0f, 0f), viewerZoomTransform(2f,-50f,-40f,50f,40f,200f,200f,1f,100f,80f)) }
    @Test fun negativePanClampsAtFarEdge() { check(ViewerZoomTransform(2f, -100f, -80f), viewerZoomTransform(2f,-50f,-40f,50f,40f,-200f,-200f,1f,100f,80f)) }
    @Test fun minimumScaleRestoresOrigin() { check(ViewerZoomTransform(1f, 0f, 0f), viewerZoomTransform(2f,-50f,-40f,50f,40f,0f,0f,0.1f,100f,80f)) }
    @Test fun maximumScaleUsesClampedFactor() { check(ViewerZoomTransform(5f, -200f, -160f), viewerZoomTransform(1f,0f,0f,50f,40f,0f,0f,100f,100f,80f)) }
    // MV-R1-011 additional source-only cases, NOT RUN.
    @Test fun doubleTapCenterZoomsToTwoPointFive() { check(ViewerZoomTransform(2.5f,-75f,-60f), viewerDoubleTapTransform(1f,0f,0f,50f,40f,100f,80f)) }
    @Test fun doubleTapOffCenterAnchors() { check(ViewerZoomTransform(2.5f,-30f,-90f), viewerDoubleTapTransform(1f,0f,0f,20f,60f,100f,80f)) }
    @Test fun doubleTapFromZoomResets() { check(ViewerZoomTransform(1f,0f,0f), viewerDoubleTapTransform(2.5f,-75f,-60f,20f,20f,100f,80f)) }
    @Test fun doubleTapEdgeStaysClamped() { check(ViewerZoomTransform(2.5f,-150f,-120f), viewerDoubleTapTransform(1f,0f,0f,100f,80f,100f,80f)) }
    // MV-R1-042 label cases, NOT RUN.
    @Test fun fitZoomLabel() { assertEquals(100, viewerZoomPercent(1f)) }
    @Test fun doubleTapZoomLabel() { assertEquals(250, viewerZoomPercent(2.5f)) }
    @Test fun maximumZoomLabel() { assertEquals(500, viewerZoomPercent(5f)) }
    @Test fun settledZoomRounds() { assertEquals(200, viewerZoomPercent(1.996f)) }
    // MV-R2-025 decode policy cases, NOT RUN.
    @Test fun decodeUsesTwiceDisplayPixels() { assertEquals(2160, viewerDecodeExtent(1080)) }
    @Test fun decodePortraitHeight() { assertEquals(3840, viewerDecodeExtent(1920)) }
    @Test fun decodeZeroBoundsSafe() { assertEquals(2, viewerDecodeExtent(0)) }
    @Test fun decodeNegativeBoundsSafe() { assertEquals(2, viewerDecodeExtent(-1)) }
    @Test fun decodeExtentDoesNotOverflow() { assertEquals(Int.MAX_VALUE, viewerDecodeExtent(Int.MAX_VALUE)) }
}
