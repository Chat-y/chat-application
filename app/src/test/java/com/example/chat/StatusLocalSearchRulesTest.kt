package com.example.chat

import com.example.chat.data.services.StatusRecord
import com.example.chat.ui.updates.StatusLocalSearchRules
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** Authored source fixtures, not executed. */
class StatusLocalSearchRulesTest {
    private fun status(id: String, text: String = "", type: String = "image", name: String? = null) = StatusRecord(
        id = id, userId = "author-$id", text = text, mediaType = type, mediaPath = null,
        mediaName = name, mediaSize = 0, durationSeconds = 0,
        createdAt = Instant.EPOCH, expiresAt = Instant.EPOCH, deletedAt = null,
    )

    @Test fun blankQueryPreservesOriginalListAndDoesNotResolveAuthors() {
        val feed = listOf(status("1"))
        assertSame(feed, StatusLocalSearchRules.filter(feed, "  ") { error("not needed") })
    }

    @Test fun findsTextMediaNameTypeAndAuthorWithoutReordering() {
        val feed = listOf(status("1", text = "Trip"), status("2", name = "TRIP.jpg"), status("3", type = "trip"), status("4"), status("5"))
        val found = StatusLocalSearchRules.filter(feed, " trip ") { if (it == "author-4") listOf("Trip Person") else emptyList() }
        assertEquals(listOf("1", "2", "3", "4"), found.map { it.id })
    }

    @Test fun neverInventsResultsOutsideLoadedFeed() {
        assertEquals(emptyList<StatusRecord>(), StatusLocalSearchRules.filter(listOf(status("1")), "missing") { emptyList() })
    }
}
