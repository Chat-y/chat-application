package com.example.chat.ui.settings.conversation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.core.theme.ThemePresets
import com.example.chat.domain.models.ConversationPreferences
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real color row and production-header preview. Picker completion simulated by callback. Unrun. */
class ConversationHeaderResetColorTest {
    @get:Rule val compose = createComposeRule()
    private fun verify(target: ConversationHeaderColorTarget, theme: ThemeConfig = ThemePresets.paper) {
        val preferences = mutableStateOf(ConversationPreferences())
        val custom = if (target == ConversationHeaderColorTarget.StatusText) theme.accentColor else theme.primaryTextColor
        compose.setContent { com.example.chat.ui.theme.ChatyTheme(theme) { Column {
            val p = preferences.value
            Box(Modifier.testTag("reset-header-preview")) { ConversationHeaderPreview(
                actionBarColor = p.actionBarColor, contactStatusBgColor = p.contactStatusBgColor,
                contactStatusTextColor = p.contactStatusTextColor) }
            val value = when (target) { ConversationHeaderColorTarget.Bar -> p.actionBarColor
                ConversationHeaderColorTarget.StatusBackground -> p.contactStatusBgColor
                ConversationHeaderColorTarget.StatusText -> p.contactStatusTextColor }
            val fallback = when (target) { ConversationHeaderColorTarget.Bar -> theme.surfaceColor
                ConversationHeaderColorTarget.StatusBackground -> theme.cardColor
                ConversationHeaderColorTarget.StatusText -> theme.secondaryTextColor }
            ConversationColorRow(title = "Test header colour", colorValue = value,
                effectiveColor = Color(value ?: fallback),
                onColorTap = { preferences.value = target.write(preferences.value, custom) },
                onReset = { preferences.value = target.write(preferences.value, null) })
        } } }
        val preview = compose.onNodeWithTag("reset-header-preview", useUnmergedTree = true)
        val initial = preview.captureToImage().toPixelMap()
        compose.onNodeWithText("Test header colour").performClick()
        compose.runOnIdle { assertNotEquals(ConversationPreferences(), preferences.value) }
        compose.onNodeWithText("Reset").performClick()
        compose.runOnIdle { assertEquals(ConversationPreferences(), preferences.value) }
        val reset = preview.captureToImage().toPixelMap()
        assertEquals(initial.width, reset.width); assertEquals(initial.height, reset.height)
        assertTrue("Reset preview differs from theme-default preview", (0 until initial.height).all { y ->
            (0 until initial.width).all { x -> initial[x, y] == reset[x, y] }
        })
    }
    @Test fun round1BarResetWritesNullAndRestoresTheme() = verify(ConversationHeaderColorTarget.Bar)
    @Test fun round2StatusBackgroundResetWritesNullAndRestoresTheme() = verify(ConversationHeaderColorTarget.StatusBackground)
    @Test fun round3StatusTextResetWritesNullAndRestoresTheme() = verify(ConversationHeaderColorTarget.StatusText)
    @Test fun round4DarkBarResetRestoresTheme() = verify(ConversationHeaderColorTarget.Bar, ThemePresets.monochromeDark)
    @Test fun round5DarkStatusResetRestoresTheme() = verify(ConversationHeaderColorTarget.StatusBackground, ThemePresets.monochromeDark)
}
