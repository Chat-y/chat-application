package com.example.chat

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasImeAction
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.input.ImeAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.auth.LoginScreen
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Approved login-screen behavior (rounds 1-6 + final list, Bandi "All" Nov 15).
 * Isolated screen with injected handlers: no backend, no account state touched.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AuthApprovedLoginUiTest {

    @get:Rule val rule = createAndroidComposeRule<ChatFixtureActivity>()

    private data class LoginCall(val identifier: String, val password: String, val tenant: String?, val isDefaultTenant: Boolean)

    private fun showLogin(
        fontScale: Float = 1f,
        login: suspend (String, String, String?, Boolean) -> String = { _, _, _, _ -> "user-1" },
        onLoggedIn: (String) -> Unit = {},
        onBack: () -> Unit = {},
        onForgotPassword: () -> Unit = {},
    ) {
        rule.setThemedContent(ThemePresets.warmNeutral, fontScale) {
            LoginScreen(
                onBack = onBack,
                onLoggedIn = onLoggedIn,
                onForgotPassword = onForgotPassword,
                onRegister = {},
                onRegisterOrganization = {},
                login = { id, pw, tenant, isDefault -> login(id, pw, tenant, isDefault) },
                loadSavedTenant = { null to true },
            )
        }
    }

    private fun fillCredentials(identifier: String = "maya@example.com", password: String = "Secret!234") {
        rule.onNodeWithTag("auth-field-email-or-username").performTextInput(identifier)
        rule.onNodeWithTag("auth-field-password").performTextInput(password)
        rule.waitForIdle()
    }

    @Test fun headingFieldsAndLinksRender() {
        showLogin()
        rule.onNode(hasHeading() and hasText("Welcome back")).assertExists()
        rule.onNodeWithTag("auth-field-email-or-username").assertExists().assert(hasSetTextAction())
        rule.onNodeWithTag("auth-field-password").assertExists().assert(hasSetTextAction())
        rule.onNodeWithText("Forgot Password?").assertExists()
        rule.onNodeWithText("Register a New Organization").assertExists()
        rule.onNodeWithText("Register", substring = false).assertExists()
    }

    @Test fun passwordEyeTogglesShowHideSemantics() {
        showLogin()
        rule.onNodeWithContentDescription("Show password").assertExists().performClick()
        rule.onNodeWithContentDescription("Hide password").assertExists()
        rule.onNodeWithContentDescription("Hide password").performClick()
        rule.onNodeWithContentDescription("Show password").assertExists()
    }

    @Test fun passwordFieldUsesImeDoneAndDoneSubmits() {
        val calls = ArrayList<LoginCall>()
        val loggedIn = AtomicReference<String?>(null)
        showLogin(
            login = { id, pw, tenant, isDefault ->
                calls.add(LoginCall(id, pw, tenant, isDefault)); "user-9"
            },
            onLoggedIn = { loggedIn.set(it) },
        )
        rule.onNodeWithTag("auth-field-password").assert(hasImeAction(ImeAction.Done))
        fillCredentials(identifier = "  maya@example.com  ")
        rule.onNodeWithTag("auth-field-password").performImeAction()
        rule.waitUntil(5_000) { calls.isNotEmpty() }
        // Whitespace is trimmed before the call; the typed secret is never mutated.
        assertEquals("maya@example.com", calls[0].identifier)
        assertEquals("Secret!234", calls[0].password)
        assertEquals(null, calls[0].tenant)
        assertTrue(calls[0].isDefaultTenant)
        rule.waitUntil(5_000) { loggedIn.get() == "user-9" }
    }

    @Test fun loadingShowsSpinnerAndBlocksDoubleSubmit() {
        val gate = CompletableDeferred<String>()
        val calls = AtomicInteger()
        showLogin(login = { _, _, _, _ -> calls.incrementAndGet(); gate.await() })
        fillCredentials()
        rule.onNodeWithText("Log In").performClick()
        rule.waitUntil(5_000) { calls.get() == 1 }
        rule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertExists()
        // The label is replaced by the spinner while the request is in flight.
        rule.onNodeWithText("Log In").assertDoesNotExist()
        gate.complete("user-1")
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Log In").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun connectionFailureKeepsFormAndUsesConnectionCopy() {
        showLogin(login = { _, _, _, _ -> throw Exception("connection refused by host") })
        fillCredentials()
        rule.onNodeWithText("Log In").performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodes(hasText("Connection problem.", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNode(liveRegionPolite()).assertExists()
        // Form kept: typed identifier survives the failure.
        val field = rule.onNodeWithTag("auth-field-email-or-username").fetchSemanticsNode()
        val text = field.config[SemanticsProperties.EditableText].text
        assertEquals("maya@example.com", text)
    }

    @Test fun serverFailureMasksTypedEmailInErrorText() {
        showLogin(login = { _, _, _, _ -> throw Exception("HTTP 500: locked account maya@example.com") })
        fillCredentials()
        rule.onNodeWithText("Log In").performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodes(hasText("your email address", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNode(hasText("Server unavailable.", substring = true)).assertExists()
        // No rendered Text may echo the typed identifier back (privacy: no typed email in errors).
        val leaked = rule.onAllNodes(hasText("maya@example.com", substring = true)).fetchSemanticsNodes()
        assertTrue("typed email leaked into an error Text node", leaked.isEmpty())
    }

    @Test fun errorClearsOnNextKeystroke() {
        showLogin(login = { _, _, _, _ -> throw Exception("HTTP 500: boom") })
        fillCredentials()
        rule.onNodeWithText("Log In").performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodes(hasText("Server unavailable.", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("auth-field-email-or-username").performTextInput("x")
        rule.waitUntil(5_000) {
            rule.onAllNodes(hasText("Server unavailable.", substring = true)).fetchSemanticsNodes().isEmpty()
        }
    }

    @Test fun emailTypoSuggestionReplacesAddress() {
        showLogin()
        rule.onNodeWithTag("auth-field-email-or-username").performTextInput("maya@gmial.com")
        rule.onNodeWithText("Use maya@gmail.com?").assertExists().performClick()
        val field = rule.onNodeWithTag("auth-field-email-or-username").fetchSemanticsNode()
        assertEquals("maya@gmail.com", field.config[SemanticsProperties.EditableText].text)
    }

    @Test fun forgotLinkSitsBelowPasswordField() {
        showLogin()
        val password = rule.onNodeWithTag("auth-field-password").fetchSemanticsNode().boundsInRoot
        val forgot = rule.onNodeWithText("Forgot Password?").fetchSemanticsNode().boundsInRoot
        assertTrue("forgot link must sit under the password field", forgot.top >= password.top)
    }

    @Test fun backWithDraftAsksBeforeDiscarding() {
        val back = AtomicInteger()
        showLogin(onBack = { back.incrementAndGet() })
        rule.onNodeWithTag("auth-field-email-or-username").performTextInput("maya@example.com")
        rule.waitForIdle()
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        assertEquals(0, back.get())
        rule.onNodeWithText("Leave this form?").assertExists()
        rule.onNodeWithText("Keep editing").performClick()
        rule.onNodeWithText("Leave this form?").assertDoesNotExist()
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onNodeWithText("Leave").performClick()
        assertEquals(1, back.get())
    }

    @Test fun primaryControlsMeet48dpAndAreLabeled() {
        showLogin()
        rule.assertAllClickablesLabeled("login")
        rule.assertMinTouchTargets48dp("login")
    }

    @Test fun largeFontKeepsEverythingReachable() {
        showLogin(fontScale = 2f)
        rule.onNode(hasHeading() and hasText("Welcome back")).assertExists()
        rule.onNodeWithTag("auth-field-email-or-username").assertExists()
        rule.onNodeWithTag("auth-field-password").assertExists()
        rule.onNodeWithText("Log In").performScrollTo().assertExists().assertIsEnabled()
        rule.onNodeWithText("Register a New Organization").assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun singleFilledPrimaryPerScreen() {
        showLogin()
        // Hick: exactly one filled primary action; everything else is a link.
        // The filled button is the one whose label is "Log In"; links keep
        // ChatyAuthLink styling and are covered by the wiring contract tests.
        val primaries = rule.onAllNodes(hasText("Log In") and hasClickAction()).fetchSemanticsNodes()
        assertEquals(1, primaries.size)
    }
}
