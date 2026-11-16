package com.example.chat.ui.chats.detail
import com.example.chat.domain.models.Conversation
/** Other ordinary inbox chats, not message count. Never reveal protected/archived/task rooms. */
internal fun conversationUnreadChatCount(conversations: List<Conversation>,currentId: String,canCount: Boolean,isProtected: (String)->Boolean): Int =
    if (!canCount) 0 else conversations.asSequence().distinctBy { it.id }.count {
        it.id != currentId && it.unreadCount > 0 && !it.isArchived && !it.isTask && !isProtected(it.id)
    }
internal fun conversationUnreadChatBadgeText(count: Int): String = if (count > 99) "99+" else
    java.text.NumberFormat.getIntegerInstance().format(count.coerceAtLeast(0))
