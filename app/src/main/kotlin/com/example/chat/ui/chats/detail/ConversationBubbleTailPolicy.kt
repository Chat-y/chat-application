package com.example.chat.ui.chats.detail

internal const val CONVERSATION_TAIL_LAST_ONLY = "conv_classic_tail_last_only"
/** Existing sender clusters are supplied by caller. No grouping, ordering or message policy changes. */
internal fun conversationClassicGroupedShape(cluster: SenderClusterPosition, tailLastOnly: Boolean): Boolean =
    cluster != SenderClusterPosition.SINGLE && (!tailLastOnly || cluster != SenderClusterPosition.LAST)
