package com.example.chat.data.services

/** Presentation only. A name from the account-scoped directory is not a verification badge. */
internal object CallIncomingIdentity {
    fun displayName(directoryName: String?): String =
// Previous implementation retained: directoryName?.trim()?.takeIf { it.isNotEmpty() } ?: "Unknown caller"
        CallContactName.meaningful(directoryName) ?: "Unknown caller"
}
