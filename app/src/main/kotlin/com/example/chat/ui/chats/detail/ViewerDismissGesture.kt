package com.example.chat.ui.chats.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.abs

internal fun viewerDismissClaimsVertical(dxDp: Float, dyDp: Float): Boolean =
    dxDp.isFinite() && dyDp.isFinite() && dyDp >= 24f && dyDp > abs(dxDp)

internal fun viewerDismissThreshold(distanceDp: Float, velocityDpPerSecond: Float): Boolean =
    distanceDp.isFinite() && velocityDpPerSecond.isFinite() && distanceDp > 0f &&
        (distanceDp >= 120f || velocityDpPerSecond >= 300f)

/** MV-R1-050/MV-R3-026 passive media-region only, never wraps seek/toolbar controls.
 * Original helper name retained after extension to video/fallback/audio art.
 * Inspect Initial pass before the image transform handler. A second pointer or zoomed image
 * yields the whole sequence; do not re-enter dismiss when a pinch lifts its second finger.
 */
internal fun Modifier.viewerImageDismissGesture(
    identity: Any,
    reduceMotion: Boolean,
    canDismiss: () -> Boolean,
    onDismiss: () -> Unit,
): Modifier = composed {
    val currentCanDismiss by rememberUpdatedState(canDismiss)
    val currentDismiss by rememberUpdatedState(onDismiss)
    var distance by remember(identity) { mutableFloatStateOf(0f) }
    var dragging by remember(identity) { mutableStateOf(false) }
    var dismissed by remember(identity) { mutableStateOf(false) }
    val shownDistance by animateFloatAsState(
        targetValue = if (dragging || dismissed) distance else 0f,
        animationSpec = if (dragging || dismissed || reduceMotion) snap() else spring(),
        label = "Viewer dismiss spring return",
    )
    this.pointerInput(identity) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (down.isConsumed || dismissed || !currentCanDismiss()) return@awaitEachGesture
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            var claimed = false
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.count { it.pressed } > 1 || !currentCanDismiss()) break
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    tracker.addPosition(change.uptimeMillis, change.position)
                    val delta = change.position - down.position
                    val dx = delta.x / density
                    val dy = delta.y / density
                    if (!claimed) {
                        //149 prior: if (change.isConsumed) break
                        // The1x child transform detector can consume platform slop before24dp
                        // even though its pan is clamped. Do not let that cancel vertical intent.
                        // Initial down must still be unconsumed; canDismiss rejects actual zoom,
                        // any second pointer yields, and these passive regions exclude controls.
                        // Horizontal intent yields; vertical only starts after the proposal's24dp.
                        if (abs(dx) >= 24f && abs(dx) >= abs(dy)) break
                        if (viewerDismissClaimsVertical(dx, dy)) claimed = true
                    }
                    if (claimed) {
                        dragging = true
                        distance = delta.y.coerceIn(0f, size.height.toFloat())
                        change.consume()
                    }
                    if (!change.pressed) {
                        if (claimed && viewerDismissThreshold(distance / density, tracker.calculateVelocity().y / density)) {
                            dismissed = true
                            currentDismiss()
                        }
                        break
                    }
                }
            } finally {
                dragging = false
                // Retain distance target until navigation only after an actual accepted dismiss.
                // Otherwise animate from current drawn distance back to zero, including cancellation.
                if (!dismissed) distance = 0f
            }
        }
    }.graphicsLayer {
        if (!reduceMotion) {
            translationY = shownDistance
            alpha = (1f - .6f * (shownDistance / size.height.coerceAtLeast(1f))).coerceIn(.4f, 1f)
        }
    }
}
