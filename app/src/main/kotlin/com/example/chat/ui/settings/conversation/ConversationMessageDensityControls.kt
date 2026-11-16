package com.example.chat.ui.settings.conversation

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.ui.chats.detail.CONVERSATION_MESSAGE_GAP
import com.example.chat.ui.chats.detail.conversationMessageGap

@Composable
internal fun rememberConversationMessageGap(preferences: ChatyPreferencesController, generation: Long = preferences.scopeGeneration): Int? {
    var state by remember(preferences,generation) { mutableStateOf(conversationMessageGap(preferences.gbFeatures[CONVERSATION_MESSAGE_GAP])) }
    DisposableEffect(preferences,generation) {
        val listener: () -> Unit = { if (preferences.scopeGeneration == generation) state = conversationMessageGap(preferences.gbFeatures[CONVERSATION_MESSAGE_GAP]) }
        preferences.addListener(listener); listener()
        onDispose { preferences.removeListener(listener) }
    }
    return state
}

@Composable
internal fun ConversationMessageDensityControls(preferences: ChatyPreferencesController, gap: Int?, generation: Long = preferences.scopeGeneration) {
    val strings = androidx.compose.ui.platform.LocalContext.current.resources
    fun label(value: Int?) = strings.getString(when (value) {
        4 -> com.example.chat.R.string.conv_proposal_gap_compact
        8 -> com.example.chat.R.string.conv_proposal_gap_roomy
        else -> com.example.chat.R.string.conv_proposal_weight_default
    })
    var pickerOpen by rememberSaveable(generation) { mutableStateOf(false) }
    com.example.chat.ui.kit.GbSettingRow(title = strings.getString(com.example.chat.R.string.conv_proposal_message_density),
        subtitle = label(gap), subtitleMaxLines = 1, onTap = { pickerOpen = true })
    if (pickerOpen) {
        var draft by rememberSaveable(generation) { mutableStateOf(gap ?: 0) }
        com.example.chat.ui.kit.ChatySelectionModal(title = strings.getString(com.example.chat.R.string.conv_proposal_message_density),
            onDismiss = { pickerOpen = false }, onApply = {
                if (preferences.scopeGeneration == generation) preferences.updateGbFeature(CONVERSATION_MESSAGE_GAP, conversationMessageGap(draft)); pickerOpen = false
            }) {
            Column {
                listOf(0,4,8).forEach { value ->
                    com.example.chat.ui.kit.ChatyChoiceTile(label = label(conversationMessageGap(value)),
                        selected = draft == value, onClick = { draft = value })
                }
            }
        }
    }
}
