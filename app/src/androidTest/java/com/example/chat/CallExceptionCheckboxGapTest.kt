package com.example.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.settings.CallContactOption
import com.example.chat.ui.settings.CallExceptionContactRow
import com.example.chat.ui.theme.ChatyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** NOT RUN. Checks shape/role/state without inferring selection from tint. */
class CallExceptionCheckboxGapTest {
    @get:Rule val rule = createComposeRule()
    @Test fun rowHasOneCheckboxTargetAndChangesCheckedSemantics() {
        var calls = 0
        rule.setContent {
            var checked by remember { mutableStateOf(false) }
            ChatyTheme(ThemePresets.getSystemDefaultTheme(true)) {
                CallExceptionContactRow(CallContactOption("stable-id", "Fixture person"), checked) {
                    checked = it
                    calls++
                }
            }
        }
        val checkbox = rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
        checkbox.assertHeightIsAtLeast(48.dp).assertIsOff().performClick().assertIsOn()
        rule.onNodeWithText("Fixture person").assertExists()
        rule.runOnIdle { assertEquals(1, calls) }
        checkbox.performClick().assertIsOff()
        rule.runOnIdle { assertEquals(2, calls) }
    }
}
