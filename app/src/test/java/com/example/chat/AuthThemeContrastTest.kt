package com.example.chat

import com.example.chat.core.theme.ThemeConfig
import com.example.chat.core.theme.ThemePresets
import org.junit.Assert.assertTrue
import org.junit.Test

/** Auth-specific contrast fallback must pass AA without changing global theme choices. */
class AuthThemeContrastTest {

    private fun luminance(argb: Long): Double {
        fun channel(shift: Int): Double {
            val v = ((argb shr shift) and 0xFF) / 255.0
            return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private fun ratio(fg: Long, bg: Long): Double {
        val a = luminance(fg)
        val b = luminance(bg)
        val hi = maxOf(a, b)
        val lo = minOf(a, b)
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun forEachPreset(block: (ThemeConfig) -> Unit) {
        assertTrue(ThemePresets.all.isNotEmpty())
        ThemePresets.all.forEach(block)
    }

    @Test fun bodyAndSecondaryTextMeetAAInEveryTheme() {
        forEachPreset { preset ->
            assertTrue(
                "${preset.id} primary/background ${"%.2f".format(ratio(preset.primaryTextColor, preset.backgroundColor))}",
                ratio(preset.primaryTextColor, preset.backgroundColor) >= 4.5,
            )
            assertTrue(
                "${preset.id} secondary/background ${"%.2f".format(ratio(preset.secondaryTextColor, preset.backgroundColor))}",
                ratio(preset.secondaryTextColor, preset.backgroundColor) >= 4.5,
            )
        }
    }

    @Test fun systemDefaultThemesMeetAAForLinksAndErrors() {
        for (preset in listOf(ThemePresets.warmNeutral, ThemePresets.warmNeutralDark)) {
            assertTrue("${preset.id} link", ratio(preset.linkColor, preset.backgroundColor) >= 4.5)
            assertTrue("${preset.id} danger", ratio(preset.dangerColor, preset.backgroundColor) >= 4.5)
        }
    }

    @Test fun authAdjustedAccentTextMeetsAAEveryTheme() {
        forEachPreset { preset ->
            val adjusted = com.example.chat.ui.auth.AuthPresentationPolicy.readableText(preset.onAccentColor.toInt(),preset.accentColor.toInt())
            assertTrue(preset.id,com.example.chat.ui.auth.AuthPresentationPolicy.contrast(adjusted,preset.accentColor.toInt()) >= 4.5)
        }
    }
}
