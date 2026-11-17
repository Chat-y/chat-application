package com.example.chat.notifications

/** One repair request per accepted message-sync work item, not per retry and never on the call path. */
internal object UnknownBindingRecoveryPolicy {
    fun shouldRequestRegistration(isCall: Boolean, attempt: Int, mappingKnown: Boolean): Boolean =
        !isCall && attempt == 0 && !mappingKnown
}
