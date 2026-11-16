package com.example.chat

import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.settings.CallPresentationState
import com.example.chat.ui.settings.CallSettingsScreen
import com.example.chat.ui.theme.ChatyTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * CS4-38: layout-order proxy for Switch Access - privacy controls are laid out above
 * appearance controls, which are above quality/permission controls, matching the
 * intended focus order. This compares semantics bounds only; it is NOT a Switch
 * Access device run. NOT RUN by the author.
 * Uses the post-delta-05 (CS1-02) signature; apply after deltas 01-07.
 */
@RunWith(AndroidJUnit4::class)
class CallSettingsTraversalOrderTest {
    @get:Rule val rule = createAndroidComposeRule<ChatFixtureActivity>()

    @Test fun privacyControlsAppearBeforeAppearanceInLayoutOrder() {
        rule.activity.setContent {
            ChatyTheme(ThemePresets.getSystemDefaultTheme(true)) {
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
        rule.waitForIdle()
        fun topOf(text: String) = rule.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top
        val whoCanCall = topOf("Who can call me")
        val appearance = topOf("Call screen style")
        val quality = topOf("Low data usage for calls")
        val permissions = topOf("Microphone")
        assertTrue("privacy before appearance", whoCanCall < appearance)
        assertTrue("appearance before quality", appearance < quality)
        assertTrue("quality before permissions", quality < permissions)
    }
}

// Previous call-setting preview callbacks returned Unit: onDynamicIsland = {}, onPictureInPicture = {}, onLowData = {}
