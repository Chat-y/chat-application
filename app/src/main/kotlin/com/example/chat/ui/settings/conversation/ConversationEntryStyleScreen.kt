package com.example.chat.ui.settings.conversation

import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.FormatColorText
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.SmartButton
import androidx.compose.material.icons.rounded.Style
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.core.preferences.ChatyPreferencesController
import com.example.chat.ui.kit.ChatyBackButton
import com.example.chat.ui.kit.GbCardContainer
import com.example.chat.ui.kit.GbLivePreviewCard
import com.example.chat.ui.kit.GbSettingRow
import com.example.chat.ui.kit.rememberGbPickers
import com.example.chat.ui.theme.LocalAppColors
import com.example.chat.ui.theme.LocalThemeConfig
import kotlinx.coroutines.launch

/* Legacy display-string registry and dead preview palette, retained for rollback.
private val entryStyles = listOf(
    "Stock",
    "Reference 2",
    "YOWA Style",
    "WAMOD",
    "Hangouts",
    "Simple",
    "Aran V2",
    "Mood",
    "WANH",
    "Hangouts V2",
    "Dribble",
    "Dribble V2",
    "Allo",
    "Rounded",
    "Old Stock (2016)",
    "RC-MSG Google",
    "RC-Telegram X iOS",
    "RC-IOS 11",
    "RC-Line",
    "Concept",
    "Instagram",
    "iOS 14",
    "FB Messenger",
    "RC Mess",
)

private fun normalizeStyle(raw: String): String {
    val v = raw.trim()
    return if (v.isEmpty() || v == "Stock (Chaty Default)") "Stock" else v
}

private class EntryPreviewColors(
    val buttons: Color, val emoji: Color, val send: Color, val micCircle: Color,
    val textEntryBg: Color, val textEntry: Color,
)

*/
private fun normalizeStyle(raw: String): String = com.example.chat.core.theme.ConversationEntryStyles.layoutName(raw)

/**
 * Port of ConversationEntryStyleScreen including its own static _buildStylePreview (the Dart
 * screen draws its own preview rather than the chat composer). UNCOMPILED (Compose).
 * Deviation: Dart BoxShadow blur uses elevation-shadow here (shadow(elevation) approximates it).
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ConversationEntryStyleScreen(preferences: ChatyPreferencesController, onBack: () -> Unit) {
    val strings = androidx.compose.ui.platform.LocalContext.current.resources
    val entryRollout = rememberConversationStyleRollout(preferences,CONVERSATION_ENTRY_ROLLOUT)
    val config = LocalThemeConfig.current
    val colors = LocalAppColors.current
    // Legacy whole-controller version listener replaced by a conversation-only snapshot.
    // Previous broad snapshot: val p = rememberConversationPreferences(preferences)
    // Previous snapshot: val p = rememberConversationPreferences(preferences, ::selectEntryStylePreferences)
    val model: EntryStyleViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        key = "conversation-entry-${System.identityHashCode(preferences)}",
        factory = androidx.compose.runtime.remember(preferences) {
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(EntryStyleViewModel::class.java))
                    return EntryStyleViewModel(read = { preferences.conversation },
                        write = { preferences.updateConversation(it) },
                        subscribe = { preferences.addListener(it) }, unsubscribe = { preferences.removeListener(it) },
                        transaction = { preferences.editConversation(it) },
                        useStableIds = { conversationStyleRollout(preferences.gbFeatures,CONVERSATION_ENTRY_ROLLOUT) }) as T
                }
            }
        },
    )
    // Lifecycle-aware collection is separately blocked by the excluded dependency/controller work.
    val p by model.uiState.collectAsState()
    val composerAppearance = rememberConversationComposerAppearance(preferences)

    var previewOtherMode by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var selectionHaptics by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val selectionView = androidx.compose.ui.platform.LocalView.current
    val pickers = rememberGbPickers()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notice by model.uiEvent.collectAsState()
    androidx.compose.runtime.LaunchedEffect(model, notice?.id) {
        val event = notice ?: return@LaunchedEffect
        val result = snackbar.showSnackbar("${event.label} applied",
            actionLabel = strings.getString(com.example.chat.R.string.conv_ui_undo_39fc72))
        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
            model.onEvent(EntryStyleEvent.Undo(event.id))
        }
        model.acknowledge(event.id)
    }
    val previewRequester = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    var previewText by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val fs = config.fontScale.toFloat().let { if (it.isFinite()) it.coerceIn(0.8f, 2f) else 1f }
    fun pickColor(title: String, current: Long?, apply: (Long) -> Unit) =
        pickers.showColor(title, current ?: config.accentColor) { apply(it) }

    fun c(x: Long?, fb: Color) = x?.let { Color(it) } ?: fb
    val uiEntryBg = c(p.uiEntryBackgroundColor, Color(config.surfaceColor))
    val composerColors = com.example.chat.ui.chats.detail.ComposerColors.resolve(config,
        p.uiButtonsColor, p.emojiButtonColor, p.sendButtonColor, p.micSendBgCircleColor,
        p.textEntryBackgroundColor, p.textEntryColor)


    Box(Modifier.fillMaxSize().background(Color(config.backgroundColor))) {
        // Previous per-screen shell: Column(Modifier.fillMaxSize()) {
        // Previous per-screen header: com.example.chat.ui.kit.ChatyAppBar(title = strings.getString(com.example.chat.R.string.conv_ui_message_bar_880ea4), onBack = onBack, titleModifier = Modifier.semantics { heading() })
        ConversationSettingsScaffold(title = strings.getString(com.example.chat.R.string.conv_ui_message_bar_880ea4), onBack = onBack) {

            Column(Modifier.weight(1f).align(Alignment.CenterHorizontally).widthIn(max = 600.dp).fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)).padding(horizontal = 16.dp, vertical = 16.dp)) {
                // Previous stock text action:
                // androidx.compose.material3.TextButton(onClick = { previewOtherMode = !previewOtherMode }) {
                //                     Text(strings.getString(if (previewOtherMode) com.example.chat.R.string.conv_proposal_use_app_mode
                //                         else com.example.chat.R.string.conv_proposal_other_mode))
                //                 }
                com.example.chat.ui.kit.ChatyTextButton(
                    label = strings.getString(if (previewOtherMode) com.example.chat.R.string.conv_proposal_use_app_mode
                        else com.example.chat.R.string.conv_proposal_other_mode),
                    onClick = { previewOtherMode = !previewOtherMode },
                )
                val previewTheme = conversationAlternatePreviewTheme(config, previewOtherMode)
                val previewEntryBg = p.uiEntryBackgroundColor?.let(::Color) ?: Color(previewTheme.surfaceColor)
                val previewComposerColors = com.example.chat.ui.chats.detail.ComposerColors.resolve(previewTheme,
                    p.uiButtonsColor, p.emojiButtonColor, p.sendButtonColor, p.micSendBgCircleColor,
                    p.textEntryBackgroundColor, p.textEntryColor)
                com.example.chat.ui.theme.ChatyTheme(previewTheme) {
                GbLivePreviewCard(modifier = Modifier.semantics {
                    contentDescription = "Message composer preview. Style ${com.example.chat.core.theme.ConversationEntryStyles.resolve(p.entryStyle).label}. Type a sample message to see the send button."
                }, padding = PaddingValues(horizontal = 8.dp, vertical = 16.dp)) {
                    Box(Modifier.fillMaxWidth().bringIntoViewRequester(previewRequester).background(previewEntryBg, RoundedCornerShape(24.dp))// Previous inner horizontal padding: 10.dp (off the 8dp grid).
                        // Previous/default preview vertical padding:8.dp.
                        .padding(horizontal = ConversationPreviewLayout.innerPaddingDp.dp, vertical = composerAppearance.verticalPaddingDp.dp)) {
                        com.example.chat.ui.chats.detail.ChatEntryBar(
                            entryStyle = normalizeStyle(p.entryStyle),
                            colors = previewComposerColors,
                            appearance = composerAppearance,
                            template = com.example.chat.core.templates.ComposerTemplate(com.example.chat.core.templates.ComposerActionPlacement.integrated),
                            theme = previewTheme, text = previewText, onTextChange = { previewText = it },
                            onCamera = {}, onSend = { previewText = "" },
                            onTextInputFocused = { scope.launch { previewRequester.bringIntoView() } },
                        )
                    }
                }
                }
                /* Original app-theme-only preview retained.
                GbLivePreviewCard(modifier = Modifier.semantics {
                    contentDescription = "Message composer preview. Style ${com.example.chat.core.theme.ConversationEntryStyles.resolve(p.entryStyle).label}. Type a sample message to see the send button."
                }, padding = PaddingValues(horizontal = 8.dp, vertical = 16.dp)) {
                    Box(Modifier.fillMaxWidth().bringIntoViewRequester(previewRequester).background(uiEntryBg, RoundedCornerShape(24.dp))// Previous inner horizontal padding: 10.dp (off the 8dp grid).
                        .padding(horizontal = ConversationPreviewLayout.innerPaddingDp.dp, vertical = 8.dp)) {
                        com.example.chat.ui.chats.detail.ChatEntryBar(
                            entryStyle = normalizeStyle(p.entryStyle),
                            colors = composerColors,
                            template = com.example.chat.core.templates.ComposerTemplate(com.example.chat.core.templates.ComposerActionPlacement.integrated),
                            theme = config, text = previewText, onTextChange = { previewText = it },
                            onCamera = {}, onSend = { previewText = "" },
                            onTextInputFocused = { scope.launch { previewRequester.bringIntoView() } },
                        )
                    }
                }
                */
                // Previous stock text action:
                // androidx.compose.material3.TextButton(onClick = { previewText = if (previewText.isBlank()) strings.getString(com.example.chat.R.string.conv_ui_preview_message_fa769f) else "" }) {
                //                     Text(if (previewText.isBlank()) strings.getString(com.example.chat.R.string.conv_ui_preview_send_state_598ff4) else strings.getString(com.example.chat.R.string.conv_ui_preview_microphone_state_e707e8))
                //                 }
                com.example.chat.ui.kit.ChatyTextButton(
                    label = if (previewText.isBlank()) strings.getString(com.example.chat.R.string.conv_ui_preview_send_state_598ff4) else strings.getString(com.example.chat.R.string.conv_ui_preview_microphone_state_e707e8),
                    onClick = { previewText = if (previewText.isBlank()) strings.getString(com.example.chat.R.string.conv_ui_preview_message_fa769f) else "" },
                )
                Text(strings.getString(com.example.chat.R.string.conv_ui_preview_only_nothing_is_sent_5c68fa), color = Color(config.secondaryTextColor), fontSize = 12.sp)
                ConversationContrastNote(composerColors.inputText, composerColors.inputBg, 4.5f, strings.getString(com.example.chat.R.string.conv_ui_field_text_a2f608))
                ConversationContrastNote(composerColors.uiButtons, uiEntryBg, 3f, strings.getString(com.example.chat.R.string.conv_ui_bar_icons_29dbfc))
                // Previous fixed theme glyph: Color(config.onAccentColor)
                ConversationContrastNote(com.example.chat.ui.kit.themeGlyphColor(composerColors.sendFill, config), composerColors.sendFill, 3f, strings.getString(com.example.chat.R.string.conv_ui_send_icon_ff1d9a))
                Spacer(Modifier.height(16.dp))
                GbCardContainer(headerText = strings.getString(com.example.chat.R.string.conv_ui_style_99a0ef)) {
                    Column {
                        com.example.chat.ui.kit.GbSettingRow(title = strings.getString(com.example.chat.R.string.conv_proposal_style_rollout),
                            subtitle = strings.getString(com.example.chat.R.string.conv_proposal_style_rollout_scope),switchValue = entryRollout,
                            onSwitchChanged = { preferences.updateGbFeature(CONVERSATION_ENTRY_ROLLOUT,it) })
                        ConversationComposerAppearanceControls(preferences, composerAppearance)
                        com.example.chat.ui.kit.GbSettingRow(
                            title = strings.getString(com.example.chat.R.string.conv_proposal_entry_haptics),
                            subtitle = strings.getString(com.example.chat.R.string.conv_proposal_session_only),
                            switchValue = selectionHaptics, onSwitchChanged = { selectionHaptics = it },
                            iconContent = { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.tune, semanticLabel = null) })
                        ConversationStyleGallery(
                            title = strings.getString(com.example.chat.R.string.conv_ui_message_bar_style_91e972),
                            subtitle = strings.getString(com.example.chat.R.string.conv_ui_preview_the_choices_apply_sa_4217ca),
                            values = com.example.chat.core.theme.ConversationEntryStyles.options,
                            enhanced = entryRollout,
                            quickPickCount = 3, quickPickKeys = conversationRecommendedEntryKeys,
                            quickPickTitle = strings.getString(com.example.chat.R.string.conv_proposal_recommended_entries),
                            keyOf = { it.id },
                            selected = com.example.chat.core.theme.ConversationEntryStyles.resolve(p.entryStyle),
                            labelOf = { if (entryRollout) it.label else it.legacyName },
                            familyOf = { it.family },
                            onSelected = { option ->
                                /* Previous callback-owned write/snackbar:
                                val previous = preferences.conversation.entryStyle
                                preferences.editConversation { latest -> latest.copy(entryStyle = option.id) }
                                scope.launch {
                                    snackbar.currentSnackbarData?.dismiss()
                                    val result = snackbar.showSnackbar("${option.label} applied", actionLabel = strings.getString(com.example.chat.R.string.conv_ui_undo_39fc72))
                                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed &&
                                        preferences.conversation.entryStyle == option.id) {
                                        preferences.editConversation { latest -> latest.copy(entryStyle = previous) }
                                    }
                                }
                                */
                                if (shouldPlayEntrySelectionHaptic(selectionHaptics,
                                    com.example.chat.core.theme.ConversationEntryStyles.resolve(p.entryStyle).id, option.id)) {
                                    com.example.chat.ui.kit.ChatyHapticsPort.light(selectionView)
                                }
                                model.onEvent(EntryStyleEvent.SelectStyle(option.id))
                            },
                        ) { option ->
                            com.example.chat.ui.chats.detail.ChatEntryBar(
                                entryStyle = option.legacyName, colors = composerColors,
                                appearance = composerAppearance,
                                template = com.example.chat.core.templates.ComposerTemplate(com.example.chat.core.templates.ComposerActionPlacement.integrated),
                                theme = config, text = strings.getString(com.example.chat.R.string.conv_ui_preview_f1fbb2), onTextChange = {}, onCamera = {}, onSend = {},
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                GbCardContainer(headerText = strings.getString(com.example.chat.R.string.conv_ui_colours_eb4e91)) {
                    Column {
                ConversationSettingsSection(title = "Bar and icons") {
                ConversationColorRow(
                    icon = com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.layers), title = strings.getString(com.example.chat.R.string.conv_ui_bar_background_854515), subtitle = strings.getString(com.example.chat.R.string.conv_ui_change_the_color_of_the_entr_32703b),
                    colorValue = p.uiEntryBackgroundColor,
                    // Previous row: onColorTap = { pickColor(strings.getString(com.example.chat.R.string.conv_ui_entry_background_color_edfc4d), (uiEntryBg).toArgb().toLong() and 0xFFFFFFFFL) { c -> preferences.editConversation { latest -> latest.copy(uiEntryBackgroundColor = c) } } },
                    onColorTap = { pickColor(strings.getString(com.example.chat.R.string.conv_ui_entry_background_color_edfc4d), (uiEntryBg).toArgb().toLong() and 0xFFFFFFFFL) { c -> model.onEvent(EntryStyleEvent.PickColor(EntryColorTarget.uiEntryBackgroundColor, c)) } },
                    effectiveColor = uiEntryBg,
                    // Previous row: onReset = { preferences.editConversation { latest -> latest.copy(uiEntryBackgroundColor = null) } },
                    onReset = { model.onEvent(EntryStyleEvent.PickColor(EntryColorTarget.uiEntryBackgroundColor, null)) },
                )
                ConversationColorRow(
                    icon = com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.toggle), title = strings.getString(com.example.chat.R.string.conv_ui_icon_colour_38def4), subtitle = strings.getString(com.example.chat.R.string.conv_ui_change_the_color_of_the_entr_193a28),
                    colorValue = p.uiButtonsColor,
                    // Previous row: onColorTap = { pickColor(strings.getString(com.example.chat.R.string.conv_ui_ui_buttons_color_9dd723), (composerColors.uiButtons).toArgb().toLong() and 0xFFFFFFFFL) { c -> preferences.editConversation { latest -> latest.copy(uiButtonsColor = c) } } },
                    onColorTap = { pickColor(strings.getString(com.example.chat.R.string.conv_ui_ui_buttons_color_9dd723), (composerColors.uiButtons).toArgb().toLong() and 0xFFFFFFFFL) { c -> model.onEvent(EntryStyleEvent.PickColor(EntryColorTarget.uiButtonsColor, c)) } },
                    effectiveColor = composerColors.uiButtons,
                    // Previous row: onReset = { preferences.editConversation { latest -> latest.copy(uiButtonsColor = null) } },
                    onReset = { model.onEvent(EntryStyleEvent.PickColor(EntryColorTarget.uiButtonsColor, null)) },
                )
                ConversationColorRow(
                    icon = com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.emoji), title = strings.getString(com.example.chat.R.string.conv_ui_emoji_icon_464a44), subtitle = strings.getString(com.example.chat.R.string.conv_ui_emoji_button_in_conversation_40bf37),
                    colorValue = p.emojiButtonColor,
                    // Previous row: onColorTap = { pickColor(strings.getString(com.example.chat.R.string.conv_ui_emoji_icon_464a44), (composerColors.emojiButton).toArgb().toLong() and 0xFFFFFFFFL) { c -> preferences.editConversation { latest -> latest.copy(emojiButtonColor = c) } } },
                    onColorTap = { pickColor(strings.getString(com.example.chat.R.string.conv_ui_emoji_icon_464a44), (composerColors.emojiButton).toArgb().toLong() and 0xFFFFFFFFL) { c -> model.onEvent(EntryStyleEvent.PickColor(EntryColorTarget.emojiButtonColor, c)) } },
                    effectiveColor = composerColors.emojiButton,
                    // Previous row: onReset = { preferences.editConversation { latest -> latest.copy(emojiButtonColor = null) } },
                    onReset = { model.onEvent(EntryStyleEvent.PickColor(EntryColorTarget.emojiButtonColor, null)) },
                )
                }
                ConversationSettingsSection(title = "Send and microphone buttons") {
                ConversationColorRow(
                    icon = com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.send), title = strings.getString(com.example.chat.R.string.conv_ui_send_fill_f4a388), subtitle = strings.getString(com.example.chat.R.string.conv_ui_colour_behind_the_send_glyph_11e831),
                    colorValue = p.sendButtonColor,
                    // Previous row: onColorTap = { pickColor(strings.getString(com.example.chat.R.string.conv_ui_send_icon_ff1d9a), (composerColors.sendFill).toArgb().toLong() and 0xFFFFFFFFL) { c -> preferences.editConversation { latest -> latest.copy(sendButtonColor = c) } } },
                    onColorTap = { pickColor(strings.getString(com.example.chat.R.string.conv_ui_send_icon_ff1d9a), (composerColors.sendFill).toArgb().toLong() and 0xFFFFFFFFL) { c -> model.onEvent(EntryStyleEvent.PickColor(EntryColorTarget.sendButtonColor, c)) } },
                    effectiveColor = composerColors.sendFill,
                    // Previous row: onReset = { preferences.editConversation { latest -> latest.copy(sendButtonColor = null) } },
                    onReset = { model.onEvent(EntryStyleEvent.PickColor(EntryColorTarget.sendButtonColor, null)) },
                )
                ConversationColorRow(
                    icon = com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.radioOn), title = strings.getString(com.example.chat.R.string.conv_ui_send_button_7c13d0), subtitle = strings.getString(com.example.chat.R.string.conv_ui_change_the_color_of_the_circ_79cb96),
                    colorValue = p.micSendBgCircleColor,
                    // Previous row: onColorTap = { pickColor(strings.getString(com.example.chat.R.string.conv_ui_mic_send_circle_color_d894d6), (composerColors.micCircleFill).toArgb().toLong() and 0xFFFFFFFFL) { c -> preferences.editConversation { latest -> latest.copy(micSendBgCircleColor = c) } } },
                    onColorTap = { pickColor(strings.getString(com.example.chat.R.string.conv_ui_mic_send_circle_color_d894d6), (composerColors.micCircleFill).toArgb().toLong() and 0xFFFFFFFFL) { c -> model.onEvent(EntryStyleEvent.PickColor(EntryColorTarget.micSendBgCircleColor, c)) } },
                    effectiveColor = composerColors.micCircleFill,
                    // Previous row: onReset = { preferences.editConversation { latest -> latest.copy(micSendBgCircleColor = null) } },
                    onReset = { model.onEvent(EntryStyleEvent.PickColor(EntryColorTarget.micSendBgCircleColor, null)) },
                )
                }
                ConversationSettingsSection(title = "Typing field") {
                ConversationColorRow(
                    icon = com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.paintFill), title = strings.getString(com.example.chat.R.string.conv_ui_field_background_062e6c), subtitle = strings.getString(com.example.chat.R.string.conv_ui_colour_behind_the_typing_are_e5c837),
                    colorValue = p.textEntryBackgroundColor,
                    // Previous row: onColorTap = { pickColor(strings.getString(com.example.chat.R.string.conv_ui_field_background_color_ec1969), (composerColors.inputBg).toArgb().toLong() and 0xFFFFFFFFL) { c -> preferences.editConversation { latest -> latest.copy(textEntryBackgroundColor = c) } } },
                    onColorTap = { pickColor(strings.getString(com.example.chat.R.string.conv_ui_field_background_color_ec1969), (composerColors.inputBg).toArgb().toLong() and 0xFFFFFFFFL) { c -> model.onEvent(EntryStyleEvent.PickColor(EntryColorTarget.textEntryBackgroundColor, c)) } },
                    effectiveColor = composerColors.inputBg,
                    // Previous row: onReset = { preferences.editConversation { latest -> latest.copy(textEntryBackgroundColor = null) } },
                    onReset = { model.onEvent(EntryStyleEvent.PickColor(EntryColorTarget.textEntryBackgroundColor, null)) },
                )
                ConversationColorRow(
                    icon = com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.textFormat), title = strings.getString(com.example.chat.R.string.conv_ui_field_text_a2f608), subtitle = strings.getString(com.example.chat.R.string.conv_ui_change_text_color_in_the_con_bc662f),
                    colorValue = p.textEntryColor,
                    // Previous row: onColorTap = { pickColor(strings.getString(com.example.chat.R.string.conv_ui_field_text_a2f608), (composerColors.inputText).toArgb().toLong() and 0xFFFFFFFFL) { c -> preferences.editConversation { latest -> latest.copy(textEntryColor = c) } } },
                    onColorTap = { pickColor(strings.getString(com.example.chat.R.string.conv_ui_field_text_a2f608), (composerColors.inputText).toArgb().toLong() and 0xFFFFFFFFL) { c -> model.onEvent(EntryStyleEvent.PickColor(EntryColorTarget.textEntryColor, c)) } },
                    effectiveColor = composerColors.inputText,
                    // Previous row: onReset = { preferences.editConversation { latest -> latest.copy(textEntryColor = null) } },
                    onReset = { model.onEvent(EntryStyleEvent.PickColor(EntryColorTarget.textEntryColor, null)) },
                )
                }
                    }
                    // Previous stock text action:
                    // androidx.compose.material3.TextButton(onClick = {
                    //                         /* Previous reset: preferences.editConversation { latest -> latest.copy(uiEntryBackgroundColor = null, uiButtonsColor = null,
                    //                             emojiButtonColor = null, sendButtonColor = null, micSendBgCircleColor = null,
                    //                             textEntryBackgroundColor = null, textEntryColor = null) } */
                    //                         model.onEvent(EntryStyleEvent.Reset)
                    //                     }) { Text(strings.getString(com.example.chat.R.string.conv_ui_reset_all_entry_colours_b2d814)) }
                    com.example.chat.ui.kit.ChatyTextButton(
                        label = strings.getString(com.example.chat.R.string.conv_ui_reset_all_entry_colours_b2d814),
                        onClick = {
                        /* Previous reset: preferences.editConversation { latest -> latest.copy(uiEntryBackgroundColor = null, uiButtonsColor = null,
                            emojiButtonColor = null, sendButtonColor = null, micSendBgCircleColor = null,
                            textEntryBackgroundColor = null, textEntryColor = null) } */
                        model.onEvent(EntryStyleEvent.Reset)
                    },
                    )
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
    pickers.Host()
}

/* Obsolete static renderer. The real ChatEntryBar above is the only active preview.
@Composable
private fun EntryStylePreview(style: String, pc: EntryPreviewColors) {
    @Composable fun emojiI(size: Dp = 22.dp) = com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.emoji, semanticLabel = null, color = pc.emoji, modifier = Modifier.size(size))
    @Composable fun attachI(size: Dp = 20.dp) = com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.attachment, semanticLabel = null, color = pc.buttons, modifier = Modifier.size(size))
    @Composable fun cameraI(size: Dp = 20.dp) = com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.camera, semanticLabel = null, color = pc.buttons, modifier = Modifier.size(size))
    @Composable fun micCircle() = Box(Modifier.size(44.dp).background(pc.micCircle, CircleShape), contentAlignment = Alignment.Center) {
        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.microphone, semanticLabel = null, color = pc.send, modifier = Modifier.size(22.dp))
    }
    @Composable fun sp(w: Int) = Spacer(Modifier.width(w.dp))

    @Composable
    fun RowScope.field(
        radius: Int = 20,
        transparent: Boolean = false,
        border: Boolean = false,
        shadow: Boolean = false,
        leading: (@Composable () -> Unit)? = null,
        trailing: (@Composable () -> Unit)? = null,
    ) {
        val shape = RoundedCornerShape(radius.dp)
        var m = Modifier.weight(1f)
        if (shadow) m = m.shadow(4.dp, shape, ambientColor = Color.Black.copy(alpha = 0.16f), spotColor = Color.Black.copy(alpha = 0.16f))
        m = m.background(if (transparent) Color.Transparent else pc.textEntryBg, shape)
        if (border) m = m.border(BorderStroke(1.dp, pc.textEntry.copy(alpha = 0.3f)), shape)
        Row(m.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) { leading(); sp(8) }
            Text(
                "Message", maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = pc.textEntry.copy(alpha = 0.6f), fontSize = 15.sp, modifier = Modifier.weight(1f),
            )
            if (trailing != null) { sp(8); trailing() }
        }
    }

    @Composable
    fun miniDots(n: Int) = Row(
        Modifier.fillMaxWidth().height(22.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (k in 0 until n) {
            if (k == 0) com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.emoji, semanticLabel = null, color = pc.emoji, modifier = Modifier.size(16.dp))
            else Icon(com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.presenceDot), null, tint = pc.buttons.copy(alpha = 0.7f), modifier = Modifier.size(5.dp))
        }
    }

    @Composable
    fun twoRow(top: @Composable () -> Unit) = Column { top(); Spacer(Modifier.height(4.dp)); miniDots(6) }

    val row: @Composable (@Composable RowScope.() -> Unit) -> Unit = { content ->
        Row(verticalAlignment = Alignment.CenterVertically, content = content)
    }

    when (style) {
        "WAMOD", "Hangouts", "Rounded" -> twoRow {
            row { field(16, leading = { emojiI(18.dp) }, trailing = { attachI(18.dp) }); sp(8); micCircle() }
        }
        "Hangouts V2", "Dribble", "Dribble V2" -> Column {
            miniDots(6); Spacer(Modifier.height(4.dp))
            row { field(14); sp(8); micCircle() }
        }
        "Aran V2", "WANH" -> row { attachI(20.dp); sp(6); emojiI(20.dp); sp(6); field(16); sp(8); micCircle() }
        "Allo" -> row { field(20, leading = { attachI(18.dp) }, trailing = { emojiI(18.dp) }); sp(8); micCircle() }
        "RC-Telegram X iOS", "iOS 14" -> row { attachI(20.dp); sp(6); field(18, trailing = { emojiI(18.dp) }); sp(8); micCircle() }
        "RC-IOS 11" -> row { emojiI(20.dp); sp(6); field(18, trailing = { attachI(18.dp) }); sp(8); micCircle() }
        "RC-Line", "FB Messenger", "RC Mess", "Instagram" ->
            row { attachI(20.dp); sp(4); cameraI(20.dp); sp(6); field(18); sp(8); micCircle() }
        "Concept" -> row { field(18, trailing = { cameraI(18.dp); sp(4); emojiI(18.dp) }); sp(8); micCircle() }
        "YOWA Style", "Mood" -> row { field(22, leading = { emojiI(18.dp) }, trailing = { attachI(18.dp) }); sp(8); micCircle() }
        "Simple" -> row { field(22, trailing = { attachI(18.dp) }); sp(8); micCircle() }
        "Old Stock (2016)" -> row { field(8, leading = { emojiI(18.dp) }, trailing = { attachI(18.dp) }); sp(8); micCircle() }
        "Telegram Modern" -> row {
            com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.attachment, semanticLabel = null, color = pc.buttons, modifier = Modifier.size(24.dp)); sp(8)
            field(14, trailing = { emojiI(20.dp) }); sp(8); micCircle()
        }
        "iOS Clean Rounded" -> row {
            Box(Modifier.size(36.dp).background(pc.buttons, CircleShape), contentAlignment = Alignment.Center) {
                com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.add, semanticLabel = null, color = Color.White, modifier = Modifier.size(22.dp))
            }
            sp(8); field(22); sp(8); micCircle()
        }
        "Hangouts Minimal" -> row { emojiI(); sp(8); field(8, transparent = true, border = true); sp(8); attachI(22.dp); sp(8); micCircle() }
        "Simple Pill" -> row { field(24, leading = { emojiI(20.dp) }); sp(8); attachI(22.dp); sp(6); cameraI(22.dp); sp(8); micCircle() }
        "RC-Flat Floating" -> {
            val shape = RoundedCornerShape(16.dp)
            Box(
                Modifier.fillMaxWidth()
                    .shadow(6.dp, shape, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.18f))
                    .background(pc.textEntryBg, shape).padding(4.dp),
            ) {
                row { field(12, transparent = true, leading = { emojiI(20.dp) }, trailing = { attachI() }); micCircle() }
            }
        }
        "WhatsApp Classic 3D" ->
            row { field(22, shadow = true, leading = { emojiI(20.dp) }, trailing = { attachI(); sp(8); cameraI() }); sp(8); micCircle() }
        "Signal Compact Bubble" -> row { field(18, trailing = { attachI() }); sp(8); micCircle() }
        else -> // "RC-MSG Google", "Stock", default
            row { field(leading = { emojiI() }, trailing = { attachI(); sp(8); cameraI() }); sp(8); micCircle() }
    }
}

*/
