package com.example.chat

import androidx.compose.material.icons.rounded.Info
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.settings.AudioRadioRow
import com.example.chat.ui.theme.ChatyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Source-only renderer tests, no audio service or real route change. NOT RUN. */
class AudioSettingsRadioGapTest {
    @get:Rule val rule = createComposeRule()
    @Test fun rowHasSingleRadioSelectionPath() {
        val selected = mutableStateOf(false)
        var clicks = 0
        rule.setContent {
            ChatyTheme(ThemePresets.getSystemDefaultTheme(false)) {
                AudioRadioRow("Fixture mode", "Fixture explanation", selected.value, true) {
                    clicks++; selected.value = true
                }
            }
        }
        rule.onNodeWithText("Fixture mode").assertIsNotSelected().performClick().assertIsSelected()
        rule.runOnIdle { assertEquals(1, clicks) }
    }
    @Test fun disabledModeRetainsExplanationAndDisabledState() {
        rule.setContent {
            ChatyTheme(ThemePresets.getSystemDefaultTheme(true)) {
                AudioRadioRow("Unavailable fixture", "Not available on fixture device", false, false) {}
            }
        }
        rule.onNodeWithText("Unavailable fixture").assertIsNotEnabled().assertIsNotSelected()
        rule.onNodeWithText("Not available on fixture device").assertExists()
    }
    @Test fun largeTextRowStillHasMinimumTarget() {
        rule.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides
                androidx.compose.ui.unit.Density(density.density, 2f)) {
                ChatyTheme(ThemePresets.getSystemDefaultTheme(false)) {
                    AudioRadioRow("Large text fixture", "A longer explanation which may wrap", false, true) {}
                }
            }
        }
        rule.onNodeWithText("Large text fixture").assertHeightIsAtLeast(56.dp).assertIsNotSelected()
    }
    @Test fun statusChipKeepsLongVisibleNameAtLargeScale() {
        rule.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides
                androidx.compose.ui.unit.Density(density.density, 2f)) {
                ChatyTheme(ThemePresets.getSystemDefaultTheme(false)) {
                    com.example.chat.ui.settings.AudioStatusChip("Long fixture status", androidx.compose.material.icons.Icons.Rounded.Info,
                        com.example.chat.ui.theme.LocalAppColors.current.accent)
                }
            }
        }
        rule.onNodeWithText("Long fixture status").assertExists()
    }
    @Test fun disabledRowCannotInvokeCallbackAndHasRadioRole() {
        var calls = 0
        rule.setContent {
            ChatyTheme(ThemePresets.getSystemDefaultTheme(false)) {
                AudioRadioRow("Disabled fixture", "Fixture reason", false, false) { calls++ }
            }
        }
        // Previous implementation retained:         rule.onNodeWithText("Disabled fixture").assertIsNotEnabled().performClick()
        rule.onNodeWithText("Disabled fixture").assertIsNotEnabled().performTouchInput { click() }
        rule.onNodeWithText("Disabled fixture").assert(androidx.compose.ui.test.SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.Role, androidx.compose.ui.semantics.Role.RadioButton))
        rule.runOnIdle { assertEquals(0, calls) }
    }
    @Test fun selectedButUnavailableModeKeepsItsStateVisible() {
        rule.setContent {
            ChatyTheme(ThemePresets.getSystemDefaultTheme(false)) {
                AudioRadioRow("Selected unavailable fixture", "Unavailable reason", true, false) {}
            }
        }
        rule.onNodeWithText("Selected unavailable fixture").assertIsSelected().assertIsNotEnabled()
    }
}
