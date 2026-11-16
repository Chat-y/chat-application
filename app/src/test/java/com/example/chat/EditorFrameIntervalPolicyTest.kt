package com.example.chat

import com.example.chat.ui.chats.detail.editorFrameIntervalMillis
import org.junit.Assert.*
import org.junit.Test

/** Source-only cases, NOT RUN. No Android timing claims. */
class EditorFrameIntervalPolicyTest {
    @Test fun firstFrameHasNoInterval() { assertNull(editorFrameIntervalMillis(null, 100)) }
    @Test fun duplicateFrameIgnored() { assertNull(editorFrameIntervalMillis(100, 100)) }
    @Test fun backwardFrameIgnored() { assertNull(editorFrameIntervalMillis(200, 100)) }
    @Test fun sixtyHzLikeIntervalKeepsMilliseconds() { assertEquals(16.666667, editorFrameIntervalMillis(0, 16666667)!!, 0.000001) }
    @Test fun oneTwentyHzLikeIntervalKeepsMilliseconds() { assertEquals(8.333333, editorFrameIntervalMillis(0, 8333333)!!, 0.000001) }
    @Test fun largeElapsedIntervalIsNotCalledCompositionTime() { assertEquals(1000.0, editorFrameIntervalMillis(0, 1000000000)!!, 0.0) }
}
