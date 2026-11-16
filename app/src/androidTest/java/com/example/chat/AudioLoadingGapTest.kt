package com.example.chat

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.settings.AudioCapabilitiesPlaceholder
import com.example.chat.ui.theme.ChatyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** NOT RUN. Static loading/failure fixtures, no network/native readiness timing proof. */
class AudioLoadingGapTest {
    @get:Rule val rule = createComposeRule()
    @Test fun loadingDoesNotOfferPrematureRouteActionsOrRetry() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        rule.setContent { ChatyTheme(ThemePresets.getSystemDefaultTheme(true)) {
            AudioCapabilitiesPlaceholder(false, onRetry = {})
        } }
        rule.onNodeWithText(context.getString(R.string.audio_capabilities_loading)).assertExists()
        rule.onNodeWithText(context.getString(R.string.audio_capabilities_retry)).assertDoesNotExist()
    }
    @Test fun failureProvidesOneExplicitRetryAction() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        var retries = 0
        rule.setContent { ChatyTheme(ThemePresets.getSystemDefaultTheme(true)) {
            AudioCapabilitiesPlaceholder(true, onRetry = { retries++ })
        } }
        rule.onNodeWithText(context.getString(R.string.audio_capabilities_retry)).performClick()
        rule.runOnIdle { assertEquals(1, retries) }
    }
}
