package com.example.chat

import com.example.chat.ui.chats.detail.viewerUsesActionRail
import org.junit.Assert.*
import org.junit.Test

/** Source-only cases, NOT RUN. */
class ViewerRailLayoutPolicyTest {
    @Test fun justBelow600KeepsTopBarActions() { assertFalse(viewerUsesActionRail(599.99f)) }
    @Test fun exactly600UsesRail() { assertTrue(viewerUsesActionRail(600f)) }
    @Test fun wideUsesRail() { assertTrue(viewerUsesActionRail(1000f)) }
    @Test fun invalidUsesCompactPath() {
        assertFalse(viewerUsesActionRail(Float.NaN))
        assertFalse(viewerUsesActionRail(Float.POSITIVE_INFINITY))
        assertFalse(viewerUsesActionRail(-1f))
    }
}
