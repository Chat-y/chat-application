package com.example.chat.ui.settings.conversation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.core.theme.ConversationEntryStyles

internal const val CONVERSATION_BUBBLE_ROLLOUT = "conv_bubble_gallery_enabled"
internal const val CONVERSATION_ENTRY_ROLLOUT = "conv_entry_gallery_enabled"
internal fun conversationStyleRollout(values: Map<String,Any?>,key: String): Boolean = values[key] as? Boolean ?: false
internal fun conversationEntrySavedValue(raw: String,enabled: Boolean): String = ConversationEntryStyles.resolve(raw).let { if (enabled) it.id else it.legacyName }
@Composable
internal fun rememberConversationStyleRollout(preferences: ChatyPreferencesController,key: String,generation: Long = preferences.scopeGeneration): Boolean {
    var value by remember(preferences,key,generation) { mutableStateOf(conversationStyleRollout(preferences.gbFeatures,key)) }
    DisposableEffect(preferences,key,generation) {
        val listener: () -> Unit = { if (preferences.scopeGeneration == generation) value = conversationStyleRollout(preferences.gbFeatures,key) }
        preferences.addListener(listener);listener()
        onDispose { preferences.removeListener(listener) }
    }
    return value
}

/** Some historic display labels collide or are deliberately skipped by the legacy parser.
 * In those cases keep the unambiguous existing enum name rather than changing the user's style. */
internal fun conversationLegacyTickValue(id: com.example.chat.core.theme.DeliveryIconStyle): String =
    id.displayName.takeIf { com.example.chat.core.theme.deliveryIconStyleFromString(it) == id } ?: id.name
internal fun conversationLegacyBubbleValue(id: com.example.chat.core.theme.BubbleStyleId): String =
    id.displayName.takeIf { com.example.chat.core.theme.bubbleStyleIdFromString(it) == id } ?: id.name
