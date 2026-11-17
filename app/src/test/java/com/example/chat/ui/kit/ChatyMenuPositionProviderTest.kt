package com.example.chat.ui.kit

import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatyMenuPositionProviderTest {
    private val position = ChatyMenuPositionProvider(8)

    @Test fun opensBelowAnchorWhenThereIsRoom() {
        val p = position.calculatePosition(IntRect(40, 80, 120, 120), IntSize(400, 800), LayoutDirection.Ltr, IntSize(200, 250))
        assertEquals(40, p.x)
        assertEquals(128, p.y)
    }

    @Test fun flipsAboveNearBottomAndClampsHorizontalEdge() {
        val p = position.calculatePosition(IntRect(350, 650, 390, 700), IntSize(400, 800), LayoutDirection.Ltr, IntSize(200, 250))
        assertEquals(192, p.x)
        assertEquals(392, p.y)
    }

    @Test fun rtlAlignsToAnchorEndAndSmallWindowCannotCrash() {
        val p = position.calculatePosition(IntRect(120, 80, 200, 120), IntSize(400, 800), LayoutDirection.Rtl, IntSize(176, 250))
        assertEquals(24, p.x)
        val tiny = position.calculatePosition(IntRect(0, 0, 10, 10), IntSize(100, 100), LayoutDirection.Ltr, IntSize(200, 250))
        assertTrue(tiny.x >= 8 && tiny.y >= 8)
    }
    @Test fun bottomQuickActionsUseAboveSpaceInsteadOfCollapsingToBelowSliver() {
        var available = -1
        val provider = ChatyMenuPositionProvider(8, preferBelow = false,
            onAvailableHeight = { available = it })
        val result = provider.calculatePosition(IntRect(240, 720, 390, 776),
            IntSize(400, 800), LayoutDirection.Ltr, IntSize(240, 360))
        assertEquals(352, result.y)
        assertEquals(704, available)
        assertTrue(result.y + 360 < 720)
    }

    @Test fun quickActionsRetainRtlAnchorAlignmentAndScreenBounds() {
        val result = position.calculatePosition(IntRect(8, 720, 168, 776),
            IntSize(400, 800), LayoutDirection.Rtl, IntSize(240, 360))
        assertEquals(8, result.x)
        assertEquals(352, result.y)
    }

    @Test fun composerBottomAnchorHasUsableHeightAndStaysAboveAfterMeasurement() {
        var available = -1
        val provider = ChatyMenuPositionProvider(8, preferBelow = false, onAvailableHeight = { available = it })
        val anchor = IntRect(340, 744, 388, 792)
        val window = IntSize(400, 800)
        val first = provider.calculatePosition(anchor, window, LayoutDirection.Ltr, IntSize(220, 200))
        assertEquals(536, first.y)
        assertEquals(728, available)
        val resized = provider.calculatePosition(anchor, window, LayoutDirection.Ltr, IntSize(220, 120))
        assertEquals(616, resized.y)
        assertEquals(728, available)
        assertTrue(resized.y + 120 < anchor.top)
    }

    @Test fun allActiveComposerStylesShareThePatchedSendOptionsMenu() {
        val root = if (java.io.File("app/src/main").exists()) java.io.File("app/src/main") else java.io.File("src/main")
        val source = java.io.File(root, "kotlin/com/example/chat/ui/chats/detail/ChatComposer.kt").readText()
        // The rest of this file is the preserved pre-review rollback block, not another renderer.
        val active = source.substringBefore("/* Pre-review source retained unchanged.")
            .lineSequence().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")
        val invocation = "com.example.chat.ui.kit.ChatyDropdownMenu(expanded = sendOptionsOpen,"
        assertEquals(1, active.windowed(invocation.length).count { it == invocation })
        val sendMenu = active.substringAfter(invocation).substringBefore("\n                }")
        assertTrue(sendMenu.contains("preferBelow = false"))
        assertTrue(sendMenu.contains("onSendViewOnce()"))
        assertTrue(sendMenu.contains("onScheduleSend()"))
        assertTrue(active.substringAfter("fun sendMic()").contains("onLongClick = { sendOptionsOpen = true }"))
    }
}
