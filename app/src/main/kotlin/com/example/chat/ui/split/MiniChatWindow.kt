package com.example.chat.ui.split

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.ui.icons.ChatyIcon
import com.example.chat.ui.icons.ChatyIconId
import com.example.chat.ui.theme.LocalAppColors
import kotlin.math.roundToInt

/**
 * Movable, resizable, boundary-clamped floating Mini Chat Window.
 * When minimized, collapses into a compact floating pill.
 */
@Composable
fun MiniChatWindow(
    paneItem: SplitPaneItem,
    isMinimized: Boolean,
    onToggleMinimize: () -> Unit,
    onExpandToFull: () -> Unit,
    onClose: () -> Unit,
    onFocus: () -> Unit = {},
    initialXFraction: Float = 1f,
    initialYFraction: Float = 0.15f,
    initialWidthDp: Float = 320f,
    initialHeightDp: Float = 460f,
    isFocused: Boolean = false, // SPL-49: focus is shown by border, not elevation alone
    content: @Composable () -> Unit,
) {
    val colors = LocalAppColors.current
    val density = LocalDensity.current
    val focusCurrent by rememberUpdatedState(onFocus)
    val paneKey = paneItem.stateKey()

    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        val windowWidthPx = with(density) { maxWidth.toPx() }
        val windowHeightPx = with(density) { maxHeight.toPx() }

        var showSnapGuides by remember(paneKey) { mutableStateOf(false) }
        var requestedWidth by rememberSaveable(paneKey) { mutableFloatStateOf(initialWidthDp) }
        var requestedHeight by rememberSaveable(paneKey) { mutableFloatStateOf(initialHeightDp) }
        val maxMiniWidth = (maxWidth - 24.dp).coerceAtLeast(1.dp)
        val maxMiniHeight = (maxHeight - 24.dp).coerceAtLeast(1.dp)
        val minMiniWidth = 280.dp.coerceAtMost(maxMiniWidth)
        val minMiniHeight = 320.dp.coerceAtMost(maxMiniHeight)
        val miniWidthDp = (if (requestedWidth.isFinite()) requestedWidth else initialWidthDp).dp.coerceIn(minMiniWidth, maxMiniWidth)
        val miniHeightDp = (if (requestedHeight.isFinite()) requestedHeight else initialHeightDp).dp.coerceIn(minMiniHeight, maxMiniHeight)
        val miniWidthPx = with(density) { miniWidthDp.toPx() }
        val miniHeightPx = with(density) { miniHeightDp.toPx() }
        val pillWidthDp = 180.dp.coerceAtMost(maxMiniWidth)
        val pillHeightDp = 48.dp.coerceAtMost(maxMiniHeight)

        // SPL-17/18: placement is stored as a fraction of the free room, not pixels, so a density
        // change or a smaller-then-larger canvas restores the intended relative position.
        // Prior: pixel offsets captured once from the first composition's canvas size.
        // var offsetX by rememberSaveable(paneKey) { mutableFloatStateOf((windowWidthPx - miniWidthPx).coerceAtLeast(0f) * initialXFraction.coerceIn(0f, 1f)) }
        // var offsetY by rememberSaveable(paneKey) { mutableFloatStateOf((windowHeightPx - miniHeightPx).coerceAtLeast(0f) * initialYFraction.coerceIn(0f, 1f)) }
        var offsetXFraction by rememberSaveable(paneKey) { mutableFloatStateOf(initialXFraction.coerceIn(0f, 1f)) }
        var offsetYFraction by rememberSaveable(paneKey) { mutableFloatStateOf(initialYFraction.coerceIn(0f, 1f)) }
        val roomXPx = (windowWidthPx - (if (isMinimized) with(density) { pillWidthDp.toPx() } else miniWidthPx)).coerceAtLeast(0f)
        val roomYPx = (windowHeightPx - (if (isMinimized) with(density) { pillHeightDp.toPx() } else miniHeightPx)).coerceAtLeast(0f)
        val offsetX = roomXPx * offsetXFraction
        val offsetY = roomYPx * offsetYFraction

        fun moveWindow(amount: Offset) {
            // Previous arguments retained:
            // currentX = visible.first + amount.x,
            // currentY = visible.second + amount.y,
            // These used the saved, possibly
            // offscreen origin after IME/viewport changes. Start at the visible position.
            val visible = SplitChatLayoutPolicy.clampOffset(
                currentX = offsetX,
                currentY = offsetY,
                windowWidthPx = windowWidthPx,
                windowHeightPx = windowHeightPx,
                miniWidthPx = if (isMinimized) with(density) { pillWidthDp.toPx() } else miniWidthPx,
                miniHeightPx = if (isMinimized) with(density) { pillHeightDp.toPx() } else miniHeightPx,
                marginPx = with(density) { 12.dp.toPx() },
            )
            val next = SplitChatLayoutPolicy.clampOffset(
                currentX = visible.first + amount.x,
                currentY = visible.second + amount.y,
                windowWidthPx = windowWidthPx,
                windowHeightPx = windowHeightPx,
                miniWidthPx = if (isMinimized) with(density) { pillWidthDp.toPx() } else miniWidthPx,
                miniHeightPx = if (isMinimized) with(density) { pillHeightDp.toPx() } else miniHeightPx,
                marginPx = with(density) { 12.dp.toPx() },
            )
            // SPL-17/18: write back as fractions so placement survives density and canvas changes.
            // Prior: offsetX = next.first and offsetY = next.second stored raw pixels.
            offsetXFraction = if (roomXPx > 0f) (next.first / roomXPx).coerceIn(0f, 1f) else 0f
            offsetYFraction = if (roomYPx > 0f) (next.second / roomYPx).coerceIn(0f, 1f) else 0f
        }

        fun snapWindow() {
            val margin = with(density) { 12.dp.toPx() }
            val tolerance = with(density) { 12.dp.toPx() }
            val right = (windowWidthPx - miniWidthPx - margin).coerceAtLeast(margin)
            val bottom = (windowHeightPx - miniHeightPx - margin).coerceAtLeast(margin)
            // SPL-17/18: snap in pixels, then store the fraction.
            // Prior: offsetX and offsetY were assigned the snapped pixel values directly.
            val snappedX = SplitChatLayoutPolicy.snapNear(offsetX, listOf(margin, (windowWidthPx - miniWidthPx) / 2f, right), tolerance)
            val snappedY = SplitChatLayoutPolicy.snapNear(offsetY, listOf(margin, (windowHeightPx - miniHeightPx) / 2f, bottom), tolerance)
            offsetXFraction = if (roomXPx > 0f) (snappedX / roomXPx).coerceIn(0f, 1f) else 0f
            offsetYFraction = if (roomYPx > 0f) (snappedY / roomYPx).coerceIn(0f, 1f) else 0f
        }

        // Accessibility movement is specified in dp, just like the visible resize controls.
        val accessibleMovePx = with(density) { 48.dp.toPx() }

        // Clamping inside box boundaries
        val clampedOffset = SplitChatLayoutPolicy.clampOffset(
            currentX = offsetX,
            currentY = offsetY,
            windowWidthPx = windowWidthPx,
            windowHeightPx = windowHeightPx,
            miniWidthPx = if (isMinimized) with(density) { pillWidthDp.toPx() } else miniWidthPx,
            miniHeightPx = if (isMinimized) with(density) { pillHeightDp.toPx() } else miniHeightPx,
            marginPx = with(density) { 12.dp.toPx() },
        )

        val windowShape = RoundedCornerShape(16.dp)
        if (showSnapGuides) {
            Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) { // SPL-30: guides are decorative
                val guideColor = colors.accent.copy(alpha = .55f)
                drawLine(guideColor, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), 1.dp.toPx())
                drawLine(guideColor, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), 1.dp.toPx())
            }
        }

        if (isMinimized) {
            // Minimized Floating Pill
            Row(
                Modifier
                    .offset { IntOffset(clampedOffset.first.roundToInt(), clampedOffset.second.roundToInt()) }
                    .pointerInput(paneKey, windowWidthPx, windowHeightPx, miniWidthPx, miniHeightPx, isMinimized) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            moveWindow(dragAmount)
                        }
                    }
                    .size(pillWidthDp, pillHeightDp)
                    .shadow(12.dp, CircleShape)
                    .clip(CircleShape)
                    .background(colors.surfaceElevated)
                    .border(1.dp, colors.borderSubtle, CircleShape)
                    .clickable { onToggleMinimize() }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChatyIcon(ChatyIconId.chats, color = colors.accent, semanticLabel = null)
                Text(
                    paneItem.title,
                    color = colors.foreground,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                    ChatyIcon(ChatyIconId.close, color = colors.foregroundSecondary, semanticLabel = "Close mini chat")
                }
            }
        } else {
            // Expanded Floating Mini Chat Window
            Column(
                Modifier
                    .offset { IntOffset(clampedOffset.first.roundToInt(), clampedOffset.second.roundToInt()) }
                    .size(width = miniWidthDp, height = miniHeightDp)
                    .shadow(16.dp, windowShape)
                    .clip(windowShape)
                    .background(colors.surfaceElevated)
                    // .border(1.dp, colors.borderSubtle, windowShape), // SPL-49: replaced, focused window gets an accent border
                    .border(if (isFocused) 2.dp else 1.dp, if (isFocused) colors.accent else colors.borderSubtle, windowShape),
            ) {
                // Drag Header
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(colors.surface)
                        .clickable { focusCurrent() }
                        // SPL-24: non-gesture move actions (48dp steps, then snap) for assistive tech.
                        .semantics {
                            customActions = listOf(
                                // Prior step used raw 48px instead of 48dp.
                                CustomAccessibilityAction("Move chat window left") { moveWindow(Offset(-accessibleMovePx, 0f)); snapWindow(); true },
                                CustomAccessibilityAction("Move chat window right") { moveWindow(Offset(accessibleMovePx, 0f)); snapWindow(); true },
                                CustomAccessibilityAction("Move chat window up") { moveWindow(Offset(0f, -accessibleMovePx)); snapWindow(); true },
                                CustomAccessibilityAction("Move chat window down") { moveWindow(Offset(0f, accessibleMovePx)); snapWindow(); true },
                            )
                        }
                        .pointerInput(paneKey, windowWidthPx, windowHeightPx, miniWidthPx, miniHeightPx, isMinimized) {
                            detectDragGestures(onDragStart = { focusCurrent(); showSnapGuides = true },
                                onDragEnd = { showSnapGuides = false; snapWindow() },
                                onDragCancel = { showSnapGuides = false }) { change, dragAmount ->
                                change.consume()
                                moveWindow(dragAmount)
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChatyIcon(ChatyIconId.chats, color = colors.accent, semanticLabel = null)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            paneItem.title,
                            color = colors.foreground,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (paneItem.accountNamespace != null) {
                            Text(
                                // SPL-21: shorter, readable label (last 4 characters) instead of the first 8 of the raw namespace.
                                // "Account: ${paneItem.accountNamespace.take(8)}",
                                "Account ending ${paneItem.accountNamespace.takeLast(4)}",
                                color = colors.foregroundSecondary,
                                // SPL-27: 10sp -> 11sp for the subtitle.
                                fontSize = 11.sp,
                            )
                        }
                    }
                    IconButton(onClick = onToggleMinimize, modifier = Modifier.size(48.dp)) {
                        ChatyIcon(ChatyIconId.chevronDown, color = colors.foregroundSecondary, semanticLabel = "Minimize ${paneItem.title}")
                    }
                    IconButton(onClick = onExpandToFull, modifier = Modifier.size(48.dp)) {
                        ChatyIcon(ChatyIconId.forward, color = colors.foregroundSecondary, semanticLabel = "Open full chat ${paneItem.title}")
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                        ChatyIcon(ChatyIconId.close, color = colors.foregroundSecondary, semanticLabel = "Close ${paneItem.title}")
                    }
                }

                // Reserve a side resize lane, never overlay the message content.
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    Box(Modifier.weight(1f).fillMaxHeight()) { content() }
                    Box(Modifier.width(48.dp).fillMaxHeight().background(colors.surface)
                        .semantics {
                            contentDescription = "Resize chat width"
                            customActions = listOf(
                                CustomAccessibilityAction("Make wider") { requestedWidth = (requestedWidth + 32f).coerceIn(minMiniWidth.value, maxMiniWidth.value); true },
                                CustomAccessibilityAction("Make narrower") { requestedWidth = (requestedWidth - 32f).coerceIn(minMiniWidth.value, maxMiniWidth.value); true },
                            )
                        }
                        .pointerInput(density, maxMiniWidth) {
                            detectDragGestures(onDragStart = { focusCurrent(); showSnapGuides = true },
                                onDragCancel = { showSnapGuides = false }, onDragEnd = {
                                showSnapGuides = false
                                requestedWidth = SplitChatLayoutPolicy.snapNear(requestedWidth,
                                    listOf(minMiniWidth.value, maxMiniWidth.value / 2f, maxMiniWidth.value), 12f)
                                    .coerceIn(minMiniWidth.value, maxMiniWidth.value)
                            }) { change, amount ->
                                change.consume()
                                requestedWidth = (requestedWidth.coerceIn(minMiniWidth.value, maxMiniWidth.value) +
                                    with(density) { amount.x.toDp().value }).coerceIn(minMiniWidth.value, maxMiniWidth.value)
                            }
                        }, contentAlignment = Alignment.Center) {
                        Box(Modifier.width(4.dp).height(40.dp).background(colors.foregroundSecondary, CircleShape))
                    }
                }
                // Dedicated resize lane, never covering the message Send/Mic button.
                Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.End) {
                    Box(
                        Modifier.size(48.dp)
                            .semantics {
                                contentDescription = "Resize chat window"
                                customActions = listOf(
                                    CustomAccessibilityAction("Make wider") { requestedWidth = (requestedWidth + 32f).coerceIn(minMiniWidth.value, maxMiniWidth.value); true },
                                    CustomAccessibilityAction("Make narrower") { requestedWidth = (requestedWidth - 32f).coerceIn(minMiniWidth.value, maxMiniWidth.value); true },
                                    CustomAccessibilityAction("Make taller") { requestedHeight = (requestedHeight + 32f).coerceIn(minMiniHeight.value, maxMiniHeight.value); true },
                                    CustomAccessibilityAction("Make shorter") { requestedHeight = (requestedHeight - 32f).coerceIn(minMiniHeight.value, maxMiniHeight.value); true },
                                )
                            }
                            .background(colors.surface.copy(alpha = 0.9f), RoundedCornerShape(topStart = 12.dp))
                            .pointerInput(density, maxMiniWidth, maxMiniHeight) {
                                detectDragGestures(onDragStart = { focusCurrent(); showSnapGuides = true },
                                onDragCancel = { showSnapGuides = false }, onDragEnd = {
                                showSnapGuides = false
                                    requestedWidth = SplitChatLayoutPolicy.snapNear(requestedWidth,
                                        listOf(minMiniWidth.value, maxMiniWidth.value / 2f, maxMiniWidth.value), 12f)
                                        .coerceIn(minMiniWidth.value, maxMiniWidth.value)
                                    requestedHeight = SplitChatLayoutPolicy.snapNear(requestedHeight,
                                        listOf(minMiniHeight.value, maxMiniHeight.value / 2f, maxMiniHeight.value), 12f)
                                        .coerceIn(minMiniHeight.value, maxMiniHeight.value)
                                    snapWindow()
                                }) { change, amount ->
                                    change.consume()
                                    requestedWidth = (requestedWidth.coerceIn(minMiniWidth.value, maxMiniWidth.value) + with(density) { amount.x.toDp().value })
                                        .coerceIn(minMiniWidth.value, maxMiniWidth.value)
                                    requestedHeight = (requestedHeight.coerceIn(minMiniHeight.value, maxMiniHeight.value) + with(density) { amount.y.toDp().value })
                                        .coerceIn(minMiniHeight.value, maxMiniHeight.value)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("◢", color = colors.accent, fontSize = 20.sp)
                    }
                }
            }
        }
    }
}

