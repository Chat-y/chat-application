package com.example.chat

import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.settings.CallContactOption
import com.example.chat.ui.settings.CallPresentationState
import com.example.chat.ui.settings.CallSettingsScreen
import com.example.chat.ui.theme.ChatyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * CS3-18: exception-dialog cancel paths on the real screen composable.
 * Uses the real local preferences controller (CallBundleMatrixProofTest precedent) and
 * restores the original privacy preferences in finally. Local preference writes only;
 * no backend, account, signalling or device-permission proof. NOT RUN by the author.
 * Uses the post-delta-05 (CS1-02) signature; apply after deltas 01-07.
 */
@RunWith(AndroidJUnit4::class)
class CallExceptionDialogCancelTest {
    @get:Rule val rule = createAndroidComposeRule<ChatFixtureActivity>()

    private fun showScreen(contacts: List<CallContactOption>) {
        rule.activity.setContent {
            ChatyTheme(ThemePresets.getSystemDefaultTheme(true)) {
                CallSettingsScreen(
                    preferences = ServiceLocator.preferencesController,
                    call = CallPresentationState(incomingCallTemplate = "sheet", ongoingCallTemplate = "card"),
                    contacts = contacts,
                    onBack = {},
                    onDynamicIsland = { Result.success(Unit) }, onPictureInPicture = { Result.success(Unit) }, onLowData = { Result.success(Unit) },
                    onOpenAudioSettings = {}, onOpenCallBundles = {},
                )
            }
        }
    }

    @Test fun cancelKeepsPersistedExceptionsUnchanged() {
        val prefs = ServiceLocator.preferencesController
        val original = prefs.privacy
        try {
            prefs.updatePrivacy(
                original.copy(whoCanCallMe = "My Contacts Except…", whoCanCallMeExceptions = listOf("c1")),
                logTitle = "CallExceptionDialogCancelTest setup",
            )
            showScreen(listOf(CallContactOption("c1", "Ada Lovelace"), CallContactOption("c2", "Grace Hopper")))
            rule.onNodeWithText("Excluded contacts").performClick()
            rule.waitForIdle()
            // Stage an extra selection, then cancel without saving.
            rule.onNodeWithText("Grace Hopper").performClick()
            rule.onNodeWithText("Cancel").performClick()
            rule.runOnIdle {
                assertEquals(listOf("c1"), prefs.privacy.whoCanCallMeExceptions)
                assertEquals("My Contacts Except…", prefs.privacy.whoCanCallMe)
            }
        } finally {
            prefs.updatePrivacy(original, logTitle = "CallExceptionDialogCancelTest restore")
        }
    }

    @Test fun cancelAfterEmptyExceptChoiceRestoresPreviousAudience() {
        val prefs = ServiceLocator.preferencesController
        val original = prefs.privacy
        try {
            prefs.updatePrivacy(
                original.copy(whoCanCallMe = "My Contacts", whoCanCallMeExceptions = emptyList()),
                logTitle = "CallExceptionDialogCancelTest setup",
            )
            showScreen(listOf(CallContactOption("c1", "Ada Lovelace")))
            rule.onNodeWithText("Who can call me").performClick()
            rule.onNodeWithText("My Contacts Except…").performClick()
            rule.onNodeWithText("Apply").performClick()
            rule.waitForIdle()
            // Cancel straight after choosing Except with an empty list restores the previous audience (CS1-04).
            rule.onNodeWithText("Cancel").performClick()
            rule.runOnIdle {
                assertEquals("My Contacts", prefs.privacy.whoCanCallMe)
            }
        } finally {
            prefs.updatePrivacy(original, logTitle = "CallExceptionDialogCancelTest restore")
        }
    }
}

// Previous call-setting preview callbacks returned Unit: onDynamicIsland = {}, onPictureInPicture = {}, onLowData = {}
