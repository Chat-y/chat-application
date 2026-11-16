package com.example.chat.ui.chats.home

import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.ConversationType
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source-only regression cases for the existing namespaced key allocator. */
class HomeListKeyCollisionTest {
    private fun item(id: String) = HomeEntry.Item(
        Conversation(
            id = id,
            type = ConversationType.direct,
            title = id,
            participantIds = listOf("me", "other"),
            lastMessageText = "hello",
            lastMessageTime = Instant.EPOCH,
            lastMessageSenderId = "other",
        ),
    )

    @Test fun duplicateChatSkipsSuffixesAlreadyUsedByOpaqueIds() {
        val seen = mutableSetOf<String>()
        assertEquals("chat:a", homeEntryKey(item("a"), seen))
        assertEquals("chat:a#2", homeEntryKey(item("a#2"), seen))
        assertEquals("chat:a#3", homeEntryKey(item("a#3"), seen))
        assertEquals("chat:a#4", homeEntryKey(item("a"), seen))
        assertEquals("chat:a#2#2", homeEntryKey(item("a#2"), seen))
        assertEquals(5, seen.size)
    }

    @Test fun repeatedSectionsSkipARealLabelThatLooksLikeASuffix() {
        val seen = mutableSetOf<String>()
        assertEquals("section:A", homeEntryKey(HomeEntry.Section("A"), seen))
        assertEquals("section:A#2", homeEntryKey(HomeEntry.Section("A#2"), seen))
        assertEquals("section:A#3", homeEntryKey(HomeEntry.Section("A"), seen))
    }

    @Test fun archiveCountDoesNotChangeBaseIdentityAndDuplicatesRemainUnique() {
        val seen = mutableSetOf<String>()
        assertEquals("archive:entry", homeEntryKey(HomeEntry.Archived(1), seen))
        assertEquals("archive:entry#2", homeEntryKey(HomeEntry.Archived(99), seen))
        assertEquals("archive:entry", homeEntryKey(HomeEntry.Archived(99), mutableSetOf()))
    }

    @Test fun syntheticLookingIdsAndLabelsStayInTheirOwnNamespaces() {
        val entries = listOf(
            item("archive:entry"),
            item("section:A"),
            HomeEntry.Section("chat:archive:entry"),
            HomeEntry.Archived(3),
        )
        val seen = mutableSetOf<String>()
        val keys = entries.map { homeEntryKey(it, seen) }
        assertEquals(entries.size, keys.toSet().size)
        assertEquals(entries.size, seen.size)
        assertTrue(keys.all { it is String })
    }

    @Test fun aFreshListPassReproducesTheSameDeterministicKeys() {
        val entries = listOf(item("a"), item("a#2"), item("a"), HomeEntry.Section("A"))
        fun keys(): List<Any> {
            val seen = mutableSetOf<String>()
            return entries.map { homeEntryKey(it, seen) }
        }
        assertEquals(listOf("chat:a", "chat:a#2", "chat:a#3", "section:A"), keys())
        assertEquals(keys(), keys())
    }
}
