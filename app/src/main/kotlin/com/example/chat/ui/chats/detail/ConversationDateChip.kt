package com.example.chat.ui.chats.detail

internal const val CONVERSATION_DATE_CHIP = "conv_date_chip_preset"
enum class ConversationDateChip(val radius: Int, val horizontal: Int, val vertical: Int) {
    Default(12,10,4), Compact(8,8,2), Pill(50,12,4);
    companion object {
        fun fromMap(values: Map<String,Any?>): ConversationDateChip = if (!conversationExtraOptionsEnabled(values)) Default else
            entries.firstOrNull { it.name == values[CONVERSATION_DATE_CHIP] } ?: Default
    }
}
