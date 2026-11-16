package com.example.chat.ui.chats.detail

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** MR-R3-021: decorate the existing button focus target, without adding another target. */
internal fun Modifier.reviewHeaderFocusRing(): Modifier = composed {
    var hasFocus by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    // MR-R4-038 uses theme scrim for requested dark surround, no hard-coded black.
    val surround = MaterialTheme.colorScheme.scrim
    // Prior accent-only border retained inactive:
    // this.onFocusChanged { hasFocus = it.isFocused }
    //     .then(if (hasFocus) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)) else Modifier)
    this.onFocusChanged { hasFocus = it.isFocused }.drawWithContent {
        drawContent()
        if (hasFocus && size.minDimension > 4.dp.toPx()) {
            val inset = 2.dp.toPx()
            val bounds = Size((size.width - inset * 2).coerceAtLeast(0f), (size.height - inset * 2).coerceAtLeast(0f))
            val corner = CornerRadius(8.dp.toPx().coerceAtMost(size.minDimension / 2))
            //4dp backing minus2dp accent leaves1dp dark outline on each side.
            // Draw after content and within bounds so preview/content and parent clips retain ring.
            drawRoundRect(surround, Offset(inset, inset), bounds, corner, style = Stroke(4.dp.toPx()))
            drawRoundRect(accent, Offset(inset, inset), bounds, corner, style = Stroke(2.dp.toPx()))
        }
    }
}
