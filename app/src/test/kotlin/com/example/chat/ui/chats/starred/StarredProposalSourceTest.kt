package com.example.chat.ui.chats.starred

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Source checks only. Not substitutes for Compose semantics or device tests. */
class StarredProposalSourceTest {
    private fun source(): String = File("src/main/kotlin/com/example/chat/ui/chats/starred/StarredMessagesScreen.kt").readText()
    @Test fun focusRingUsesThemeAndRowFocus() {
        val s = source()
        assertTrue(s.contains(".onFocusChanged { rowFocused = it.hasFocus }"))
        assertTrue(s.contains("if (rowFocused) colors.primary"))
        assertTrue(s.contains("if (rowFocused) 2.dp else 1.dp"))
    }
    @Test fun rowSummaryIsLocalizedAndMenuActionsRemain() {
        val s = source()
        assertTrue(s.contains("social_starred_row_summary, conversation.title, preview, dateLabel"))
        assertTrue(s.contains(".semantics(mergeDescendants = true)"))
        assertTrue(s.contains("CustomAccessibilityAction(openLabel)"))
        assertTrue(s.contains("CustomAccessibilityAction(unstarLabel)"))
    }
    @Test fun menuHitAreaReachesTrailingCardEdge() {
        assertTrue(source().contains(".padding(start = 14.dp, top = 7.dp, end = 0.dp, bottom = 7.dp)"))
    }
}
