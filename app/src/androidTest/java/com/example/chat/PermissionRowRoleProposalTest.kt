package com.example.chat

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.example.chat.ui.settings.permissions.PermissionSwitch
import com.example.chat.ui.settings.permissions.PermStatus
import org.junit.Rule
import org.junit.Test

/** PM1-60 mode-specific roles, uncompiled/unrun. No Android permission or settings action. */
class PermissionRowRoleProposalTest {
    @get:Rule val compose = createComposeRule()

    @Test fun grantedSettingsRouteIsButtonNotSwitch() {
        compose.setContent { MaterialTheme {
            PermissionSwitch(Icons.Rounded.CameraAlt, MaterialTheme.colorScheme.primary, "Camera",
                "Camera access", PermStatus.Granted, {})
        } }
        compose.onNodeWithTag("permission-camera")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    @Test fun deniedRequestRowRetainsSwitchRole() {
        compose.setContent { MaterialTheme {
            PermissionSwitch(Icons.Rounded.CameraAlt, MaterialTheme.colorScheme.primary, "Camera",
                "Camera access", PermStatus.Denied, {})
        } }
        compose.onNodeWithTag("permission-camera")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
    }
    @Test fun approximateSettingsRouteIsButton() {
        compose.setContent { MaterialTheme {
            PermissionSwitch(Icons.Rounded.CameraAlt, MaterialTheme.colorScheme.primary, "Camera",
                "Synthetic access state", PermStatus.Approximate, {})
        } }
        compose.onNodeWithTag("permission-camera")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    @Test fun limitedSettingsRouteIsButton() {
        compose.setContent { MaterialTheme {
            PermissionSwitch(Icons.Rounded.CameraAlt, MaterialTheme.colorScheme.primary, "Camera",
                "Synthetic access state", PermStatus.Limited, {})
        } }
        compose.onNodeWithTag("permission-camera")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    @Test fun blockedSettingsRouteIsButton() {
        compose.setContent { MaterialTheme {
            PermissionSwitch(Icons.Rounded.CameraAlt, MaterialTheme.colorScheme.primary, "Camera",
                "Synthetic access state", PermStatus.PermanentlyDenied, {})
        } }
        compose.onNodeWithTag("permission-camera")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

}
