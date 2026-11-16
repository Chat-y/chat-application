package com.example.chat.ui.settings.security

/** Legacy toast events have no conversation id. With protected chats present, redact unknown-origin
 * bodies instead of equating a sender id to a conversation id. Non-protected events may be masked too. */
object ProtectedToastPolicy {
    fun conceal(concealWhileLocked: Boolean, hideContent: Boolean, protectedChatsPresent: Boolean): Boolean =
        concealWhileLocked || (hideContent && protectedChatsPresent)
}
