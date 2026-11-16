package com.example.chat

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
// Invalid package-level import retained: import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.settings.CallPresentationState
import com.example.chat.ui.settings.CallSettingsScreen
import com.example.chat.ui.theme.ChatyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * CS4-36: 320dp-compact-width source check - every section's key controls remain
 * composed (the page hosts all content in one lazy item). Existence/layout only;
 * NOT a measured reflow or screenshot proof. NOT RUN by the author.
 * Uses the post-delta-05 (CS1-02) signature; apply after deltas 01-07.
 */
@RunWith(AndroidJUnit4::class)
class CallSettingsCompactWidthTest {
    @get:Rule val rule = createAndroidComposeRule<ChatFixtureActivity>()

    @Test fun sectionsRenderAt320dpWidth() {
        rule.activity.setContent {
            ChatyTheme(ThemePresets.getSystemDefaultTheme(true)) {
                Box(Modifier.width(320.dp)) {
                    CallSettingsScreen(
                        preferences = ServiceLocator.preferencesController,
                        call = CallPresentationState(incomingCallTemplate = "sheet", ongoingCallTemplate = "card"),
                        contacts = emptyList(),
                        onBack = {},
                        onDynamicIsland = { Result.success(Unit) }, onPictureInPicture = { Result.success(Unit) }, onLowData = { Result.success(Unit) },
                        onOpenAudioSettings = {}, onOpenCallBundles = {},
                    )
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("Who can call me").assertExists()
        rule.onNodeWithText("Call screen style").assertExists()
        rule.onNodeWithText("Picture-in-picture video").assertExists()
        rule.onNodeWithText("Low data usage for calls").assertExists()
        rule.onNodeWithText("Camera").assertExists()
    }
}

// Previous call-setting preview callbacks returned Unit: onDynamicIsland = {}, onPictureInPicture = {}, onLowData = {}
