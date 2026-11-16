package com.example.chat.ui.settings.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.kit.ColorPickerLogic
import com.example.chat.ui.theme.LocalThemeConfig

internal data class ConversationBubbleColorPair(val id: String, val name: String, val background: Long, val foreground: Long)

/** Eight existing shipped theme pairs. No new color literals and no unchecked foreground defaults. */
internal fun conversationBubbleColorPairs(): List<ConversationBubbleColorPair> = listOf(
    ThemePresets.paper, ThemePresets.midnight, ThemePresets.sunset, ThemePresets.forest,
    ThemePresets.graphite, ThemePresets.lavender, ThemePresets.coffee, ThemePresets.cobalt,
).mapNotNull { theme ->
    val background = theme.outgoingBubbleColor
    val foreground = listOf(theme.outgoingTextColor, theme.incomingTextColor, theme.primaryTextColor,
        theme.secondaryTextColor, theme.backgroundColor).maxBy { ColorPickerLogic.contrastRatio(it, background) }
    if (ColorPickerLogic.contrastRatio(foreground, background) < 4.5) null
    else ConversationBubbleColorPair(theme.id, theme.name, background, foreground)
}

@Composable
internal fun ConversationBubblePalette(currentBackground: Long?, currentForeground: Long?, onApply: (ConversationBubbleColorPair) -> Unit) {
    val theme = LocalThemeConfig.current
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(resources.getString(com.example.chat.R.string.conv_proposal_bubble_pairs), color = Color(theme.primaryTextColor))
        Text(resources.getString(com.example.chat.R.string.conv_proposal_bubble_pairs_scope), color = Color(theme.secondaryTextColor))
        LazyRow(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(conversationBubbleColorPairs(), key = { it.id }) { pair ->
                com.example.chat.ui.kit.ChatyOptionCard(label = pair.name,
                    selected = currentBackground == pair.background && currentForeground == pair.foreground,
                    onTap = { onApply(pair) }) {
                    com.example.chat.ui.kit.ChatyBubbleContainer(styleId = theme.bubbleStyle,
                        isMe = true, fillColor = Color(pair.background), accentColor = Color(theme.accentColor)) {
                        Text(resources.getString(com.example.chat.R.string.conv_ui_hi_94dd9e), color = Color(pair.foreground))
                    }
                }
            }
        }
    }
}
