package com.example.chat.ui.settings

import com.example.chat.core.theme.ThemeConfig
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.kit.ColorPickerLogic
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * CS4-28: contrast gate for the exact colour pairs CallSettingsScreen renders:
 * tile titles, tile subtitles and tile icons, each on the settings surface.
 *
 * Modeled on BtDefaultPairsContrastGateTest: checks shipped static defaults in
 * ThemePresets.all at WCAG 4.5:1, asserts the theme list is non-empty first, and
 * treats a non-finite ratio as failure.
 *
 * Status: SOURCE-ONLY, NOT EXECUTED by the author (build/test execution is outside
 * the authoring scope); not wired into CI; NO claim is made that shipped themes
 * comply. A failure list means shipped defaults need product remediation - this
 * gate changes no theme. Per-user overrides and adaptive resolution are out of scope.
 */
class CallSettingsContrastGateTest {

    private val minimum = 4.5

    private val pairs: List<Pair<String, (ThemeConfig) -> Pair<Long, Long>>> = listOf(
        "tile title (primaryTextColor on surfaceColor)" to { theme -> theme.primaryTextColor to theme.surfaceColor },
        "tile subtitle (secondaryTextColor on surfaceColor)" to { theme -> theme.secondaryTextColor to theme.surfaceColor },
        "tile icon (accentColor on surfaceColor)" to { theme -> theme.accentColor to theme.surfaceColor },
    )

    @Test
    fun shippedThemesMeetCallSettingsPairMinimum() {
        assertTrue(
            "ThemePresets.all is empty; a contrast gate over zero themes would be a vacuous pass",
            ThemePresets.all.isNotEmpty(),
        )
        val failures = ThemePresets.all.flatMap { theme ->
            pairs.mapNotNull { (label, select) ->
                val (foreground, background) = select(theme)
                val ratio = ColorPickerLogic.contrastRatio(foreground, background)
                if (!java.lang.Double.isFinite(ratio) || ratio + 1e-9 < minimum) {
                    "%s (%s) %s: %.2f:1 foreground 0x%08X on background 0x%08X".format(
                        theme.id, theme.name, label, ratio, foreground, background,
                    )
                } else {
                    null
                }
            }
        }
        if (failures.isNotEmpty()) {
            fail("Call settings contrast pairs below ${minimum}:1 ->\n" + failures.joinToString("\n"))
        }
    }
}
