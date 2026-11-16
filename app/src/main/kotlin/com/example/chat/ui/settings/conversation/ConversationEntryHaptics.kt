package com.example.chat.ui.settings.conversation

import com.example.chat.core.theme.ConversationEntryStyles

/** Session opt-in only; no buzz for a repeated or unknown selection. */
internal fun shouldPlayEntrySelectionHaptic(enabled: Boolean, previousId: String, nextId: String): Boolean =
    enabled && previousId != nextId && ConversationEntryStyles.options.any { it.id == nextId }
