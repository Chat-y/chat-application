package com.example.chat.ui.settings.conversation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.domain.models.ConversationPreferences
import com.example.chat.ui.kit.GbPickers
import com.example.chat.ui.kit.GbSwitchTile
import com.example.chat.ui.kit.ChatySelectionModal

/** Explicit row-to-preference mapping used by both single-row changes and bulk apply. */
internal data class ConversationColorOption(
    val id: String,
    val group: String,
    val title: String,
    val subtitle: String?,
    val icon: ImageVector,
    val value: Long?,
    val effective: Color,
    val write: (ConversationPreferences, Long?) -> ConversationPreferences,
)

@Composable
internal fun ConversationApplyColorTo(options: List<ConversationColorOption>, preferences: ChatyPreferencesController, pickers: GbPickers, initial: Long) {
    val strings = androidx.compose.ui.platform.LocalContext.current.resources
    var openSheet by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    TextButton(onClick = { selectedIds = arrayListOf(); openSheet = true }) { Text(strings.getString(com.example.chat.R.string.conv_helper_apply_colour_many)) }
    if (openSheet) ChatySelectionModal(title = strings.getString(com.example.chat.R.string.conv_helper_choose_colour_targets), onDismiss = { openSheet = false },
        applyLabel = strings.getString(com.example.chat.R.string.conv_helper_choose_colour), applyEnabled = selectedIds.isNotEmpty(), onApply = {
            val selected = options.filter { it.id in selectedIds }
            openSheet = false
            pickers.showColor(strings.getString(com.example.chat.R.string.conv_helper_apply_colour_selected), initial) { colour ->
                preferences.editConversation { original -> selected.fold(original) { state, option -> option.write(state, colour) } }
            }
        }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
            options.forEach { option ->
                GbSwitchTile(option.icon, option.title, option.id in selectedIds, { checked ->
                    selectedIds = ArrayList(if (checked) (selectedIds + option.id).distinct() else selectedIds - option.id)
                }, option.subtitle)
            }
        }
    }
}

/** Presets write the existing three colour fields only; choosing none preserves today's behaviour. */
@Composable
internal fun ConversationEmojiPresets(preferences: ChatyPreferencesController) {
    val strings = androidx.compose.ui.platform.LocalContext.current.resources
    val current = com.example.chat.ui.theme.LocalThemeConfig.current
    val presets = listOf(
        "light" to com.example.chat.core.theme.ThemePresets.monochromeLight,
        "dark" to com.example.chat.core.theme.ThemePresets.monochromeDark,
        "accent" to current,
    )
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()) {
        presets.forEach { (label, theme) ->
            TextButton(modifier = Modifier.weight(1f), onClick = {
                preferences.editConversation { state -> state.copy(
                    emojiHeaderColor = if (label == "accent") theme.accentColor else theme.surfaceColor,
                    emojiHeaderIconsColor = if (label == "accent") theme.onAccentColor else theme.primaryTextColor,
                    emojiPickerBgColor = theme.surfaceColor,
                ) }
            }) { Text(when (label) { "light" -> strings.getString(com.example.chat.R.string.conv_helper_emoji_light); "dark" -> strings.getString(com.example.chat.R.string.conv_helper_emoji_dark); else -> strings.getString(com.example.chat.R.string.conv_helper_emoji_accent) }) }
        }
    }
}
