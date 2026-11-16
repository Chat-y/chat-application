package com.example.chat.ui.settings.conversation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

internal enum class ConversationSimpleExtraGroup(val targets: Set<ConversationExtraColor>) {
    Emoji(setOf(ConversationExtraColor.emojiHeaderColor, ConversationExtraColor.emojiHeaderIconsColor, ConversationExtraColor.emojiPickerBgColor)),
    Links(setOf(ConversationExtraColor.hyperlinksColor)),
    Voice(setOf(ConversationExtraColor.voiceNotePlayingBarColor, ConversationExtraColor.voiceNotePlayButtonColor)),
}

/** Three entry points, retaining advanced-only settings unchanged. Session-only disclosure. */
@Composable
internal fun ConversationSimpleExtras(
    options: List<ConversationColorOption>,
    onPick: (String) -> Unit,
    onReset: (String) -> Unit,
    emojiPresets: @Composable () -> Unit,
) {
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    var selectedGroup by rememberSaveable { mutableStateOf<String?>(null) }
    fun label(group: ConversationSimpleExtraGroup): String = resources.getString(when (group) {
        ConversationSimpleExtraGroup.Emoji -> com.example.chat.R.string.conv_proposal_simple_emoji
        ConversationSimpleExtraGroup.Links -> com.example.chat.R.string.conv_proposal_simple_links
        ConversationSimpleExtraGroup.Voice -> com.example.chat.R.string.conv_proposal_simple_voice
    })
    Column {
        ConversationSimpleExtraGroup.entries.forEach { group ->
            com.example.chat.ui.kit.ChatyChoiceTile(label(group), { selectedGroup = group.name })
        }
    }
    ConversationSimpleExtraGroup.entries.firstOrNull { it.name == selectedGroup }?.let { group ->
        com.example.chat.ui.kit.ChatySelectionModal(title = label(group), onDismiss = { selectedGroup = null },
            applyLabel = resources.getString(com.example.chat.R.string.conv_proposal_done), onApply = { selectedGroup = null }) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                if (group == ConversationSimpleExtraGroup.Emoji) emojiPresets()
                options.filter { option -> group.targets.any { it.key == option.id } }.forEach { option ->
                    ConversationColorRow(title = option.title, subtitle = option.subtitle, subtitleMaxLines = 1,
                        iconContent = { com.example.chat.ui.icons.ChatyIcon(ConversationExtraColor.entries.first { it.key == option.id }.rowIcon(), semanticLabel = null) },
                        colorValue = option.value, effectiveColor = option.effective,
                        onColorTap = { selectedGroup = null; onPick(option.id) }, onReset = { onReset(option.id) })
                }
                Text(resources.getString(com.example.chat.R.string.conv_proposal_simple_advanced_note))
            }
        }
    }
}
