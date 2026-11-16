package com.example.chat

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.chat.core.theme.ThemePresets
import com.example.chat.data.services.AudioRouteType
import com.example.chat.ui.calls.CallAudioRouteIndicator
import com.example.chat.ui.theme.ChatyTheme
import org.junit.Rule
import org.junit.Test

/** NOT RUN. Presentation state only, not proof of a real AudioManager route change. */
class CallAudioRouteIndicatorGapTest {
    @get:Rule val rule = createComposeRule()
    @Test fun currentOutputLabelChangesWithPublishedSessionRoute() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val route = mutableStateOf(AudioRouteType.bluetooth)
        rule.setContent {
            ChatyTheme(ThemePresets.getSystemDefaultTheme(true)) { CallAudioRouteIndicator(route.value) }
        }
        val bluetooth = context.getString(R.string.call_audio_bluetooth)
        val receiver = context.getString(R.string.call_audio_earpiece)
        rule.onNodeWithContentDescription(context.getString(R.string.call_audio_output, bluetooth)).assertExists()
        rule.runOnIdle { route.value = AudioRouteType.earpiece }
        rule.onNodeWithContentDescription(context.getString(R.string.call_audio_output, receiver)).assertExists()
        rule.onNodeWithContentDescription(context.getString(R.string.call_audio_output, bluetooth)).assertDoesNotExist()
    }
}
