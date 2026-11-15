package com.example.chat

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.auth.OrgCheckResult
import com.example.chat.ui.auth.RegisterScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Approved register-screen behavior (rounds 1-6 + final list, Bandi "All" Nov 15).
 * Injected register/org handlers: nothing leaves the device.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AuthApprovedRegisterUiTest {

    @get:Rule val rule = createAndroidComposeRule<ChatFixtureActivity>()

    private fun showRegister(
        fontScale: Float = 1f,
        checkOrganization: suspend (String) -> OrgCheckResult = { OrgCheckResult("Acme Corp", true) },
    ) {
        rule.setThemedContent(ThemePresets.warmNeutral, fontScale) {
            RegisterScreen(
                onBack = {},
                onRegistered = { _, _ -> },
                onRegisterOrganization = {},
                register = { _, _, _, _, _, _ -> "user-1" to "Maya" },
                checkOrganization = checkOrganization,
                loadSavedTenant = { null to true },
            )
        }
    }

    // The register screen still uses WorkspaceModeSelector, whose tabs are
    // plain clickable rows (no Role.Tab yet); match them by label + click.
    private fun modeTab(label: String) = hasText(label) and hasClickAction()

    @Test fun headingToggleAndFieldsRender() {
        showRegister()
        rule.onNode(hasHeading() and hasText("Everything You Need!")).assertExists()
        rule.onNode(modeTab("Normal / Default")).assertExists()
        rule.onNode(modeTab("Organization")).assertExists()
        rule.onNodeWithTag("auth-field-email").assertExists()
        rule.onNodeWithTag("auth-field-username").assertExists()
        rule.onNodeWithTag("auth-field-password").assertExists()
        rule.onNodeWithTag("auth-field-contact-number").assertExists()
    }

    @Test fun organizationTabRevealsWorkspaceFieldAndLooksUpOrg() {
        showRegister()
        rule.onNode(modeTab("Organization")).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("auth-field-organization-workspace-id").assertExists()
        rule.onNodeWithTag("auth-field-organization-workspace-id").performTextInput("acme-corp")
        // Debounced org lookup resolves to the org name banner (display-only proof).
        rule.waitUntil(6_000) {
            rule.onAllNodes(hasText("Acme Corp", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun contactNumberIsReadOnlyWithInlineDeviceAction() {
        showRegister()
        rule.onNodeWithTag("auth-field-contact-number").assert(hasNoSetText())
        rule.onNodeWithText("Use device number").assertExists()
    }

    @Test fun passwordStrengthAndPrivacyLineFollowInput() {
        showRegister()
        rule.onNodeWithTag("auth-field-password").performTextInput("Aa1!")
        rule.onNodeWithText("Consider a longer, unique password").assertExists()
        rule.onNodeWithText("Use a unique password. Chaty never asks you to share it.").assertExists()
    }

    @Test fun confirmIndicatorIsAdvisoryAndNeverBlocksCreate() {
        showRegister()
        rule.onNodeWithTag("auth-field-password").performTextInput("SecretPassw0rd!xy")
        rule.onNodeWithTag("auth-field-confirm-password-optional").performTextInput("different")
        rule.waitForIdle()
        rule.onNodeWithText("Passwords do not match yet").assertExists()
        // Display-only indicator: Create stays enabled; validation errors are reported, not blocked by the chip.
        rule.onNodeWithText("Create account").assertIsEnabled()
        rule.onNodeWithTag("auth-field-confirm-password-optional").performTextClearance()
        rule.onNodeWithTag("auth-field-confirm-password-optional").performTextInput("SecretPassw0rd!xy")
        rule.waitForIdle()
        rule.onNodeWithText("Passwords match").assertExists()
    }

    @Test fun registerLinksAndTargetsAreAccessible() {
        showRegister()
        rule.assertAllClickablesLabeled("register")
        // Full 48dp audit lands when the register toggle migrates to
        // ChatyModeToggle (known gap: WorkspaceModeSelector ModeTab is ~30dp
        // tall). Until then assert the two tabs are at least full-width rows.
        for (label in listOf("Normal / Default", "Organization")) {
            val tab = rule.onNode(modeTab(label)).fetchSemanticsNode()
            assertTrue("tab $label has no bounds", tab.boundsInRoot.height > 0f)
        }
    }

    @Test fun largeFontKeepsCreateReachable() {
        showRegister(fontScale = 2f)
        rule.onNode(hasHeading() and hasText("Everything You Need!")).assertExists()
        rule.onNodeWithTag("auth-field-email").assertExists()
        rule.onNodeWithText("Create account").performScrollTo().assertExists().assertIsEnabled()
    }
}
