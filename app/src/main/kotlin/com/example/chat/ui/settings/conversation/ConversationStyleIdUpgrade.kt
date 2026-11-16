package com.example.chat.ui.settings.conversation

import com.example.chat.domain.models.ConversationPreferences
import com.example.chat.core.theme.ConversationEntryStyles
import com.example.chat.core.theme.bubbleStyleIdFromString
import com.example.chat.core.theme.deliveryIconStyleFromString

/** Explicit-only upgrade projection for migration acceptance. Never runs automatically on load.
 * Readers still accept old strings; callers choose whether/when to persist this projection. */
internal fun upgradeConversationBubbleTickIds(source: ConversationPreferences): ConversationPreferences = source.copy(
    bubbleStyle = bubbleStyleIdFromString(source.bubbleStyle).name,
    tickStyle = deliveryIconStyleFromString(source.tickStyle).name,
)
internal fun upgradeConversationEntryId(source: ConversationPreferences): ConversationPreferences =
    source.copy(entryStyle = ConversationEntryStyles.resolve(source.entryStyle).id)
