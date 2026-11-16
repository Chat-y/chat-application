package com.example.chat.ui.kit

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.example.chat.core.theme.ThemeConfig
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeGlyphColorTest {
    private fun theme(dark: Boolean): ThemeConfig {
        val background = if (dark) 0xFF101820L else 0xFFF5F1E8L
        val foreground = if (dark) 0xFFE9EEF2L else 0xFF18232FL
        return ThemeConfig("test", "Test", dark, 0xFF6655CCL, background, background, background,
            foreground, foreground, background, background, foreground, foreground,
            0xFF3366AAL, 0xFFCC2244L, 0xFF22AA66L)
    }
    private fun ratio(front: Color, fill: Color): Float {
        val f = front.compositeOver(fill).luminance()
        val b = fill.luminance()
        return (maxOf(f, b) + 0.05f) / (minOf(f, b) + 0.05f)
    }
    @Test fun customFillsChooseOnlyThemeCandidatesAndNeverWorsenTheOriginalGlyph() {
        for (dark in listOf(false, true)) {
            val theme = theme(dark)
            val candidates = listOf(theme.onAccentColor, theme.primaryTextColor, theme.backgroundColor,
                theme.surfaceColor, theme.secondaryTextColor).map(::Color)
            val random = java.util.Random(17L)
            repeat(200) {
                val fill = Color(random.nextInt().toLong() and 0xFFFFFFFFL)
                val opaqueFill = fill.compositeOver(Color(theme.surfaceColor))
                val chosen = themeGlyphColor(fill, theme)
                assertTrue(chosen in candidates)
                for (candidate in candidates) assertTrue(ratio(chosen, opaqueFill) >= ratio(candidate, opaqueFill))
            }
        }
    }
}
