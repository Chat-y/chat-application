package com.example.chat

import com.example.chat.ui.chats.detail.reviewDraftScopeKey
import org.junit.Assert.*
import org.junit.Test

class ReviewDraftScopeTest {
    @Test fun sameIdentityRouteAndChatKeepStableFilenameWithoutChangingJson() {
        assertEquals(reviewDraftScopeKey("u", "t", "https://example.invalid", "c"),
            reviewDraftScopeKey("u", "t", "https://example.invalid", "c"))
        assertEquals(64, reviewDraftScopeKey("u", "t", "https://example.invalid", "c")!!.length)
    }
    @Test fun accountChatTenantAndRouteEachSeparateDrafts() {
        val keys = listOf(
            reviewDraftScopeKey("u", "t", "route", "c"), reviewDraftScopeKey("other", "t", "route", "c"),
            reviewDraftScopeKey("u", "t", "route", "other"), reviewDraftScopeKey("u", "other", "route", "c"),
            reviewDraftScopeKey("u", "t", "other", "c"))
        assertEquals(keys.size, keys.toSet().size)
        assertNotEquals(reviewDraftScopeKey("a:b", "c", "d", "e"), reviewDraftScopeKey("a", "b:c", "d", "e"))
        assertNull(reviewDraftScopeKey(null, "t", "r", "c"))
        assertNull(reviewDraftScopeKey("u", "t", "r", ""))
    }
}
