package com.example.chat.ui.chats.contact_info

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.chat.ui.navigation.ReferenceLayoutMetrics as Metrics
import com.example.chat.ui.kit.ChatyMotionTokens
import com.example.chat.ui.theme.LocalAppColors

/** Presentation only. Scroll is already owned by the details screen; no list/data state changes. */
@Composable
internal fun infoHeaderFraction(scroll: ScrollState, measuredHeaderPx: Float): State<Float> {
    return remember(scroll, measuredHeaderPx) { derivedStateOf {
        // Short content still reaches the compact state at its real maximum scroll.
        val range = minOf(measuredHeaderPx, scroll.maxValue.toFloat())
        if (range <= 0f) 0f else (scroll.value / range).coerceIn(0f, 1f)
    } }
}

/** Same existing URL, a bounded Coil request, and layer-only scroll movement. */
@Composable
internal fun InfoAvatarHero(
    identity: Any,
    url: String?,
    initials: String,
    avatarColor: Color,
    name: String,
    collapsedSize: androidx.compose.ui.unit.Dp,
    fraction: State<Float>,
    badge: (@Composable () -> Unit)? = null,
) {
    val reduced = com.example.chat.ui.kit.rememberChatyReduceMotion() ||
        com.example.chat.ui.theme.LocalThemeConfig.current.animationLevel <= 0.0
    var expanded by remember(identity) { mutableStateOf(false) }
    var dragDistance by remember(identity) { mutableFloatStateOf(0f) }
    androidx.activity.compose.BackHandler(expanded) { expanded = false }
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val wide = maxWidth.coerceAtMost(Metrics.identityExpandedMaxWidthDp.dp)
        val width by animateDpAsState(if (expanded) wide else collapsedSize,
            tween(if (reduced) 0 else ChatyMotionTokens.BASE_MS, easing = ChatyMotionTokens.enter), label = "infoAvatarWidth")
        val height by animateDpAsState(if (expanded) wide.coerceAtMost(Metrics.identityExpandedMaxHeightDp.dp) else collapsedSize,
            tween(if (reduced) 0 else ChatyMotionTokens.BASE_MS, easing = ChatyMotionTokens.enter), label = "infoAvatarHeight")
        val corner by animateDpAsState(if (expanded) 0.dp else collapsedSize / 2,
            tween(if (reduced) 0 else ChatyMotionTokens.BASE_MS, easing = ChatyMotionTokens.enter), label = "infoAvatarCorner")
        // Decode for the largest displayed state, not every intermediate animation frame.
        val target = with(density) { wide.roundToPx().coerceAtLeast(1) }
        // Drag threshold follows the real displayed hero height, not a fixed phone-only value.
        val dismissThreshold = with(density) { height.toPx().coerceAtLeast(1f) * Metrics.identityDismissFraction }
        val latestDismissThreshold by rememberUpdatedState(dismissThreshold)
        val fallback: @Composable () -> Unit = {
            Box(Modifier.fillMaxSize().background(avatarColor), contentAlignment = Alignment.Center) {
                com.example.chat.ui.chats.home.HomeAvatar(initials.ifBlank { "?" }, avatarColor,
                    minOf(width, height).value, "circle")
            }
        }
        Box(
            Modifier.size(width, height)
                .graphicsLayer {
                    val progress = fraction.value
                    scaleX = 1f
                    scaleY = 1f
                    translationY = 0f
                    if (!reduced && expanded) {
                        translationY = dragDistance * 0.35f
                        scaleX = 1f - (dragDistance / (dismissThreshold * 5)).coerceIn(0f, 0.12f)
                        scaleY = scaleX
                    }
                    if (!reduced && !expanded) {
                        scaleX = 1f - progress * 0.2f
                        scaleY = scaleX
                        translationY = -progress * Metrics.identityScrollLiftDp.dp.toPx()
                    }
                }
                .then(if (expanded) Modifier.pointerInput(identity) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (dragDistance >= latestDismissThreshold) expanded = false
                            dragDistance = 0f
                        },
                        onDragCancel = { dragDistance = 0f },
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            dragDistance = (dragDistance + amount).coerceAtLeast(0f)
                        },
                    )
                } else Modifier)
                .semantics { stateDescription = if (expanded) "Expanded photo" else "Profile photo" }
                .clickable(role = Role.Button, onClickLabel = if (expanded) "Collapse photo" else "Expand photo") { expanded = !expanded },
        ) {
            // Clip only the photograph; the status badge must remain outside the circular mask.
            Box(Modifier.matchParentSize().clip(RoundedCornerShape(corner))) {
                com.example.chat.ui.theme.ChatyAsyncImage(
                    url, "$name photo", Modifier.fillMaxSize(), placeholder = fallback, error = fallback,
                    requestSize = coil.size.Size(target, target),
                )
            }
            if (badge != null) Box(Modifier.align(Alignment.TopEnd)) { badge() }
        }
    }
}

/** Toolbar identity fades in only once the main identity scrolls away. */
@Composable
internal fun InfoToolbarIdentity(name: String, subtitle: String?, url: String?, initials: String,
    color: Color, fraction: State<Float>, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    val reduced = com.example.chat.ui.kit.rememberChatyReduceMotion() ||
        com.example.chat.ui.theme.LocalThemeConfig.current.animationLevel <= 0.0
    // A threshold keeps invisible duplicate text out of TalkBack traversal.
    val visible by remember(fraction) { derivedStateOf { fraction.value >= 1f } }
    if (!visible) { Spacer(modifier); return }
    // The original name owns accessibility identity. This repeated compact caption is decorative.
    Row(modifier.clearAndSetSemantics { }.graphicsLayer {
        val progress = fraction.value.coerceIn(0f, 1f)
        alpha = if (reduced) 1f else progress
        translationY = if (reduced) 0f else (1f - progress) * Metrics.identityToolbarLiftDp.dp.toPx()
    },
        verticalAlignment = Alignment.CenterVertically) {
        val pixels = with(LocalDensity.current) { Metrics.identityToolbarAvatarDp.dp.roundToPx().coerceAtLeast(1) }
        val fallback: @Composable () -> Unit = {
            com.example.chat.ui.chats.home.HomeAvatar(initials.ifBlank { "?" }, color,
                Metrics.identityToolbarAvatarDp.toFloat(), "circle")
        }
        Box(Modifier.size(Metrics.identityToolbarAvatarDp.dp).clip(androidx.compose.foundation.shape.CircleShape)) {
            com.example.chat.ui.theme.ChatyAsyncImage(url, null, Modifier.fillMaxSize(),
                placeholder = fallback, error = fallback, requestSize = coil.size.Size(pixels, pixels))
        }
        Spacer(Modifier.width(Metrics.identityToolbarGapDp.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = colors.foreground, style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) Text(subtitle, color = colors.foregroundSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
