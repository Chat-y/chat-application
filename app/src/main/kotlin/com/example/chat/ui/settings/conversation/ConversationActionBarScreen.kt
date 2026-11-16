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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.FormatColorText
import androidx.compose.material.icons.rounded.FormatPaint
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneDisabled
import androidx.compose.material.icons.rounded.SpeakerNotesOff
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Web
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.ui.kit.ChatyBackButton
import com.example.chat.ui.kit.GbCardContainer
import com.example.chat.ui.kit.GbLivePreviewCard
import com.example.chat.ui.kit.GbSettingRow
import com.example.chat.ui.kit.GbSwitchTile
import com.example.chat.ui.kit.rememberGbPickers
import com.example.chat.ui.theme.LocalAppColors
import com.example.chat.ui.theme.LocalThemeConfig

/**
 * Port of ConversationActionBarScreen. The preview colours fall back to theme surface /
 * surfaceSecondary / secondaryText when the preference is null. UNCOMPILED (Compose).
 */
@Composable
fun ConversationActionBarScreen(preferences: ChatyPreferencesController, onBack: () -> Unit) {
    val strings = androidx.compose.ui.platform.LocalContext.current.resources
    val config = LocalThemeConfig.current
    val colors = LocalAppColors.current
    // Legacy whole-controller version listener replaced by a conversation-only snapshot.
    val p = rememberConversationPreferences(preferences)
    val pickers = rememberGbPickers()
    var longSampleName by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var samplePresence by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }

    val accent = Color(config.accentColor)
    val primaryText = Color(config.primaryTextColor)
    val secondaryText = Color(config.secondaryTextColor)
    val barColor = p.actionBarColor?.let { Color(it) } ?: Color(config.surfaceColor)
    val statusBg = p.contactStatusBgColor?.let { Color(it) } ?: colors.surfaceSecondary
    val statusText = p.contactStatusTextColor?.let { Color(it) } ?: secondaryText
    val fs = config.fontScale.toFloat().let { if (it.isFinite()) it.coerceIn(0.8f, 2f) else 1f }

    fun pickColor(title: String, current: Long?, apply: (Long) -> Unit) =
        pickers.showColor(title, current ?: config.accentColor) { apply(it) }

    Column(Modifier.fillMaxSize().background(Color(config.backgroundColor))) {
        com.example.chat.ui.kit.ChatyAppBar(title = strings.getString(com.example.chat.R.string.conv_ui_chat_header_beac6d), onBack = onBack)
        Column(Modifier.weight(1f).align(Alignment.CenterHorizontally).widthIn(max = 600.dp).fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)).padding(horizontal = 16.dp, vertical = 16.dp)) {
            Text(strings.getString(com.example.chat.R.string.conv_ui_preview_f1fbb2), color = secondaryText, fontSize = 12.sp)
            if (p.hideProfilePicture && p.hideContactName) Text(strings.getString(com.example.chat.R.string.conv_ui_you_will_not_see_who_you_are_3ef955), color = primaryText, fontSize = 14.sp)
            GbLivePreviewCard(modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "Preview. Name ${if (p.hideContactName) "hidden" else "shown"}. Profile picture ${if (p.hideProfilePicture) "hidden" else "shown"}. Call buttons ${if (p.hideCallButton) "hidden" else "shown"}. Status line ${if (p.disableContactStatus) "hidden" else "shown"}."
                liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite }, padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Column {
                    Row(
                        Modifier.fillMaxWidth()
                            .background(barColor, if (p.disableContactStatus) RoundedCornerShape(16.dp) else RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.back, semanticLabel = null, color = primaryText, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        if (!p.hideProfilePicture) {
                            Box(Modifier.size(36.dp).background(colors.surfaceSecondary, CircleShape), contentAlignment = Alignment.Center) {
                                com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.profile, semanticLabel = null, color = secondaryText, modifier = Modifier.testTag("conversation-header-avatar").size(24.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            if (!p.hideContactName) Text(if (longSampleName) "Alexandra Rao · Long sample contact name for a compact header" else strings.getString(com.example.chat.R.string.conv_ui_alex_64542d), modifier = Modifier.testTag("conversation-header-name"), color = primaryText, fontWeight = FontWeight.W700, fontSize = (16 * fs).sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(when (samplePresence % 3) { 1 -> "typing..."; 2 -> "Last seen recently"; else -> strings.getString(com.example.chat.R.string.conv_ui_online_c3e839) }, color = if (conversationContrast(accent, barColor) >= 4.5f) accent else secondaryText, fontSize = (12 * fs).sp)
                        }
                        if (!p.hideCallButton) {
                            com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.callAudio, semanticLabel = null, color = primaryText, modifier = Modifier.testTag("conversation-header-audio").size(24.dp))
                            Spacer(Modifier.width(14.dp))
                            com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.callVideo, semanticLabel = null, color = primaryText, modifier = Modifier.testTag("conversation-header-video").size(24.dp))
                            Spacer(Modifier.width(12.dp))
                        }
                        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.moreOptions, semanticLabel = null, color = primaryText, modifier = Modifier.size(20.dp))
                    }
                    if (!p.disableContactStatus) {
                        Text(
                            strings.getString(com.example.chat.R.string.conv_ui_hey_there_i_am_using_chaty_5ad60b),
                            Modifier.fillMaxWidth().testTag("conversation-header-status")
                                .background(statusBg, RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp))
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            color = statusText, fontSize = (13 * fs).sp, maxLines = 2, textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            androidx.compose.material3.TextButton(onClick = { samplePresence = (samplePresence + 1) % 3 }) {
                Text("Cycle presence preview")
            }
            if (com.example.chat.BuildConfig.DEBUG) androidx.compose.material3.TextButton(onClick = { longSampleName = !longSampleName }) {
                Text(if (longSampleName) "Use short sample name" else "Use long sample name")
            }
            Spacer(Modifier.height(16.dp))
            ConversationContrastNote(primaryText, barColor, 4.5f, strings.getString(com.example.chat.R.string.conv_ui_header_text_7f7131))
            ConversationContrastNote(statusText, statusBg, 4.5f, strings.getString(com.example.chat.R.string.conv_ui_status_text_b68390))
            GbCardContainer(headerText = strings.getString(com.example.chat.R.string.conv_ui_show_in_header_43f0df)) {
                GbSwitchTile(com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.profile), strings.getString(com.example.chat.R.string.conv_ui_hide_contact_profile_picture_7aaf75), p.hideProfilePicture, {
                    preferences.editConversation { latest -> latest.copy(hideProfilePicture = it) }
                }, strings.getString(com.example.chat.R.string.conv_ui_header_only_your_contacts_ar_233f2c))
                GbSwitchTile(com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.personOff), strings.getString(com.example.chat.R.string.conv_ui_hide_contact_name_c646d4), p.hideContactName, {
                    preferences.editConversation { latest -> latest.copy(hideContactName = it) }
                }, strings.getString(com.example.chat.R.string.conv_ui_header_only_notifications_an_9cabcf))
                GbSwitchTile(com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.phone), strings.getString(com.example.chat.R.string.conv_ui_hide_call_buttons_e56c01), p.hideCallButton, {
                    preferences.editConversation { latest -> latest.copy(hideCallButton = it) }
                }, strings.getString(com.example.chat.R.string.conv_ui_hide_audio_and_video_call_sh_32d3f8))
                GbSwitchTile(com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.personOff), strings.getString(com.example.chat.R.string.conv_ui_hide_status_line_b148da), p.disableContactStatus, {
                    preferences.editConversation { latest -> latest.copy(disableContactStatus = it) }
                }, strings.getString(com.example.chat.R.string.conv_ui_hide_the_line_below_the_chat_c2bc9e))
            }
            Spacer(Modifier.height(16.dp))
            GbCardContainer(headerText = strings.getString(com.example.chat.R.string.conv_ui_colours_eb4e91)) {
                ConversationColorRow(
                    title = strings.getString(com.example.chat.R.string.conv_ui_header_background_394ff0), icon = com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.dock), subtitle = strings.getString(com.example.chat.R.string.conv_ui_background_behind_the_contac_758a6d),
                    colorValue = p.actionBarColor,
                    onColorTap = { pickColor(strings.getString(com.example.chat.R.string.conv_ui_action_bar_color_77c122), (barColor).toArgb().toLong() and 0xFFFFFFFFL) { preferences.editConversation { latest -> latest.copy(actionBarColor = it) } } },
                    effectiveColor = barColor,
                    onReset = { preferences.editConversation { latest -> latest.copy(actionBarColor = null) } },
                )
                ConversationColorRow(
                    title = strings.getString(com.example.chat.R.string.conv_ui_status_background_3da647), icon = com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.paint),
                    subtitle = if (p.disableContactStatus) strings.getString(com.example.chat.R.string.conv_ui_turn_on_the_status_line_to_u_587f77) else strings.getString(com.example.chat.R.string.conv_ui_colour_behind_the_status_lin_e7f000),
                    colorValue = p.contactStatusBgColor,
                    enabled = !p.disableContactStatus,
                    onColorTap = { pickColor(strings.getString(com.example.chat.R.string.conv_ui_status_background_color_27cecc), (statusBg).toArgb().toLong() and 0xFFFFFFFFL) { preferences.editConversation { latest -> latest.copy(contactStatusBgColor = it) } } },
                    effectiveColor = statusBg,
                    onReset = { preferences.editConversation { latest -> latest.copy(contactStatusBgColor = null) } },
                )
                ConversationColorRow(
                    title = strings.getString(com.example.chat.R.string.conv_ui_status_text_b68390), icon = com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.textFormat),
                    subtitle = if (p.disableContactStatus) strings.getString(com.example.chat.R.string.conv_ui_turn_on_the_status_line_to_u_587f77) else strings.getString(com.example.chat.R.string.conv_ui_text_in_the_status_line_b420ec),
                    colorValue = p.contactStatusTextColor,
                    enabled = !p.disableContactStatus,
                    onColorTap = { pickColor(strings.getString(com.example.chat.R.string.conv_ui_status_text_color_78882a), (statusText).toArgb().toLong() and 0xFFFFFFFFL) { preferences.editConversation { latest -> latest.copy(contactStatusTextColor = it) } } },
                    effectiveColor = statusText,
                    onReset = { preferences.editConversation { latest -> latest.copy(contactStatusTextColor = null) } },
                )
            }
            androidx.compose.material3.TextButton(onClick = {
                preferences.editConversation { latest -> latest.copy(actionBarColor = null, hideProfilePicture = false,
                    hideContactName = false, hideCallButton = false, disableContactStatus = false,
                    contactStatusBgColor = null, contactStatusTextColor = null) }
            }) { Text(strings.getString(com.example.chat.R.string.conv_ui_classic_header_preset_bec9e1)) }
            Text(strings.getString(com.example.chat.R.string.conv_ui_header_appearance_is_include_e9b259), color = secondaryText, fontSize = 12.sp)
            ConversationSupportCopy(mapOf("actionBarColor" to p.actionBarColor, "hideProfilePicture" to p.hideProfilePicture,
                "hideContactName" to p.hideContactName, "hideCallButton" to p.hideCallButton,
                "disableContactStatus" to p.disableContactStatus, "contactStatusBgColor" to p.contactStatusBgColor,
                "contactStatusTextColor" to p.contactStatusTextColor))
        }
    }
    pickers.Host()
}
