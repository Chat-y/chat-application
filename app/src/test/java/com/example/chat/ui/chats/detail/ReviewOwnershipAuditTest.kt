package com.example.chat.ui.chats.detail

import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Source fixtures only. NOT RUN. */
class ReviewOwnershipAuditTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun lateCreatedCopyIsDeletedAfterClose() {
        val owner = ReviewOwnedFiles()
        owner.close()
        val file = temporary.newFile()
        try { owner += file; fail("Closed review accepted a file") }
        catch (_: CancellationException) { }
        assertFalse(file.exists())
    }
    @Test fun acceptedSenderKeepsFinalFileButNotSupersededCopy() {
        val owner = ReviewOwnedFiles()
        val finalCopy = temporary.newFile()
        val superseded = temporary.newFile()
        owner += finalCopy
        owner += superseded
        owner.discardExcept(setOf(finalCopy.path))
        owner.handOff()
        owner.close()
        assertTrue(finalCopy.exists())
        assertFalse(superseded.exists())
    }
    @Test fun unicodeCaptionIncludesEllipsisWithinWireBudget() {
        val text = reviewCaptionWithinByteLimit("😀".repeat(1025), 4096)
        assertTrue(text.toByteArray(Charsets.UTF_8).size <= 4096)
        assertTrue(text.endsWith("\u2026"))
        assertFalse(text.dropLast(1).last().isHighSurrogate())
    }

    @Test fun boundedPrefixLeavesFollowingBytesUnread() {
        val input = byteArrayOf(1, 2, 3, 4).inputStream()
        assertArrayEquals(byteArrayOf(1, 2), readReviewPrefix(input, 2))
        assertEquals(3, input.read())
    }
}
