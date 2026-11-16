package com.example.chat.ui.settings.conversation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.ui.chats.detail.ConversationHeaderAppearance
import com.example.chat.ui.kit.ChatyChoiceTile
import com.example.chat.ui.kit.ChatySelectionModal
import com.example.chat.ui.kit.GbCardContainer
import com.example.chat.ui.kit.GbSettingRow

@Composable
internal fun rememberConversationHeaderAppearance(preferences: ChatyPreferencesController): ConversationHeaderAppearance {
    var state by remember(preferences) { mutableStateOf(ConversationHeaderAppearance.fromMap(preferences.gbFeatures)) }
    DisposableEffect(preferences) {
        val listener: () -> Unit = { state = ConversationHeaderAppearance.fromMap(preferences.gbFeatures) }
        preferences.addListener(listener)
        listener()
        onDispose { preferences.removeListener(listener) }
    }
    return state
}

/** Existing map merge API writes only an explicit chosen key after Apply. No controller edits. */
@Composable
internal fun ConversationHeaderAppearanceControls(preferences: ChatyPreferencesController, appearance: ConversationHeaderAppearance) {
    val strings = androidx.compose.ui.platform.LocalContext.current.resources
    var showIconPicker by rememberSaveable { mutableStateOf(false) }
    val theme = com.example.chat.ui.theme.LocalThemeConfig.current
    val headerCanvas = opaqueConversationStoredColor(preferences.conversation.actionBarColor) ?: theme.surfaceColor
    val defaultIconColor = if (appearance.autoContrast) com.example.chat.ui.chats.detail.conversationHeaderContrastForeground(headerCanvas) else theme.primaryTextColor
    var target by rememberSaveable { mutableStateOf<String?>(null) }
    val title = when (target) {
        ConversationHeaderAppearance.ALPHA_PERCENT -> com.example.chat.R.string.conv_proposal_header_alpha
        ConversationHeaderAppearance.SUBTITLE_MODE -> com.example.chat.R.string.conv_proposal_subtitle_content
        ConversationHeaderAppearance.NAME_WEIGHT -> com.example.chat.R.string.conv_proposal_name_weight
        ConversationHeaderAppearance.AVATAR_SHAPE -> com.example.chat.R.string.conv_proposal_avatar_shape
        else -> com.example.chat.R.string.conv_proposal_avatar_size
    }
    fun weightLabel(weight: String) = strings.getString(when (weight) {
        "regular" -> com.example.chat.R.string.conv_proposal_weight_regular
        "medium" -> com.example.chat.R.string.conv_proposal_weight_medium
        "bold" -> com.example.chat.R.string.conv_proposal_weight_bold
        else -> com.example.chat.R.string.conv_proposal_weight_default
    })
    fun shapeLabel(rounded: Boolean) = strings.getString(if (rounded) com.example.chat.R.string.conv_proposal_avatar_square
        else com.example.chat.R.string.conv_proposal_avatar_circle)
    GbSettingRow(title = strings.getString(com.example.chat.R.string.conv_proposal_header_options_gate),
        subtitle = strings.getString(com.example.chat.R.string.conv_proposal_header_options_gate_scope), subtitleMaxLines = 1,
        switchValue = appearance.optionsEnabled,
        onSwitchChanged = { preferences.updateGbFeature(ConversationHeaderAppearance.OPTIONS_ENABLED, it) })
    androidx.compose.runtime.LaunchedEffect(appearance.optionsEnabled) {
        if (!appearance.optionsEnabled) { target = null; showIconPicker = false }
    }
    if (appearance.optionsEnabled) {
    GbCardContainer(headerText = strings.getString(com.example.chat.R.string.conv_proposal_header_layout)) {
        GbSettingRow(title = strings.getString(com.example.chat.R.string.conv_proposal_subtitle_content),
            subtitle = strings.getString(subtitleLabel(appearance.subtitleMode)),
            onTap = { target = ConversationHeaderAppearance.SUBTITLE_MODE })
        GbSettingRow(subtitleMaxLines = 1, title = strings.getString(com.example.chat.R.string.conv_proposal_name_weight), subtitle = weightLabel(appearance.nameWeight),
            onTap = { target = ConversationHeaderAppearance.NAME_WEIGHT })
        GbSettingRow(subtitleMaxLines = 1, title = strings.getString(com.example.chat.R.string.conv_proposal_centered_title),
            subtitle = strings.getString(com.example.chat.R.string.conv_proposal_centered_title_scope),
            switchValue = appearance.centeredTitle, onSwitchChanged = { preferences.updateGbFeature(ConversationHeaderAppearance.CENTERED_TITLE, it) })
        GbSettingRow(title = strings.getString(com.example.chat.R.string.conv_proposal_unread_chat_badge),
            subtitle = strings.getString(com.example.chat.R.string.conv_proposal_unread_chat_badge_scope),subtitleMaxLines = 1,
            switchValue = appearance.unreadChatBadge,onSwitchChanged = { preferences.updateGbFeature(ConversationHeaderAppearance.UNREAD_CHAT_BADGE,it) })
        GbSettingRow(title = strings.getString(com.example.chat.R.string.conv_proposal_show_audio_shortcut),
            subtitle = strings.getString(com.example.chat.R.string.conv_proposal_split_call_scope),subtitleMaxLines = 1,
            switchValue = appearance.audioCallVisible,onSwitchChanged = { preferences.updateGbFeature(ConversationHeaderAppearance.AUDIO_VISIBLE,it) })
        GbSettingRow(title = strings.getString(com.example.chat.R.string.conv_proposal_show_video_shortcut),
            subtitle = strings.getString(com.example.chat.R.string.conv_proposal_split_call_scope),subtitleMaxLines = 1,
            switchValue = appearance.videoCallVisible,onSwitchChanged = { preferences.updateGbFeature(ConversationHeaderAppearance.VIDEO_VISIBLE,it) })
        GbSettingRow(subtitleMaxLines = 1, title = strings.getString(com.example.chat.R.string.conv_proposal_calls_overflow),
            switchValue = appearance.callsInOverflow,
            onSwitchChanged = { preferences.updateGbFeature(ConversationHeaderAppearance.CALLS_IN_OVERFLOW, it) })
        GbSettingRow(subtitleMaxLines = 1, title = strings.getString(com.example.chat.R.string.conv_proposal_compact_landscape),
            subtitle = strings.getString(com.example.chat.R.string.conv_proposal_compact_landscape_scope),
            switchValue = appearance.compactLandscape,
            onSwitchChanged = { preferences.updateGbFeature(ConversationHeaderAppearance.COMPACT_LANDSCAPE, it) })
        GbSettingRow(subtitleMaxLines = 1, title = strings.getString(com.example.chat.R.string.conv_proposal_avatar_shape), subtitle = shapeLabel(appearance.roundedSquareAvatar),
            onTap = { target = ConversationHeaderAppearance.AVATAR_SHAPE })
        GbSettingRow(subtitleMaxLines = 1, title = strings.getString(com.example.chat.R.string.conv_proposal_avatar_size),
            subtitle = strings.getString(com.example.chat.R.string.conv_proposal_dp_size, appearance.avatarSizeDp),
            onTap = { target = ConversationHeaderAppearance.AVATAR_SIZE })
    }
    GbCardContainer(headerText = strings.getString(com.example.chat.R.string.conv_ui_colours_eb4e91)) {
        val translucency = com.example.chat.ui.chats.detail.conversationHeaderTranslucency(headerCanvas,appearance.alphaPercent)
        GbSettingRow(title = strings.getString(com.example.chat.R.string.conv_proposal_header_alpha),
            subtitle = strings.getString(com.example.chat.R.string.conv_proposal_header_alpha_value,appearance.alphaPercent,translucency.percent),
            onTap = { target = ConversationHeaderAppearance.ALPHA_PERCENT })
        androidx.compose.material3.Text(strings.getString(com.example.chat.R.string.conv_proposal_header_alpha_scope))
        GbSettingRow(subtitleMaxLines = 1, title = strings.getString(com.example.chat.R.string.conv_proposal_auto_contrast),
            subtitle = strings.getString(com.example.chat.R.string.conv_proposal_auto_contrast_scope),
            switchValue = appearance.autoContrast, onSwitchChanged = { preferences.updateGbFeature(ConversationHeaderAppearance.AUTO_CONTRAST, it) })
        ConversationColorRow(subtitleMaxLines = 1, title = strings.getString(com.example.chat.R.string.conv_proposal_header_icons),
            colorValue = appearance.iconsColor,
            effectiveColor = androidx.compose.ui.graphics.Color(appearance.iconsColor ?: defaultIconColor),
            onColorTap = { showIconPicker = true },
            onReset = { preferences.updateGbFeature(ConversationHeaderAppearance.ICONS_COLOR, null) })
        GbSettingRow(subtitleMaxLines = 1, title = strings.getString(com.example.chat.R.string.conv_proposal_status_marquee),
            subtitle = strings.getString(com.example.chat.R.string.conv_proposal_status_marquee_scope),
            switchValue = appearance.statusMarqueeOnTap,
            onSwitchChanged = { preferences.updateGbFeature(ConversationHeaderAppearance.STATUS_MARQUEE, it) })
        GbSettingRow(subtitleMaxLines = 1, title = strings.getString(com.example.chat.R.string.conv_proposal_header_divider),
            switchValue = appearance.divider, onSwitchChanged = { preferences.updateGbFeature(ConversationHeaderAppearance.DIVIDER, it) })
    }
    }
    if (appearance.optionsEnabled && showIconPicker) com.example.chat.ui.kit.ChatyColorPickerModal(
        title = strings.getString(com.example.chat.R.string.conv_proposal_header_icons),
        namedPalette = conversationNamedPalette(strings),
        currentColor = appearance.iconsColor ?: defaultIconColor,
        onColorSelected = { preferences.updateGbFeature(ConversationHeaderAppearance.ICONS_COLOR, it); showIconPicker = false },
        onDismiss = { showIconPicker = false })
    if (appearance.optionsEnabled) target?.let { key ->
        val current = when (key) {
            ConversationHeaderAppearance.ALPHA_PERCENT -> appearance.alphaPercent.toString()
            ConversationHeaderAppearance.SUBTITLE_MODE -> appearance.subtitleMode
            ConversationHeaderAppearance.NAME_WEIGHT -> appearance.nameWeight
            ConversationHeaderAppearance.AVATAR_SHAPE -> appearance.roundedSquareAvatar.toString()
            else -> appearance.avatarSizeDp.toString()
        }
        var draft by rememberSaveable(key) { mutableStateOf(current) }
        val choices = when (key) {
            ConversationHeaderAppearance.ALPHA_PERCENT -> (40..100 step 10).map(Int::toString)
            ConversationHeaderAppearance.SUBTITLE_MODE -> listOf("presence","status","none")
            ConversationHeaderAppearance.NAME_WEIGHT -> listOf("default", "regular", "medium", "bold")
            ConversationHeaderAppearance.AVATAR_SHAPE -> listOf("false", "true")
            else -> (32..44 step 2).map(Int::toString)
        }
        ChatySelectionModal(title = strings.getString(title), onDismiss = { target = null }, onApply = {
            if (draft in choices) {
                val value: Any = when (key) {
                    ConversationHeaderAppearance.ALPHA_PERCENT -> draft.toInt()
                    ConversationHeaderAppearance.SUBTITLE_MODE -> draft
                    ConversationHeaderAppearance.NAME_WEIGHT -> draft
                    ConversationHeaderAppearance.AVATAR_SHAPE -> draft == "true"
                    else -> draft.toInt()
                }
                preferences.updateGbFeature(key, value)
            }
            target = null
        }) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                choices.forEach { choice ->
                    val label = when (key) {
                        ConversationHeaderAppearance.ALPHA_PERCENT -> strings.getString(com.example.chat.R.string.conv_proposal_header_alpha_percent,choice.toInt())
                        ConversationHeaderAppearance.SUBTITLE_MODE -> strings.getString(subtitleLabel(choice))
                        ConversationHeaderAppearance.NAME_WEIGHT -> weightLabel(choice)
                        ConversationHeaderAppearance.AVATAR_SHAPE -> shapeLabel(choice == "true")
                        else -> strings.getString(com.example.chat.R.string.conv_proposal_dp_size, choice.toInt())
                    }
                    ChatyChoiceTile(label = label, selected = draft == choice, onClick = { draft = choice })
                }
            }
        }
    }
}

private fun subtitleLabel(mode: String): Int = when (mode) {
    "status" -> com.example.chat.R.string.conv_proposal_subtitle_status
    "none" -> com.example.chat.R.string.conv_proposal_subtitle_none
    else -> com.example.chat.R.string.conv_proposal_subtitle_presence
}
