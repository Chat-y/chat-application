package com.example.chat

import com.example.chat.ui.media.*
import java.time.Instant
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

/** FIL-R3-132 source cases, NOT RUN. */
class PickerSearchIndexTest {
    private fun file(path: String, name: String, category: MediaCategory = MediaCategory.document) =
        MediaFileDescriptor(path, name, path, 1, "text/plain", category, Instant.EPOCH)
    @Test fun emptyQueryKeepsInputOrderAndDuplicates() {
        val a = file("a", "One"); val b = file("b", "Two")
        assertEquals(listOf(b, a, a), PickerSearchIndex(listOf(b, a, a)).filter("", null))
    }
    @Test fun categoryAndSubstringCompose() {
        val a = file("a", "PHOTO.JPG", MediaCategory.image); val b = file("b", "Photo notes")
        val index = PickerSearchIndex(listOf(a,b))
        assertEquals(listOf(a,b), index.filter("photo", null))
        assertEquals(listOf(a), index.filter("PHOTO", MediaCategory.image))
        assertTrue(index.filter("photo", MediaCategory.video).isEmpty())
    }
    @Test fun literalWhitespaceAndPunctuationPreserved() {
        val a = file("a", "a [b].txt"); val index = PickerSearchIndex(listOf(a))
        assertEquals(listOf(a), index.filter("[B]", null))
        assertTrue(index.filter(" a", null).isEmpty())
        assertTrue(index.filter("a.*", null).isEmpty())
    }
    @Test fun snapshotDoesNotFollowMutableInputOrOldNames() {
        val a = file("a", "before"); val files = mutableListOf(a); val old = PickerSearchIndex(files)
        files[0] = file("a", "after")
        assertEquals(listOf(a), old.filter("before", null))
        assertTrue(PickerSearchIndex(files).filter("before", null).isEmpty())
    }
    @Test fun parityWithServicePredicateAcrossQueriesAndCategories() {
        val files = listOf(file("a", "Istanbul"), file("b", "İMAGE", MediaCategory.image), file("c", "Straße"))
        val index = PickerSearchIndex(files)
        for (query in listOf("", "i", "İ", "ß", "SS", " ")) for (category in listOf(null, MediaCategory.image, MediaCategory.document)) {
            val expected = files.filter { (category == null || it.category == category) && (query.isEmpty() || it.name.lowercase().contains(query.lowercase())) }
            assertEquals(expected, index.filter(query, category))
        }
    }
}
