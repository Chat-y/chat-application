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
import androidx.compose.runtime.collectAsState
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

/* Legacy duplicated picker lists retained for comparison/rollback.
private val tickStyleIds: List<DeliveryIconStyle> = listOf(
    DeliveryIconStyle.stock,
    DeliveryIconStyle.allo,
    DeliveryIconStyle.alien,
    DeliveryIconStyle.altCrayonV2,
    DeliveryIconStyle.altCrayon,
    DeliveryIconStyle.aran,
    DeliveryIconStyle.bar,
    DeliveryIconStyle.bat,
    DeliveryIconStyle.bbm,
    DeliveryIconStyle.blend,
    DeliveryIconStyle.ngAndroidBot,
    DeliveryIconStyle.bpgCircle,
    DeliveryIconStyle.gbCircles,
    DeliveryIconStyle.samyadeepCircles,
    DeliveryIconStyle.coalfield,
    DeliveryIconStyle.colorSimple,
    DeliveryIconStyle.ctoon,
    DeliveryIconStyle.eclipsis,
    DeliveryIconStyle.faceSwitch,
    DeliveryIconStyle.feet,
    DeliveryIconStyle.flatCircles,
    DeliveryIconStyle.gifcon,
    DeliveryIconStyle.googleV2,
    DeliveryIconStyle.google,
    DeliveryIconStyle.grw,
    DeliveryIconStyle.sidzyHd,
    DeliveryIconStyle.svHeart,
    DeliveryIconStyle.hike,
    DeliveryIconStyle.inbox,
    DeliveryIconStyle.insect,
    DeliveryIconStyle.gbIos,
    DeliveryIconStyle.jalopy,
    DeliveryIconStyle.jessy,
    DeliveryIconStyle.joaquins,
    DeliveryIconStyle.jokerTraffic,
    DeliveryIconStyle.letter,
    DeliveryIconStyle.like,
    DeliveryIconStyle.loveSwitch,
    DeliveryIconStyle.materialV2,
    DeliveryIconStyle.materialSwitch,
    DeliveryIconStyle.messenger,
    DeliveryIconStyle.mickeyHands,
    DeliveryIconStyle.minions,
    DeliveryIconStyle.newWaca,
    DeliveryIconStyle.neyHeart,
    DeliveryIconStyle.nhsc,
    DeliveryIconStyle.nazaNh,
    DeliveryIconStyle.nlCircle,
    DeliveryIconStyle.oldWaca,
    DeliveryIconStyle.pacman,
    DeliveryIconStyle.pixelated,
    DeliveryIconStyle.pokemon,
    DeliveryIconStyle.quinn,
    DeliveryIconStyle.snapChat,
    DeliveryIconStyle.sDot,
    DeliveryIconStyle.seasons,
    DeliveryIconStyle.smile,
    DeliveryIconStyle.star,
    DeliveryIconStyle.flatSwitch,
    DeliveryIconStyle.timeBomb,
    DeliveryIconStyle.jokerTraffic2,
    DeliveryIconStyle.treble,
    DeliveryIconStyle.twitterConcept,
    DeliveryIconStyle.wbg,
    DeliveryIconStyle.x,
    DeliveryIconStyle.pointer,
    DeliveryIconStyle.ticTicCircle2,
    DeliveryIconStyle.iceCream,
    DeliveryIconStyle.pony,
    DeliveryIconStyle.kitty,
    DeliveryIconStyle.flower,
    DeliveryIconStyle.sticker,
    DeliveryIconStyle.rcIos11,
    DeliveryIconStyle.bbmV2,
    DeliveryIconStyle.bwTicks,
    DeliveryIconStyle.cirCheck,
    DeliveryIconStyle.circlePrint,
    DeliveryIconStyle.gabCircle,
    DeliveryIconStyle.gabFace,
    DeliveryIconStyle.gabIflo,
    DeliveryIconStyle.greenTick,
    DeliveryIconStyle.ios2,
    DeliveryIconStyle.letterCircle,
    DeliveryIconStyle.rcAlo,
    DeliveryIconStyle.rcTick,
    DeliveryIconStyle.triangle,
    DeliveryIconStyle.vantCircle,
    DeliveryIconStyle.wings,
    DeliveryIconStyle.trafficLights,
    DeliveryIconStyle.stars,
    DeliveryIconStyle.hearts,
    DeliveryIconStyle.batman,
    DeliveryIconStyle.doubleSword,
    DeliveryIconStyle.glowingDots,
    DeliveryIconStyle.material3Rounded,
)

private val bubbleStyleIds: List<BubbleStyleId> = listOf(
    BubbleStyleId.stock,
    BubbleStyleId.facebookMessenger,
    BubbleStyleId.oldHangouts,
    BubbleStyleId.threeD,
    BubbleStyleId.rounded,
    BubbleStyleId.materialized,
    BubbleStyleId.md,
    BubbleStyleId.samyadeepCrayonAlt,
    BubbleStyleId.newHangouts4,
    BubbleStyleId.waPaperRedesigned,
    BubbleStyleId.transparent,
    BubbleStyleId.twitter,
    BubbleStyleId.whatsappLb,
    BubbleStyleId.bbm,
    BubbleStyleId.ios,
    BubbleStyleId.hike,
    BubbleStyleId.textra,
    BubbleStyleId.goSms,
    BubbleStyleId.bubbleDrop,
    BubbleStyleId.samyadeepDual,
    BubbleStyleId.plusMessengerEd,
    BubbleStyleId.waChatOn,
    BubbleStyleId.telegram,
    BubbleStyleId.inBubble,
    BubbleStyleId.bryed,
    BubbleStyleId.fold,
    BubbleStyleId.foldV2,
    BubbleStyleId.ilkhang,
    BubbleStyleId.line,
    BubbleStyleId.popzup,
    BubbleStyleId.doodleHang,
    BubbleStyleId.aranbor,
    BubbleStyleId.win,
    BubbleStyleId.mood,
    BubbleStyleId.crMessenger,
    BubbleStyleId.rcBurbuja2,
    BubbleStyleId.rcBurbuja5,
    BubbleStyleId.threeDV2,
    BubbleStyleId.kitty,
    BubbleStyleId.amor,
    BubbleStyleId.rcSamsBord,
    BubbleStyleId.rcIos11,
    BubbleStyleId.rcGoogleAssistan,
    BubbleStyleId.gabiOutline,
    BubbleStyleId.gabiSqua,
    BubbleStyleId.gabiDot,
    BubbleStyleId.rcLine,
    BubbleStyleId.gabiDot2,
    BubbleStyleId.roundle,
    BubbleStyleId.gabyRon,
    BubbleStyleId.cvIos,
    BubbleStyleId.rcPal,
    BubbleStyleId.rcIosLine,
    BubbleStyleId.rcImLine,
    BubbleStyleId.rcGoSms,
    BubbleStyleId.rcTranLine,
    BubbleStyleId.rcImess,
    BubbleStyleId.gabiMess,
    BubbleStyleId.rcFancy,
)

*/
/* Previous local enum lists/family function; registry now owns metadata.
private val tickStyleIds = DeliveryIconStyle.entries.toList()
private val bubbleStyleIds = BubbleStyleId.entries.toList()

private fun conversationStyleFamily(name: String): String {
    val key = name.lowercase(java.util.Locale.ROOT)
    return when {
        "stock" in key || "classic" in key -> "Classic"
        "circle" in key || "dot" in key || "round" in key -> "Rounded"
        "line" in key || "flat" in key || "material" in key || "transparent" in key -> "Minimal"
        else -> "Decorative"
    }
}

*/

private fun conv(v: Long?, fallback: Color) = v?.let { Color(it) } ?: fallback

/**
 * Port of ConversationBubbleAndTicksScreen (_ConversationBubbleAndTicksScreenState).
 * Tick key: gbIos/gbCircles persist by enum name, others by displayName.
 * Bubble set: bubbleStyle (bubbleShape is a read-only alias in Kotlin) + updateGbFeatures
 * {'bubble_style'} with logTitle 'Bubbles Style'. UNCOMPILED (Compose); tick preview paints the
 * 16dp W2 icon inside a 22x16 box (Dart paints the painter on a 22x16 canvas).
 */
@Composable
fun ConversationBubbleAndTicksScreen(preferences: ChatyPreferencesController, onBack: () -> Unit) {
    var generation by remember(preferences) { androidx.compose.runtime.mutableLongStateOf(preferences.scopeGeneration) }
    DisposableEffect(preferences) {
        // Previous generation listener updated state on the notifying thread without a disposal guard.
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        var attached = true
        val listener: () -> Unit = {
            val refresh = { if (attached) generation = preferences.scopeGeneration }
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) refresh() else main.post { refresh() }
        }
        preferences.addListener(listener); listener()
        onDispose { attached = false; preferences.removeListener(listener) }
    }
    // Dispose pending picker/gallery/density state when the reusable controller changes account.
    androidx.compose.runtime.key(preferences,generation) {
        ScopedConversationBubbleAndTicksScreen(preferences,generation,onBack)
    }
}

@Composable
private fun ScopedConversationBubbleAndTicksScreen(
    preferences: ChatyPreferencesController, generation: Long, onBack: () -> Unit,
) {
    val scopeIsCurrent = remember(preferences,generation) { { preferences.scopeGeneration == generation } }

    val strings = androidx.compose.ui.platform.LocalContext.current.resources
    val config = LocalThemeConfig.current
    val colors = LocalAppColors.current
    // Legacy whole-controller version listener replaced by a conversation-only snapshot.
    // Previous broad snapshot: val p = rememberConversationPreferences(preferences)
    // Previous snapshot: val p = rememberConversationPreferences(preferences, ::selectBubbleAndTicksPreferences)
    val model: BubbleAndTicksViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        // Previous key omitted the account generation.
        key = "conversation-bubbles-${System.identityHashCode(preferences)}-$generation",
        factory = androidx.compose.runtime.remember(preferences,generation) {
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(BubbleAndTicksViewModel::class.java))
                    return BubbleAndTicksViewModel(read = { if (scopeIsCurrent()) preferences.conversation else com.example.chat.domain.models.ConversationPreferences() },
                        write = { if (scopeIsCurrent()) preferences.updateConversation(it) },
                        subscribe = { preferences.addListener(it) }, unsubscribe = { preferences.removeListener(it) },
                        initialTheme = BubblePreviewTheme(config, colors.surface, colors.surfaceSecondary),
                        // Retained model callbacks must not edit the controller's later account.
                        transaction = { transform -> if (scopeIsCurrent()) preferences.editConversation { latest ->
                            if (scopeIsCurrent()) transform(latest) else latest
                        } }, scopeIsCurrent = scopeIsCurrent) as T
                }
            }
        },
    )
    // Lifecycle-aware collection is separately blocked by the excluded dependency/controller work.
    val state by model.uiState.collectAsState()
    val bubbleRollout = rememberConversationStyleRollout(preferences,CONVERSATION_BUBBLE_ROLLOUT,generation)
    val p = state.preferences
    val messageGap = rememberConversationMessageGap(preferences,generation)
    val previewState = state.preview
    androidx.compose.runtime.SideEffect { model.setTheme(BubblePreviewTheme(config, colors.surface, colors.surfaceSecondary)) }

    val accent = Color(config.accentColor)
    val fs = config.fontScale.toFloat().let { if (it.isFinite()) it.coerceIn(0.8f, 2f) else 1f }

    /* Previous screen-owned derived colors:
    val convBg = conv(p.convBackgroundColor, colors.surface)
    val rightBubbleBg = conv(p.rightBubbleColor, accent)
    val rightTextCol = conv(p.rightChatBubbleTextColor, conversationOnColor(rightBubbleBg, Color(config.primaryTextColor), Color(config.backgroundColor)))
    val rightTimeCol = conv(p.rightBubbleTimeColor, rightTextCol)
    val leftBubbleBg = conv(p.leftBubbleColor, colors.surfaceSecondary)
    val leftTextCol = conv(p.leftChatBubbleTextColor, Color(config.primaryTextColor))
    val leftTimeCol = conv(p.leftBubbleTimeColor, Color(config.secondaryTextColor))
    val quotedBg = conv(p.quotedBackgroundColor, colors.surface)
    val quotedDivider = conv(p.quotedDividerColor, accent)
    val quotedName = conv(p.quotedNameColor, accent)
    val quotedMsg = conv(p.quotedMessageColor, Color(config.secondaryTextColor))
    val textSize = p.messageTextSize.toFloat().coerceIn(12f, 26f)
    */
    val convBg = previewState.convBg
    val rightBubbleBg = previewState.rightBubbleBg
    val rightTextCol = previewState.rightTextCol
    val rightTimeCol = previewState.rightTimeCol
    val leftBubbleBg = previewState.leftBubbleBg
    val leftTextCol = previewState.leftTextCol
    val leftTimeCol = previewState.leftTimeCol
    val quotedBg = previewState.quotedBg
    val quotedDivider = previewState.quotedDivider
    val quotedName = previewState.quotedName
    val quotedMsg = previewState.quotedMsg
    val textSize = previewState.textSize
    val sliderView = androidx.compose.ui.platform.LocalView.current
    var lastTextDetent by androidx.compose.runtime.remember(textSize) { androidx.compose.runtime.mutableStateOf(conversationTextDetent(textSize)) }
    val sampleContext = androidx.compose.ui.platform.LocalContext.current
    val sampleTime = android.text.format.DateFormat.getTimeFormat(sampleContext).format(java.util.Date(0))

    fun setTickStyle(id: DeliveryIconStyle) {
        val key = if (bubbleRollout) id.name else conversationLegacyTickValue(id)
        // Previous writer: preferences.editConversation { latest -> latest.copy(tickStyle = key) }
        model.edit { latest -> latest.copy(tickStyle = key) }
    }
    fun setBubbleStyle(id: BubbleStyleId) {
        val value = if (bubbleRollout) id.name else conversationLegacyBubbleValue(id)
        // Previous writer: preferences.editConversation { latest -> latest.copy(bubbleStyle = value) }
        model.edit { latest -> latest.copy(bubbleStyle = value) }
        // Legacy duplicate GB write disabled: conversation prefs are the authoritative style.
        // preferences.updateGbFeatures(mapOf("bubble_style" to value), logTitle = "Bubbles Style")
    }

    /* Previous local tick renderer, now shared with BubblePreview.
    @Composable
    fun tickPreview(id: DeliveryIconStyle, color: Color) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // Previous local preview-state list: sent, delivered, read. Registry owns it now.
            ConversationStyleRegistry.tick(id).previewStates.forEach { state ->
                DeliveryStatusIcon(style = id, state = state, unreadColor = color, readColor = accent, size = 16.dp)
            }
        }
    }

    */

    val pickers = rememberGbPickers()
    // Previous picker callback unconditionally applied into the reusable controller.
    fun pickColor(title: String, current: Long?, apply: (Long) -> Unit) {
        if (scopeIsCurrent()) pickers.showColor(title, current ?: config.accentColor) {
            if (scopeIsCurrent()) apply(it)
        }
    }

    // Legacy fixed incoming shape disabled; both sides now use ChatyBubbleContainer.

    // Previous per-screen shell: Column(Modifier.fillMaxSize().background(Color(config.backgroundColor))) {
    // Previous per-screen header: com.example.chat.ui.kit.ChatyAppBar(title = strings.getString(com.example.chat.R.string.conv_ui_bubbles_and_ticks_f7fcbf), onBack = onBack, titleModifier = Modifier.semantics { heading() })
    ConversationSettingsScaffold(title = strings.getString(com.example.chat.R.string.conv_ui_bubbles_and_ticks_f7fcbf), onBack = onBack) {

        var groupedPreview by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
        var showPreview by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(true) }
        androidx.compose.material3.TextButton(onClick = { showPreview = !showPreview }) {
            Text(if (showPreview) strings.getString(com.example.chat.R.string.conv_ui_hide_preview_78cb0e) else strings.getString(com.example.chat.R.string.conv_ui_show_preview_0e7698))
        }
        ConversationAdaptiveSettingsBody(
            modifier = Modifier.weight(1f).fillMaxWidth().navigationBarsPadding().imePadding()
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)),
            compactPinnedPreview = true,
            preview = {
        // Previous preview bounds: if (showPreview) Box(Modifier.align(Alignment.CenterHorizontally).widthIn(max = 600.dp).padding(16.dp, 8.dp)) {
        if (showPreview) Box(Modifier.fillMaxWidth()) {
            BubblePreview(previewState, groupedCorners = groupedPreview, messageGapDp = messageGap)
            /* Previous preview assembly:
            BubblePreview(BubblePreviewState(
                bubbleStyle = bubbleStyleIdFromString(p.bubbleStyle),
                tickStyle = deliveryIconStyleFromString(p.tickStyle),
                convBg = convBg,
                rightBubbleBg = rightBubbleBg,
                rightTextCol = rightTextCol,
                rightTimeCol = rightTimeCol,
                leftBubbleBg = leftBubbleBg,
                leftTextCol = leftTextCol,
                leftTimeCol = leftTimeCol,
                quotedBg = quotedBg,
                quotedDivider = quotedDivider,
                quotedName = quotedName,
                quotedMsg = quotedMsg,
                deletedIconColor = conv(p.deletedMessageIconColor, Color(config.secondaryTextColor)),
                textSize = textSize, makeTextSelectable = p.makeTextSelectable, removeReadMore = p.removeReadMore,
            ))
            */
            /* Original inline preview retained; BubblePreview above now owns rendering.
            GbLivePreviewCard(modifier = Modifier.semantics { contentDescription = strings.getString(com.example.chat.R.string.conv_ui_sample_conversation_with_inc_d78404) }, padding = PaddingValues(ConversationPreviewLayout.cardPaddingDp.dp /* previous: 12.dp */)) {
                androidx.compose.foundation.layout.BoxWithConstraints {
                val sampleBubbleWidth = maxWidth * config.bubbleMaxWidthFactor.toFloat()
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState()).background(convBg, RoundedCornerShape(16.dp)).padding(ConversationPreviewLayout.cardPaddingDp.dp /* previous: 12.dp */),
                ) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        ChatyBubbleContainer(styleId = bubbleStyleIdFromString(p.bubbleStyle), isMe = false,
                            fillColor = leftBubbleBg, accentColor = accent, modifier = Modifier.widthIn(max = sampleBubbleWidth)) {
                            Column {
                                Text(strings.getString(com.example.chat.R.string.conv_ui_alex_64542d), color = accent, fontSize = (11 * fs).sp, fontWeight = FontWeight.W700)
                                Text(strings.getString(com.example.chat.R.string.conv_ui_hello_69342c), color = leftTextCol, fontSize = textSize.sp)
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.CenterStart) {
                        ChatyBubbleContainer(styleId = bubbleStyleIdFromString(p.bubbleStyle), isMe = false,
                            fillColor = leftBubbleBg, accentColor = accent, modifier = Modifier.widthIn(max = sampleBubbleWidth)) {
                            Column {
                                Text(strings.getString(com.example.chat.R.string.conv_ui_do_you_like_my_theme_757cae), color = leftTextCol, fontSize = textSize.sp)
                                Text(sampleTime, color = leftTimeCol, fontSize = (11 * fs).sp)
                            }
                        }
                    }
                    ChatyBubbleContainer(styleId = bubbleStyleIdFromString(p.bubbleStyle), isMe = false,
                        fillColor = leftBubbleBg, accentColor = accent, modifier = Modifier.widthIn(max = sampleBubbleWidth).padding(vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.close,
                                semanticLabel = null,
                                color = conv(p.deletedMessageIconColor, Color(config.secondaryTextColor)),
                                modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(7.dp))
                            Text(strings.getString(com.example.chat.R.string.conv_ui_deleted_message_5426bd),
                                color = Color(config.secondaryTextColor), fontSize = (12 * fs).sp)
                        }
                        /* Previous sample coloured only text, not the deleted icon.
                        Text(strings.getString(com.example.chat.R.string.conv_ui_deleted_message_5426bd),
                            color = conv(p.deletedMessageIconColor, Color(config.secondaryTextColor)), fontSize = (12 * fs).sp)
                        */
                    }
                    val longSample = strings.getString(com.example.chat.R.string.conv_ui_a_longer_preview_message_sho_ead0e2).repeat(5)
                    val sample: @Composable () -> Unit = {
                        Text(longSample, color = leftTextCol, fontSize = textSize.sp,
                            maxLines = if (p.removeReadMore) Int.MAX_VALUE else 3,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    ChatyBubbleContainer(styleId = bubbleStyleIdFromString(p.bubbleStyle), isMe = false,
                        fillColor = leftBubbleBg, accentColor = accent, modifier = Modifier.widthIn(max = sampleBubbleWidth).padding(vertical = 8.dp)) {
                        Column {
                            if (p.makeTextSelectable) androidx.compose.foundation.text.selection.SelectionContainer { sample() } else sample()
                            if (!p.removeReadMore) Text(strings.getString(com.example.chat.R.string.conv_ui_read_more_ad086c), color = accent, fontSize = (12 * fs).sp)
                        }
                    }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Box(Modifier.widthIn(max = sampleBubbleWidth)) {
                            ChatyBubbleContainer(
                                styleId = bubbleStyleIdFromString(p.bubbleStyle),
                                isMe = true, fillColor = rightBubbleBg, accentColor = accent,
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
                                        tickPreview(deliveryIconStyleFromString(p.tickStyle), rightTimeCol)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
            */
        }
            },
            controls = {
            ConversationMessageDensityControls(preferences, messageGap,generation)
            ConversationBubbleTailControls(preferences,generation)
            com.example.chat.ui.kit.GbSettingRow(title = strings.getString(com.example.chat.R.string.conv_proposal_grouped_preview),
                subtitle = strings.getString(com.example.chat.R.string.conv_proposal_preview_only),
                switchValue = groupedPreview, onSwitchChanged = { groupedPreview = it },
                iconContent = { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.tune, semanticLabel = null) })

        /* Previous option body, scrolling now owned by adaptive shell:
        Column(
            Modifier.weight(1f).align(Alignment.CenterHorizontally).widthIn(max = 600.dp).fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)).padding(horizontal = ConversationPreviewLayout.outerPaddingDp.dp, vertical = 8.dp /* previous horizontal: 14.dp */),
        ) {
        */
            com.example.chat.ui.kit.GbSettingRow(title = strings.getString(com.example.chat.R.string.conv_proposal_style_rollout),
                subtitle = strings.getString(com.example.chat.R.string.conv_proposal_style_rollout_scope),switchValue = bubbleRollout,
                onSwitchChanged = { if (scopeIsCurrent()) preferences.updateGbFeature(CONVERSATION_BUBBLE_ROLLOUT,it) })
            ConversationSettingsSection(title = strings.getString(com.example.chat.R.string.conv_ui_styles_look_and_feel_9ebc23)) {
                Column {
                    ConversationStyleGallery(
                        title = strings.getString(com.example.chat.R.string.conv_ui_ticks_style_ea6c4e),
                        subtitle = strings.getString(com.example.chat.R.string.conv_ui_preview_choices_apply_saves__bc203c),
                        values = ConversationStyleRegistry.ticks, quickPickCount = 8, enhanced = bubbleRollout,
                        keyOf = { it.id.name },
                        familyOf = { it.family },
                        selected = ConversationStyleRegistry.tick(deliveryIconStyleFromString(p.tickStyle)),
                        // Legacy label: labelOf = { id -> id.displayName },
                        labelOf = { if (bubbleRollout) it.label else it.id.displayName },
                        onSelected = { setTickStyle(it.id) },
                    ) { option -> ConversationTickPreview(option.id, rightTimeCol) }
                    ConversationStyleGallery(
                        title = strings.getString(com.example.chat.R.string.conv_ui_bubbles_style_8eac85),
                        subtitle = strings.getString(com.example.chat.R.string.conv_ui_preview_shapes_apply_saves_y_32b25b),
                        values = ConversationStyleRegistry.bubbles, quickPickCount = 8, enhanced = bubbleRollout,
                        keyOf = { it.id.name },
                        familyOf = { it.family },
                        selected = ConversationStyleRegistry.bubble(bubbleStyleIdFromString(p.bubbleStyle)),
                        // Legacy label: labelOf = { id -> id.displayName },
                        labelOf = { if (!bubbleRollout) it.id.displayName else if (it.recommended) strings.getString(com.example.chat.R.string.conv_proposal_classic_recommended) else it.label },
                        onSelected = { setBubbleStyle(it.id) },
                    ) { option ->
                        Box(Modifier.width(64.dp)) {
                            ChatyBubbleContainer(
                                styleId = option.id, isMe = true,
                                fillColor = accent.copy(alpha = 0.85f), accentColor = accent,
                            ) {
                                // Previous gallery text used Color.White regardless of fill.
                                Text(strings.getString(com.example.chat.R.string.conv_ui_hi_94dd9e),
                                    color = com.example.chat.ui.kit.themeGlyphColor(accent.copy(alpha = 0.85f), config),
                                    fontSize = (11 * fs).sp)
                            }
                        }
                    }
                    GbSliderTile(
                        title = strings.getString(com.example.chat.R.string.conv_ui_message_text_size_67448e),
                        subtitle = strings.getString(com.example.chat.R.string.conv_ui_before_system_font_scaling_1389fe),
                        value = textSize,
                        unit = strings.getString(com.example.chat.R.string.conv_proposal_points_unit),
                        min = 12f, max = 26f, divisions = 14,
                        // Previous writer: onChanged = { preferences.editConversation { latest -> latest.copy(messageTextSize = it.toDouble()) } },
                        // Previous adapter callback: onChanged = { model.edit { latest -> latest.copy(messageTextSize = it.toDouble()) } },
                        onChanged = { value ->
                            val detent = conversationTextDetent(value)
                            if (detent != null && detent != lastTextDetent) {
                                com.example.chat.ui.kit.ChatyHapticsPort.selection(sliderView)
                                lastTextDetent = detent
                            }
                            model.edit { latest -> latest.copy(messageTextSize = value.toDouble()) }
                        },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            ConversationBubblePalette(p.rightBubbleColor, p.rightChatBubbleTextColor) { pair ->
                model.edit { latest -> latest.copy(rightBubbleColor = pair.background,
                    rightChatBubbleTextColor = pair.foreground, rightBubbleTimeColor = pair.foreground) }
            }
            ConversationContrastNote(rightTextCol, rightBubbleBg, 4.5f, strings.getString(com.example.chat.R.string.conv_ui_your_message_text_98dcca))
            ConversationContrastNote(rightTimeCol, rightBubbleBg, 4.5f, strings.getString(com.example.chat.R.string.conv_ui_your_message_time_2be207))
            ConversationContrastNote(leftTextCol, leftBubbleBg, 4.5f, strings.getString(com.example.chat.R.string.conv_ui_their_message_text_5eb8ee))
            ConversationContrastNote(quotedMsg, quotedBg, 4.5f, strings.getString(com.example.chat.R.string.conv_ui_quoted_text_b5a1ed))
            ConversationContrastNote(accent, leftBubbleBg, 4.5f, strings.getString(com.example.chat.R.string.conv_ui_sender_name_96d72a))
            ConversationContrastNote(rightBubbleBg, convBg, 1.3f, strings.getString(com.example.chat.R.string.conv_ui_your_message_boundary_34220a))
            ConversationContrastNote(leftBubbleBg, convBg, 1.3f, strings.getString(com.example.chat.R.string.conv_ui_their_message_boundary_c2dbc0))
            ConversationContrastNote(accent, rightTimeCol, 3f, strings.getString(com.example.chat.R.string.conv_ui_read_and_delivered_ticks_021cc2))
            ConversationSettingsSection(title = strings.getString(com.example.chat.R.string.conv_ui_conversation_background_2f906b)) {
                Column {
                    ConversationColorRow(
                        icon = com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.wallpaper), title = strings.getString(com.example.chat.R.string.conv_ui_conversation_background_colo_ebd884), subtitle = if (p.wallpaperType.equals("none", true) || p.wallpaperType.equals("no wallpaper", true)) strings.getString(com.example.chat.R.string.conv_ui_conversation_background_2f906b) else strings.getString(com.example.chat.R.string.conv_ui_applies_only_without_wallpap_60db0c),
                        colorValue = p.convBackgroundColor,
                        enabled = p.wallpaperType.equals("none", true) || p.wallpaperType.equals("no wallpaper", true),
                        // Previous writer: onColorTap = { pickColor(strings.getString(com.example.chat.R.string.conv_ui_conversation_background_colo_ebd884), (convBg).toArgb().toLong() and 0xFFFFFFFFL) { c -> preferences.editConversation { latest -> latest.copy(convBackgroundColor = c) } } },
                        onColorTap = { pickColor(strings.getString(com.example.chat.R.string.conv_ui_conversation_background_colo_ebd884), (convBg).toArgb().toLong() and 0xFFFFFFFFL) { c -> model.edit { latest -> latest.copy(convBackgroundColor = c) } } },
                        effectiveColor = convBg,
                    // Previous writer: onReset = { preferences.editConversation { latest -> latest.copy(convBackgroundColor = null) } },
                    onReset = { model.edit { latest -> latest.copy(convBackgroundColor = null) } },
                )
                }
            }
            Spacer(Modifier.height(16.dp))
            ConversationSettingsSection(title = strings.getString(com.example.chat.R.string.conv_ui_your_messages_2634b8)) {
                Column {
                    ConversationColorRow(
                        icon = com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.chats), title = strings.getString(com.example.chat.R.string.conv_ui_your_message_background_b0a1fc), subtitle = strings.getString(com.example.chat.R.string.conv_ui_change_right_chat_bubble_col_c4975d),
                        colorValue = p.rightBubbleColor,
                        // Previous writer: onColorTap = { pickColor(strings.getString(com.example.chat.R.string.conv_ui_right_bubble_color_ba37f3), (rightBubbleBg).toArgb().toLong() and 0xFFFFFFFFL) { c -> preferences.editConversation { latest -> latest.copy(rightBubbleColor = c) } } },
                        onColorTap = { pickColor(strings.getString(com.example.chat.R.string.conv_ui_right_bubble_color_ba37f3), (rightBubbleBg).toArgb().toLong() and 0xFFFFFFFFL) { c -> model.edit { latest -> latest.copy(rightBubbleColor = c) } } },
                        effectiveColor = rightBubbleBg,
                    // Previous writer: onReset = { preferences.editConversation { latest -> latest.copy(rightBubbleColor = null) } },
                    onReset = { model.edit { latest -> latest.copy(rightBubbleColor = null) } },
                )
                    ConversationColorRow(
                        icon = com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.textFormat), title = strings.getString(com.example.chat.R.string.conv_ui_your_message_text_98dcca), subtitle = strings.getString(com.example.chat.R.string.conv_ui_sets_right_chat_bubble_text__e9bdcc),
                        colorValue = p.rightChatBubbleTextColor,
                        // Previous writer: onColorTap = { pickColor(strings.getString(com.example.chat.R.string.conv_ui_right_bubble_text_color_e3d3d4), (rightTextCol).toArgb().toLong() and 0xFFFFFFFFL) { c -> preferences.editConversation { latest -> latest.copy(rightChatBubbleTextColor = c) } } },
                        onColorTap = { pickColor(strings.getString(com.example.chat.R.string.conv_ui_right_bubble_text_color_e3d3d4), (rightTextCol).toArgb().toLong() and 0xFFFFFFFFL) { c -> model.edit { latest -> latest.copy(rightChatBubbleTextColor = c) } } },
                        effectiveColor = rightTextCol,
                    // Previous writer: onReset = { preferences.editConversation { latest -> latest.copy(rightChatBubbleTextColor = null) } },
                    onReset = { model.edit { latest -> latest.copy(rightChatBubbleTextColor = null) } },
                )
                    ConversationColorRow(
                        icon = com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.timer), title = strings.getString(com.example.chat.R.string.conv_ui_your_message_time_2be207), subtitle = strings.getString(com.example.chat.R.string.conv_ui_change_the_color_of_the_time_e42c50),
                        colorValue = p.rightBubbleTimeColor,
                        // Previous writer: onColorTap = { pickColor(strings.getString(com.example.chat.R.string.conv_ui_right_bubble_time_color_7f2d8c), (rightTimeCol).toArgb().toLong() and 0xFFFFFFFFL) { c -> preferences.editConversation { latest -> latest.copy(rightBubbleTimeColor = c) } } },
                        onColorTap = { pickColor(strings.getString(com.example.chat.R.string.conv_ui_right_bubble_time_color_7f2d8c), (rightTimeCol).toArgb().toLong() and 0xFFFFFFFFL) { c -> model.edit { latest -> latest.copy(rightBubbleTimeColor = c) } } },
                        effectiveColor = rightTimeCol,
                    // Previous writer: onReset = { preferences.editConversation { latest -> latest.copy(rightBubbleTimeColor = null) } },
                    onReset = { model.edit { latest -> latest.copy(rightBubbleTimeColor = null) } },
                )
                }
            }
            Spacer(Modifier.height(16.dp))
            ConversationSettingsSection(title = strings.getString(com.example.chat.R.string.conv_ui_their_messages_286928)) {
                Column {
                    ConversationColorRow(
                        icon = com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.bubbleChart), title = strings.getString(com.example.chat.R.string.conv_ui_their_message_background_c34216), subtitle = strings.getString(com.example.chat.R.string.conv_ui_change_left_chat_bubble_colo_d3417c),
                        colorValue = p.leftBubbleColor,
                        // Previous writer: onColorTap = { pickColor(strings.getString(com.example.chat.R.string.conv_ui_left_bubble_color_de8179), (leftBubbleBg).toArgb().toLong() and 0xFFFFFFFFL) { c -> preferences.editConversation { latest -> latest.copy(leftBubbleColor = c) } } },
                        onColorTap = { pickColor(strings.getString(com.example.chat.R.string.conv_ui_left_bubble_color_de8179), (leftBubbleBg).toArgb().toLong() and 0xFFFFFFFFL) { c -> model.edit { latest -> latest.copy(leftBubbleColor = c) } } },
                        effectiveColor = leftBubbleBg,
                    // Previous writer: onReset = { preferences.editConversation { latest -> latest.copy(leftBubbleColor = null) } },
                    onReset = { model.edit { latest -> latest.copy(leftBubbleColor = null) } },
                )
                    ConversationColorRow(
                        icon = com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.textFormat), title = strings.getString(com.example.chat.R.string.conv_ui_their_message_text_5eb8ee), subtitle = strings.getString(com.example.chat.R.string.conv_ui_sets_left_chat_bubble_text_c_16a599),
                        colorValue = p.leftChatBubbleTextColor,
                        // Previous writer: onColorTap = { pickColor(strings.getString(com.example.chat.R.string.conv_ui_left_bubble_text_color_b0e74d), (leftTextCol).toArgb().toLong() and 0xFFFFFFFFL) { c -> preferences.editConversation { latest -> latest.copy(leftChatBubbleTextColor = c) } } },
                        onColorTap = { pickColor(strings.getString(com.example.chat.R.string.conv_ui_left_bubble_text_color_b0e74d), (leftTextCol).toArgb().toLong() and 0xFFFFFFFFL) { c -> model.edit { latest -> latest.copy(leftChatBubbleTextColor = c) } } },
                        effectiveColor = leftTextCol,
                    // Previous writer: onReset = { preferences.editConversation { latest -> latest.copy(leftChatBubbleTextColor = null) } },
                    onReset = { model.edit { latest -> latest.copy(leftChatBubbleTextColor = null) } },
                )
                    ConversationColorRow(
                        icon = com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.timer), title = strings.getString(com.example.chat.R.string.conv_ui_their_message_time_62e14e), subtitle = strings.getString(com.example.chat.R.string.conv_ui_change_the_color_of_the_time_5ff7a1),
                        colorValue = p.leftBubbleTimeColor,
                        // Previous writer: onColorTap = { pickColor(strings.getString(com.example.chat.R.string.conv_ui_left_bubble_time_color_c564db), (leftTimeCol).toArgb().toLong() and 0xFFFFFFFFL) { c -> preferences.editConversation { latest -> latest.copy(leftBubbleTimeColor = c) } } },
                        onColorTap = { pickColor(strings.getString(com.example.chat.R.string.conv_ui_left_bubble_time_color_c564db), (leftTimeCol).toArgb().toLong() and 0xFFFFFFFFL) { c -> model.edit { latest -> latest.copy(leftBubbleTimeColor = c) } } },
                        effectiveColor = leftTimeCol,
                    // Previous writer: onReset = { preferences.editConversation { latest -> latest.copy(leftBubbleTimeColor = null) } },
                    onReset = { model.edit { latest -> latest.copy(leftBubbleTimeColor = null) } },
                )
                }
            }
            Spacer(Modifier.height(16.dp))
            ConversationSettingsSection(title = strings.getString(com.example.chat.R.string.conv_ui_quotes_and_extras_f99edc)) {
                Column {
                    ConversationColorRow(
                        icon = com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.deleteOutline), title = strings.getString(com.example.chat.R.string.conv_ui_deleted_message_icon_color_067f99), subtitle = strings.getString(com.example.chat.R.string.conv_ui_change_the_color_of_the_revo_5bcd15),
                        colorValue = p.deletedMessageIconColor,
                        // Previous writer: onColorTap = { pickColor(strings.getString(com.example.chat.R.string.conv_ui_deleted_message_icon_color_067f99), (previewState.deletedIconColor).toArgb().toLong() and 0xFFFFFFFFL) { c -> preferences.editConversation { latest -> latest.copy(deletedMessageIconColor = c) } } },
                        onColorTap = { pickColor(strings.getString(com.example.chat.R.string.conv_ui_deleted_message_icon_color_067f99), (previewState.deletedIconColor).toArgb().toLong() and 0xFFFFFFFFL) { c -> model.edit { latest -> latest.copy(deletedMessageIconColor = c) } } },
                        effectiveColor = previewState.deletedIconColor,
                    // Previous writer: onReset = { preferences.editConversation { latest -> latest.copy(deletedMessageIconColor = null) } },
                    onReset = { model.edit { latest -> latest.copy(deletedMessageIconColor = null) } },
                )
                    ConversationColorRow(
                        icon = com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.quote), title = strings.getString(com.example.chat.R.string.conv_ui_quoted_divider_color_945344), subtitle = strings.getString(com.example.chat.R.string.conv_ui_color_of_the_vertical_bar_be_d3e5e1),
                        colorValue = p.quotedDividerColor,
                        // Previous writer: onColorTap = { pickColor(strings.getString(com.example.chat.R.string.conv_ui_quoted_divider_color_945344), (quotedDivider).toArgb().toLong() and 0xFFFFFFFFL) { c -> preferences.editConversation { latest -> latest.copy(quotedDividerColor = c) } } },
                        onColorTap = { pickColor(strings.getString(com.example.chat.R.string.conv_ui_quoted_divider_color_945344), (quotedDivider).toArgb().toLong() and 0xFFFFFFFFL) { c -> model.edit { latest -> latest.copy(quotedDividerColor = c) } } },
                        effectiveColor = quotedDivider,
                    // Previous writer: onReset = { preferences.editConversation { latest -> latest.copy(quotedDividerColor = null) } },
                    onReset = { model.edit { latest -> latest.copy(quotedDividerColor = null) } },
                )
                    ConversationColorRow(
                        icon = com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.profile), title = strings.getString(com.example.chat.R.string.conv_ui_quoted_name_color_3768f5), subtitle = strings.getString(com.example.chat.R.string.conv_ui_sender_name_color_inside_quo_47fc7e),
                        colorValue = p.quotedNameColor,
                        // Previous writer: onColorTap = { pickColor(strings.getString(com.example.chat.R.string.conv_ui_quoted_name_color_3768f5), (quotedName).toArgb().toLong() and 0xFFFFFFFFL) { c -> preferences.editConversation { latest -> latest.copy(quotedNameColor = c) } } },
                        onColorTap = { pickColor(strings.getString(com.example.chat.R.string.conv_ui_quoted_name_color_3768f5), (quotedName).toArgb().toLong() and 0xFFFFFFFFL) { c -> model.edit { latest -> latest.copy(quotedNameColor = c) } } },
                        effectiveColor = quotedName,
                    // Previous writer: onReset = { preferences.editConversation { latest -> latest.copy(quotedNameColor = null) } },
                    onReset = { model.edit { latest -> latest.copy(quotedNameColor = null) } },
                )
                    ConversationColorRow(
                        icon = com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.note), title = strings.getString(com.example.chat.R.string.conv_ui_quoted_message_color_e7d8cf), subtitle = strings.getString(com.example.chat.R.string.conv_ui_quoted_body_text_color_f0bcf5),
                        colorValue = p.quotedMessageColor,
                        // Previous writer: onColorTap = { pickColor(strings.getString(com.example.chat.R.string.conv_ui_quoted_message_color_e7d8cf), (quotedMsg).toArgb().toLong() and 0xFFFFFFFFL) { c -> preferences.editConversation { latest -> latest.copy(quotedMessageColor = c) } } },
                        onColorTap = { pickColor(strings.getString(com.example.chat.R.string.conv_ui_quoted_message_color_e7d8cf), (quotedMsg).toArgb().toLong() and 0xFFFFFFFFL) { c -> model.edit { latest -> latest.copy(quotedMessageColor = c) } } },
                        effectiveColor = quotedMsg,
                    // Previous writer: onReset = { preferences.editConversation { latest -> latest.copy(quotedMessageColor = null) } },
                    onReset = { model.edit { latest -> latest.copy(quotedMessageColor = null) } },
                )
                    ConversationColorRow(
                        icon = com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.layers), title = strings.getString(com.example.chat.R.string.conv_ui_quoted_background_color_9dab35), subtitle = strings.getString(com.example.chat.R.string.conv_ui_background_of_quote_containe_3b5b90),
                        colorValue = p.quotedBackgroundColor,
                        // Previous writer: onColorTap = { pickColor(strings.getString(com.example.chat.R.string.conv_ui_quoted_background_color_9dab35), (quotedBg).toArgb().toLong() and 0xFFFFFFFFL) { c -> preferences.editConversation { latest -> latest.copy(quotedBackgroundColor = c) } } },
                        onColorTap = { pickColor(strings.getString(com.example.chat.R.string.conv_ui_quoted_background_color_9dab35), (quotedBg).toArgb().toLong() and 0xFFFFFFFFL) { c -> model.edit { latest -> latest.copy(quotedBackgroundColor = c) } } },
                        effectiveColor = quotedBg,
                    // Previous writer: onReset = { preferences.editConversation { latest -> latest.copy(quotedBackgroundColor = null) } },
                    onReset = { model.edit { latest -> latest.copy(quotedBackgroundColor = null) } },
                )
                }
            }
            Spacer(Modifier.height(16.dp))
            ConversationSettingsSection(title = strings.getString(com.example.chat.R.string.conv_ui_behaviour_171ca0)) {
                Column {
                    GbSwitchTile(
                        icon = com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.selectAll), title = strings.getString(com.example.chat.R.string.conv_ui_make_text_selectable_acbcb4), subtitle = strings.getString(com.example.chat.R.string.conv_ui_enable_chat_bubble_text_sele_055cc8),
                        value = p.makeTextSelectable,
                        // Previous writer: onChanged = { preferences.editConversation { latest -> latest.copy(makeTextSelectable = it) } },
                        onChanged = { model.edit { latest -> latest.copy(makeTextSelectable = it) } },
                    )
                    GbSwitchTile(
                        icon = com.example.chat.ui.icons.ChatyGlyphVectors.of(com.example.chat.ui.icons.ChatyIconId.document), title = strings.getString(com.example.chat.R.string.conv_ui_expand_long_messages_3e1481), subtitle = strings.getString(com.example.chat.R.string.conv_ui_long_messages_take_more_spac_9d3a52),
                        value = p.removeReadMore,
                        // Previous writer: onChanged = { preferences.editConversation { latest -> latest.copy(removeReadMore = it) } },
                        onChanged = { model.edit { latest -> latest.copy(removeReadMore = it) } },
                    )
                }
            }
            // Previous writer: androidx.compose.material3.TextButton(onClick = { preferences.editConversation { latest -> latest.copy(messageTextSize = 16.0) } }) {
            androidx.compose.material3.TextButton(onClick = { model.edit { latest -> latest.copy(messageTextSize = 16.0) } }) {
                Text(strings.getString(com.example.chat.R.string.conv_ui_reset_text_size_343a72))
            }
            androidx.compose.material3.TextButton(onClick = {
                // Previous writer: preferences.editConversation { latest -> latest.copy(convBackgroundColor = null, rightBubbleColor = null,
                model.edit { latest -> latest.copy(convBackgroundColor = null, rightBubbleColor = null,
                    rightChatBubbleTextColor = null, rightBubbleTimeColor = null, leftBubbleColor = null,
                    leftChatBubbleTextColor = null, leftBubbleTimeColor = null, deletedMessageIconColor = null,
                    quotedDividerColor = null, quotedNameColor = null, quotedMessageColor = null, quotedBackgroundColor = null) }
            }) { Text(strings.getString(com.example.chat.R.string.conv_ui_reset_all_bubble_colours_d8233c)) }
            },
        )
    }
    pickers.Host()
}
