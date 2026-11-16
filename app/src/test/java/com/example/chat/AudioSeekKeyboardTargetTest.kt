package com.example.chat

import com.example.chat.ui.media.audioKeyboardSeekTarget
import org.junit.Assert.assertEquals
import org.junit.Test

/** MV-R3-041 pure source cases, NOT RUN. No real seek/player. */
class AudioSeekKeyboardTargetTest {
    @Test fun forwardMovesFiveSeconds() { assertEquals(15_000L, audioKeyboardSeekTarget(10_000L, 60_000L, true)) }
    @Test fun backwardMovesFiveSeconds() { assertEquals(5_000L, audioKeyboardSeekTarget(10_000L, 60_000L, false)) }
    @Test fun forwardClampsAtDuration() { assertEquals(12_000L, audioKeyboardSeekTarget(10_000L, 12_000L, true)) }
    @Test fun backwardClampsAtStart() { assertEquals(0L, audioKeyboardSeekTarget(1_000L, 60_000L, false)) }
    @Test fun zeroAndUnknownDurationReturnZero() { assertEquals(0L, audioKeyboardSeekTarget(10_000L, 0L, true)); assertEquals(0L, audioKeyboardSeekTarget(10_000L, -1L, true)) }
    @Test fun invalidPositionClampsBeforeStep() { assertEquals(5_000L, audioKeyboardSeekTarget(-1L, 60_000L, true)); assertEquals(55_000L, audioKeyboardSeekTarget(80_000L, 60_000L, false)) }
    @Test fun maximumLongCannotOverflow() { assertEquals(Long.MAX_VALUE, audioKeyboardSeekTarget(Long.MAX_VALUE - 2L, Long.MAX_VALUE, true)) }
}
