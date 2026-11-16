package com.example.chat.ui.chats.detail

internal const val CONVERSATION_MESSAGE_GAP = "conv_message_gap_dp"
/** Null preserves actual existing2/12dp cluster gaps rather than inventing an8dp default. */
internal fun conversationMessageGap(value: Any?): Int? = (value as? Number)?.toDouble()?.let {
    when (it) { 4.0 -> 4; 8.0 -> 8; else -> null }
}
internal fun conversationMessageBottomGap(overrideDp: Int?, cluster: SenderClusterPosition): Int =
    conversationMessageGap(overrideDp) ?: if (cluster == SenderClusterPosition.FIRST || cluster == SenderClusterPosition.MIDDLE) 2 else 12
