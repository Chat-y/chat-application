package com.example.chat.core.messages

/** Pure decisions for async composer completion. Never overwrite a newer draft or a new session. */
object ConversationDraftRecovery {
    enum class Decision { restore, keepSeparately, ignore }
    fun decide(sameOwner: Boolean, submittedRevision: Int, currentRevision: Int, currentText: String): Decision = when {
        !sameOwner -> Decision.ignore
        submittedRevision == currentRevision && currentText.isEmpty() -> Decision.restore
        else -> Decision.keepSeparately
    }
}

/** Only additions after the last still-present known row are new arrivals, not prepended history. */
object ConversationArrivalPolicy {
    fun appendedIds(previous: Set<String>, current: List<String>): List<String> {
        if (previous.isEmpty()) return emptyList()
        val lastKnown = current.indexOfLast { it in previous }
        if (lastKnown < 0) return emptyList()
        return current.drop(lastKnown + 1).filter { it.isNotBlank() && it !in previous }.distinct()
    }
}

/** Session-local failed text with its original operation, never a permission to resend it. */
data class ConversationRecoverableDraft(
    val text: String,
    val kind: Kind = Kind.message,
    val editMessageId: String? = null,
) {
    enum class Kind { message, edit, viewOnce }

    init {
        require(kind != Kind.edit || !editMessageId.isNullOrBlank()) { "An edit recovery needs its original message id." }
        require(kind == Kind.edit || editMessageId == null) { "Only edit recovery may carry a message id." }
    }
}
