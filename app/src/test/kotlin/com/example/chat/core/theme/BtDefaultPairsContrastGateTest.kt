package com.example.chat.core.theme

import com.example.chat.ui.kit.ColorPickerLogic
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BT-5.04 contrast gate for default bubble/glyph pairs (SOURCE-ONLY, UNRUN DELIVERABLE).
 *
 * Criterion (source PDF Chaty-Conversation-BubblesAndTicks-Rounds-1-to-5, page 9):
 * defaults such as white-on-accent must be checked over all shipped themes at
 * WCAG 4.5:1.
 *
 * Gated default pairs (explicitly scoped; NOT full-UI coverage):
 *  - outgoingTextColor on outgoingBubbleColor
 *  - incomingTextColor on incomingBubbleColor
 *  - onAccentColor on accentColor (the computed "black or white on accent" glyph
 *    default; ThemeConfig.kt derives it from a 0.5 relative-luminance threshold,
 *    which is not itself a 4.5:1 proof)
 * Settings-level adaptive colour resolution and per-user overrides are out of scope
 * for this gate; it checks shipped static defaults only.
 *
 * Guards against vacuous or meaningless passes: the gate first asserts
 * ThemePresets.all is non-empty, and a non-finite ratio (NaN/Infinity) counts as a
 * failure rather than silently passing the comparison.
 *
 * Status: prepared but NOT EXECUTED by its author (build/test execution is outside
 * the authoring scope); not wired into CI; no passing-contrast claim is made. A
 * failure list means shipped defaults need product remediation - this gate changes
 * no theme.
 */
class BtDefaultPairsContrastGateTest {

    private val minimum = 4.5

    private val pairs: List<Pair<String, (ThemeConfig) -> Pair<Long, Long>>> = listOf(
        "outgoingTextColor on outgoingBubbleColor" to { theme -> theme.outgoingTextColor to theme.outgoingBubbleColor },
        "incomingTextColor on incomingBubbleColor" to { theme -> theme.incomingTextColor to theme.incomingBubbleColor },
        "onAccentColor on accentColor" to { theme -> theme.onAccentColor to theme.accentColor },
    )

    @Test
    fun everyShippedThemeDefaultPairMeetsMinimum() {
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
        assertTrue(
            "BT-5.04 shipped-theme default-pair contrast failures (min 4.5:1):\n" +
                failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }
}
