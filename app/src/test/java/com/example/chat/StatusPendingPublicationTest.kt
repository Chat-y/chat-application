package com.example.chat

import com.example.chat.ui.updates.PickedStatusMedia
import com.example.chat.ui.updates.StatusPendingPublication
import com.example.chat.ui.updates.StatusPublicationWork
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusPendingPublicationTest {
    @Test fun eachTextTicketCanBeConsumedOnlyOnce() {
        val action = StatusPendingPublication.Text("approved text")
        assertTrue(action.consumeOnce())
        assertFalse(action.consumeOnce())
        assertEquals("approved text", action.value)
    }
    @Test fun differentPayloadsHaveIndependentApprovalIdentity() {
        val first = StatusPendingPublication.Text("same")
        val second = StatusPendingPublication.Text("same")
        assertFalse(first === second)
        assertTrue(first.consumeOnce())
        assertTrue(second.consumeOnce())
    }
    @Test fun mediaTicketRetainsExactCaptionAndOwnedBytesWithoutExtraCopy() {
        val picked = PickedStatusMedia(byteArrayOf(1, 2), "file.jpg", "image/jpeg")
        val action = StatusPendingPublication.Media("image", picked, "caption at selection")
        assertSame(picked, action.picked)
        assertEquals("caption at selection", action.caption)
        assertEquals("image", StatusPublicationWork.Picking("image").type)
        assertEquals("image", StatusPublicationWork.Publishing(action).type)
    }
}
