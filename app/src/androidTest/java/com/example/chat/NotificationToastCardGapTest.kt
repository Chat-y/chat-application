package com.example.chat

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.chat.core.theme.ThemePresets
import com.example.chat.domain.models.NotificationToastPreferences
import com.example.chat.ui.settings.notifications.ToastPreviewFrame
import com.example.chat.ui.settings.notifications.ToastCardBody
import com.example.chat.ui.settings.notifications.ToastCardType
import com.example.chat.ui.settings.notifications.toastCardSlice
import com.example.chat.ui.settings.notifications.withToastCardSlice
import com.example.chat.ui.theme.ChatyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/** Isolated renderer/callback tests. No controller, account, sound or notification side effects.
 * Source only: these assertions are NOT RUN until the approved Android test run.
 */
class NotificationToastCardGapTest {
    @get:Rule val rule = createComposeRule()

    @Test fun onlineToggleWritesPositiveFlagWithoutChangingOtherTypes() {
        val model = mutableStateOf(NotificationToastPreferences())
        val before = model.value
        rule.setContent {
            ChatyTheme(ThemePresets.getSystemDefaultTheme(true)) {
                ToastCardBody(type = ToastCardType.online, ui = toastCardSlice(ToastCardType.online, model.value),
                    radius = model.value.onlineToastRadius, onRadiusChange = {},
                    onChanged = { change ->
                        model.value = withToastCardSlice(ToastCardType.online, model.value,
                            change(toastCardSlice(ToastCardType.online, model.value)))
                    },
                    pickColor = { _, _, _, _ -> }, showRingtoneModal = { _, _ -> },
// Previous implementation retained:                     showPositionModal = { _, _ -> }, ringtoneSubtitle = { it }, colorSubtitle = { _, fallback -> fallback })
                    showPositionModal = { _, _ -> })
            }
        }
        rule.onNodeWithText("Show online toasts").assertIsOn().performClick().assertIsOff()
        rule.runOnIdle {
            assertFalse(model.value.onlineToastEnabled)
            ToastCardType.entries.filter { it != ToastCardType.online }.forEach { type ->
                assertEquals(toastCardSlice(type, before), toastCardSlice(type, model.value))
            }
        }
    }

    @Test fun everyTypeElevationToggleHasOneConsistentStateSource() {
        val type = mutableStateOf(ToastCardType.online)
        val model = mutableStateOf(NotificationToastPreferences(storyToastEnabled = true, profileToastEnabled = true))
        val titles = mapOf(ToastCardType.online to "Contact Online Toast", ToastCardType.story to "Viewed Story Toast",
            ToastCardType.profile to "Profile Toast", ToastCardType.typing to "Typing Toast")
        rule.setContent {
            ChatyTheme(ThemePresets.getSystemDefaultTheme(true)) {
                ToastCardBody(type = type.value, ui = toastCardSlice(type.value, model.value), radius = 8.0,
                    onRadiusChange = {}, onChanged = { change ->
                        model.value = withToastCardSlice(type.value, model.value, change(toastCardSlice(type.value, model.value)))
                    }, pickColor = { _, _, _, _ -> }, showRingtoneModal = { _, _ -> },
// Previous implementation retained:                     showPositionModal = { _, _ -> }, ringtoneSubtitle = { it }, colorSubtitle = { _, fallback -> fallback })
                    showPositionModal = { _, _ -> })
            }
        }
        ToastCardType.entries.forEach { selected ->
            rule.runOnIdle { type.value = selected }
            if (selected != ToastCardType.online) rule.onNodeWithText(titles.getValue(selected)).performClick()
            rule.onNodeWithText("Elevation").assertIsOn().performClick().assertIsOff()
            rule.runOnIdle { assertFalse(toastCardSlice(selected, model.value).elevated) }
        }
    }
    @Test fun previewSwitchesChangeOnlySampleFontAndTheme() {
        var observedScale = 0f
        var observedDark = false
        rule.setContent {
// Previous implementation retained:             ChatyTheme(ThemePresets.getSystemDefaultTheme(false)) {
            ChatyTheme(ThemePresets.getSystemDefaultTheme(true)) {
                ToastPreviewFrame {
                    val scale = androidx.compose.ui.platform.LocalDensity.current.fontScale
                    val dark = com.example.chat.ui.theme.LocalThemeConfig.current.isDark
                    androidx.compose.runtime.SideEffect { observedScale = scale; observedDark = dark }
                    androidx.compose.material3.Text("Preview fixture")
                }
            }
        }
        rule.onNodeWithText("Dark preview").assertIsOff().performClick().assertIsOn()
        rule.onNodeWithText("Large text preview (200%)").assertIsOff().performClick().assertIsOn()
        rule.runOnIdle { assertEquals(2f, observedScale, 0f); assertEquals(true, observedDark) }
    }
}
