package com.example.chat.ui.chats.detail
import org.junit.Assert.*
import org.junit.Test
class ConversationHeaderSubtitleTest {
    @Test fun defaultRetainsPresenceOrMemberText() = assertEquals("4 participants",conversationHeaderSubtitle("presence","4 participants","bio",true))
    @Test fun noneDoesNotLeakOtherLine() = assertNull(conversationHeaderSubtitle("none","online","bio",true))
    @Test fun statusUsesExistingValueOnly() = assertEquals("bio",conversationHeaderSubtitle("status","online","bio",true))
    @Test fun blockedGroupOrMissingStatusStaysEmpty() {
        assertNull(conversationHeaderSubtitle("status","online","bio",false))
        assertNull(conversationHeaderSubtitle("status","online"," ",true))
    }
    @Test fun rolledBackMasterPreservesSavedChoiceButUsesPresence() {
        val values = mapOf<String,Any?>(ConversationHeaderAppearance.SUBTITLE_MODE to "status")
        val saved = ConversationHeaderAppearance.fromMap(values)
        assertEquals("status",saved.subtitleMode);assertEquals("presence",saved.effective().subtitleMode)
    }
}
