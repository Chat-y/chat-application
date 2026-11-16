package com.example.chat

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SdkSuppress
import com.example.chat.core.theme.ThemePresets
import com.example.chat.domain.models.UserProfile
import com.example.chat.ui.calls.CallContactPickerMode
import com.example.chat.ui.calls.CallContactPickerScreen
import com.example.chat.ui.theme.ChatyTheme
import java.io.File
import java.time.Instant
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** NOT RUN. Capture preparation, not golden approval, real split-screen or visual verification. */
@SdkSuppress(minSdkVersion = 26)
@androidx.annotation.RequiresApi(26)
class CallPickerFontCaptureGapTest {
    @get:Rule val rule = createComposeRule()
    private val contact = UserProfile(
        id = "synthetic-long-name", displayName = "Synthetic fixture contact with a long display name",
        username = "synthetic_fixture", avatarInitials = "SF", avatarColorHex = "",
        about = "Synthetic fixture explanation which may wrap at larger text sizes",
        lastSeenAt = Instant.EPOCH,
    )

    private fun capture(scale: Float, mode: CallContactPickerMode) {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                ChatyTheme(ThemePresets.getSystemDefaultTheme(true)) {
                    Box(Modifier.requiredWidth(320.dp).requiredHeight(640.dp).testTag("picker-capture")) {
                        CallContactPickerScreen(listOf(contact), "self", mode, onBack = {},
                            onStartCall = { _, _ -> }, showToast = { _, _ -> })
                    }
                }
            }
        }
        rule.waitForIdle()
        if (mode == CallContactPickerMode.dialpad) {
            for (key in listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")) {
                rule.onNodeWithTag("dial-key-$key").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
            }
            rule.onNodeWithTag("dial-call").assertHeightIsAtLeast(48.dp)
        } else {
            rule.onNodeWithText(contact.displayName).assertIsDisplayed()
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val destination = File(context.cacheDir, "picker-${mode.name}-${scale}-320dp.png")
        val bitmap = rule.onNodeWithTag("picker-capture").captureToImage().asAndroidBitmap()
        destination.outputStream().use { output -> assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) }
        assertTrue(destination.length() > 0)
    }

    @Test fun contactsAtOnePointThreeFontScale() = capture(1.3f, CallContactPickerMode.contacts)
    @Test fun contactsAtDoubleFontScale() = capture(2f, CallContactPickerMode.contacts)
    @Test fun dialpadAtOnePointThreeFontScale() = capture(1.3f, CallContactPickerMode.dialpad)
    @Test fun dialpadAtDoubleFontScale() = capture(2f, CallContactPickerMode.dialpad)

    private fun captureLog(scale: Float) {
        val record = com.example.chat.domain.models.CallRecord(
            id = "synthetic-call", callerId = "self", participantIds = listOf("self", contact.id),
            type = com.example.chat.domain.models.CallType.voice,
            direction = com.example.chat.domain.models.CallDirection.outgoing,
            timestamp = Instant.EPOCH, durationSeconds = 61,
        )
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                ChatyTheme(ThemePresets.getSystemDefaultTheme(true)) {
                    Box(Modifier.requiredWidth(320.dp).requiredHeight(640.dp).testTag("log-capture")) {
                        com.example.chat.ui.calls.CallsScreen(
                            records = listOf(record), historyUnavailable = false, currentUserId = "self",
                            contactFor = { if (it == contact.id) contact else null }, onRefresh = {},
                            onOpenAddCall = {}, onRedial = { _, _, _, _, _ -> })
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText(contact.displayName).assertIsDisplayed()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val destination = File(context.cacheDir, "calls-log-${scale}-320dp.png")
        val bitmap = rule.onNodeWithTag("log-capture").captureToImage().asAndroidBitmap()
        destination.outputStream().use { output -> assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) }
        assertTrue(destination.length() > 0)
    }

    @Test fun logAtOnePointThreeFontScale() = captureLog(1.3f)
    @Test fun logAtDoubleFontScale() = captureLog(2f)

}
