package com.example.chat.ui.chats.detail
import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.ConversationType
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test
class ConversationUnreadChatBadgeTest {
    private fun chat(id:String,unread:Int=1)=Conversation(id,ConversationType.direct,id,emptyList(),lastMessageText="",lastMessageTime=Instant.EPOCH,lastMessageSenderId="",unreadCount=unread)
    @Test fun countsChatsNotMessagesAndDedupesIds() = assertEquals(2,conversationUnreadChatCount(listOf(chat("a",99),chat("a",99),chat("b",1)),"current",true) { false })
    @Test fun currentReadAndArchivedRoomsNeverCount() = assertEquals(0,conversationUnreadChatCount(listOf(chat("current"),chat("read",0),chat("archived").copy(isArchived=true)),"current",true) { false })
    @Test fun protectedAndTaskRoomsNeverCount() = assertEquals(0,conversationUnreadChatCount(listOf(chat("hidden"),chat("locked"),chat("task").copy(roomType="task")),"current",true) { it in setOf("hidden","locked") })
    @Test fun unreadyOrDecoyGateCannotExposePartialCount() = assertEquals(0,conversationUnreadChatCount(listOf(chat("a")),"current",false) { false })
    @Test fun badgeDefaultsOffAndLargeCountsAreBounded() { assertFalse(ConversationHeaderAppearance().unreadChatBadge);assertEquals("99+",conversationUnreadChatBadgeText(100));assertEquals(ConversationHeaderAppearance(),ConversationHeaderAppearance(unreadChatBadge=true).effective()) }
}
