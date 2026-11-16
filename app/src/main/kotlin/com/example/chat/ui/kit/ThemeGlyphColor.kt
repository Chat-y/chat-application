package com.example.chat.ui.kit

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.example.chat.core.theme.ThemeConfig

/** Pick a theme colour, never a fixed white glyph, for a user-customised button fill. */
internal fun themeGlyphColor(fill: Color, theme: ThemeConfig): Color {
    val opaqueFill = fill.compositeOver(Color(theme.surfaceColor))
    fun contrast(candidate: Color): Float {
        val front = candidate.compositeOver(opaqueFill).luminance()
        val back = opaqueFill.luminance()
        return (maxOf(front, back) + 0.05f) / (minOf(front, back) + 0.05f)
    }
    return listOf(theme.onAccentColor, theme.primaryTextColor, theme.backgroundColor,
        theme.surfaceColor, theme.secondaryTextColor).map(::Color).maxBy { contrast(it) }
}
