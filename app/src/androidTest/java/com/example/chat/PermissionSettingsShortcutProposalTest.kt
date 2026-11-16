package com.example.chat

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.example.chat.ui.settings.permissions.PermissionSwitch
import com.example.chat.ui.settings.permissions.PermStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** PM2-28 source-only cases, uncompiled/unrun. */
class PermissionSettingsShortcutProposalTest {
    @get:Rule val compose = createComposeRule()

    private fun verifyShortcut(status: PermStatus) {
        var settings = 0
        var toggles = 0
        compose.setContent { MaterialTheme {
            PermissionSwitch(Icons.Rounded.CameraAlt, MaterialTheme.colorScheme.primary, "Camera",
                "Camera access", status, { toggles++ }, { settings++ })
        } }
        val actions = compose.onNodeWithTag("permission-camera").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle {
            assertEquals(1, actions.size)
            assertEquals("Open app settings", actions.single().label)
            actions.single().action()
        }
        compose.runOnIdle { assertEquals(1, settings); assertEquals(0, toggles) }
    }

    @Test fun deniedRowShortcutDoesNotTriggerRuntimePrompt() = verifyShortcut(PermStatus.Denied)
    @Test fun grantedRowShortcutUsesSettingsCallbackOnly() = verifyShortcut(PermStatus.Granted)
}
