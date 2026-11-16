package com.example.chat.ui.chats.detail

/** MR-R3-044: display-only labels; never derive them from a private URI/path or exception. */
internal fun reviewItemErrorLabel(index: Int): String = if (index >= 0) "Attachment ${index.toLong() + 1L}" else "Attachment"
internal fun mediaItemError(label: String, problem: String, recovery: String? = null): String =
    "${label.ifBlank { "Attachment" }}: $problem" + if (recovery.isNullOrBlank()) "" else " $recovery"
