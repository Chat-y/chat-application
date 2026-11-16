package com.example.chat.ui.media

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/** MV-R3-038: anchor to the measured Material thumb, not a guessed fractional track inset. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SeekTimeBubbleThumb(
    interactionSource: MutableInteractionSource,
    colors: SliderColors,
    enabled: Boolean,
    targetTime: String,
) {
    val dragging by interactionSource.collectIsDraggedAsState()
    val gap = with(LocalDensity.current) { 24.dp.roundToPx() }
    val position = remember(gap) { object : PopupPositionProvider {
        override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
            // The platform reports the actual physical thumb bounds for LTR and RTL alike.
            val (x, y) = seekBubbleWindowOffset(anchorBounds.left, anchorBounds.top, anchorBounds.width,
                windowSize.width, windowSize.height, popupContentSize.width, popupContentSize.height, gap)
            return IntOffset(x, y)
        }
    } }
    Box {
        SliderDefaults.Thumb(interactionSource = interactionSource, colors = colors, enabled = enabled)
        if (enabled && dragging) Popup(
            popupPositionProvider = position,
            properties = PopupProperties(focusable = false, dismissOnBackPress = false, dismissOnClickOutside = false, clippingEnabled = true),
        ) {
            // Slider retains progress semantics; do not announce every visual drag-frame twice.
            Surface(modifier = Modifier.clearAndSetSemantics {}, shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface) {
                Text(targetTime, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"))
            }
        }
    }
}

/** Place24dp above thumb when space allows, otherwise keep feedback inside the window. */
internal fun seekBubbleWindowOffset(left: Int, top: Int, thumbWidth: Int, windowWidth: Int, windowHeight: Int,
    bubbleWidth: Int, bubbleHeight: Int, gap: Int): Pair<Int, Int> {
    val x = left.toLong() + thumbWidth.coerceAtLeast(0).toLong() / 2 - bubbleWidth.coerceAtLeast(0).toLong() / 2
    val y = top.toLong() - gap.coerceAtLeast(0) - bubbleHeight.coerceAtLeast(0)
    return x.coerceIn(0L, (windowWidth.toLong() - bubbleWidth).coerceAtLeast(0L)).toInt() to
        y.coerceIn(0L, (windowHeight.toLong() - bubbleHeight).coerceAtLeast(0L)).toInt()
}
