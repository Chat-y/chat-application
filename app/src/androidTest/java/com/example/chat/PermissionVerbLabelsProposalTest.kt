package com.example.chat

import androidx.compose.material3.MaterialTheme
// b35: invalid top-level import retained as comment: import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.chat.ui.settings.permissions.SystemPermissionsScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** PM1-57 regression cases, authored only and uncompiled/unrun. */
class PermissionVerbLabelsProposalTest {
    @get:Rule val compose = createComposeRule()

    @Test fun everyRuntimePermissionRowUsesAnAllowVerb() {
        compose.setContent { MaterialTheme {
            SystemPermissionsScreen({}, {}, { false }, { false })
        } }
        for (label in listOf("Allow notifications", "Allow photos and videos", "Allow location",
            "Allow camera", "Allow microphone", "Allow contacts")) {
            compose.onNodeWithText(label).assertExists()
        }
    }

    @Test fun showingNewLabelsDoesNotInvokeTestActions() {
        var notificationTests = 0
        var authentications = 0
        compose.setContent { MaterialTheme {
            SystemPermissionsScreen({}, { notificationTests++ }, { false }, { authentications++; false })
        } }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(0, notificationTests)
            assertEquals(0, authentications)
        }
    }
}
