package com.example.chat

import com.example.chat.domain.models.UserProfile
import com.example.chat.ui.chats.newchat.NewChatRules
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic directory responses only, not a server uniqueness or account authorization test. */
class NewChatExactRecipientBoundaryTest {
    private fun user(id: String, username: String, name: String = "Fixture") =
        UserProfile(id, name, username, "F", "0xFF6366F1", "", lastSeenAt = Instant.EPOCH)

    @Test(expected = IllegalStateException::class)
    fun distinctAccountsWithSameCanonicalUsernameFailClosed() {
        NewChatRules.exactUsernameMatch(listOf(user("first", "Maya"), user("second", "MAYA")), "maya", "self")
    }

    @Test fun repeatedSameAccountDoesNotBecomeAnAmbiguousRecipient() {
        val first = user("one", "maya", "First fixture")
        val duplicate = user("one", "MAYA", "Replacement fixture")
        assertEquals(listOf(first), NewChatRules.exactUsernameMatch(listOf(first, duplicate), " @MAYA ", "self"))
    }

    @Test fun selfIsExcludedBeforeCheckingRemoteAmbiguity() {
        val other = user("other", "maya")
        assertEquals(listOf(other), NewChatRules.exactUsernameMatch(listOf(user("self", "MAYA"), other), "maya", "self"))
        assertTrue(NewChatRules.exactUsernameMatch(listOf(user("self", "maya")), "maya", "self").isEmpty())
    }

    @Test fun displayNameAndPartialUsernameNeverSelectARecipient() {
        val candidate = user("other", "mayali", "maya")
        assertTrue(NewChatRules.exactUsernameMatch(listOf(candidate), "maya", "self").isEmpty())
        assertTrue(NewChatRules.exactUsernameMatch(listOf(candidate), " ", "self").isEmpty())
    }

    @Test fun unauthenticatedAndBlankRecipientIdsFailClosed() {
        assertTrue(NewChatRules.exactUsernameMatch(listOf(user("other", "maya")), "maya", null).isEmpty())
        assertTrue(NewChatRules.exactUsernameMatch(listOf(user("other", "maya")), "maya", " ").isEmpty())
        assertTrue(NewChatRules.exactUsernameMatch(listOf(user("", "maya")), "maya", "self").isEmpty())
    }

    @Test fun onlyOneLeadingAtIsRemovedWithoutConfusableFolding() {
        val candidate = user("other", "maya")
        assertEquals(listOf(candidate), NewChatRules.exactUsernameMatch(listOf(candidate), " @MAYA ", "self"))
        for (query in listOf("@@maya", "máya", "mаya")) {
            assertTrue(NewChatRules.exactUsernameMatch(listOf(candidate), query, "self").isEmpty())
        }
    }
}

// Superseded source lines retained inactive for review:
//         assertEquals(listOf(candidate), NewChatRules.exactUsernameMatch(listOf(candidate), " @MAYA ", null))
//             assertTrue(NewChatRules.exactUsernameMatch(listOf(candidate), query, null).isEmpty())
