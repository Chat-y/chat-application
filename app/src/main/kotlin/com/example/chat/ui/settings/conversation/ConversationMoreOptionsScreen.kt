package com.example.chat.ui.settings.conversation

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.MarkChatUnread
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayCircleFilled
import androidx.compose.material.icons.rounded.SentimentSatisfiedAlt
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.ui.icons.ChatyIcon
import com.example.chat.ui.icons.ChatyIconId
import com.example.chat.ui.kit.ChatyBackButton
import com.example.chat.ui.kit.GbCardContainer
import com.example.chat.ui.kit.GbLivePreviewCard
import com.example.chat.ui.kit.GbSettingRow
import com.example.chat.ui.kit.rememberGbPickers
import com.example.chat.ui.theme.LocalThemeConfig

/** Port of ConversationMoreOptionsScreen. UNCOMPILED (Compose); uses the ported ChatyIcon for the play glyph. */
@Composable
fun ConversationMoreOptionsScreen(preferences: ChatyPreferencesController, onBack: () -> Unit) {
    val strings = androidx.compose.ui.platform.LocalContext.current.resources
    val config = LocalThemeConfig.current
    // Legacy whole-controller version listener replaced by a conversation-only snapshot.
    val p = rememberConversationPreferences(preferences)
    val pickers = rememberGbPickers()

    val accent = Color(config.accentColor)
    val primaryText = Color(config.primaryTextColor)
    val secondaryText = Color(config.secondaryTextColor)
    val emojiHeader = p.emojiHeaderColor?.let(::Color) ?: Color(config.surfaceColor)
    val emojiIcons = p.emojiHeaderIconsColor?.let(::Color) ?: primaryText
    val emojiBackground = p.emojiPickerBgColor?.let(::Color) ?: Color(config.surfaceColor)
    val linkColor = p.hyperlinksColor?.let(::Color) ?: Color(config.linkColor)
    val infoText = p.infoBalloonsTextColor?.let(::Color) ?: secondaryText
    val infoBackground = p.infoBalloonsBgColor?.let(::Color) ?: Color(config.surfaceColor).copy(alpha = 0.8f)
    val participantColor = p.groupParticipantNameColor?.let(::Color) ?: accent
    val voiceProgress = p.voiceNotePlayingBarColor?.let(::Color) ?: accent
    val voiceButton = p.voiceNotePlayButtonColor?.let(::Color) ?: accent
    val fs = config.fontScale.toFloat().let { if (it.isFinite()) it.coerceIn(0.8f, 2f) else 1f }

    val colourOptions = listOf(
        ConversationColorOption("emojiHeaderColor", "Emoji popup", strings.getString(com.example.chat.R.string.conv_ui_emoji_header_color_1c7dd8), strings.getString(com.example.chat.R.string.conv_ui_change_emoji_popup_header_ba_3f92a1), com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.alignTop), p.emojiHeaderColor, emojiHeader,
            { state, colour -> state.copy(emojiHeaderColor = colour) }),
        ConversationColorOption("emojiHeaderIconsColor", "Emoji popup", strings.getString(com.example.chat.R.string.conv_ui_emoji_header_icons_color_85cd20), strings.getString(com.example.chat.R.string.conv_ui_change_emoji_popup_icons_col_5606e8), com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.emoji), p.emojiHeaderIconsColor, emojiIcons,
            { state, colour -> state.copy(emojiHeaderIconsColor = colour) }),
        ConversationColorOption("emojiPickerBgColor", "Emoji popup", strings.getString(com.example.chat.R.string.conv_ui_emoji_picker_background_colo_56e57d), strings.getString(com.example.chat.R.string.conv_ui_change_emoji_popup_backgroun_23fe59), com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.palette), p.emojiPickerBgColor, emojiBackground,
            { state, colour -> state.copy(emojiPickerBgColor = colour) }),
        ConversationColorOption("hyperlinksColor", "Links and group names", strings.getString(com.example.chat.R.string.conv_ui_links_014bcd), strings.getString(com.example.chat.R.string.conv_ui_links_in_incoming_and_outgoi_7b17b4), com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.link), p.hyperlinksColor, linkColor,
            { state, colour -> state.copy(hyperlinksColor = colour) }),
        ConversationColorOption("groupParticipantNameColor", "Links and group names", strings.getString(com.example.chat.R.string.conv_ui_group_participant_name_color_aad4e0), strings.getString(com.example.chat.R.string.conv_ui_sender_names_above_group_mes_8110fa), com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.groups), p.groupParticipantNameColor, participantColor,
            { state, colour -> state.copy(groupParticipantNameColor = colour) }),
        ConversationColorOption("infoBalloonsTextColor", "System bubbles", strings.getString(com.example.chat.R.string.conv_ui_system_bubble_text_943ad0), strings.getString(com.example.chat.R.string.conv_ui_text_in_date_and_missed_call_6edb90), com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.info), p.infoBalloonsTextColor, infoText,
            { state, colour -> state.copy(infoBalloonsTextColor = colour) }),
        ConversationColorOption("infoBalloonsBgColor", "System bubbles", strings.getString(com.example.chat.R.string.conv_ui_system_bubble_background_f79af7), strings.getString(com.example.chat.R.string.conv_ui_background_of_date_and_misse_0116fd), com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.markUnread), p.infoBalloonsBgColor, infoBackground,
            { state, colour -> state.copy(infoBalloonsBgColor = colour) }),
        ConversationColorOption("voiceNotePlayingBarColor", "Voice notes", strings.getString(com.example.chat.R.string.conv_ui_voice_note_progress_b44151), strings.getString(com.example.chat.R.string.conv_ui_playback_progress_and_wavefo_e90549), com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.waveform), p.voiceNotePlayingBarColor, voiceProgress,
            { state, colour -> state.copy(voiceNotePlayingBarColor = colour) }),
        ConversationColorOption("voiceNotePlayButtonColor", "Voice notes", strings.getString(com.example.chat.R.string.conv_ui_voice_note_play_button_d19469), strings.getString(com.example.chat.R.string.conv_ui_change_color_of_voice_messag_4cd41b), com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.playCircle), p.voiceNotePlayButtonColor, voiceButton,
            { state, colour -> state.copy(voiceNotePlayButtonColor = colour) }),
    )

    Column(Modifier.fillMaxSize().background(Color(config.backgroundColor))) {
        com.example.chat.ui.kit.ChatyAppBar(title = strings.getString(com.example.chat.R.string.conv_ui_bubble_extras_5ef1a3), onBack = onBack)
        Column(Modifier.weight(1f).align(Alignment.CenterHorizontally).widthIn(max = 600.dp).fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)).padding(horizontal = 16.dp, vertical = 16.dp)) {
            GbLivePreviewCard(modifier = Modifier.semantics { contentDescription = strings.getString(com.example.chat.R.string.conv_ui_preview_of_emoji_popup_group_bd1be8) }) {
                Column {
                    Column {
                        Text(strings.getString(com.example.chat.R.string.conv_ui_preview_only_nothing_is_sent_5c68fa), color = secondaryText, fontSize = (12 * fs).sp)
                        Column(Modifier.fillMaxWidth().testTag("conversation-extra-emoji-preview").semantics { contentDescription = strings.getString(com.example.chat.R.string.conv_ui_emoji_popup_sample_696c4e) }) {
                            Row(Modifier.fillMaxWidth().background(emojiHeader).padding(16.dp)) {
                                ChatyIcon(ChatyIconId.emoji, semanticLabel = strings.getString(com.example.chat.R.string.conv_ui_emoji_categories_c50484), color = emojiIcons)
                                Text(strings.getString(com.example.chat.R.string.conv_ui_emoji_2de039), color = emojiIcons, fontSize = (14 * fs).sp)
                            }
                            Text(strings.getString(com.example.chat.R.string.conv_ui__43df72), color = primaryText, fontSize = (20 * fs).sp,
                                modifier = Modifier.fillMaxWidth().background(emojiBackground).padding(16.dp))
                        }
                        Text(strings.getString(com.example.chat.R.string.conv_ui_alex_64542d), modifier = Modifier.testTag("conversation-extra-name-preview"), color = participantColor, fontSize = (14 * fs).sp, maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(strings.getString(com.example.chat.R.string.conv_ui_https_example_org_434178), modifier = Modifier.testTag("conversation-extra-link-preview"), color = linkColor, fontSize = (14 * fs).sp,
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier.fillMaxWidth()
                                .background(Color(config.surfaceColor), RoundedCornerShape(16.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(48.dp).background(voiceButton, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { ChatyIcon(ChatyIconId.playArrow, size = 20.dp, color = conversationOnColor(voiceButton, primaryText, Color(config.backgroundColor))) }
                            Spacer(Modifier.width(10.dp))
                            Box(Modifier.weight(1f).height(4.dp).background(voiceProgress, RoundedCornerShape(2.dp)))
                            Spacer(Modifier.width(10.dp))
                            Text(strings.getString(com.example.chat.R.string.conv_ui_0_42_bcf112), color = secondaryText, fontSize = (12 * fs).sp)
                        }
                        Spacer(Modifier.height(10.dp))
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(
                                strings.getString(com.example.chat.R.string.conv_ui_today_24345a),
                                Modifier.background(
                                    infoBackground,
                                    RoundedCornerShape(12.dp),
                                ).padding(horizontal = 14.dp, vertical = 6.dp),
                                color = infoText,
                                fontSize = (12 * fs).sp, fontWeight = FontWeight.W500,
                            )
                        }
                        Text(strings.getString(com.example.chat.R.string.conv_ui_missed_voice_call_da7aba), color = infoText, fontSize = (12 * fs).sp,
                            modifier = Modifier.background(infoBackground, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            ConversationContrastNote(emojiIcons, emojiHeader, 3f, strings.getString(com.example.chat.R.string.conv_ui_emoji_header_icons_1ac4f6))
            ConversationContrastNote(infoText, infoBackground, 4.5f, strings.getString(com.example.chat.R.string.conv_ui_system_bubble_text_943ad0))
            ConversationContrastNote(participantColor, p.leftBubbleColor?.let(::Color) ?: Color(config.incomingBubbleColor), 4.5f, strings.getString(com.example.chat.R.string.conv_ui_participant_name_e8a7e9))
            ConversationContrastNote(linkColor, p.leftBubbleColor?.let(::Color) ?: Color(config.incomingBubbleColor), 4.5f, strings.getString(com.example.chat.R.string.conv_ui_incoming_links_650176))
            ConversationContrastNote(linkColor, p.rightBubbleColor?.let(::Color) ?: Color(config.outgoingBubbleColor), 4.5f, strings.getString(com.example.chat.R.string.conv_ui_outgoing_links_f4ba79))
            ConversationEmojiPresets(preferences)
            colourOptions.groupBy { it.group }.forEach { (group, options) ->
                GbCardContainer(headerText = group) {
                    options.forEach { option ->
                        ConversationColorRow(title = option.title, icon = option.icon, subtitle = option.subtitle,
                            colorValue = option.value, effectiveColor = option.effective,
                            onColorTap = { pickers.showColor(option.title, option.effective.toArgb().toLong() and 0xFFFFFFFFL) { colour ->
                                preferences.editConversation { latest -> option.write(latest, colour) }
                            } },
                            onReset = { preferences.editConversation { latest -> option.write(latest, null) } })
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            ConversationApplyColorTo(colourOptions, preferences, pickers, config.accentColor)
            androidx.compose.material3.TextButton(onClick = {
                preferences.editConversation { latest -> latest.copy(emojiHeaderColor = null, emojiHeaderIconsColor = null,
                    emojiPickerBgColor = null, hyperlinksColor = null, infoBalloonsTextColor = null,
                    infoBalloonsBgColor = null, groupParticipantNameColor = null, voiceNotePlayingBarColor = null,
                    voiceNotePlayButtonColor = null) }
            }) { Text(strings.getString(com.example.chat.R.string.conv_ui_reset_all_extras_colours_fd382e)) }
            Text(strings.getString(com.example.chat.R.string.conv_ui_these_appearance_settings_ar_9572c0), color = secondaryText, fontSize = 12.sp)
            ConversationSupportCopy(mapOf("emojiHeaderColor" to p.emojiHeaderColor, "emojiHeaderIconsColor" to p.emojiHeaderIconsColor,
                "emojiPickerBgColor" to p.emojiPickerBgColor, "hyperlinksColor" to p.hyperlinksColor,
                "infoBalloonsTextColor" to p.infoBalloonsTextColor, "infoBalloonsBgColor" to p.infoBalloonsBgColor,
                "groupParticipantNameColor" to p.groupParticipantNameColor, "voiceNotePlayingBarColor" to p.voiceNotePlayingBarColor,
                "voiceNotePlayButtonColor" to p.voiceNotePlayButtonColor))
        }
    }
    pickers.Host()
}
