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
import androidx.compose.runtime.collectAsState
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

/** Port of ConversationMoreOptionsScreen. UNCOMPILED (Compose); uses the ported ChatyIcon for the play glyph. */
@Composable
fun ConversationMoreOptionsScreen(preferences: ChatyPreferencesController, onBack: () -> Unit) {
    val strings = androidx.compose.ui.platform.LocalContext.current.resources
    val config = LocalThemeConfig.current
    val previewColors = com.example.chat.ui.theme.LocalAppColors.current
    val materialColors = androidx.compose.material3.MaterialTheme.colorScheme
    // Legacy whole-controller version listener replaced by a conversation-only snapshot.
    // Previous broad snapshot: val p = rememberConversationPreferences(preferences)
    // Previous snapshot: val p = rememberConversationPreferences(preferences, ::selectMoreOptionsPreferences)
    val model: MoreOptionsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        key = "conversation-extras-${System.identityHashCode(preferences)}",
        factory = androidx.compose.runtime.remember(preferences) {
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(MoreOptionsViewModel::class.java))
                    return MoreOptionsViewModel(read = { preferences.conversation },
                        write = { preferences.updateConversation(it) },
                        subscribe = { preferences.addListener(it) }, unsubscribe = { preferences.removeListener(it) },
                        transaction = { preferences.editConversation(it) }) as T
                }
            }
        },
    )
    // Lifecycle-aware collection is separately blocked by the excluded dependency/controller work.
    val p by model.uiState.collectAsState()
    val multiColourNames = rememberConversationMultiColourNames(preferences)
    val dateChip = rememberConversationDateChip(preferences)
    val extraOptions = rememberConversationExtraOptions(preferences)
    val requestedVaryCustomNames = rememberConversationVaryCustomNames(preferences)
    val varyCustomNames = extraOptions && requestedVaryCustomNames
    var recentColours by remember { androidx.compose.runtime.mutableStateOf(emptyList<Long>()) }
    var simpleMode by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(extraOptions) { if (!extraOptions) simpleMode = false }
    var openColourTarget by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val pickers = rememberGbPickers()

    val accent = Color(config.accentColor)
    val primaryText = Color(config.primaryTextColor)
    val secondaryText = Color(config.secondaryTextColor)
    // Previous fallback: val emojiHeader = p.emojiHeaderColor?.let(::Color) ?: Color(config.surfaceColor)
    /* Previous raw color reads:
    val emojiHeader = p.emojiHeaderColor?.let(::Color) ?: materialColors.surfaceVariant
    // Previous fallback: val emojiIcons = p.emojiHeaderIconsColor?.let(::Color) ?: primaryText
    val emojiIcons = p.emojiHeaderIconsColor?.let(::Color) ?: materialColors.onSurfaceVariant
    // Previous fallback: val emojiBackground = p.emojiPickerBgColor?.let(::Color) ?: Color(config.surfaceColor)
    val emojiBackground = p.emojiPickerBgColor?.let(::Color) ?: materialColors.surfaceVariant
    val linkColor = p.hyperlinksColor?.let(::Color) ?: Color(config.linkColor)
    // Previous fallback: val infoText = p.infoBalloonsTextColor?.let(::Color) ?: secondaryText
    val infoText = p.infoBalloonsTextColor?.let(::Color) ?: materialColors.onSurfaceVariant
    // Previous fallback: val infoBackground = p.infoBalloonsBgColor?.let(::Color) ?: Color(config.surfaceColor).copy(alpha = 0.8f)
    val infoBackground = p.infoBalloonsBgColor?.let(::Color) ?: materialColors.surfaceVariant
    val participantColor = p.groupParticipantNameColor?.let(::Color) ?: accent
    val voiceProgress = p.voiceNotePlayingBarColor?.let(::Color) ?: accent
    val voiceButton = p.voiceNotePlayButtonColor?.let(::Color) ?: accent
    */
    val emojiHeader = opaqueConversationStoredColor(p.emojiHeaderColor)?.let(::Color) ?: materialColors.surfaceVariant
    // Previous fallback: val emojiIcons = opaqueConversationStoredColor(p.emojiHeaderIconsColor)?.let(::Color) ?: primaryText
    val emojiIcons = opaqueConversationStoredColor(p.emojiHeaderIconsColor)?.let(::Color) ?: materialColors.onSurfaceVariant
    // Previous fallback: val emojiBackground = opaqueConversationStoredColor(p.emojiPickerBgColor)?.let(::Color) ?: Color(config.surfaceColor)
    val emojiBackground = opaqueConversationStoredColor(p.emojiPickerBgColor)?.let(::Color) ?: materialColors.surfaceVariant
    // Previous sample fallback: val linkColor = opaqueConversationStoredColor(p.hyperlinksColor)?.let(::Color) ?: Color(config.linkColor)
    val linkColor = Color(com.example.chat.ui.chats.detail.resolveConversationLinkColor(config,
        p.leftBubbleColor ?: config.incomingBubbleColor, opaqueConversationStoredColor(p.hyperlinksColor)))
    // Previous fallback: val infoText = opaqueConversationStoredColor(p.infoBalloonsTextColor)?.let(::Color) ?: secondaryText
    val infoText = opaqueConversationStoredColor(p.infoBalloonsTextColor)?.let(::Color) ?: materialColors.onSurfaceVariant
    // Previous fallback: val infoBackground = opaqueConversationStoredColor(p.infoBalloonsBgColor)?.let(::Color) ?: Color(config.surfaceColor).copy(alpha = 0.8f)
    val infoBackground = opaqueConversationStoredColor(p.infoBalloonsBgColor)?.let(::Color) ?: materialColors.surfaceVariant
    val participantColor = opaqueConversationStoredColor(p.groupParticipantNameColor)?.let(::Color) ?: accent
    val voiceProgress = opaqueConversationStoredColor(p.voiceNotePlayingBarColor)?.let(::Color) ?: accent
    val voiceButton = opaqueConversationStoredColor(p.voiceNotePlayButtonColor)?.let(::Color) ?: accent
    val fs = config.fontScale.toFloat().let { if (it.isFinite()) it.coerceIn(0.8f, 2f) else 1f }

    val colourOptions = listOf(
        ConversationColorOption(ConversationExtraColor.emojiHeaderColor.key, "Emoji popup", strings.getString(com.example.chat.R.string.conv_ui_emoji_header_color_1c7dd8), strings.getString(com.example.chat.R.string.conv_ui_change_emoji_popup_header_ba_3f92a1), com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.alignTop), p.emojiHeaderColor, emojiHeader,
            /* Previous writer: { state, colour -> state.copy(emojiHeaderColor = colour) } */ ConversationExtraColor.emojiHeaderColor::write, iconId = ConversationExtraColor.emojiHeaderColor.rowIcon()),
        ConversationColorOption(ConversationExtraColor.emojiHeaderIconsColor.key, "Emoji popup", strings.getString(com.example.chat.R.string.conv_ui_emoji_header_icons_color_85cd20), strings.getString(com.example.chat.R.string.conv_ui_change_emoji_popup_icons_col_5606e8), com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.emoji), p.emojiHeaderIconsColor, emojiIcons,
            /* Previous writer: { state, colour -> state.copy(emojiHeaderIconsColor = colour) } */ ConversationExtraColor.emojiHeaderIconsColor::write, iconId = ConversationExtraColor.emojiHeaderIconsColor.rowIcon()),
        ConversationColorOption(ConversationExtraColor.emojiPickerBgColor.key, "Emoji popup", strings.getString(com.example.chat.R.string.conv_ui_emoji_picker_background_colo_56e57d), strings.getString(com.example.chat.R.string.conv_ui_change_emoji_popup_backgroun_23fe59), com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.palette), p.emojiPickerBgColor, emojiBackground,
            /* Previous writer: { state, colour -> state.copy(emojiPickerBgColor = colour) } */ ConversationExtraColor.emojiPickerBgColor::write, iconId = ConversationExtraColor.emojiPickerBgColor.rowIcon()),
        ConversationColorOption(ConversationExtraColor.hyperlinksColor.key, "Links and group names", strings.getString(com.example.chat.R.string.conv_ui_links_014bcd), strings.getString(com.example.chat.R.string.conv_ui_links_in_incoming_and_outgoi_7b17b4), com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.link), p.hyperlinksColor, linkColor,
            /* Previous writer: { state, colour -> state.copy(hyperlinksColor = colour) } */ ConversationExtraColor.hyperlinksColor::write, iconId = ConversationExtraColor.hyperlinksColor.rowIcon()),
        ConversationColorOption(ConversationExtraColor.groupParticipantNameColor.key, "Links and group names", strings.getString(com.example.chat.R.string.conv_ui_group_participant_name_color_aad4e0), strings.getString(com.example.chat.R.string.conv_ui_sender_names_above_group_mes_8110fa), com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.groups), p.groupParticipantNameColor, participantColor,
            /* Previous writer: { state, colour -> state.copy(groupParticipantNameColor = colour) } */ ConversationExtraColor.groupParticipantNameColor::write, iconId = ConversationExtraColor.groupParticipantNameColor.rowIcon()),
        ConversationColorOption(ConversationExtraColor.infoBalloonsTextColor.key, "System bubbles", strings.getString(com.example.chat.R.string.conv_ui_system_bubble_text_943ad0), strings.getString(com.example.chat.R.string.conv_ui_text_in_date_and_missed_call_6edb90), com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.info), p.infoBalloonsTextColor, infoText,
            /* Previous writer: { state, colour -> state.copy(infoBalloonsTextColor = colour) } */ ConversationExtraColor.infoBalloonsTextColor::write, iconId = ConversationExtraColor.infoBalloonsTextColor.rowIcon()),
        ConversationColorOption(ConversationExtraColor.infoBalloonsBgColor.key, "System bubbles", strings.getString(com.example.chat.R.string.conv_ui_system_bubble_background_f79af7), strings.getString(com.example.chat.R.string.conv_ui_background_of_date_and_misse_0116fd), com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.markUnread), p.infoBalloonsBgColor, infoBackground,
            /* Previous writer: { state, colour -> state.copy(infoBalloonsBgColor = colour) } */ ConversationExtraColor.infoBalloonsBgColor::write, iconId = ConversationExtraColor.infoBalloonsBgColor.rowIcon()),
        ConversationColorOption(ConversationExtraColor.voiceNotePlayingBarColor.key, "Voice notes", strings.getString(com.example.chat.R.string.conv_ui_voice_note_progress_b44151), strings.getString(com.example.chat.R.string.conv_ui_playback_progress_and_wavefo_e90549), com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.waveform), p.voiceNotePlayingBarColor, voiceProgress,
            /* Previous writer: { state, colour -> state.copy(voiceNotePlayingBarColor = colour) } */ ConversationExtraColor.voiceNotePlayingBarColor::write, iconId = ConversationExtraColor.voiceNotePlayingBarColor.rowIcon()),
        ConversationColorOption(ConversationExtraColor.voiceNotePlayButtonColor.key, "Voice notes", strings.getString(com.example.chat.R.string.conv_ui_voice_note_play_button_d19469), strings.getString(com.example.chat.R.string.conv_ui_change_color_of_voice_messag_4cd41b), com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.playCircle), p.voiceNotePlayButtonColor, voiceButton,
            /* Previous writer: { state, colour -> state.copy(voiceNotePlayButtonColor = colour) } */ ConversationExtraColor.voiceNotePlayButtonColor::write, iconId = ConversationExtraColor.voiceNotePlayButtonColor.rowIcon()),
    )

    // Previous per-screen shell: Column(Modifier.fillMaxSize().background(Color(config.backgroundColor))) {
    // Previous per-screen header: com.example.chat.ui.kit.ChatyAppBar(title = strings.getString(com.example.chat.R.string.conv_ui_bubble_extras_5ef1a3), onBack = onBack, titleModifier = Modifier.semantics { heading() })
    // Previous shell title used kit default styling.
    ConversationSettingsScaffold(title = strings.getString(com.example.chat.R.string.conv_ui_bubble_extras_5ef1a3), onBack = onBack,
        titleTextStyle = androidx.compose.material3.MaterialTheme.typography.titleMedium) {

        // Previous compact body: Column(Modifier.weight(1f).align(Alignment.CenterHorizontally).widthIn(max = 600.dp).fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)).padding(horizontal = 16.dp, vertical = 16.dp)) {
        ConversationAdaptiveSettingsBody(
            modifier = Modifier.weight(1f).fillMaxWidth().navigationBarsPadding().imePadding()
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)),
            preview = {
            MoreOptionsPreview(MoreOptionsPreviewState(
                bubbleStyle = com.example.chat.core.theme.bubbleStyleIdFromString(p.bubbleStyle),
                incomingFill = p.leftBubbleColor?.let(::Color) ?: previewColors.surfaceSecondary,
                emojiHeader = emojiHeader,
                emojiIcons = emojiIcons,
                emojiBackground = emojiBackground,
                primaryText = primaryText,
                secondaryText = secondaryText,
                participantColor = participantColor,
                multiColourNames = multiColourNames, dateChip = dateChip,
                varyCustomNames = varyCustomNames, customParticipantSeed = p.groupParticipantNameColor,
                linkColor = linkColor,
                voiceButton = voiceButton,
                voiceProgress = voiceProgress,
                infoBackground = infoBackground,
                infoText = infoText,
                playbackSpeed = p.voicePlaybackSpeed,
            ))
            /* Previous inline sample kept as reference; the state-only preview above owns rendering.
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
                            styleId = com.example.chat.core.theme.bubbleStyleIdFromString(p.bubbleStyle),
                            isMe = false, fillColor = p.leftBubbleColor?.let(::Color) ?: previewColors.surfaceSecondary,
                            accentColor = Color(config.accentColor),
                        ) {
                            Column {
                                Text(strings.getString(com.example.chat.R.string.conv_ui_alex_64542d), modifier = Modifier.testTag("conversation-extra-name-preview"), color = participantColor, fontSize = (14 * fs).sp, maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
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
                        }
                        Spacer(Modifier.height(10.dp))
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(
                                strings.getString(com.example.chat.R.string.conv_ui_today_24345a),
                                Modifier.testTag("conversation-extra-date-preview").background(
                                    infoBackground,
                                    RoundedCornerShape(12.dp),
                                )// Previous date chip padding: horizontal = 14.dp, vertical = 6.dp
                                .padding(horizontal = ConversationPreviewLayout.outerPaddingDp.dp, vertical = ConversationPreviewLayout.innerPaddingDp.dp),
                                color = infoText,
                                fontSize = (12 * fs).sp, fontWeight = FontWeight.W500,
                            )
                        }
                        Text(strings.getString(com.example.chat.R.string.conv_ui_missed_voice_call_da7aba), color = infoText, fontSize = (12 * fs).sp,
                            modifier = Modifier.testTag("conversation-extra-missed-call-preview").background(infoBackground, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }
            }
            */
            Spacer(Modifier.height(16.dp))
            },
            controls = {
            // Check the actual elapsed-label canvas, not the custom progress-track colour.
            ConversationContrastNote(secondaryText, Color(config.surfaceColor), 4.5f,
                strings.getString(com.example.chat.R.string.conv_proposal_voice_elapsed))
            ConversationContrastNote(emojiIcons, emojiHeader, 3f, strings.getString(com.example.chat.R.string.conv_ui_emoji_header_icons_1ac4f6))
            ConversationContrastNote(infoText, infoBackground, 4.5f, strings.getString(com.example.chat.R.string.conv_ui_system_bubble_text_943ad0))
            ConversationContrastNote(participantColor, p.leftBubbleColor?.let(::Color) ?: Color(config.incomingBubbleColor), 4.5f, strings.getString(com.example.chat.R.string.conv_ui_participant_name_e8a7e9))
            ConversationContrastNote(linkColor, p.leftBubbleColor?.let(::Color) ?: Color(config.incomingBubbleColor), 4.5f, strings.getString(com.example.chat.R.string.conv_ui_incoming_links_650176))
            ConversationContrastNote(linkColor, p.rightBubbleColor?.let(::Color) ?: Color(config.outgoingBubbleColor), 4.5f, strings.getString(com.example.chat.R.string.conv_ui_outgoing_links_f4ba79))
            com.example.chat.ui.kit.GbSettingRow(title = strings.getString(com.example.chat.R.string.conv_proposal_extra_options_gate),
                subtitle = strings.getString(com.example.chat.R.string.conv_proposal_extra_options_gate_scope), subtitleMaxLines = 1,
                switchValue = extraOptions, onSwitchChanged = { preferences.updateGbFeature(com.example.chat.ui.chats.detail.CONVERSATION_EXTRA_OPTIONS,it) })
            if (extraOptions) {
            com.example.chat.ui.kit.GbSettingRow(title = strings.getString(com.example.chat.R.string.conv_proposal_multi_colour_names),
                subtitle = strings.getString(com.example.chat.R.string.conv_proposal_multi_colour_names_scope),subtitleMaxLines = 1,
                switchValue = multiColourNames,onSwitchChanged = { preferences.updateGbFeature(com.example.chat.ui.chats.detail.CONVERSATION_MULTI_COLOUR_NAMES,it) })
            ConversationDateChipControls(preferences,dateChip)
            ConversationParticipantNameControls(preferences, requestedVaryCustomNames)
            com.example.chat.ui.kit.GbSettingRow(title = strings.getString(com.example.chat.R.string.conv_proposal_simple_mode),
                subtitle = strings.getString(com.example.chat.R.string.conv_proposal_session_only), switchValue = simpleMode,
                onSwitchChanged = { simpleMode = it },
                iconContent = { ChatyIcon(ChatyIconId.tune, semanticLabel = null) })
            }
            val emojiPresetContent: @Composable () -> Unit = {
                if (extraOptions) {
                // Previous stock text action:
                // androidx.compose.material3.TextButton(onClick = { model.onEvent(MoreOptionsEvent.ResetEmojiStyle) }) {
                //                     Text(strings.getString(com.example.chat.R.string.conv_proposal_reset_emoji_style))
                //                 }
                com.example.chat.ui.kit.ChatyTextButton(
                    label = strings.getString(com.example.chat.R.string.conv_proposal_reset_emoji_style),
                    onClick = { model.onEvent(MoreOptionsEvent.ResetEmojiStyle) },
                )
                ConversationEmojiPresets(preferences, onPreset = { header, icons, background ->
                model.onEvent(MoreOptionsEvent.ColorsChanged(mapOf(
                    ConversationExtraColor.emojiHeaderColor to header,
                    ConversationExtraColor.emojiHeaderIconsColor to icons,
                    ConversationExtraColor.emojiPickerBgColor to background,
                )))
            })
                }
            }
            if (extraOptions && simpleMode) ConversationSimpleExtras(options = colourOptions,
                onPick = { openColourTarget = it },
                onReset = { key -> model.onEvent(MoreOptionsEvent.ColorChanged(ConversationExtraColor.entries.first { it.key == key }, null)) },
                emojiPresets = emojiPresetContent)
            else {
                emojiPresetContent()
            colourOptions.groupBy { it.group }.forEach { (group, options) ->
                GbCardContainer(headerText = group) {
                    options.forEach { option ->
                        ConversationColorRow(title = option.title, icon = option.icon, subtitle = option.subtitle, subtitleMaxLines = 1,
                            iconContent = { ChatyIcon(ConversationExtraColor.entries.first { it.key == option.id }.rowIcon(), semanticLabel = null) },
                            colorValue = option.value, effectiveColor = option.effective,
                            onColorTap = { openColourTarget = option.id },
                            /* Previous non-saveable callback retained.
                            onColorTap = { pickers.showColor(option.title, option.effective.toArgb().toLong() and 0xFFFFFFFFL) { colour ->
                                /* Previous write: preferences.editConversation { latest -> option.write(latest, colour) } */
                model.onEvent(MoreOptionsEvent.ColorChanged(ConversationExtraColor.entries.first { it.key == option.id }, colour))
                            } },
                            */
                            // Previous reset: onReset = { preferences.editConversation { latest -> option.write(latest, null) } })
                            onReset = { model.onEvent(MoreOptionsEvent.ColorChanged(ConversationExtraColor.entries.first { it.key == option.id }, null)) })
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            }
            /* Previous advanced-only presentation, retained; same rows now live behind default-off simple disclosure.
            ConversationEmojiPresets(preferences, onPreset = { header, icons, background ->
                model.onEvent(MoreOptionsEvent.ColorsChanged(mapOf(
                    ConversationExtraColor.emojiHeaderColor to header,
                    ConversationExtraColor.emojiHeaderIconsColor to icons,
                    ConversationExtraColor.emojiPickerBgColor to background,
                )))
            })
            colourOptions.groupBy { it.group }.forEach { (group, options) ->
                GbCardContainer(headerText = group) {
                    options.forEach { option ->
                        ConversationColorRow(title = option.title, icon = option.icon, subtitle = option.subtitle, subtitleMaxLines = 1,
                            iconContent = { ChatyIcon(ConversationExtraColor.entries.first { it.key == option.id }.rowIcon(), semanticLabel = null) },
                            colorValue = option.value, effectiveColor = option.effective,
                            onColorTap = { openColourTarget = option.id },
                            /* Previous non-saveable callback retained.
                            onColorTap = { pickers.showColor(option.title, option.effective.toArgb().toLong() and 0xFFFFFFFFL) { colour ->
                                /* Previous write: preferences.editConversation { latest -> option.write(latest, colour) } */
                model.onEvent(MoreOptionsEvent.ColorChanged(ConversationExtraColor.entries.first { it.key == option.id }, colour))
                            } },
                            */
                            // Previous reset: onReset = { preferences.editConversation { latest -> option.write(latest, null) } })
                            onReset = { model.onEvent(MoreOptionsEvent.ColorChanged(ConversationExtraColor.entries.first { it.key == option.id }, null)) })
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            */
            ConversationApplyColorTo(colourOptions, preferences, pickers, config.accentColor,
                onApplied = { colour -> recentColours = withRecentConversationColour(recentColours, colour) },
                onApplySelection = { selected, colour -> model.onEvent(MoreOptionsEvent.ColorsChanged(
                    selected.associate { option -> ConversationExtraColor.entries.first { it.key == option.id } to colour }
                )) })
            if (extraOptions) // Previous stock text action:
            // androidx.compose.material3.TextButton(onClick = {
            //                 /* Previous reset: preferences.editConversation { latest -> latest.copy(emojiHeaderColor = null, emojiHeaderIconsColor = null,
            //                     emojiPickerBgColor = null, hyperlinksColor = null, infoBalloonsTextColor = null,
            //                     infoBalloonsBgColor = null, groupParticipantNameColor = null, voiceNotePlayingBarColor = null,
            //                     voiceNotePlayButtonColor = null) } */
            //                 model.onEvent(MoreOptionsEvent.ResetColors)
            //             }) { Text(strings.getString(com.example.chat.R.string.conv_ui_reset_all_extras_colours_fd382e)) }
            com.example.chat.ui.kit.ChatyTextButton(
                label = strings.getString(com.example.chat.R.string.conv_ui_reset_all_extras_colours_fd382e),
                onClick = {
                /* Previous reset: preferences.editConversation { latest -> latest.copy(emojiHeaderColor = null, emojiHeaderIconsColor = null,
                    emojiPickerBgColor = null, hyperlinksColor = null, infoBalloonsTextColor = null,
                    infoBalloonsBgColor = null, groupParticipantNameColor = null, voiceNotePlayingBarColor = null,
                    voiceNotePlayButtonColor = null) } */
                model.onEvent(MoreOptionsEvent.ResetColors)
            },
            )
            Text(strings.getString(com.example.chat.R.string.conv_ui_these_appearance_settings_ar_9572c0), color = secondaryText, fontSize = 12.sp)
            ConversationSupportCopy(moreOptionsSupportValues(p))
            /* Previous inline support mapping:
            ConversationSupportCopy(mapOf("emojiHeaderColor" to p.emojiHeaderColor, "emojiHeaderIconsColor" to p.emojiHeaderIconsColor,
                "emojiPickerBgColor" to p.emojiPickerBgColor, "hyperlinksColor" to p.hyperlinksColor,
                "infoBalloonsTextColor" to p.infoBalloonsTextColor, "infoBalloonsBgColor" to p.infoBalloonsBgColor,
                "groupParticipantNameColor" to p.groupParticipantNameColor, "voiceNotePlayingBarColor" to p.voiceNotePlayingBarColor,
                "voiceNotePlayButtonColor" to p.voiceNotePlayButtonColor))
            */
            },
        )
    }
    colourOptions.firstOrNull { it.id == openColourTarget }?.let { option ->
        com.example.chat.ui.kit.ChatyColorPickerModal(title = option.title,
            namedPalette = conversationNamedPalette(strings), recentColors = recentColours,
            currentColor = option.effective.toArgb().toLong() and 0xFFFFFFFFL,
            onColorSelected = { colour ->
                /* Previous write: preferences.editConversation { latest -> option.write(latest, colour) } */
                model.onEvent(MoreOptionsEvent.ColorChanged(ConversationExtraColor.entries.first { it.key == option.id }, colour))
                recentColours = withRecentConversationColour(recentColours, colour)
                openColourTarget = null
            }, onDismiss = { openColourTarget = null })
    }
    pickers.Host()
}

// Complete original-line retention for review (inactive).
            //             if (extraOptions) androidx.compose.material3.TextButton(onClick = {
