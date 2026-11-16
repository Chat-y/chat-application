package com.example.chat.ui.settings.conversation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.ui.chats.detail.ConversationComposerAppearance

@Composable
internal fun rememberConversationComposerAppearance(preferences: ChatyPreferencesController): ConversationComposerAppearance {
    var value by remember(preferences) { mutableStateOf(ConversationComposerAppearance.fromMap(preferences.gbFeatures)) }
    DisposableEffect(preferences) {
        val listener: () -> Unit = { value = ConversationComposerAppearance.fromMap(preferences.gbFeatures) }
        preferences.addListener(listener); listener()
        onDispose { preferences.removeListener(listener) }
    }
    return value
}

@Composable
internal fun ConversationComposerAppearanceControls(preferences: ChatyPreferencesController, appearance: ConversationComposerAppearance) {
    val strings = androidx.compose.ui.platform.LocalContext.current.resources
    com.example.chat.ui.kit.GbSettingRow(title = strings.getString(com.example.chat.R.string.conv_proposal_compact_composer),
        subtitle = strings.getString(com.example.chat.R.string.conv_proposal_compact_composer_scope), subtitleMaxLines = 1,
        switchValue = appearance.compact, onSwitchChanged = { preferences.updateGbFeature(ConversationComposerAppearance.COMPACT,it) },
        iconContent = { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.entry,semanticLabel = null) })
    com.example.chat.ui.kit.GbSettingRow(title = strings.getString(com.example.chat.R.string.conv_proposal_floating_composer),
        subtitle = strings.getString(com.example.chat.R.string.conv_proposal_floating_composer_scope), subtitleMaxLines = 1,
        switchValue = appearance.floating, onSwitchChanged = { preferences.updateGbFeature(ConversationComposerAppearance.FLOATING,it) },
        iconContent = { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.layers,semanticLabel = null) })
}
