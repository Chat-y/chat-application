package com.example.chat.ui.settings.conversation

/** Logical start/end corners in dp. Consecutive sample messages only, no live grouping policy. */
internal data class ConversationGroupedCorners(val topStart: Int, val topEnd: Int, val bottomStart: Int, val bottomEnd: Int)

internal fun conversationGroupedCorners(isMe: Boolean, isFirst: Boolean, isLast: Boolean): ConversationGroupedCorners =
    if (isMe) ConversationGroupedCorners(16, if (isFirst) 16 else 4, 16, if (isLast) 16 else 4)
    else ConversationGroupedCorners(if (isFirst) 16 else 4, 16, if (isLast) 16 else 4, 16)
