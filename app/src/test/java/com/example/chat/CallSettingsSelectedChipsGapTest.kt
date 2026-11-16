package com.example.chat

import com.example.chat.ui.settings.filterCallContacts
import com.example.chat.ui.settings.callExceptionsUseFullScreen
import com.example.chat.ui.settings.CallContactOption
import com.example.chat.ui.settings.selectedCallContacts
import org.junit.Assert.assertEquals
import org.junit.Test

/** Source-only presentation tests; no privacy policy writes. NOT RUN. */
class CallSettingsSelectedChipsGapTest {
    @Test fun selectionLabelsUseIdsNotAmbiguousDisplayNames() {
        val contacts = listOf(CallContactOption("one", "Same"), CallContactOption("two", "Same"))
        assertEquals(listOf(contacts[1]), selectedCallContacts(contacts, setOf("two")))
    }
    @Test fun chipsAreSortedDeduplicatedAndNeverInventMissingContacts() {
        val alpha = CallContactOption("a", "Alpha")
        val zed = CallContactOption("z", "zed")
        val contacts = listOf(zed, alpha, alpha)
        val ids = setOf("z", "a", "no-longer-loaded")
        assertEquals(listOf(alpha, zed), selectedCallContacts(contacts, ids))
        assertEquals(3, ids.size)
        assertEquals(3, contacts.size)
    }
    @Test fun emptySelectionHasNoChip() {
        assertEquals(emptyList<CallContactOption>(), selectedCallContacts(listOf(CallContactOption("a", "Alpha")), emptySet()))
    }
    @Test fun queryMatchingIsTrimmedCaseInsensitiveAndStable() {
        val contacts = listOf(CallContactOption("z", "zed"), CallContactOption("a", "Alpha"))
        assertEquals(listOf(contacts[1]), filterCallContacts(contacts, " ALP "))
        assertEquals(listOf(contacts[1], contacts[0]), filterCallContacts(contacts, " "))
        assertEquals(emptyList<CallContactOption>(), filterCallContacts(contacts, "missing"))
        assertEquals("z", contacts[0].id)
    }
    @Test fun longListPresentationStartsAfterTwentyContacts() {
        listOf(0, 1, 20).forEach { assertEquals(false, callExceptionsUseFullScreen(it)) }
        listOf(21, 500).forEach { assertEquals(true, callExceptionsUseFullScreen(it)) }
    }
    @Test fun filteringAndReorderingRetainsDistinctContactIdsWithDuplicateNames() {
        val first = CallContactOption("one", "Same")
        val second = CallContactOption("two", "Same")
        val third = CallContactOption("three", "Other")
        val original = listOf(first, third, second)
        val filtered = filterCallContacts(original, "same")
        assertEquals(listOf("one", "two"), filtered.map { it.id })
        assertEquals(listOf("one", "three", "two"), original.map { it.id })
        assertEquals(2, filtered.map { it.id }.toSet().size)
    }

}
