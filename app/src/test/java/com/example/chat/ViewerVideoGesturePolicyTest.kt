package com.example.chat

import com.example.chat.ui.chats.detail.*
import org.junit.Assert.*
import org.junit.Test

/** MV-R1-051 source-only cases, NOT RUN. */
class ViewerVideoGesturePolicyTest {
    @Test fun physicalThirdsAndBoundaries() { assertEquals(-1, viewerVideoTapDirection(20f,300f)); assertEquals(0,viewerVideoTapDirection(100f,300f)); assertEquals(0,viewerVideoTapDirection(200f,300f)); assertEquals(1,viewerVideoTapDirection(250f,300f)) }
    @Test fun invalidGeometryDoesNotRequestSeek() { assertEquals(0,viewerVideoTapDirection(Float.NaN,300f)); assertEquals(0,viewerVideoTapDirection(20f,0f)) }
    @Test fun tenSecondTargets() { assertEquals(10000,viewerVideoGestureSeek(20000,60000,-1)); assertEquals(30000,viewerVideoGestureSeek(20000,60000,1)) }
    @Test fun boundedTargetsAndUnknownDuration() { assertEquals(0,viewerVideoGestureSeek(1000,60000,-1)); assertEquals(60000,viewerVideoGestureSeek(59000,60000,1)); assertEquals(0,viewerVideoGestureSeek(1000,0,1)) }
    @Test fun largeTargetsDoNotOverflowOrMultiplyUnboundedDirection() { assertEquals(Int.MAX_VALUE,viewerVideoGestureSeek(Int.MAX_VALUE,Int.MAX_VALUE,10)); assertEquals(10000,viewerVideoGestureSeek(20000,60000,-10)) }
}
