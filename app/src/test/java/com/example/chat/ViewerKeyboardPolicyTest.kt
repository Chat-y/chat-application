package com.example.chat

import com.example.chat.ui.chats.detail.viewerKeyboardSeek
import org.junit.Assert.assertEquals
import org.junit.Test

/** MV-R2-036 source-only cases, NOT RUN. */
class ViewerKeyboardPolicyTest {
    @Test fun forwardFiveSeconds() { assertEquals(15000, viewerKeyboardSeek(10000,60000,true)) }
    @Test fun backwardFiveSeconds() { assertEquals(5000, viewerKeyboardSeek(10000,60000,false)) }
    @Test fun clampStart() { assertEquals(0, viewerKeyboardSeek(1000,60000,false)) }
    @Test fun clampEnd() { assertEquals(60000, viewerKeyboardSeek(59000,60000,true)) }
    @Test fun unknownDurationTargetZero() { assertEquals(0, viewerKeyboardSeek(10000,0,true)) }
    @Test fun largePositionDoesNotOverflow() { assertEquals(Int.MAX_VALUE, viewerKeyboardSeek(Int.MAX_VALUE,Int.MAX_VALUE,true)) }
}
