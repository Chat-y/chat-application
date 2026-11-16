package com.example.chat.ui.settings.conversation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.ui.chats.detail.CONVERSATION_TAIL_LAST_ONLY

@Composable
internal fun ConversationBubbleTailControls(preferences: ChatyPreferencesController, generation: Long = preferences.scopeGeneration) {
    var enabled by remember(preferences,generation) { mutableStateOf(preferences.gbBool(CONVERSATION_TAIL_LAST_ONLY,false)) }
    DisposableEffect(preferences,generation) {
        val listener: () -> Unit = { if (preferences.scopeGeneration == generation) enabled = preferences.gbBool(CONVERSATION_TAIL_LAST_ONLY,false) }
        preferences.addListener(listener); listener()
        onDispose { preferences.removeListener(listener) }
    }
    val strings = androidx.compose.ui.platform.LocalContext.current.resources
    com.example.chat.ui.kit.GbSettingRow(title = strings.getString(com.example.chat.R.string.conv_proposal_tail_last_only),
        subtitle = strings.getString(com.example.chat.R.string.conv_proposal_tail_last_only_scope), subtitleMaxLines = 1,
        switchValue = enabled,onSwitchChanged = { if (preferences.scopeGeneration == generation) preferences.updateGbFeature(CONVERSATION_TAIL_LAST_ONLY,it) },
        iconContent = { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.corner,semanticLabel = null) })
}
