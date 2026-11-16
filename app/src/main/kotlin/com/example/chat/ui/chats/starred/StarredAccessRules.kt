package com.example.chat.ui.chats.starred

/** Content-free UI gate. No account tokens, message text or persistent data retained. */
internal object StarredAccessRules {
    fun mayShow(sameOwner: Boolean, authenticated: Boolean, hidden: Boolean,
                protectedConversation: Boolean, registryLoading: Boolean, registryFailed: Boolean,
                vaultLocked: Boolean): Boolean = sameOwner && authenticated && !hidden &&
        !protectedConversation && !registryLoading && !registryFailed && !vaultLocked
}
