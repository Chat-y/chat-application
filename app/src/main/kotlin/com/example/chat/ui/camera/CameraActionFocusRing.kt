package com.example.chat.ui.camera

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** CAM-R4-034: observe the existing clickable focus target, never add a duplicate focus node.
 * Put before clickable/Button's focus target. Draw inside bounds so a parent clip retains it.
 */
internal fun Modifier.cameraActionFocusRing(radius: Dp = 12.dp): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    val surround = MaterialTheme.colorScheme.scrim
    this.onFocusChanged { focused = it.isFocused }.drawWithContent {
        drawContent()
        if (focused && size.minDimension > 4.dp.toPx()) {
            val inset = 2.dp.toPx()
            val bounds = Size((size.width - 2 * inset).coerceAtLeast(0f), (size.height - 2 * inset).coerceAtLeast(0f))
            val corner = CornerRadius(radius.toPx().coerceAtMost(size.minDimension / 2))
            drawRoundRect(surround, Offset(inset, inset), bounds, corner, style = Stroke(4.dp.toPx()))
            drawRoundRect(accent, Offset(inset, inset), bounds, corner, style = Stroke(2.dp.toPx()))
        }
    }
}
