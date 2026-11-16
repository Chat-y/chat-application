package com.example.chat

import com.example.chat.ui.settings.CallContactOption
import com.example.chat.ui.settings.callContactSectionStarts
import org.junit.Assert.assertEquals
import org.junit.Test

/** ICU section navigation fixtures only, no policy/identity changes. NOT RUN. */
class CallContactSectionsGapTest {
    @Test fun sectionIndicesMatchVisibleFilteredList() {
        val contacts = listOf(CallContactOption("a", "Alpha"), CallContactOption("b", "Amber"), CallContactOption("c", "Beta"))
        assertEquals(listOf("A" to 0, "B" to 2), callContactSectionStarts(contacts))
        assertEquals(listOf("B" to 0), callContactSectionStarts(contacts.takeLast(1)))
    }
    @Test fun emptyListHasNoInventedSections() {
        assertEquals(emptyList<Pair<String, Int>>(), callContactSectionStarts(emptyList()))
    }
}
