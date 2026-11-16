package com.example.chat

import com.example.chat.ui.chats.detail.editorUsesTwoPanes
import org.junit.Assert.*
import org.junit.Test

/** Source-only cases, NOT RUN. */
class EditorPaneLayoutPolicyTest {
    @Test fun belowBreakpointSinglePane() { assertFalse(editorUsesTwoPanes(839.99f)) }
    @Test fun exactBreakpointUsesRail() { assertTrue(editorUsesTwoPanes(840f)) }
    @Test fun widerUsesRail() { assertTrue(editorUsesTwoPanes(1200f)) }
    @Test fun invalidWidthFallsBack() {
        assertFalse(editorUsesTwoPanes(Float.NaN))
        assertFalse(editorUsesTwoPanes(Float.POSITIVE_INFINITY))
        assertFalse(editorUsesTwoPanes(-1f))
    }
}
