package com.example.chat.ui.settings.conversation

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.IntrinsicSize.Min
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.FormatColorText
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.VerticalSplit
import androidx.compose.material.icons.rounded.PersonPin
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.ReadMore
import com.example.chat.ui.kit.GbSettingRow
import com.example.chat.ui.kit.GbSwitchTile
import com.example.chat.ui.kit.rememberGbPickers
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.core.theme.BubbleStyleId
import com.example.chat.core.theme.DeliveryIconStyle
import com.example.chat.core.theme.bubbleStyleIdFromString
import com.example.chat.core.theme.deliveryIconStyleFromString
import com.example.chat.core.theme.gbBubbleVectorInterpretations
import com.example.chat.core.theme.gbTickVectorInterpretations
import com.example.chat.domain.models.DeliveryState
import com.example.chat.ui.chats.detail.DeliveryStatusIcon
import com.example.chat.ui.kit.ChatyBackButton
import com.example.chat.ui.kit.ChatyBubbleContainer
import com.example.chat.ui.kit.ChatyOptionGallery
import com.example.chat.ui.kit.GbCardContainer
import com.example.chat.ui.kit.GbLivePreviewCard
import com.example.chat.ui.kit.GbSliderTile
import com.example.chat.ui.theme.LocalAppColors
import com.example.chat.ui.theme.LocalThemeConfig
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.stateDescription

/** Resolved preview values only, no controller or preference snapshot. */
@androidx.compose.runtime.Immutable
internal data class BubblePreviewState(
    val bubbleStyle: BubbleStyleId,
    val tickStyle: DeliveryIconStyle,
    val convBg: Color,
    val rightBubbleBg: Color,
    val rightTextCol: Color,
    val rightTimeCol: Color,
    val leftBubbleBg: Color,
    val leftTextCol: Color,
    val leftTimeCol: Color,
    val quotedBg: Color,
    val quotedDivider: Color,
    val quotedName: Color,
    val quotedMsg: Color,
    val deletedIconColor: Color,
    val textSize: Float,
    val makeTextSelectable: Boolean,
    val removeReadMore: Boolean,
)

@Composable
internal fun BubblePreview(state: BubblePreviewState, groupedCorners: Boolean = false, messageGapDp: Int? = null) {
    fun groupClip(first: Boolean, last: Boolean): Modifier {
        if (!groupedCorners) return Modifier
        val corners = conversationGroupedCorners(isMe = false, isFirst = first, isLast = last)
        return Modifier.clip(RoundedCornerShape(topStart = corners.topStart.dp, topEnd = corners.topEnd.dp,
            bottomStart = corners.bottomStart.dp, bottomEnd = corners.bottomEnd.dp))
    }
    val strings = androidx.compose.ui.platform.LocalContext.current.resources
    val config = LocalThemeConfig.current
    val fs = config.fontScale.toFloat().let { if (it.isFinite()) it.coerceIn(0.8f, 2f) else 1f }
    val accent = Color(config.accentColor)
    val sampleContext = androidx.compose.ui.platform.LocalContext.current
    val sampleTime = android.text.format.DateFormat.getTimeFormat(sampleContext).format(java.util.Date(0))
    val textSize = state.textSize
    val convBg = state.convBg
    val rightBubbleBg = state.rightBubbleBg
    val rightTextCol = state.rightTextCol
    val rightTimeCol = state.rightTimeCol
    val leftBubbleBg = state.leftBubbleBg
    val leftTextCol = state.leftTextCol
    val leftTimeCol = state.leftTimeCol
    val quotedBg = state.quotedBg
    val quotedDivider = state.quotedDivider
    val quotedName = state.quotedName
    val quotedMsg = state.quotedMsg
            GbLivePreviewCard(modifier = Modifier.semantics { contentDescription = strings.getString(com.example.chat.R.string.conv_ui_sample_conversation_with_inc_d78404) }, padding = PaddingValues(ConversationPreviewLayout.cardPaddingDp.dp /* previous: 12.dp */)) {
                androidx.compose.foundation.layout.BoxWithConstraints {
                val sampleBubbleWidth = maxWidth * config.bubbleMaxWidthFactor.toFloat()
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState()).background(convBg, RoundedCornerShape(16.dp)).padding(ConversationPreviewLayout.cardPaddingDp.dp /* previous: 12.dp */),
                ) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        ChatyBubbleContainer(styleId = state.bubbleStyle, isMe = false,
                            fillColor = leftBubbleBg, accentColor = accent, modifier = groupClip(first = true, last = false).testTag("bubble-preview-incoming").semantics { stateDescription = state.bubbleStyle.name }.widthIn(max = sampleBubbleWidth)) {
                            Column {
                                Text(strings.getString(com.example.chat.R.string.conv_ui_alex_64542d), color = accent, fontSize = (11 * fs).sp, fontWeight = FontWeight.W700)
                                Text(strings.getString(com.example.chat.R.string.conv_ui_hello_69342c), color = leftTextCol, fontSize = textSize.sp)
                            }
                        }
                    }
                    // Previous fixed gap:8.dp. Grouped preview uses4dp between consecutive samples.
                    Box(Modifier.fillMaxWidth().padding(vertical = (com.example.chat.ui.chats.detail.conversationMessageGap(messageGapDp) ?: if (groupedCorners) 4 else 8).dp), contentAlignment = Alignment.CenterStart) {
                        ChatyBubbleContainer(styleId = state.bubbleStyle, isMe = false,
                            fillColor = leftBubbleBg, accentColor = accent, modifier = groupClip(first = false, last = true).testTag("bubble-preview-group-last").widthIn(max = sampleBubbleWidth)) {
                            Column {
                                Text(strings.getString(com.example.chat.R.string.conv_ui_do_you_like_my_theme_757cae), color = leftTextCol, fontSize = textSize.sp)
                                Text(sampleTime, color = leftTimeCol, fontSize = (11 * fs).sp)
                            }
                        }
                    }
                    ChatyBubbleContainer(styleId = state.bubbleStyle, isMe = false,
                        fillColor = leftBubbleBg, accentColor = accent, modifier = Modifier.widthIn(max = sampleBubbleWidth).padding(vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.close,
                                semanticLabel = null,
                                color = state.deletedIconColor,
                                modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(7.dp))
                            Text(strings.getString(com.example.chat.R.string.conv_ui_deleted_message_5426bd),
                                color = Color(config.secondaryTextColor), fontSize = (12 * fs).sp)
                        }
                        /* Previous sample coloured only text, not the deleted icon.
                        Text(strings.getString(com.example.chat.R.string.conv_ui_deleted_message_5426bd),
                            color = state.deletedIconColor, fontSize = (12 * fs).sp)
                        */
                    }
                    val longSample = strings.getString(com.example.chat.R.string.conv_ui_a_longer_preview_message_sho_ead0e2).repeat(5)
                    val sample: @Composable () -> Unit = {
                        Text(longSample, color = leftTextCol, fontSize = textSize.sp,
                            maxLines = if (state.removeReadMore) Int.MAX_VALUE else 3,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    ChatyBubbleContainer(styleId = state.bubbleStyle, isMe = false,
                        fillColor = leftBubbleBg, accentColor = accent, modifier = Modifier.widthIn(max = sampleBubbleWidth).padding(vertical = 8.dp)) {
                        Column {
                            if (state.makeTextSelectable) androidx.compose.foundation.text.selection.SelectionContainer { sample() } else sample()
                            if (!state.removeReadMore) Text(strings.getString(com.example.chat.R.string.conv_ui_read_more_ad086c), color = accent, fontSize = (12 * fs).sp)
                        }
                    }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Box(Modifier.widthIn(max = sampleBubbleWidth)) {
                            ChatyBubbleContainer(
                                styleId = state.bubbleStyle,
                                isMe = true, fillColor = rightBubbleBg, accentColor = accent,
                                modifier = Modifier.testTag("bubble-preview-outgoing").semantics { stateDescription = state.bubbleStyle.name },
                            ) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Column(
                                        Modifier.padding(bottom = ConversationPreviewLayout.detailGapDp.dp /* previous: 4.dp */)
                                            .clip(RoundedCornerShape(8.dp)).background(quotedBg, RoundedCornerShape(8.dp))
                                            .drawBehind {
                                                // Border(left: 3.5) over rounded 8 box
                                                drawRect(quotedDivider, Offset.Zero, androidx.compose.ui.geometry.Size(3.5.dp.toPx(), size.height))
                                            }
                                            .padding(ConversationPreviewLayout.quotePaddingDp.dp /* previous: 6.dp */),
                                    ) {
                                        Text(strings.getString(com.example.chat.R.string.conv_ui_alex_64542d), color = quotedName, fontSize = (11 * fs).sp, fontWeight = FontWeight.Bold)
                                        Text(strings.getString(com.example.chat.R.string.conv_ui_do_you_like_my_theme_757cae), color = quotedMsg, fontSize = (11 * fs).sp, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    }
                                    Text(strings.getString(com.example.chat.R.string.conv_ui_yes_your_theme_looks_awesome_c283ee), color = rightTextCol, fontSize = textSize.sp)
                                    Spacer(Modifier.height(ConversationPreviewLayout.detailGapDp.dp /* previous: 3.dp */))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(sampleTime, color = rightTimeCol, fontSize = (11 * fs).sp)
                                        Spacer(Modifier.width(ConversationPreviewLayout.detailGapDp.dp /* previous: 4.dp */))
                                        ConversationTickPreview(state.tickStyle, rightTimeCol)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
}

@Composable
internal fun ConversationTickPreview(id: DeliveryIconStyle, color: Color) {
    val accent = Color(LocalThemeConfig.current.accentColor)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ConversationStyleRegistry.tick(id).previewStates.forEach { state ->
            DeliveryStatusIcon(style = id, state = state, unreadColor = color, readColor = accent, size = 16.dp)
        }
    }
}
