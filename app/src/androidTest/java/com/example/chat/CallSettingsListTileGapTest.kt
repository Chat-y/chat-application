package com.example.chat

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.settings.CallSettingsListTile
import com.example.chat.ui.theme.ChatyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Isolated UI anatomy, no privacy-policy or preference writes. NOT RUN. */
class CallSettingsListTileGapTest {
    @get:Rule val rule = createComposeRule()
    @Test fun entireTileHasMinimumTargetAndOneAction() {
        var calls = 0
        rule.setContent {
            ChatyTheme(ThemePresets.getSystemDefaultTheme(false)) {
                CallSettingsListTile(title = "Fixture setting", subtitle = "Fixture value", trailing = null, onTap = { calls++ })
            }
        }
        rule.onNodeWithText("Fixture setting").assertHeightIsAtLeast(56.dp).performClick()
        rule.runOnIdle { assertEquals(1, calls) }
    }
    @Test fun longSubtitleRemainsInSemanticsAtLargeFont() {
        rule.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides
                androidx.compose.ui.unit.Density(density.density, 2f)) {
                ChatyTheme(ThemePresets.getSystemDefaultTheme(true)) {
                    CallSettingsListTile(title = "Fixture", subtitle = "A long fixture value which can wrap", trailing = null, onTap = {})
                }
            }
        }
        rule.onNodeWithText("A long fixture value which can wrap").assertExists()
    }
    @Test fun exceptionDialogKeepsExplicitActionsWithoutTimeout() {
        var confirms = 0
        rule.setContent {
            ChatyTheme(ThemePresets.getSystemDefaultTheme(true)) {
                com.example.chat.ui.settings.CallExceptionDialog(fullScreen = true, onDismissRequest = {},
                    containerColor = com.example.chat.ui.theme.LocalAppColors.current.surface,
                    title = { androidx.compose.material3.Text("Fixture people") },
                    text = { androidx.compose.material3.Text("Fixture list") },
                    dismissButton = { androidx.compose.material3.TextButton(onClick = {}) { androidx.compose.material3.Text("Fixture cancel") } },
                    confirmButton = { androidx.compose.material3.TextButton(onClick = { confirms++ }) { androidx.compose.material3.Text("Fixture save") } })
            }
        }
        rule.mainClock.advanceTimeBy(60000)
        rule.onNodeWithText("Fixture save").assertExists().performClick()
        rule.onNodeWithText("Fixture cancel").assertExists()
        rule.runOnIdle { assertEquals(1, confirms) }
    }
    @Test fun existingSwitchTileIsFullWidthSingleRoleAndAnnouncesState() {
        var calls = 0
        rule.setContent {
            var checked by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            ChatyTheme(ThemePresets.getSystemDefaultTheme(true)) {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.width(320.dp)) {
                    com.example.chat.ui.settings.ChatySwitchTile(title = "Fixture switch", value = checked,
                        onChanged = { checked = it; calls++ })
                }
            }
        }
        val node = rule.onNode(androidx.compose.ui.test.SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Switch))
        node.assertWidthIsEqualTo(320.dp).assertHeightIsAtLeast(48.dp).assertIsOff().performClick().assertIsOn()
        rule.runOnIdle { assertEquals(1, calls) }
    }

    @Test fun excludedContactsCountsRenderFromTheSameProductionLabel() {
        rule.setContent {
            ChatyTheme(ThemePresets.getSystemDefaultTheme(true)) {
                androidx.compose.foundation.layout.Column {
                    listOf(0, 1, 3).forEach { count ->
                        CallSettingsListTile(title = "Excluded contacts", trailing = null, onTap = {},
                            subtitle = com.example.chat.ui.settings.excludedContactsLabel(count))
                    }
                }
            }
        }
        rule.onNodeWithText("No contacts excluded").assertExists()
        rule.onNodeWithText("1 contact excluded").assertExists()
        rule.onNodeWithText("3 contacts excluded").assertExists()
    }

}
