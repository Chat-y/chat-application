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
import kotlinx.coroutines.launch
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.heading
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

/**
 * Port of ConversationActionBarScreen. The preview colours fall back to theme surface /
 * surfaceSecondary / secondaryText when the preference is null. UNCOMPILED (Compose).
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ConversationActionBarScreen(preferences: ChatyPreferencesController, onBack: () -> Unit) {
    val strings = androidx.compose.ui.platform.LocalContext.current.resources
    val config = LocalThemeConfig.current
    val colors = LocalAppColors.current
    val materialColors = androidx.compose.material3.MaterialTheme.colorScheme
    // Legacy whole-controller version listener replaced by a conversation-only snapshot.
    // Previous broad snapshot: val p = rememberConversationPreferences(preferences)
    // Previous screen snapshot: val p = rememberConversationPreferences(preferences, ::selectActionBarPreferences)
    val model: ActionBarViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        key = "conversation-header-${System.identityHashCode(preferences)}",
        factory = androidx.compose.runtime.remember(preferences) {
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(ActionBarViewModel::class.java))
                    return ActionBarViewModel(read = { preferences.conversation },
                        write = { preferences.updateConversation(it) },
                        subscribe = { preferences.addListener(it) }, unsubscribe = { preferences.removeListener(it) },
                        transaction = { preferences.editConversation(it) },
                        enableDebugUsage = com.example.chat.BuildConfig.DEBUG) as T
                }
            }
        },
    )
    // Lifecycle-aware collection is separately blocked by the excluded dependency/controller work.
    val p by model.uiState.collectAsState()
    val debugUsage by model.debugUsage.collectAsState()
    val headerAppearance = rememberConversationHeaderAppearance(preferences)
    var openColourTarget by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val headerCallbacks = androidx.compose.runtime.remember(preferences,model) {
        ConversationHeaderCallbacks(onEvent = model::onEvent,onPickerTarget = { openColourTarget = it.name },
            afterReset = { preferences.updateGbFeatures(com.example.chat.ui.chats.detail.ConversationHeaderAppearance.defaultValues()) })
    }
    val pickers = rememberGbPickers()
    var previewOtherMode by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var longSampleName by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var samplePresence by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
    var sampleIndicators by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var highlightCalls by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val callRowRequester = androidx.compose.runtime.remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    val previewScope = androidx.compose.runtime.rememberCoroutineScope()
    androidx.compose.runtime.LaunchedEffect(highlightCalls) {
        if (highlightCalls) { kotlinx.coroutines.delay(2000); highlightCalls = false }
    }

    val accent = Color(config.accentColor)
    val primaryText = Color(config.primaryTextColor)
    val secondaryText = Color(config.secondaryTextColor)
    val resolvedHeaderColors = resolveConversationHeaderColors(p.actionBarColor, p.contactStatusBgColor,
        /* Previous fallback arguments: p.contactStatusTextColor, config.surfaceColor,
        colors.surfaceSecondary.toArgb().toLong() and 0xFFFFFFFFL, config.secondaryTextColor) */
        p.contactStatusTextColor,
        materialColors.surface.toArgb().toLong() and 0xFFFFFFFFL,
        materialColors.surfaceVariant.toArgb().toLong() and 0xFFFFFFFFL,
        materialColors.onSurfaceVariant.toArgb().toLong() and 0xFFFFFFFFL)
    val barColor = Color(resolvedHeaderColors.bar)
    val statusBg = Color(resolvedHeaderColors.statusBackground)
    val statusText = Color(resolvedHeaderColors.statusText)
    /* Previous inline fallbacks retained for reference.
    val barColor = p.actionBarColor?.let { Color(it) } ?: Color(config.surfaceColor)
    val statusBg = p.contactStatusBgColor?.let { Color(it) } ?: colors.surfaceSecondary
    val statusText = p.contactStatusTextColor?.let { Color(it) } ?: secondaryText
    */
    val fs = config.fontScale.toFloat().let { if (it.isFinite()) it.coerceIn(0.8f, 2f) else 1f }

    fun pickColor(title: String, current: Long?, apply: (Long) -> Unit) =
        pickers.showColor(title, current ?: config.accentColor) { apply(it) }

    // Previous per-screen shell: Column(Modifier.fillMaxSize().background(Color(config.backgroundColor))) {
    // Previous per-screen header: com.example.chat.ui.kit.ChatyAppBar(title = strings.getString(com.example.chat.R.string.conv_ui_chat_header_beac6d), onBack = onBack, titleModifier = Modifier.semantics { heading() })
    ConversationSettingsScaffold(title = strings.getString(com.example.chat.R.string.conv_ui_chat_header_beac6d), onBack = onBack,
        titleTextStyle = androidx.compose.material3.MaterialTheme.typography.titleMedium) {

        // Previous compact body: Column(Modifier.weight(1f).align(Alignment.CenterHorizontally).widthIn(max = 600.dp).fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)).padding(horizontal = ConversationPreviewLayout.outerPaddingDp.dp, vertical = ConversationPreviewLayout.outerPaddingDp.dp)) {
        ConversationAdaptiveSettingsBody(
            modifier = Modifier.weight(1f).fillMaxWidth().navigationBarsPadding().imePadding()
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)),
            preview = {
            Text(strings.getString(com.example.chat.R.string.conv_ui_preview_f1fbb2), color = secondaryText, /* Previous caption size:12.sp */ style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            // Previous inline warning: if (p.hideProfilePicture && p.hideContactName) Text(strings.getString(com.example.chat.R.string.conv_ui_you_will_not_see_who_you_are_3ef955), color = primaryText, fontSize = 14.sp)
            ConversationHeaderIdentityWarning(p.hideProfilePicture, p.hideContactName)
            Text(strings.getString(com.example.chat.R.string.conv_proposal_header_size_choice), color = secondaryText, /* Previous caption size:12.sp */ style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            // Previous stock text action:
            // androidx.compose.material3.TextButton(onClick = { previewOtherMode = !previewOtherMode }) {
            //                 Text(strings.getString(if (previewOtherMode) com.example.chat.R.string.conv_proposal_use_app_mode
            //                     else com.example.chat.R.string.conv_proposal_other_mode))
            //             }
            com.example.chat.ui.kit.ChatyTextButton(
                label = strings.getString(if (previewOtherMode) com.example.chat.R.string.conv_proposal_use_app_mode
                    else com.example.chat.R.string.conv_proposal_other_mode),
                onClick = { previewOtherMode = !previewOtherMode },
            )
            com.example.chat.ui.theme.ChatyTheme(conversationAlternatePreviewTheme(config, previewOtherMode)) {
            ConversationHeaderPreview(hideProfilePicture = p.hideProfilePicture,
                hideContactName = p.hideContactName, hideCallButton = p.hideCallButton,
                disableContactStatus = p.disableContactStatus, actionBarColor = opaqueConversationStoredColor(p.actionBarColor),
                contactStatusBgColor = opaqueConversationStoredColor(p.contactStatusBgColor), contactStatusTextColor = opaqueConversationStoredColor(p.contactStatusTextColor),
                longSampleName = longSampleName, samplePresence = samplePresence,
                sampleMuted = sampleIndicators, samplePinned = sampleIndicators,
                appearance = headerAppearance,
                onCallPreviewTap = { highlightCalls = true; previewScope.launch { callRowRequester.bringIntoView() } })
            }
            /* Previous inline preview body retained, now rendered by ConversationHeaderPreview.
            // Previous inline English accessibility summary retained below.
            // Preview. Name hidden/shown. Profile picture hidden/shown. Call buttons hidden/shown. Status line hidden/shown.
            GbLivePreviewCard(modifier = Modifier.semantics(mergeDescendants = true) {
                contentDescription = strings.getString(com.example.chat.R.string.conv_proposal_header_summary,
                    strings.getString(if (p.hideContactName) com.example.chat.R.string.conv_proposal_hidden else com.example.chat.R.string.conv_proposal_shown),
                    strings.getString(if (p.hideProfilePicture) com.example.chat.R.string.conv_proposal_hidden else com.example.chat.R.string.conv_proposal_shown),
                    strings.getString(if (p.hideCallButton) com.example.chat.R.string.conv_proposal_hidden else com.example.chat.R.string.conv_proposal_shown),
                    strings.getString(if (p.disableContactStatus) com.example.chat.R.string.conv_proposal_hidden else com.example.chat.R.string.conv_proposal_shown))
                liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite }, padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                com.example.chat.ui.chats.ConversationHeader(
                    theme = config,
                    conversation = com.example.chat.domain.models.Conversation(
                        id = "appearance-preview", type = com.example.chat.domain.models.ConversationType.direct,
                        title = if (longSampleName) strings.getString(com.example.chat.R.string.conv_proposal_long_name)
                            else strings.getString(com.example.chat.R.string.conv_ui_alex_64542d),
                        participantIds = emptyList(), avatarInitials = "AR", lastMessageText = "",
                        lastMessageTime = java.time.Instant.EPOCH, lastMessageSenderId = "",
                    ),
                    presenceText = when (samplePresence % 3) {
                        1 -> strings.getString(com.example.chat.R.string.conv_proposal_typing); 2 -> strings.getString(com.example.chat.R.string.conv_proposal_last_seen)
                        else -> strings.getString(com.example.chat.R.string.conv_ui_online_c3e839)
                    },
                    presenceIsOnline = samplePresence % 3 == 0,
                    showHeaderAvatar = !p.hideProfilePicture, showHeaderName = !p.hideContactName,
                    showPresenceLine = !p.disableContactStatus, showHeaderCalls = !p.hideCallButton,
                    headerBarColor = p.actionBarColor, presencePillColor = p.contactStatusBgColor,
                    presenceTextColor = p.contactStatusTextColor, callsAllowed = true,
                    onStartCall = {}, connectionState = null, onConnectionDetailsTap = {}, onBack = {},
                    onOpenInfo = {}, menuOpen = false, onMenuOpenChange = {}, onSearch = {},
                    onOpenWallpaperPicker = {}, onOpenAppearance = null, onToggleMute = null,
                    ghostModeEnabled = false, onToggleGhostMode = null, secureChannelEnabled = false,
                    onToggleSecureChannel = null, onBlockContact = null, onClearConversation = {},
                )
            }
            /* Previous hand-built preview retained, inactive.
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
            */
            */
            // Previous stock text action:
            // androidx.compose.material3.TextButton(onClick = { samplePresence = (samplePresence + 1) % 4 }) {
            //                 Text(strings.getString(com.example.chat.R.string.conv_proposal_cycle_presence))
            //             }
            com.example.chat.ui.kit.ChatyTextButton(
                label = strings.getString(com.example.chat.R.string.conv_proposal_cycle_presence),
                onClick = { samplePresence = (samplePresence + 1) % 4 },
            )
            // Previous stock text action:
            // androidx.compose.material3.TextButton(onClick = { sampleIndicators = !sampleIndicators }) {
            //                 Text(strings.getString(if (sampleIndicators) com.example.chat.R.string.conv_proposal_hide_sample_indicators
            //                     else com.example.chat.R.string.conv_proposal_show_sample_indicators))
            //             }
            com.example.chat.ui.kit.ChatyTextButton(
                label = strings.getString(if (sampleIndicators) com.example.chat.R.string.conv_proposal_hide_sample_indicators
                    else com.example.chat.R.string.conv_proposal_show_sample_indicators),
                onClick = { sampleIndicators = !sampleIndicators },
            )
            if (com.example.chat.BuildConfig.DEBUG) // Previous stock text action:
            // androidx.compose.material3.TextButton(onClick = { longSampleName = !longSampleName }) {
            //                 Text(if (longSampleName) strings.getString(com.example.chat.R.string.conv_proposal_short_name) else strings.getString(com.example.chat.R.string.conv_proposal_use_long_name))
            //             }
            com.example.chat.ui.kit.ChatyTextButton(
                label = if (longSampleName) strings.getString(com.example.chat.R.string.conv_proposal_short_name) else strings.getString(com.example.chat.R.string.conv_proposal_use_long_name),
                onClick = { longSampleName = !longSampleName },
            )
            Spacer(Modifier.height(ConversationPreviewLayout.sectionGapDp.dp))
            },
            controls = {
            ConversationHeaderAppearanceControls(preferences, headerAppearance)
            ConversationContrastNote(primaryText, barColor, 4.5f, strings.getString(com.example.chat.R.string.conv_ui_header_text_7f7131))
            ConversationContrastNote(statusText, statusBg, 4.5f, strings.getString(com.example.chat.R.string.conv_ui_status_text_b68390))
            // Conservative row-order choice: keep the header colour first; status colours follow their visibility switch.
            GbCardContainer(headerText = strings.getString(com.example.chat.R.string.conv_ui_colours_eb4e91)) {
                ConversationColorRow(subtitleMaxLines = 1,
                    title = strings.getString(com.example.chat.R.string.conv_ui_header_background_394ff0), iconContent = { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.dock, semanticLabel = null) } /* Previous vector: icon = com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.dock) */, subtitle = strings.getString(com.example.chat.R.string.conv_ui_background_behind_the_contac_758a6d),
                    colorValue = p.actionBarColor,
                    // Previous non-saveable callback: onColorTap = { pickColor(strings.getString(com.example.chat.R.string.conv_ui_action_bar_color_77c122), (barColor).toArgb().toLong() and 0xFFFFFFFFL) { preferences.editConversation { latest -> latest.copy(actionBarColor = it) } } },
                    // Previous inline target callback retained by the remembered handler.
                    onColorTap = headerCallbacks.pickBar,
                    effectiveColor = barColor,
                    // Previous reset: onReset = { preferences.editConversation { latest -> latest.copy(actionBarColor = null) } },
                    onReset = headerCallbacks.resetBar,
                )
            }
            Spacer(Modifier.height(ConversationPreviewLayout.sectionGapDp.dp))
            GbCardContainer(headerText = strings.getString(com.example.chat.R.string.conv_ui_show_in_header_43f0df)) {
                com.example.chat.ui.kit.GbSettingRow(subtitleMaxLines = 1, iconContent = { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.profile, semanticLabel = null) }, title = strings.getString(com.example.chat.R.string.conv_ui_hide_contact_profile_picture_7aaf75), switchValue = p.hideProfilePicture, /* Previous inline ToggleAvatar callback dispatched the same model event. */ onSwitchChanged = headerCallbacks.toggleAvatar, subtitle = strings.getString(com.example.chat.R.string.conv_ui_header_only_your_contacts_ar_233f2c))
                com.example.chat.ui.kit.GbSettingRow(subtitleMaxLines = 1, iconContent = { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.personOff, semanticLabel = null) }, title = strings.getString(com.example.chat.R.string.conv_ui_hide_contact_name_c646d4), switchValue = p.hideContactName, /* Previous inline ToggleName callback dispatched the same model event. */ onSwitchChanged = headerCallbacks.toggleName, subtitle = strings.getString(com.example.chat.R.string.conv_ui_header_only_notifications_an_9cabcf))
                androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().bringIntoViewRequester(callRowRequester)
                    .then(if (highlightCalls) Modifier.background(Color(config.accentColor).copy(alpha = 0.12f)) else Modifier)
                    .semantics { stateDescription = if (highlightCalls) strings.getString(com.example.chat.R.string.conv_proposal_highlighted_setting) else "" }) {
                com.example.chat.ui.kit.GbSettingRow(subtitleMaxLines = 1, iconContent = { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.phone, semanticLabel = null) }, title = strings.getString(com.example.chat.R.string.conv_ui_hide_call_buttons_e56c01), switchValue = p.hideCallButton, /* Previous inline ToggleCall callback dispatched the same model event. */ onSwitchChanged = headerCallbacks.toggleCall, subtitle = strings.getString(com.example.chat.R.string.conv_ui_hide_audio_and_video_call_sh_32d3f8))
                }
                com.example.chat.ui.kit.GbSettingRow(subtitleMaxLines = 1, iconContent = { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.personOff, semanticLabel = null) }, title = strings.getString(com.example.chat.R.string.conv_ui_hide_status_line_b148da), switchValue = p.disableContactStatus, /* Previous inline ToggleStatus callback dispatched the same model event. */ onSwitchChanged = headerCallbacks.toggleStatus, subtitle = strings.getString(com.example.chat.R.string.conv_ui_hide_the_line_below_the_chat_c2bc9e))
                ConversationColorRow(subtitleMaxLines = 1,
                    title = strings.getString(com.example.chat.R.string.conv_ui_status_background_3da647), iconContent = { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.paint, semanticLabel = null) } /* Previous vector: icon = com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.paint) */,
                    subtitle = if (p.disableContactStatus) strings.getString(com.example.chat.R.string.conv_ui_turn_on_the_status_line_to_u_587f77) else strings.getString(com.example.chat.R.string.conv_ui_colour_behind_the_status_lin_e7f000),
                    colorValue = p.contactStatusBgColor,
                    enabled = !p.disableContactStatus,
                    // Previous non-saveable callback: onColorTap = { pickColor(strings.getString(com.example.chat.R.string.conv_ui_status_background_color_27cecc), (statusBg).toArgb().toLong() and 0xFFFFFFFFL) { preferences.editConversation { latest -> latest.copy(contactStatusBgColor = it) } } },
                    // Previous inline target callback retained by the remembered handler.
                    onColorTap = headerCallbacks.pickStatusBackground,
                    effectiveColor = statusBg,
                    // Previous reset: onReset = { preferences.editConversation { latest -> latest.copy(contactStatusBgColor = null) } },
                    onReset = headerCallbacks.resetStatusBackground,
                )
                ConversationColorRow(subtitleMaxLines = 1,
                    title = strings.getString(com.example.chat.R.string.conv_ui_status_text_b68390), iconContent = { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.textFormat, semanticLabel = null) } /* Previous vector: icon = com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.textFormat) */,
                    subtitle = if (p.disableContactStatus) strings.getString(com.example.chat.R.string.conv_ui_turn_on_the_status_line_to_u_587f77) else strings.getString(com.example.chat.R.string.conv_ui_text_in_the_status_line_b420ec),
                    colorValue = p.contactStatusTextColor,
                    enabled = !p.disableContactStatus,
                    // Previous non-saveable callback: onColorTap = { pickColor(strings.getString(com.example.chat.R.string.conv_ui_status_text_color_78882a), (statusText).toArgb().toLong() and 0xFFFFFFFFL) { preferences.editConversation { latest -> latest.copy(contactStatusTextColor = it) } } },
                    // Previous inline target callback retained by the remembered handler.
                    onColorTap = headerCallbacks.pickStatusText,
                    effectiveColor = statusText,
                    // Previous reset: onReset = { preferences.editConversation { latest -> latest.copy(contactStatusTextColor = null) } },
                    onReset = headerCallbacks.resetStatusText,
                )
            }
            /* Previous separated visibility/color groups retained:
            GbCardContainer(headerText = strings.getString(com.example.chat.R.string.conv_ui_show_in_header_43f0df)) {
                com.example.chat.ui.kit.GbSettingRow(iconContent = { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.profile, semanticLabel = null) }, title = strings.getString(com.example.chat.R.string.conv_ui_hide_contact_profile_picture_7aaf75), switchValue = p.hideProfilePicture, onSwitchChanged = {
                    // Previous write: preferences.editConversation { latest -> latest.copy(hideProfilePicture = it) }
                    model.onEvent(ActionBarEvent.ToggleAvatar(it))
                }, subtitle = strings.getString(com.example.chat.R.string.conv_ui_header_only_your_contacts_ar_233f2c))
                com.example.chat.ui.kit.GbSettingRow(iconContent = { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.personOff, semanticLabel = null) }, title = strings.getString(com.example.chat.R.string.conv_ui_hide_contact_name_c646d4), switchValue = p.hideContactName, onSwitchChanged = {
                    // Previous write: preferences.editConversation { latest -> latest.copy(hideContactName = it) }
                    model.onEvent(ActionBarEvent.ToggleName(it))
                }, subtitle = strings.getString(com.example.chat.R.string.conv_ui_header_only_notifications_an_9cabcf))
                com.example.chat.ui.kit.GbSettingRow(iconContent = { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.phone, semanticLabel = null) }, title = strings.getString(com.example.chat.R.string.conv_ui_hide_call_buttons_e56c01), switchValue = p.hideCallButton, onSwitchChanged = {
                    // Previous write: preferences.editConversation { latest -> latest.copy(hideCallButton = it) }
                    model.onEvent(ActionBarEvent.ToggleCall(it))
                }, subtitle = strings.getString(com.example.chat.R.string.conv_ui_hide_audio_and_video_call_sh_32d3f8))
                com.example.chat.ui.kit.GbSettingRow(iconContent = { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.personOff, semanticLabel = null) }, title = strings.getString(com.example.chat.R.string.conv_ui_hide_status_line_b148da), switchValue = p.disableContactStatus, onSwitchChanged = {
                    // Previous write: preferences.editConversation { latest -> latest.copy(disableContactStatus = it) }
                    model.onEvent(ActionBarEvent.ToggleStatus(it))
                }, subtitle = strings.getString(com.example.chat.R.string.conv_ui_hide_the_line_below_the_chat_c2bc9e))
            }
            Spacer(Modifier.height(ConversationPreviewLayout.sectionGapDp.dp))
            GbCardContainer(headerText = strings.getString(com.example.chat.R.string.conv_ui_colours_eb4e91)) {
                ConversationColorRow(
                    title = strings.getString(com.example.chat.R.string.conv_ui_header_background_394ff0), iconContent = { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.dock, semanticLabel = null) } /* Previous vector: icon = com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.dock) */, subtitle = strings.getString(com.example.chat.R.string.conv_ui_background_behind_the_contac_758a6d),
                    colorValue = p.actionBarColor,
                    // Previous non-saveable callback: onColorTap = { pickColor(strings.getString(com.example.chat.R.string.conv_ui_action_bar_color_77c122), (barColor).toArgb().toLong() and 0xFFFFFFFFL) { preferences.editConversation { latest -> latest.copy(actionBarColor = it) } } },
                    onColorTap = { openColourTarget = ConversationHeaderColorTarget.Bar.name },
                    effectiveColor = barColor,
                    // Previous reset: onReset = { preferences.editConversation { latest -> latest.copy(actionBarColor = null) } },
                    onReset = { model.onEvent(ActionBarEvent.PickColor(ConversationHeaderColorTarget.Bar, null)) },
                )
                ConversationColorRow(
                    title = strings.getString(com.example.chat.R.string.conv_ui_status_background_3da647), iconContent = { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.paint, semanticLabel = null) } /* Previous vector: icon = com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.paint) */,
                    subtitle = if (p.disableContactStatus) strings.getString(com.example.chat.R.string.conv_ui_turn_on_the_status_line_to_u_587f77) else strings.getString(com.example.chat.R.string.conv_ui_colour_behind_the_status_lin_e7f000),
                    colorValue = p.contactStatusBgColor,
                    enabled = !p.disableContactStatus,
                    // Previous non-saveable callback: onColorTap = { pickColor(strings.getString(com.example.chat.R.string.conv_ui_status_background_color_27cecc), (statusBg).toArgb().toLong() and 0xFFFFFFFFL) { preferences.editConversation { latest -> latest.copy(contactStatusBgColor = it) } } },
                    onColorTap = { openColourTarget = ConversationHeaderColorTarget.StatusBackground.name },
                    effectiveColor = statusBg,
                    // Previous reset: onReset = { preferences.editConversation { latest -> latest.copy(contactStatusBgColor = null) } },
                    onReset = { model.onEvent(ActionBarEvent.PickColor(ConversationHeaderColorTarget.StatusBackground, null)) },
                )
                ConversationColorRow(
                    title = strings.getString(com.example.chat.R.string.conv_ui_status_text_b68390), iconContent = { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.textFormat, semanticLabel = null) } /* Previous vector: icon = com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.textFormat) */,
                    subtitle = if (p.disableContactStatus) strings.getString(com.example.chat.R.string.conv_ui_turn_on_the_status_line_to_u_587f77) else strings.getString(com.example.chat.R.string.conv_ui_text_in_the_status_line_b420ec),
                    colorValue = p.contactStatusTextColor,
                    enabled = !p.disableContactStatus,
                    // Previous non-saveable callback: onColorTap = { pickColor(strings.getString(com.example.chat.R.string.conv_ui_status_text_color_78882a), (statusText).toArgb().toLong() and 0xFFFFFFFFL) { preferences.editConversation { latest -> latest.copy(contactStatusTextColor = it) } } },
                    onColorTap = { openColourTarget = ConversationHeaderColorTarget.StatusText.name },
                    effectiveColor = statusText,
                    // Previous reset: onReset = { preferences.editConversation { latest -> latest.copy(contactStatusTextColor = null) } },
                    onReset = { model.onEvent(ActionBarEvent.PickColor(ConversationHeaderColorTarget.StatusText, null)) },
                )
            }
            */
            // Previous inline reset: same model Reset then reset header-map defaults.
            // Previous stock text action:
            // androidx.compose.material3.TextButton(onClick = headerCallbacks.resetAll) { Text(strings.getString(com.example.chat.R.string.conv_ui_classic_header_preset_bec9e1)) }
            com.example.chat.ui.kit.ChatyTextButton(
                label = strings.getString(com.example.chat.R.string.conv_ui_classic_header_preset_bec9e1),
                onClick = headerCallbacks.resetAll,
            )
            Text(strings.getString(com.example.chat.R.string.conv_ui_header_appearance_is_include_e9b259), color = secondaryText, /* Previous caption size:12.sp */ style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            ConversationSupportCopy(actionBarSupportValues(p))
            ConversationHeaderLookExport(com.example.chat.domain.models.ConversationPreferences(
                actionBarColor = p.actionBarColor,contactStatusBgColor = p.contactStatusBgColor,contactStatusTextColor = p.contactStatusTextColor,
                hideProfilePicture = p.hideProfilePicture,hideContactName = p.hideContactName,hideCallButton = p.hideCallButton,
                disableContactStatus = p.disableContactStatus))
            ConversationHeaderDebugUsage(debugUsage)
            /* Previous inline support projection:
            ConversationSupportCopy(mapOf("actionBarColor" to p.actionBarColor, "hideProfilePicture" to p.hideProfilePicture,
                "hideContactName" to p.hideContactName, "hideCallButton" to p.hideCallButton,
                "disableContactStatus" to p.disableContactStatus, "contactStatusBgColor" to p.contactStatusBgColor,
                "contactStatusTextColor" to p.contactStatusTextColor))
            */
            },
        )
    }
    ConversationHeaderColorTarget.fromSavedKey(openColourTarget)?.let { target ->
        val pickerTitle = when (target) {
            ConversationHeaderColorTarget.Bar -> strings.getString(com.example.chat.R.string.conv_ui_action_bar_color_77c122)
            ConversationHeaderColorTarget.StatusBackground -> strings.getString(com.example.chat.R.string.conv_ui_status_background_color_27cecc)
            ConversationHeaderColorTarget.StatusText -> strings.getString(com.example.chat.R.string.conv_ui_status_text_color_78882a)
        }
        val current = when (target) {
            ConversationHeaderColorTarget.Bar -> barColor
            ConversationHeaderColorTarget.StatusBackground -> statusBg
            ConversationHeaderColorTarget.StatusText -> statusText
        }
        com.example.chat.ui.kit.ChatyColorPickerModal(title = pickerTitle,
            namedPalette = conversationNamedPalette(strings),
            currentColor = current.toArgb().toLong() and 0xFFFFFFFFL,
            onColorSelected = { colour ->
                // Previous picker write: preferences.editConversation { latest -> target.write(latest, colour) }
                model.onEvent(ActionBarEvent.PickColor(target, colour))
                openColourTarget = null
            }, onDismiss = { openColourTarget = null })
    }
    pickers.Host()
}

/* Legacy user-facing literals moved to string resources:
GbLivePreviewCard(modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "Preview. Name ${if (p.hideContactName) "hidden" else "shown"}. Profile picture ${if (p.hideProfilePicture) "hidden" else "shown"}. Call buttons ${if (p.hideCallButton) "hidden" else "shown"}. Status line ${if (p.disableContactStatus) "hidden" else "shown"}."
"Alexandra Rao · Long sample contact name for a compact header"
"typing..."
"Last seen recently"
"Cycle presence preview"
"Use short sample name"
"Use long sample name"
*/

/* Previous vector-backed switch rows retained; direct pack-aware glyph slots replace these.
GbSwitchTile(com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.profile), strings.getString(com.example.chat.R.string.conv_ui_hide_contact_profile_picture_7aaf75), p.hideProfilePicture, {
                    // Previous write: preferences.editConversation { latest -> latest.copy(hideProfilePicture = it) }
                    model.onEvent(ActionBarEvent.ToggleAvatar(it))
                }, strings.getString(com.example.chat.R.string.conv_ui_header_only_your_contacts_ar_233f2c))
GbSwitchTile(com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.personOff), strings.getString(com.example.chat.R.string.conv_ui_hide_contact_name_c646d4), p.hideContactName, {
                    // Previous write: preferences.editConversation { latest -> latest.copy(hideContactName = it) }
                    model.onEvent(ActionBarEvent.ToggleName(it))
                }, strings.getString(com.example.chat.R.string.conv_ui_header_only_notifications_an_9cabcf))
GbSwitchTile(com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.phone), strings.getString(com.example.chat.R.string.conv_ui_hide_call_buttons_e56c01), p.hideCallButton, {
                    // Previous write: preferences.editConversation { latest -> latest.copy(hideCallButton = it) }
                    model.onEvent(ActionBarEvent.ToggleCall(it))
                }, strings.getString(com.example.chat.R.string.conv_ui_hide_audio_and_video_call_sh_32d3f8))
GbSwitchTile(com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.personOff), strings.getString(com.example.chat.R.string.conv_ui_hide_status_line_b148da), p.disableContactStatus, {
                    // Previous write: preferences.editConversation { latest -> latest.copy(disableContactStatus = it) }
                    model.onEvent(ActionBarEvent.ToggleStatus(it))
                }, strings.getString(com.example.chat.R.string.conv_ui_hide_the_line_below_the_chat_c2bc9e))
*/

// Complete original-line retention for review (inactive).
            //             if (com.example.chat.BuildConfig.DEBUG) androidx.compose.material3.TextButton(onClick = { longSampleName = !longSampleName }) {
