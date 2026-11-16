package com.example.chat

import com.example.chat.domain.models.UserProfile
import com.example.chat.ui.chats.newchat.NewChatRules
import java.time.Instant
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NewChatUsernameKeyTest {
    @Test fun canonicalKeysDoNotDependOnTurkishDeviceLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("iris", NewChatRules.usernameKey("IRIS"))
            assertEquals("iris", NewChatRules.normalizedUsername(" @IRIS "))
            val user = UserProfile("other", "Iris Example", "IRIS", "I", "0xFF6366F1", "", lastSeenAt = Instant.EPOCH)
            assertEquals(listOf(user), NewChatRules.exactUsernameMatch(listOf(user), "iris", "self"))
        } finally { Locale.setDefault(previous) }
    }
    @Test fun keyDoesNotStripProfileCharactersOrFoldConfusableIdentities() {
        assertEquals(" @iris ", NewChatRules.usernameKey(" @IRIS "))
        assertNotEquals(NewChatRules.usernameKey("iris"), NewChatRules.usernameKey("ırıs"))
        assertNotEquals(NewChatRules.usernameKey("maya"), NewChatRules.usernameKey("máya"))
    }
}
