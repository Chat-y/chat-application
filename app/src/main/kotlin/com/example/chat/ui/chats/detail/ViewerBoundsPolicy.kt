package com.example.chat.ui.chats.detail

import androidx.compose.ui.geometry.Rect
import kotlin.math.abs

/** MV-R5-044: ignore subpixel measurement churn, never change the measured rectangle. */
internal fun viewerBoundsChanged(previous: Rect?, next: Rect): Boolean = previous == null ||
    abs(previous.left - next.left) > 0.5f || abs(previous.top - next.top) > 0.5f ||
    abs(previous.right - next.right) > 0.5f || abs(previous.bottom - next.bottom) > 0.5f
