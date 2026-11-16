package com.example.chat.ui.chats.starred

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Authored-only UI source checks, not device semantics acceptance. */
class GlobalSearchProposalSourceTest {
    private fun source() = File("src/main/kotlin/com/example/chat/ui/search/GlobalSearchScreen.kt").readText()
    @Test fun emptyPromptHasHeadingAndMergedReading() {
        val s = source()
        assertTrue(s.contains("padding(40.dp).semantics(mergeDescendants = true)"))
        assertTrue(s.contains("social_search_discovery"))
        assertTrue(s.contains("Modifier.semantics { heading() }"))
    }
    @Test fun bothResultKindsHaveCustomActions() {
        val s = source()
        assertTrue(s.contains("CustomAccessibilityAction(messageLabel)"))
        assertTrue(s.contains("CustomAccessibilityAction(openLabel)"))
    }
    @Test fun localizedCountsAndSafeTargetsRemain() {
        val s = source()
        assertTrue(s.contains("social_search_people_count"))
        assertTrue(s.contains("social_search_conversation_count"))
        assertTrue(s.contains(".heightIn(min = 48.dp)"))
        assertTrue(s.contains(".imePadding().navigationBarsPadding()"))
    }
}
