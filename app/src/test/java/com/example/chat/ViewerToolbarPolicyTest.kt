package com.example.chat

import com.example.chat.ui.chats.detail.viewerUsesCompactToolbar
import org.junit.Assert.*
import org.junit.Test

/** MV-R1-049/MV-R5-029 source-only width cases, NOT RUN. */
class ViewerToolbarPolicyTest {
    @Test fun below240UsesBackAndOverflowOnly() { assertTrue(viewerUsesCompactToolbar(239.9f)) }
    @Test fun exactly240KeepsPrimaryActions() { assertFalse(viewerUsesCompactToolbar(240f)) }
    @Test fun narrowMultiWindowUsesCompactLayout() { assertTrue(viewerUsesCompactToolbar(180f)) }
    @Test fun tabletUsesStandardLayout() { assertFalse(viewerUsesCompactToolbar(800f)) }
}
