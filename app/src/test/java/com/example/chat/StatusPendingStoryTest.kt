package com.example.chat

import com.example.chat.ui.updates.CapturedStoryMedia
import com.example.chat.ui.updates.StatusPendingStory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusPendingStoryTest {
    @Test fun captureTicketCanOnlyBeConsumedOnce() {
        val action = StatusPendingStory("account-a", CapturedStoryMedia(byteArrayOf(1), "caption"))
        assertTrue(action.consumeOnce())
        assertFalse(action.consumeOnce())
    }
    @Test fun reviewRetainsExactOwnerCaptionAndOwnedBytes() {
        val bytes = byteArrayOf(1, 2, 3)
        val capture = CapturedStoryMedia(bytes, " caption\nమాయ ")
        val action = StatusPendingStory("account-a", capture)
        assertEquals("account-a", action.ownerId)
        assertEquals(" caption\nమాయ ", action.captured.caption)
        assertSame(capture, action.captured)
        assertSame(bytes, action.captured.bytes)
    }
    @Test fun separateCapturesDoNotShareConsumptionState() {
        val capture = CapturedStoryMedia(byteArrayOf(1), "caption")
        val first = StatusPendingStory("account-a", capture)
        val second = StatusPendingStory("account-a", capture)
        assertTrue(first.consumeOnce())
        assertTrue(second.consumeOnce())
        assertFalse(first.consumeOnce())
    }
}
