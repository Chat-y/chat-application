package com.example.chat.ui.navigation

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import com.example.chat.ui.core.theme.ChatyColors

/** One visible composition per region; no hidden controls or measuring effects. */
@Composable
internal fun AccountHomeHeaderShell(
    controller: HomeHeaderController, colors: ChatyColors, ids: List<String>, selected: Int,
    activeId: String, onSelect: (Int) -> Unit, rootActions: @Composable (String) -> Unit,
    fab: @Composable () -> Unit, content: @Composable () -> Unit,
    rootHeaderExtension: (@Composable () -> Unit)? = null,
    showTabs: Boolean = true,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val isHome = activeId == "chats" || activeId == "groups"
    val model = controller.baseModel
    val density = LocalDensity.current
    val scale = (model?.theme?.fontScale ?: 1.0).toFloat()
    // A configuration policy, not a remembered maximum or a destination-dependent sum.
    // Each control uses the same lane in normal, selected and other-root modes.
    val baseActionHeight = with(density) {
        maxOf(ReferenceLayoutMetrics.actionBarHeightDp.dp,
            ReferenceLayoutMetrics.homeActionTextBudgetSp.sp.toDp() * scale +
                ReferenceLayoutMetrics.homeActionVerticalBudgetDp.dp)
    }
    val fieldTokens = com.example.chat.ui.core.components.auth.ChatyUiTokens
    val fieldMetrics = com.example.chat.ui.kit.ChatyFieldMetrics
    val standardDensity = com.example.chat.ui.kit.ChatyFieldDensity.Standard
    val compactDensity = com.example.chat.ui.kit.ChatyFieldDensity.CompactHeader
    val labelMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
    val fieldLabel = androidx.compose.ui.res.stringResource(com.example.chat.R.string.home_ui_search_chats)
    // Reserve for both clear+close controls so query edits never resize the lane.
    // Width matches the shared field editor lane after outer inset, leading icon/gap and controls.
    val labelWidthPx = with(density) {
        (maxWidth - fieldTokens.space16 * 2 - fieldMetrics.inset * 2 -
            fieldTokens.space24 - fieldMetrics.iconGap - fieldTokens.space48 * 2)
            .coerceAtLeast(fieldTokens.space1).roundToPx()
    }.coerceAtLeast(1)
    val labelHeight = labelMeasurer.measure(fieldLabel,
        style = androidx.compose.material3.LocalTextStyle.current.copy(fontSize = fieldMetrics.supportingSize),
        constraints = Constraints(maxWidth = labelWidthPx), softWrap = true).size.height
    val standardEditorHeight = with(density) {
        fieldMetrics.textLeading.toDp() + labelHeight.toDp() +
            fieldMetrics.verticalInset(standardDensity) * 2
    }
    val searchHeight = maxOf(fieldMetrics.minimumHeight(true, standardDensity),
        standardEditorHeight, fieldTokens.space48) + fieldTokens.space2 + fieldTokens.space8
    val headerActionSearch = model?.searchPlacement == "Header action"
    val inlineSearchHeight = with(density) {
        maxOf(fieldMetrics.minimumHeight(true, compactDensity), fieldTokens.space48,
            fieldMetrics.textLeading.toDp() + fieldMetrics.verticalInset(compactDensity) * 2)
    }
    val actionHeight = HomeHeaderGeometry.actions(baseActionHeight.value,
        if (headerActionSearch && model?.searchEnabled == true) inlineSearchHeight.value else baseActionHeight.value,
        baseActionHeight.value).dp
    val reserveSearch = model?.searchEnabled == true && !headerActionSearch
    val entry = controller.entry(activeId)
    val headerReady = !isHome || entry.receiverReady
    val reduceMotion = com.example.chat.ui.kit.rememberChatyReduceMotion() ||
        com.example.chat.ui.kit.LocalReferenceLayoutPresentation.current.animationsDisabled
    val motionMs = if (reduceMotion) 0 else com.example.chat.ui.kit.ChatyMotionTokens.BASE_MS
    var actionBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val originX = entry.searchIconCenter?.let { center ->
        actionBounds?.let { ((center.x - it.left) / it.width.coerceAtLeast(1f)).coerceIn(0f, 1f) }
    } ?: 1f
    val reserveBanner = model?.requiresSubscription == true
    // Compact WhatsApp-style header: title and controls share the fixed action lane.
    // The old large identity/collapse path is deliberately inactive, not a zero-height measurement.
// Inactive large-identity path retained:     var identityExtent by remember { mutableFloatStateOf(0f) }
// Inactive large-identity path retained:     var collapsed by remember { mutableFloatStateOf(0f) }
// Inactive large-identity path retained:     val collapseEnabled by rememberUpdatedState(isHome)
// Inactive large-identity path retained:     val connection = remember(controller) {
// Inactive large-identity path retained:         object : NestedScrollConnection {
// Inactive large-identity path retained:             override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
// Inactive large-identity path retained:                 if (!collapseEnabled || available.y >= 0f) return Offset.Zero
// Inactive large-identity path retained:                 val before = collapsed
// Inactive large-identity path retained:                 collapsed = HomeHeaderGeometry.collapsed(collapsed, available.y, identityExtent)
// Inactive large-identity path retained:                 return Offset(0f, before - collapsed)
// Inactive large-identity path retained:             }
// Inactive large-identity path retained:             override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
// Inactive large-identity path retained:                 if (!collapseEnabled || available.y <= 0f) return Offset.Zero
// Inactive large-identity path retained:                 val before = collapsed
// Inactive large-identity path retained:                 collapsed = HomeHeaderGeometry.collapsed(collapsed, available.y, identityExtent)
// Inactive large-identity path retained:                 return Offset(0f, before - collapsed)
// Inactive large-identity path retained:             }
// Inactive large-identity path retained:         }
// Inactive large-identity path retained:     }
// Inactive large-identity path retained:     Box(Modifier.fillMaxSize().nestedScroll(connection)) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
// Inactive large-identity path retained:             // Identity is composed once, measured at natural height, and clipped in its own viewport.
// Inactive large-identity path retained:             Layout(content = { ShellHomeHeader(controller, activeId, HeaderRegion.Identity) },
// Inactive large-identity path retained:                 modifier = Modifier.fillMaxWidth().clipToBounds()) { measurables, bounds ->
// Inactive large-identity path retained:                 val children = measurables.map { it.measure(bounds.copy(minHeight = 0, maxHeight = Constraints.Infinity)) }
// Inactive large-identity path retained:                 val measured = children.sumOf { it.height }
// Inactive large-identity path retained:                 if (identityExtent != measured.toFloat()) identityExtent = measured.toFloat()
// Inactive large-identity path retained:                 val hidden = collapsed.coerceIn(0f, measured.toFloat()).toInt()
// Inactive large-identity path retained:                 layout(bounds.maxWidth, HomeHeaderGeometry.height(measured, collapsed, 0, 0, 0)) {
// Inactive large-identity path retained:                     var y = -hidden
// Inactive large-identity path retained:                     children.forEach { it.placeRelative(0, y); y += it.height }
// Inactive large-identity path retained:                 }
// Inactive large-identity path retained:             }
            Box(Modifier.fillMaxWidth().height(actionHeight).onGloballyPositioned { actionBounds = it.boundsInRoot() }
                .then(if (headerReady) Modifier else Modifier.clearAndSetSemantics { disabled() }
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    }), contentAlignment = Alignment.Center) {
                Box(Modifier.then(if (headerActionSearch && entry.searchOpen) Modifier.clearAndSetSemantics { } else Modifier)) {
// Inactive large-identity path retained:                 if (isHome) CompositionLocalProvider(LocalReferenceHeaderCollapse provides
// Inactive large-identity path retained:                     (if (identityExtent > 0f) (collapsed / identityExtent).coerceIn(0f, 1f) else 0f)) {
                if (isHome) CompositionLocalProvider(LocalReferenceHeaderCollapse provides 0f) {
                    ShellHomeHeader(controller, activeId, HeaderRegion.Actions)
                } else {
                    val title = NavigationCatalog.labelFor(activeId)
                    OldUiDestinationTitleHeader(colors, title) {
                        rootActions(activeId)
                    }
                }
                }
                AnimatedVisibility(visible = isHome && headerReady && headerActionSearch && entry.searchOpen,
                    enter = fadeIn(tween(motionMs)) + scaleIn(tween(motionMs), initialScale = 0f,
                        transformOrigin = TransformOrigin(originX, 0.5f)),
                    exit = fadeOut(tween(motionMs)) + scaleOut(tween(motionMs), targetScale = 0f,
                        transformOrigin = TransformOrigin(originX, 0.5f))) {
                    Box(Modifier.fillMaxSize().background(colors.background)) {
                        ShellHomeHeader(controller, activeId, HeaderRegion.Search)
                    }
                }
            }
            if (reserveSearch) {
                Box(Modifier.fillMaxWidth().height(searchHeight)) {
                    if (isHome && headerReady && (model?.searchPlacement != "Header action" || entry.searchOpen)) {
                        ShellHomeHeader(controller, activeId, HeaderRegion.Search)
                    }
                }
            }
            if (showTabs) HomeHeaderTabsBar(colors, ids, selected, ReferenceLayoutMetrics.homeTabsHeightDp.toFloat(), onSelect)
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val contentFloor = minOf(actionHeight, maxHeight / 2)
                val optionalBudget = (maxHeight - contentFloor).coerceAtLeast(0.dp)
                Column(Modifier.fillMaxSize()) {
                // Content-safe inset lives below tabs. Optional account chrome changes only
                // the content viewport, never header/tab coordinates and never covers a row.
                Column(Modifier.fillMaxWidth().heightIn(max = optionalBudget)
                    .verticalScroll(rememberScrollState())) {
                    if (reserveBanner) {
                        Box(Modifier.fillMaxWidth().height(actionHeight * ReferenceLayoutMetrics.homeBannerActionLanes)
                            .background(colors.background).verticalScroll(rememberScrollState())) {
                            ShellHomeHeader(controller, activeId, HeaderRegion.Banner)
                        }
                    }
                    if (rootHeaderExtension != null) {
                        Box(Modifier.fillMaxWidth().height(actionHeight).clipToBounds()
                            .background(colors.background).verticalScroll(rememberScrollState())) {
                            rootHeaderExtension()
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) { content() }
                }
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(ReferenceLayoutMetrics.profileHeaderHorizontalDp.dp)) { fab() }
    }
}
}
