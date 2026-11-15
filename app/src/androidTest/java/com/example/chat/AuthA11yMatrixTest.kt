package com.example.chat

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.auth.CreateNewPasswordScreen
import com.example.chat.ui.auth.ForgotPasswordScreen
import com.example.chat.ui.auth.LoginScreen
import com.example.chat.ui.auth.OrgCheckResult
import com.example.chat.ui.auth.OtpPurpose
import com.example.chat.ui.auth.OtpVerificationScreen
import com.example.chat.ui.auth.RegisterScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Final-list accessibility matrix (items 1/2/29/30/31/33/34/35) across the
 * five auth screens, the system light/dark themes, and 100%/200% font.
 * Register keeps its legacy WorkspaceModeSelector until it migrates to
 * ChatyModeToggle, so the strict 48dp scan applies to the migrated screens;
 * register is covered by label/heading/role checks below (known gap, see
 * AuthApprovedRegisterUiTest.registerLinksAndTargetsAreAccessible).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AuthA11yMatrixTest {

    @get:Rule val rule = createAndroidComposeRule<ChatFixtureActivity>()

    private data class ScreenCase(val name: String, val strictTargets: Boolean, val show: () -> Unit)

    private fun cases(): List<ScreenCase> {
        val login = ScreenCase("login", true) {
            show {
                LoginScreen(
                    onBack = {}, onLoggedIn = {}, onForgotPassword = {},
                    onRegister = {}, onRegisterOrganization = {},
                    login = { _, _, _, _ -> "u" }, loadSavedTenant = { null to true },
                )
            }
        }
        val register = ScreenCase("register", false) {
            show {
                RegisterScreen(
                    onBack = {}, onRegistered = { _, _ -> }, onRegisterOrganization = {},
                    register = { _, _, _, _, _, _ -> "u" to "n" },
                    checkOrganization = { OrgCheckResult("Acme", true) },
                    loadSavedTenant = { null to true },
                )
            }
        }
        val otp = ScreenCase("otp", true) {
            show {
                OtpVerificationScreen(
                    email = "bandi@example.com", purpose = OtpPurpose.VerifyEmail,
                    onBack = {}, verify = {}, resend = { "sent" }, onVerified = {},
                    initialResendSeconds = 1,
                )
            }
        }
        val forgot = ScreenCase("forgot", true) {
            show { ForgotPasswordScreen(onBack = {}, sendRecovery = {}) }
        }
        val newPassword = ScreenCase("newPassword", true) {
            show {
                CreateNewPasswordScreen(
                    email = "bandi@example.com", onBack = {},
                    submitNewPassword = {}, showSuccess = {}, onPasswordUpdated = {},
                )
            }
        }
        return listOf(login, register, otp, forgot, newPassword)
    }

    private var pendingTheme: ThemeConfig = ThemePresets.warmNeutral
    private var pendingFontScale: Float = 1f

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setThemedContent(pendingTheme, pendingFontScale) { content() }
    }

    @Test fun everyScreenHasHeadingLabelsAndTargetsAcrossThemesAndFonts() {
        for (theme in listOf(ThemePresets.warmNeutral, ThemePresets.warmNeutralDark)) {
            for (fontScale in listOf(1f, 2f)) {
                for (case in cases()) {
                    pendingTheme = theme
                    pendingFontScale = fontScale
                    case.show()
                    val context = "${case.name}/${theme.id}/font=$fontScale"
                    // Item 2: a semantic heading exists on every auth screen.
                    val headings = rule.onAllNodes(hasHeading()).fetchSemanticsNodes()
                    assertTrue("$context: no heading semantics", headings.isNotEmpty())
                    // Item 35: every clickable control exposes a speakable label.
                    rule.assertAllClickablesLabeled(context)
                    // Item 29: 48dp minimum targets on migrated screens.
                    if (case.strictTargets) rule.assertMinTouchTargets48dp(context)
                }
            }
        }
    }

    @Test fun errorIsLiveRegionAndNotColorOnlyAcrossThemes() {
        for (theme in listOf(ThemePresets.warmNeutral, ThemePresets.warmNeutralDark)) {
            pendingTheme = theme
            pendingFontScale = 1f
            show {
                LoginScreen(
                    onBack = {}, onLoggedIn = {}, onForgotPassword = {},
                    onRegister = {}, onRegisterOrganization = {},
                    login = { _, _, _, _ -> throw Exception("unable to connect") },
                    loadSavedTenant = { null to true },
                )
            }
            rule.onNodeWithTag("auth-field-email-or-username").performTextInput("maya@example.com")
            rule.onNodeWithTag("auth-field-password").performTextInput("Secret!234")
            rule.onNodeWithText("Log In").performClick()
            rule.waitUntil(5_000) {
                rule.onAllNodes(hasText("Connection problem.", substring = true))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            // Items 32/33: the error is readable text inside a polite live
            // region (the region wraps the message row; text sits on the child).
            rule.onNode(liveRegionPolite()).assertExists()
            rule.onAllNodes(hasText("Connection problem.", substring = true))
                .fetchSemanticsNodes().also { assertTrue(it.size == 1) }
        }
    }

    @Test fun twoHundredPercentFontKeepsLoginCtaReachable() {
        pendingTheme = ThemePresets.warmNeutral
        pendingFontScale = 2f
        show {
            LoginScreen(
                onBack = {}, onLoggedIn = {}, onForgotPassword = {},
                onRegister = {}, onRegisterOrganization = {},
                login = { _, _, _, _ -> "u" }, loadSavedTenant = { null to true },
            )
        }
        // Item 34: 200% font matrix — the primary action still scrolls into reach.
        rule.onNodeWithText("Log In").performScrollTo().assertExists().assertIsEnabled()
    }
}
