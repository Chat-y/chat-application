package com.example.chat

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.auth.OtpPurpose
import com.example.chat.ui.auth.OtpVerificationScreen
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Approved 6-box email OTP behavior (rounds 1-3, final 13/14/16/50/51,
 * missing-list 30 fake-clock coverage via the injected countdown seed).
 * Never uses the debug-only 000000 gate: verify lambdas here are the real seam.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AuthApprovedOtpUiTest {

    @get:Rule val rule = createAndroidComposeRule<ChatFixtureActivity>()

    private val clipboard: ClipboardManager
        get() = InstrumentationRegistry.getInstrumentation().targetContext
            .getSystemService(ClipboardManager::class.java)

    private fun showOtp(
        email: String = "bandi@example.com",
        purpose: OtpPurpose = OtpPurpose.VerifyEmail,
        initialResendSeconds: Int = 2,
        verify: suspend (String) -> Unit = {},
        resend: suspend () -> String = { "Code sent" },
        onVerified: (String) -> Unit = {},
        onBack: () -> Unit = {},
    ) {
        rule.setThemedContent(ThemePresets.warmNeutral) {
            OtpVerificationScreen(
                email = email,
                purpose = purpose,
                onBack = onBack,
                verify = verify,
                resend = resend,
                onVerified = onVerified,
                initialResendSeconds = initialResendSeconds,
            )
        }
    }

    private fun codeField() = rule.onNodeWithContentDescription("Email verification code, 6 digits")

    @Test fun sixCellFieldExposesOneAccessibleEntry() {
        showOtp()
        codeField().assertExists().assert(hasSetTextAction())
        rule.onNode(hasHeading() and hasText("Verify Your Email")).assertExists()
        // Destination is masked on screen: first local character + domain only.
        rule.onAllNodes(hasText("b****@example.com", substring = true))
            .fetchSemanticsNodes().also { assertTrue(it.isNotEmpty()) }
        rule.assertAllClickablesLabeled("otp")
        rule.assertMinTouchTargets48dp("otp")
    }

    @Test fun lastDigitAutoSubmitsSanitizedCode() {
        val submitted = AtomicReference<String?>(null)
        val done = AtomicReference<String?>(null)
        showOtp(
            verify = { submitted.set(it) },
            onVerified = { done.set(it) },
        )
        codeField().performTextInput("123456")
        rule.waitUntil(5_000) { submitted.get() == "123456" }
        rule.waitUntil(5_000) { done.get() == "123456" }
    }

    @Test fun pasteStripsSpacesAndHyphens() {
        val submitted = AtomicReference<String?>(null)
        showOtp(verify = { submitted.set(it) })
        codeField().performTextInput("123-45 6")
        rule.waitUntil(5_000) { submitted.get() == "123456" }
    }

    @Test fun successfulVerifyClearsMatchingClipboardCode() {
        val done = AtomicReference<String?>(null)
        showOtp(onVerified = { done.set(it) })
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            clipboard.setPrimaryClip(ClipData.newPlainText("otp", "123456"))
        }
        codeField().performTextInput("123456")
        rule.waitUntil(5_000) { done.get() == "123456" }
        var clip: CharSequence? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            clip = clipboard.primaryClip?.getItemAt(0)?.text
        }
        assertEquals("", clip?.toString() ?: "")
    }

    @Test fun resendSitsBehindCountdownThenWorks() {
        val resendCalls = AtomicInteger()
        showOtp(initialResendSeconds = 2, resend = { resendCalls.incrementAndGet(); "Code sent" })
        rule.onNodeWithText("Resend code", substring = true).assertIsNotEnabled()
        // Countdown is monotonic (SystemClock) so a background jump cannot extend it.
        rule.waitUntil(8_000) {
            rule.onAllNodes(hasText("Resend code", substring = true))
                .fetchSemanticsNodes()
                .any { node ->
                    !node.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) &&
                        (node.config[androidx.compose.ui.semantics.SemanticsProperties.Text]
                            .joinToString(" ") { it.text } == "Resend code")
                }
        }
        rule.onNodeWithText("Resend code").assertIsEnabled().performClick()
        rule.waitUntil(5_000) { resendCalls.get() == 1 }
    }

    @Test fun wrongCodeShowsServerTextOnceInLiveRegion() {
        showOtp(verify = { throw Exception("Incorrect code. 4 attempts remaining.") })
        codeField().performTextInput("123456")
        rule.waitUntil(5_000) {
            rule.onAllNodes(hasText("Incorrect code. 4 attempts remaining.", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        val matches = rule.onAllNodes(
            hasText("Incorrect code. 4 attempts remaining.", substring = true),
        ).fetchSemanticsNodes()
        assertEquals("error must be announced once, not duplicated", 1, matches.size)
        rule.onNode(liveRegionPolite()).assertExists()
    }

    @Test fun backKeepsEmailAndAsksBeforeDiscardingCode() {
        val back = AtomicInteger()
        showOtp(onBack = { back.incrementAndGet() })
        codeField().performTextInput("12")
        rule.waitForIdle()
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        assertEquals(0, back.get())
        rule.onNodeWithText("Leave this form?").assertExists()
        rule.onNodeWithText("Leave").performClick()
        assertEquals(1, back.get())
    }
}
