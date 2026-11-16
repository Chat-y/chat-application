package com.example.chat

import com.example.chat.ui.chats.detail.trimSnappedRange
import org.junit.Assert.*
import org.junit.Test

/** MR-R3-024 source-only cases, NOT RUN. */
class TrimSnapPolicyTest {
    @Test fun ordinaryEndpointsSnapNearestSecond() { val r=trimSnappedRange(10000,0.123f,0.789f); assertEquals(0.1f,r.start,0.00001f); assertEquals(0.8f,r.endInclusive,0.00001f) }
    @Test fun fullRangeStaysFull() { val r=trimSnappedRange(10000,0f,1f); assertEquals(0f,r.start,0f); assertEquals(1f,r.endInclusive,0f) }
    @Test fun closeEndpointsKeepMinimumGap() { val r=trimSnappedRange(10000,0.49f,0.51f); assertTrue(r.endInclusive-r.start>=0.04999f); assertTrue(r.start>=0f); assertTrue(r.endInclusive<=1f) }
    @Test fun halfSecondEntireClipStaysAvailable() { val r=trimSnappedRange(500,0f,1f); assertEquals(0f,r.start,0f); assertEquals(1f,r.endInclusive,0f) }
    @Test fun unknownDurationLeavesDraftUntouched() { val r=trimSnappedRange(0,0.123f,0.789f); assertEquals(0.123f,r.start,0f); assertEquals(0.789f,r.endInclusive,0f) }
}
