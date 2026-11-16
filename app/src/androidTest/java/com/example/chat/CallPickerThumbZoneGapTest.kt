package com.example.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.calls.CallContactPickerMode
import com.example.chat.ui.calls.CallContactPickerScreen
import com.example.chat.ui.theme.ChatyTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** NOT RUN. Device geometry fixture, not a screenshot/real split-screen result. */
class CallPickerThumbZoneGapTest {
    @get:Rule val rule = createComposeRule()
    @Test fun collapsedKeypadReopenTargetUsesBottomCenterAndExpandedCallRemainsUnique() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        rule.setContent {
            ChatyTheme(ThemePresets.getSystemDefaultTheme(true)) {
                Box(Modifier.width(320.dp).fillMaxHeight().testTag("picker-fixture")) {
                    CallContactPickerScreen(emptyList(), "self", CallContactPickerMode.dialpad,
                        onBack = {}, onStartCall = { _, _ -> }, showToast = { _, _ -> })
                }
            }
        }
        rule.onNodeWithTag("dial-call").assertExists()
        rule.onNodeWithTag("dialpad-reopen").assertDoesNotExist()
        rule.onNodeWithContentDescription(context.getString(R.string.calls_ui_44)).performClick()
        val fab = rule.onNodeWithTag("dialpad-reopen").assertHeightIsAtLeast(48.dp).fetchSemanticsNode().boundsInRoot
        val root = rule.onNodeWithTag("picker-fixture").fetchSemanticsNode().boundsInRoot
        assertTrue(kotlin.math.abs(fab.center.x - root.center.x) < 2f)
        rule.onNodeWithTag("dial-call").assertDoesNotExist()
        rule.onNodeWithTag("dialpad-reopen").performClick()
        rule.onNodeWithTag("dial-call").assertExists()
    }
}
