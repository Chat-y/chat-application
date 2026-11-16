package com.example.chat.ui.calls

/** A random nonce, not a credential or a digest of one. Holds only one exact session reference. */
internal class CallReminderProcessBinding {
    private var owner: Any? = null
    private var nonce: String? = null
    @Synchronized fun bind(expected: Any): String {
        if (owner !== expected || nonce == null) {
            owner = expected
            nonce = java.util.UUID.randomUUID().toString()
        }
        return checkNotNull(nonce)
    }
    /** Never creates a binding on a worker wake; a new process therefore cannot admit old work. */
    @Synchronized fun matches(expected: Any, candidate: String?): Boolean =
        candidate != null && owner === expected && nonce == candidate
}
