package com.example.chat.ui.settings.conversation

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
// Unused legacy import: import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
// Unused legacy import: import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
// Unused legacy import: import androidx.compose.foundation.layout.size
// Unused legacy import: import androidx.compose.foundation.layout.width
// Unused legacy import: import androidx.compose.foundation.shape.CircleShape
// Unused legacy import: import androidx.compose.foundation.shape.RoundedCornerShape
// Unused legacy import: import androidx.compose.material.icons.Icons
// Unused legacy import: import androidx.compose.material.icons.outlined.AccountCircle
// Unused legacy import: import androidx.compose.material.icons.outlined.PersonOff
// Unused legacy import: import androidx.compose.material.icons.rounded.ChevronLeft
// Unused legacy import: import androidx.compose.material.icons.rounded.Call
// Unused legacy import: import androidx.compose.material.icons.rounded.FormatColorText
// Unused legacy import: import androidx.compose.material.icons.rounded.FormatPaint
// Unused legacy import: import androidx.compose.material.icons.rounded.MoreVert
// Unused legacy import: import androidx.compose.material.icons.rounded.Person
// Unused legacy import: import androidx.compose.material.icons.rounded.PhoneDisabled
// Unused legacy import: import androidx.compose.material.icons.rounded.SpeakerNotesOff
// Unused legacy import: import androidx.compose.material.icons.rounded.Videocam
// Unused legacy import: import androidx.compose.material.icons.rounded.Web
// Unused legacy import: import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
// Unused legacy import: import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
// Unused legacy import: import androidx.compose.runtime.mutableIntStateOf
// Unused legacy import: import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
// Unused legacy import: import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
// Unused legacy import: import androidx.compose.ui.text.font.FontStyle
// Unused legacy import: import androidx.compose.ui.text.font.FontWeight
// Unused legacy import: import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
// Unused legacy import: import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.chat.core.preferences.ChatyPreferencesController
// Unused legacy import: import com.example.chat.ui.kit.ChatyBackButton
import com.example.chat.ui.kit.GbCardContainer
import com.example.chat.ui.kit.GbLivePreviewCard
// Unused legacy import: import com.example.chat.ui.kit.GbSettingRow
import com.example.chat.ui.kit.GbSwitchTile
import com.example.chat.ui.kit.rememberGbPickers
import com.example.chat.ui.theme.LocalAppColors
import com.example.chat.ui.theme.LocalThemeConfig

/** Seven preference fields plus ephemeral sample controls; no controller reads or effects. */
@Composable
internal fun ConversationHeaderPreview(
    hideProfilePicture: Boolean = false,
    hideContactName: Boolean = false,
    hideCallButton: Boolean = false,
    disableContactStatus: Boolean = false,
    actionBarColor: Long? = null,
    contactStatusBgColor: Long? = null,
    contactStatusTextColor: Long? = null,
    longSampleName: Boolean = false,
    samplePresence: Int = 0,
    sampleMuted: Boolean = false,
    samplePinned: Boolean = false,
    onCallPreviewTap: () -> Unit = {},
    appearance: com.example.chat.ui.chats.detail.ConversationHeaderAppearance = com.example.chat.ui.chats.detail.ConversationHeaderAppearance(),
) {
    val strings = androidx.compose.ui.platform.LocalContext.current.resources
    val config = LocalThemeConfig.current
            // Previous inline English accessibility summary retained below.
            // Preview. Name hidden/shown. Profile picture hidden/shown. Call buttons hidden/shown. Status line hidden/shown.
            GbLivePreviewCard(modifier = Modifier.semantics(mergeDescendants = true) {
                contentDescription = strings.getString(com.example.chat.R.string.conv_proposal_header_summary,
                    strings.getString(if (hideContactName) com.example.chat.R.string.conv_proposal_hidden else com.example.chat.R.string.conv_proposal_shown),
                    strings.getString(if (hideProfilePicture) com.example.chat.R.string.conv_proposal_hidden else com.example.chat.R.string.conv_proposal_shown),
                    strings.getString(if (hideCallButton) com.example.chat.R.string.conv_proposal_hidden else com.example.chat.R.string.conv_proposal_shown),
                    strings.getString(if (disableContactStatus || appearance.effective().subtitleMode == "none") com.example.chat.R.string.conv_proposal_hidden else com.example.chat.R.string.conv_proposal_shown))
                liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite }, padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth()) {
                    if (appearance.effective().alphaPercent < 100) com.example.chat.ui.chats.detail.ChatWallpaper(
                        theme = config,wallpaperType = "Pattern",patternId = config.wallpaperId,modifier = Modifier.matchParentSize())
                Column {
                com.example.chat.ui.chats.ConversationHeader(
                    theme = config,
                    conversation = com.example.chat.domain.models.Conversation(
                        id = "appearance-preview", type = com.example.chat.domain.models.ConversationType.direct,
                        title = if (longSampleName) strings.getString(com.example.chat.R.string.conv_proposal_long_name)
                            else strings.getString(com.example.chat.R.string.conv_ui_alex_64542d),
                        participantIds = emptyList(), avatarInitials = "AR", lastMessageText = "",
                        lastMessageTime = java.time.Instant.EPOCH, lastMessageSenderId = "",
                    ),
                    presenceText = com.example.chat.ui.chats.detail.conversationHeaderSubtitle(appearance.effective().subtitleMode, when (samplePresence % 4) {
                        1 -> strings.getString(com.example.chat.R.string.conv_proposal_typing); 2 -> strings.getString(com.example.chat.R.string.conv_proposal_last_seen)
                        3 -> strings.getString(com.example.chat.R.string.conv_proposal_long_presence)
                        else -> strings.getString(com.example.chat.R.string.conv_ui_online_c3e839)
                    }, strings.getString(com.example.chat.R.string.conv_proposal_subtitle_sample), true),
                    presenceIsOnline = samplePresence % 4 == 0 && appearance.effective().subtitleMode == "presence",
                    showHeaderAvatar = !hideProfilePicture, showHeaderName = !hideContactName,
                    showPresenceLine = !disableContactStatus, showHeaderCalls = !hideCallButton,
                    headerBarColor = actionBarColor, presencePillColor = contactStatusBgColor,
                    presenceTextColor = contactStatusTextColor, callsAllowed = true,
                    headerAppearance = appearance, unreadChatCount = 3,
                    onStartCall = { onCallPreviewTap() }, connectionState = null, onConnectionDetailsTap = {}, onBack = {},
                    onOpenInfo = {}, menuOpen = false, onMenuOpenChange = {}, onSearch = {},
                    onOpenWallpaperPicker = {}, onOpenAppearance = null, onToggleMute = null,
                    ghostModeEnabled = false, onToggleGhostMode = null, secureChannelEnabled = false,
                    onToggleSecureChannel = null, onBlockContact = null, onClearConversation = {},
                    headerTrailingContent = if (!sampleMuted && !samplePinned) null else ({
                        androidx.compose.foundation.layout.Row(Modifier.padding(start = 4.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
                            if (sampleMuted) com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.mute,
                                size = 16.dp, color = Color(config.secondaryTextColor),
                                semanticLabel = strings.getString(com.example.chat.R.string.conv_proposal_sample_muted),
                                modifier = Modifier.testTag("conversation-header-sample-muted"))
                            if (samplePinned) com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.pin,
                                size = 16.dp, color = Color(config.secondaryTextColor),
                                semanticLabel = strings.getString(com.example.chat.R.string.conv_proposal_sample_pinned),
                                modifier = Modifier.testTag("conversation-header-sample-pinned"))
                        }
                    }),
                )
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        com.example.chat.ui.kit.ChatyBubbleContainer(styleId = config.bubbleStyle, isMe = false,
                            fillColor = Color(config.incomingBubbleColor), accentColor = Color(config.accentColor)) {
                            Text(strings.getString(com.example.chat.R.string.conv_proposal_incoming_sample),
                                color = Color(config.incomingTextColor), fontSize = (14 * config.fontScale).sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                            com.example.chat.ui.kit.ChatyBubbleContainer(styleId = config.bubbleStyle, isMe = true,
                                fillColor = Color(config.outgoingBubbleColor), accentColor = Color(config.accentColor)) {
                                Text(strings.getString(com.example.chat.R.string.conv_proposal_outgoing_sample),
                                    color = Color(config.outgoingTextColor), fontSize = (14 * config.fontScale).sp)
                            }
                        }
                    }
                }

                }
            }
}
