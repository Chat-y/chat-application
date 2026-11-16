package com.example.chat

import com.example.chat.ui.media.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class MediaBrowseOrderingTest {
    private val stamp = Instant.parse("2026-11-15T00:00:00Z")
    private fun file(path: String, name: String = "Same.txt", size: Long = 10, modified: Instant = stamp) =
        MediaFileDescriptor(path, name, path, size, "text/plain", MediaCategory.document, modified)

    @Test fun `newest ties have deterministic name and path ordering`() {
        val a = file("/a"); val b = file("/b")
        val recent = file("/recent", modified = stamp.plusSeconds(1))
        assertEquals(listOf(recent, a, b), orderMediaFiles(listOf(b, a, recent), MediaBrowseOrder.Newest))
    }
    @Test fun `names match independent of case and input is not mutated`() {
        val a = file("/a", "same.txt"); val b = file("/b", "Same.txt")
        val original = listOf(b, a)
        assertEquals(listOf(a, b), orderMediaFiles(original, MediaBrowseOrder.Name))
        assertEquals(listOf(b, a), original)
    }
    @Test fun `largest first preserves stable ties and empty inputs`() {
        val small = file("/small", size = 1); val a = file("/a"); val b = file("/b")
        assertEquals(listOf(a, b, small), orderMediaFiles(listOf(small, b, a), MediaBrowseOrder.Size))
        assertTrue(orderMediaFiles(emptyList(), MediaBrowseOrder.Name).isEmpty())
    }
}
