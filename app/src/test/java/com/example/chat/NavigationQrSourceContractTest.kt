package com.example.chat

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Source guards only. Device pixels, insets and QR scanning still need acceptance. */
class NavigationQrSourceContractTest {
    private fun source(path: String): String = File("src/main/kotlin/com/example/chat/$path").readLines()
        .filterNot { it.trimStart().startsWith("//") }.joinToString("\n")

    @Test fun dockHasSharedBottomUnderlineAndFixedItemWidths() {
        val shell = source("ui/navigation/NavigationShells3.kt")
        assertTrue(shell.contains("navigationUnderlineHeightDp"))
        assertTrue(shell.contains("size.height - h"))
        assertTrue(shell.contains(".weight(1f)"))
        assertFalse(shell.contains(".weight(if (isBubbles && selected)"))
        assertFalse(shell.contains(".nestedScroll(headerScroll)"))
    }

    @Test fun qrCentersFrameAgainstWholeViewportAndChatGlyphInsideFrame() {
        val qr = source("ui/chats/linked_devices/LinkedDevicesQrScreen.kt")
        assertTrue(qr.contains("viewportInsets.calculateBottomPadding()"))
        assertTrue(qr.contains("viewportInsets.calculateTopPadding() - cardHeight) / 2"))
        assertTrue(qr.contains("Box(contentAlignment = Alignment.Center)"))
        assertTrue(qr.contains("ChatyIconId.chats"))
        assertTrue(qr.contains("qrBrandGlyphDp.dp * codeScale"))
    }
}
