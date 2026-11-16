package com.example.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.chat.ui.settings.permissions.PermissionSystemSettingsButton
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** PM5-01 callback/target cases, uncompiled/unrun. Uses fake route only. */
class PermissionSystemSettingsButtonProposalTest {
    @get:Rule val compose = createComposeRule()

    @Test fun cardSettingsShortcutInvokesOnlyInjectedRouteOnce() {
        var launches = 0
        compose.setContent { MaterialTheme { PermissionSystemSettingsButton { launches++ } } }
        compose.onNodeWithText("Open system settings").performClick()
        compose.runOnIdle { assertEquals(1, launches) }
    }

    @Test fun cardShortcutRetains48dpMinimumHeight() {
        compose.setContent { MaterialTheme { PermissionSystemSettingsButton {} } }
        compose.onNodeWithText("Open system settings").assertHeightIsAtLeast(48.dp)
    }
}
