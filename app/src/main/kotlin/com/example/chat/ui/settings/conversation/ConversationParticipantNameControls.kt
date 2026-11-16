package com.example.chat.ui.settings.conversation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.ui.chats.detail.CONVERSATION_VARY_CUSTOM_NAMES

@Composable
internal fun rememberConversationVaryCustomNames(preferences: ChatyPreferencesController): Boolean {
    var value by remember(preferences) { mutableStateOf(preferences.gbBool(CONVERSATION_VARY_CUSTOM_NAMES, false)) }
    DisposableEffect(preferences) {
        val listener: () -> Unit = { value = preferences.gbBool(CONVERSATION_VARY_CUSTOM_NAMES, false) }
        preferences.addListener(listener)
        listener()
        onDispose { preferences.removeListener(listener) }
    }
    return value
}

@Composable
internal fun ConversationParticipantNameControls(preferences: ChatyPreferencesController, enabled: Boolean) {
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    com.example.chat.ui.kit.GbSettingRow(title = resources.getString(com.example.chat.R.string.conv_proposal_vary_custom_names),
        subtitle = resources.getString(com.example.chat.R.string.conv_proposal_vary_custom_names_scope), subtitleMaxLines = 1,
        switchValue = enabled, onSwitchChanged = { preferences.updateGbFeature(CONVERSATION_VARY_CUSTOM_NAMES, it) },
        iconContent = { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.groups, semanticLabel = null) })
}

@Composable
internal fun rememberConversationExtraOptions(preferences: ChatyPreferencesController): Boolean {
    var enabled by remember(preferences) { mutableStateOf(com.example.chat.ui.chats.detail.conversationExtraOptionsEnabled(preferences.gbFeatures)) }
    DisposableEffect(preferences) {
        val listener: () -> Unit = { enabled = com.example.chat.ui.chats.detail.conversationExtraOptionsEnabled(preferences.gbFeatures) }
        preferences.addListener(listener); listener()
        onDispose { preferences.removeListener(listener) }
    }
    return enabled
}

@Composable
internal fun rememberConversationDateChip(preferences: ChatyPreferencesController): com.example.chat.ui.chats.detail.ConversationDateChip {
    var value by remember(preferences) { mutableStateOf(com.example.chat.ui.chats.detail.ConversationDateChip.fromMap(preferences.gbFeatures)) }
    DisposableEffect(preferences) {
        val listener: () -> Unit = { value = com.example.chat.ui.chats.detail.ConversationDateChip.fromMap(preferences.gbFeatures) }
        preferences.addListener(listener);listener()
        onDispose { preferences.removeListener(listener) }
    }
    return value
}

@Composable
internal fun ConversationDateChipControls(preferences: ChatyPreferencesController, current: com.example.chat.ui.chats.detail.ConversationDateChip) {
    val strings = androidx.compose.ui.platform.LocalContext.current.resources
    var showDateChipPicker by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    com.example.chat.ui.kit.GbSettingRow(title = strings.getString(com.example.chat.R.string.conv_proposal_date_chip),
        subtitle = strings.getString(dateChipLabel(current)),onTap = { showDateChipPicker = true })
    if (showDateChipPicker) {
        var draft by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(current.name) }
        com.example.chat.ui.kit.ChatySelectionModal(title = strings.getString(com.example.chat.R.string.conv_proposal_date_chip),
            onDismiss = { showDateChipPicker = false },onApply = {
                if (com.example.chat.ui.chats.detail.ConversationDateChip.entries.any { it.name == draft }) preferences.updateGbFeature(com.example.chat.ui.chats.detail.CONVERSATION_DATE_CHIP,draft)
                showDateChipPicker = false
            }) {
            androidx.compose.foundation.layout.Column {
                com.example.chat.ui.chats.detail.ConversationDateChip.entries.forEach { choice ->
                    com.example.chat.ui.kit.ChatyChoiceTile(label = strings.getString(dateChipLabel(choice)),selected = draft == choice.name,onClick = { draft = choice.name })
                }
            }
        }
    }
}
private fun dateChipLabel(chip: com.example.chat.ui.chats.detail.ConversationDateChip): Int = when (chip) {
    com.example.chat.ui.chats.detail.ConversationDateChip.Default -> com.example.chat.R.string.conv_proposal_date_chip_default
    com.example.chat.ui.chats.detail.ConversationDateChip.Compact -> com.example.chat.R.string.conv_proposal_date_chip_compact
    com.example.chat.ui.chats.detail.ConversationDateChip.Pill -> com.example.chat.R.string.conv_proposal_date_chip_pill
}

@Composable
internal fun rememberConversationMultiColourNames(preferences: ChatyPreferencesController): Boolean {
    var value by remember(preferences) { mutableStateOf(com.example.chat.ui.chats.detail.conversationMultiColourNamesEnabled(preferences.gbFeatures)) }
    DisposableEffect(preferences) {
        val listener: () -> Unit = { value = com.example.chat.ui.chats.detail.conversationMultiColourNamesEnabled(preferences.gbFeatures) }
        preferences.addListener(listener);listener()
        onDispose { preferences.removeListener(listener) }
    }
    return value
}
