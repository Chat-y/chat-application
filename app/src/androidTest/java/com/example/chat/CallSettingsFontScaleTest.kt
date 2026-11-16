package com.example.chat

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
// Invalid package-level import retained: import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.settings.CallPresentationState
import com.example.chat.ui.settings.CallSettingsScreen
import com.example.chat.ui.theme.ChatyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * CS3-19: key call-settings controls stay present and laid out at font scales
 * 1.0 / 1.3 / 2.0. These are compose existence/layout checks at overridden density -
 * NOT screenshot tests and NOT a visual regression proof. NOT RUN by the author.
 * Uses the post-delta-05 (CS1-02) signature; apply after deltas 01-07.
 */
@RunWith(AndroidJUnit4::class)
class CallSettingsFontScaleTest {
    @get:Rule val rule = createAndroidComposeRule<ChatFixtureActivity>()

    private fun showAt(fontScale: Float) {
        rule.activity.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                ChatyTheme(ThemePresets.getSystemDefaultTheme(true)) {
                    CallSettingsScreen(
                        preferences = ServiceLocator.preferencesController,
                        call = CallPresentationState(incomingCallTemplate = "sheet", ongoingCallTemplate = "card"),
                        contacts = emptyList(),
                        onBack = {},
                        onDynamicIsland = {}, onPictureInPicture = {}, onLowData = {},
                        onOpenAudioSettings = {}, onOpenCallBundles = {},
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private fun assertKeyControlsPresent() {
        rule.onNodeWithText("Who can call me").assertExists()
        rule.onNodeWithText("Call screen style").assertExists()
        rule.onNodeWithText("Floating call bubble").assertExists()
        rule.onNodeWithText("Low data usage for calls").assertExists()
        rule.onNodeWithText("Microphone").assertExists()
    }

    @Test fun controlsPresentAtFontScale1x0() { showAt(1.0f); assertKeyControlsPresent() }
    @Test fun controlsPresentAtFontScale1x3() { showAt(1.3f); assertKeyControlsPresent() }
    @Test fun controlsPresentAtFontScale2x0() { showAt(2.0f); assertKeyControlsPresent() }
}
