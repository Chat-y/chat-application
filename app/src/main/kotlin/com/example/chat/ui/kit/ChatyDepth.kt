package com.example.chat.ui.kit

import androidx.compose.ui.composed
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.chat.ui.theme.LocalAppColors

/** Minimum shadow strength so depth stays visible in light themes (token colour, never a hard-coded one). */
private const val DepthMinAlpha = 0.18f

/**
 * Shared theme-aware depth for floating surfaces (modals, FABs, bars, popups). Uses the theme's shadow token for both
 * ambient and spot colours instead of the default black; the alpha is raised to a floor so light themes still show depth.
 * Same signature and clip default as Modifier.shadow, so call sites swap 1:1.
 */
fun Modifier.chatyDepth(elevation: Dp, shape: Shape, clip: Boolean = elevation > 0.dp): Modifier = composed {
    val token = LocalAppColors.current.shadow
    val tint = token.copy(alpha = token.alpha.coerceAtLeast(DepthMinAlpha))
    shadow(elevation, shape, clip, ambientColor = tint, spotColor = tint)
}
