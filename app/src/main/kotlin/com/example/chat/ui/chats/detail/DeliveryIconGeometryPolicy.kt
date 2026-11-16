package com.example.chat.ui.chats.detail

import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.kit.ColorPickerLogic
import kotlin.math.min

/** Same15-unit basis as the existing primary renderer, not a new icon design. */
internal fun deliveryGeometryScale(width: Float,height: Float): Float =
    if (width.isFinite() && height.isFinite()) min(width,height).coerceAtLeast(0f) / 15f else 0f

/** The outer fill remains the caller's chosen tint. Only the inset glyph selects contrast.
 * Existing monochrome extreme tokens avoid a fixed white check vanishing on a light fill. */
internal fun deliveryFilledForeground(fill: Long): Long = listOf(
    ThemePresets.monochromeLight.accentColor,ThemePresets.monochromeDark.accentColor,
).maxBy { ColorPickerLogic.contrastRatio(it,fill) }

/** Preserve existing left half and mirror it. No original GB/Dart graphic was available. */
internal fun deliveryBatmanVertices(): List<Pair<Float,Float>> = listOf(
    .50f to .96f,.35f to .62f,.15f to .80f,.05f to .23f,.31f to .45f,.30f to .05f,
    .50f to .27f,.70f to .05f,.69f to .45f,.95f to .23f,.85f to .80f,.65f to .62f,
)
