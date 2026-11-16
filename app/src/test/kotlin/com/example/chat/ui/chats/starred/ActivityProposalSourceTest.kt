package com.example.chat.ui.chats.starred

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Source-only guards, no Android acceptance claim. */
class ActivityProposalSourceTest {
    private fun source() = File("src/main/kotlin/com/example/chat/ui/notifications/NotificationsInboxScreen.kt").readText()
    @Test fun boundsHeaderTargetsExplicitly() {
        assertTrue(source().contains("IconButton(modifier = Modifier.size(48.dp), onClick = onBack)"))

    }
    @Test fun summaryHasTwoLineLimitAndExpansion() {
        assertTrue(source().contains("maxLines = if (expanded) Int.MAX_VALUE else 2"))
        assertTrue(source().contains("social_activity_read_full"))
    }
    @Test fun groupedItemsHaveSeparateTypesAndLocalizedCounter() {
        val s = source()
        assertTrue(s.contains("contentType ="))
        assertTrue(s.contains("R.plurals.social_activity_session_count"))
        assertTrue(s.contains("stickyHeader(key ="))
    }
}
