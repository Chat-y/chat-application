package com.example.chat.ui.settings.conversation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.ui.chats.detail.ConversationPerChatColors
import com.example.chat.ui.chats.detail.CONVERSATION_PER_CHAT_COLORS

/** Separate immediate local controls, not part of the theme editor's staged Apply operation. */
@Composable
internal fun ConversationPerChatColorControls(preferences: ChatyPreferencesController,accountChatKey: String,
    hasScopedTheme: Boolean,isScopeCurrent: () -> Boolean) {
    val strings=androidx.compose.ui.platform.LocalContext.current.resources
    val theme=com.example.chat.ui.theme.LocalThemeConfig.current
    var stored by remember(preferences,accountChatKey) { mutableStateOf(ConversationPerChatColors.fromMap(preferences.gbFeatures,accountChatKey)) }
    var picker by rememberSaveable(accountChatKey) { mutableStateOf<String?>(null) }
    DisposableEffect(preferences,accountChatKey) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        var attached = true
        val listener:()->Unit={ handler.post {
            if (attached && isScopeCurrent()) stored=ConversationPerChatColors.fromMap(preferences.gbFeatures,accountChatKey)
        }; Unit }
        preferences.addListener(listener);listener();onDispose { attached=false;preferences.removeListener(listener) }
    }
    fun update(transform:(ConversationPerChatColors)->ConversationPerChatColors) {
        if (!isScopeCurrent()) return
        val latest=ConversationPerChatColors.fromMap(preferences.gbFeatures,accountChatKey)
        preferences.updateGbFeature(CONVERSATION_PER_CHAT_COLORS,
            com.example.chat.ui.chats.detail.updatedConversationPerChatColors(preferences.gbFeatures,accountChatKey,transform(latest)))
    }
    fun title(key:String):String=strings.getString(when(key) {
        "header" -> com.example.chat.R.string.conv_proposal_per_chat_header
        "link" -> com.example.chat.R.string.conv_proposal_per_chat_link
        else -> com.example.chat.R.string.conv_proposal_per_chat_name
    })
    fun value(key:String):Long?=when(key) { "header" -> stored.header;"link" -> stored.link;else -> stored.name }
    fun fallback(key:String):Long=when(key) {
        "header" -> if (hasScopedTheme) theme.surfaceColor else opaqueConversationStoredColor(preferences.conversation.actionBarColor) ?: theme.surfaceColor
        "link" -> theme.linkColor
        else -> theme.accentColor
    }
    fun write(key:String,color:Long?)=update { old -> when(key) { "header" -> old.copy(header=color);"link" -> old.copy(link=color);else -> old.copy(name=color) } }
    com.example.chat.ui.kit.GbCardContainer(headerText=strings.getString(com.example.chat.R.string.conv_proposal_per_chat_colors)) {
        androidx.compose.material3.Text(strings.getString(com.example.chat.R.string.conv_proposal_per_chat_colors_scope))
        com.example.chat.ui.kit.GbSettingRow(title=strings.getString(com.example.chat.R.string.conv_proposal_per_chat_enable),
            switchValue=stored.enabled,onSwitchChanged={ chosen -> if (!chosen) picker=null;update { it.copy(enabled=chosen) } })
        if (stored.enabled) {
            listOf("header","link","name").forEach { key ->
                ConversationColorRow(title=title(key),colorValue=value(key),effectiveColor=Color(value(key) ?: fallback(key)),
                    onColorTap={ picker=key },onReset={ write(key,null) })
            }
            ConversationContrastNote(Color(theme.primaryTextColor),Color(stored.header ?: fallback("header")),4.5f,title("header"))
            ConversationContrastNote(Color(stored.link ?: fallback("link")),Color(theme.incomingBubbleColor),4.5f,title("link"))
            ConversationContrastNote(Color(stored.name ?: fallback("name")),Color(theme.incomingBubbleColor),4.5f,title("name"))
            androidx.compose.material3.TextButton(onClick={ update { it.copy(header=null,link=null,name=null) } }) {
                androidx.compose.material3.Text(strings.getString(com.example.chat.R.string.conv_proposal_per_chat_reset))
            }
        }
    }
    if (stored.enabled) picker?.let { key ->
        com.example.chat.ui.kit.ChatyColorPickerModal(title=title(key),namedPalette=conversationNamedPalette(strings),
            currentColor=value(key) ?: fallback(key),onColorSelected={ write(key,it);picker=null },onDismiss={ picker=null })
    }
}
