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
import androidx.compose.ui.semantics.heading
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

/** Immutable resolved UI values only; never contains account, message or controller data. */
internal data class MoreOptionsPreviewState(
    val bubbleStyle: com.example.chat.core.theme.BubbleStyleId,
    val incomingFill: Color,
    val emojiHeader: Color,
    val emojiIcons: Color,
    val emojiBackground: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val participantColor: Color,
    val linkColor: Color,
    val voiceButton: Color,
    val voiceProgress: Color,
    val infoBackground: Color,
    val infoText: Color,
    val playbackSpeed: Double = 1.0,
    val varyCustomNames: Boolean = false,
    val multiColourNames: Boolean = true,
    val dateChip: com.example.chat.ui.chats.detail.ConversationDateChip = com.example.chat.ui.chats.detail.ConversationDateChip.Default,
    val customParticipantSeed: Long? = null,
)

@Composable
internal fun MoreOptionsPreview(state: MoreOptionsPreviewState) {
    val strings = androidx.compose.ui.platform.LocalContext.current.resources
    val config = LocalThemeConfig.current
    val fs = config.fontScale.toFloat().let { if (it.isFinite()) it.coerceIn(0.8f, 2f) else 1f }
    val emojiHeader = state.emojiHeader
    val emojiIcons = state.emojiIcons
    val emojiBackground = state.emojiBackground
    val primaryText = state.primaryText
    val secondaryText = state.secondaryText
    val participantColor = if (!state.multiColourNames) Color(state.customParticipantSeed ?: config.accentColor) else if (state.varyCustomNames) Color(com.example.chat.ui.chats.detail.resolveConversationParticipantNameColor(
        config, state.incomingFill.toArgb().toLong() and 0xFFFFFFFFL, "sample-participant-1", state.customParticipantSeed,
        varyCustomSeed = true, deriveForSender = true)) else state.participantColor

    val linkColor = state.linkColor
    val voiceButton = state.voiceButton
    val voiceProgress = state.voiceProgress
    val infoBackground = state.infoBackground
    val infoText = state.infoText
            Text(strings.getString(com.example.chat.R.string.conv_ui_preview_f1fbb2),
                color = secondaryText, fontSize = 12.sp)
            GbLivePreviewCard(showLabel = false, modifier = Modifier.semantics { contentDescription = strings.getString(com.example.chat.R.string.conv_ui_preview_of_emoji_popup_group_bd1be8) }) {
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
                        com.example.chat.ui.kit.ChatyBubbleContainer(
                            styleId = state.bubbleStyle,
                            isMe = false, fillColor = state.incomingFill,
                            accentColor = Color(config.accentColor),
                        ) {
                            Column {
                                Text(strings.getString(com.example.chat.R.string.conv_ui_alex_64542d), modifier = Modifier.testTag("conversation-extra-name-preview"), color = participantColor, fontSize = (14 * fs).sp, maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                if (state.varyCustomNames) Text(strings.getString(com.example.chat.R.string.conv_proposal_second_participant_sample),
                                    modifier = Modifier.testTag("conversation-extra-name-second-preview"),
                                    color = Color(com.example.chat.ui.chats.detail.resolveConversationParticipantNameColor(config,
                                        state.incomingFill.toArgb().toLong() and 0xFFFFFFFFL, "sample-participant-2", state.customParticipantSeed,
                                        varyCustomSeed = true, deriveForSender = true)), fontSize = (14 * fs).sp)
                                Text(strings.getString(com.example.chat.R.string.conv_ui_https_example_org_434178), modifier = Modifier.testTag("conversation-extra-link-preview"), color = linkColor, fontSize = (14 * fs).sp,
                                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)
                                Text(strings.getString(com.example.chat.R.string.conv_ui_preview_f1fbb2), color = primaryText)
                            }
                        }
                        // Previous gap: 12.dp
                        Spacer(Modifier.height(ConversationPreviewLayout.sectionGapDp.dp))
                        Row(
                            Modifier.fillMaxWidth().testTag("conversation-extra-voice-preview")
                                .background(Color(config.surfaceColor), RoundedCornerShape(16.dp))
                                // Previous voice sample padding: horizontal = 14.dp, vertical = 10.dp
                                .padding(horizontal = ConversationPreviewLayout.outerPaddingDp.dp, vertical = ConversationPreviewLayout.innerPaddingDp.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(48.dp).background(voiceButton, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { ChatyIcon(ChatyIconId.playArrow, size = 20.dp, color = com.example.chat.ui.kit.themeGlyphColor(voiceButton, config)) }
                            // Previous inline gap: 10.dp
                            Spacer(Modifier.width(ConversationPreviewLayout.inlineGapDp.dp))
                            Box(Modifier.weight(1f).height(4.dp).background(voiceProgress, RoundedCornerShape(2.dp)))
                            // Previous inline gap: 10.dp
                            Spacer(Modifier.width(ConversationPreviewLayout.inlineGapDp.dp))
                            Text(strings.getString(com.example.chat.R.string.conv_ui_0_42_bcf112), color = secondaryText, fontSize = (12 * fs).sp)
                            Spacer(Modifier.width(8.dp))
                            Text(strings.getString(com.example.chat.R.string.conv_proposal_playback_speed,
                                java.text.NumberFormat.getNumberInstance().format(state.playbackSpeed)),
                                color = secondaryText, fontSize = (12 * fs).sp,
                                modifier = Modifier.testTag("conversation-extra-speed-preview"))

                        }
                        Spacer(Modifier.height(10.dp))
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(
                                strings.getString(com.example.chat.R.string.conv_ui_today_24345a),
                                Modifier.testTag("conversation-extra-date-preview").background(
                                    infoBackground,
                                    // Default keeps this preview's original12dp shape and16x8padding.
                                    RoundedCornerShape(state.dateChip.radius.dp),
                                )
                                .padding(horizontal = (if (state.dateChip == com.example.chat.ui.chats.detail.ConversationDateChip.Default) ConversationPreviewLayout.outerPaddingDp else state.dateChip.horizontal).dp,
                                    vertical = (if (state.dateChip == com.example.chat.ui.chats.detail.ConversationDateChip.Default) ConversationPreviewLayout.innerPaddingDp else state.dateChip.vertical).dp),
                                color = infoText,
                                fontSize = (12 * fs).sp, fontWeight = FontWeight.W500,
                            )
                        }
                        ConversationInfoSample.entries.forEach { sample ->
                            Text(strings.getString(sample.stringId), color = infoText, fontSize = (12 * fs).sp,
                                modifier = Modifier.testTag(sample.testTag).background(infoBackground, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 16.dp, vertical = 8.dp))
                        }
                        Text(strings.getString(com.example.chat.R.string.conv_ui_missed_voice_call_da7aba), color = infoText, fontSize = (12 * fs).sp,
                            modifier = Modifier.testTag("conversation-extra-missed-call-preview").background(infoBackground, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }
            }
}
