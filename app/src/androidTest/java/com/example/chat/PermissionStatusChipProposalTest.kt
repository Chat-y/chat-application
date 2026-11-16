package com.example.chat

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.unit.dp
import com.example.chat.ui.settings.permissions.PermStatus
import com.example.chat.ui.settings.permissions.StatusChip
import org.junit.Rule
import org.junit.Test

/** PM5-46/PM2-34 source-only cases, uncompiled/unrun. */
class PermissionStatusChipProposalTest {
    @get:Rule val compose = createComposeRule()

    @Test fun grantedChipKeepsReadableStatusAndMinimumHeight() {
        compose.setContent { MaterialTheme { StatusChip("Camera", PermStatus.Granted, Icons.Rounded.CameraAlt) } }
        compose.onNodeWithText("Camera: On").assertHeightIsAtLeast(32.dp)
    }

    @Test fun checkingGlyphDoesNotDuplicateTheTextStatus() {
        compose.setContent { MaterialTheme { StatusChip("Camera", PermStatus.Checking, Icons.Rounded.CameraAlt) } }
        compose.onNodeWithText("Camera: Checking").assertHeightIsAtLeast(32.dp)
        compose.onAllNodes(hasContentDescription("hourglass", substring = true, ignoreCase = true), useUnmergedTree = true)
            .assertCountEquals(0)
    }
    @Test fun deniedChipRemainsInformationalAfterIconResize() {
        compose.setContent { MaterialTheme { StatusChip("Camera", PermStatus.Denied, Icons.Rounded.CameraAlt) } }
        compose.onNodeWithText("Camera: Off").assertHasNoClickAction().assertHeightIsAtLeast(32.dp)
    }

    @Test fun longChipLabelRemainsAvailableInSemantics() {
        val label = "Camera access on a managed work device"
        compose.setContent { MaterialTheme { StatusChip(label, PermStatus.Granted, Icons.Rounded.CameraAlt) } }
        compose.onNodeWithText("$label: On").assertHasNoClickAction().assertHeightIsAtLeast(32.dp)
    }
    @Test fun limitedChipRemainsInformational() {
        compose.setContent { MaterialTheme { StatusChip("Camera", PermStatus.Limited, Icons.Rounded.CameraAlt) } }
        compose.onNodeWithText("Camera: Limited").assertHasNoClickAction().assertHeightIsAtLeast(32.dp)
    }

    @Test fun approximateChipRemainsInformational() {
        compose.setContent { MaterialTheme { StatusChip("Camera", PermStatus.Approximate, Icons.Rounded.CameraAlt) } }
        compose.onNodeWithText("Camera: Approximate").assertHasNoClickAction().assertHeightIsAtLeast(32.dp)
    }

    @Test fun blockedChipRemainsInformational() {
        compose.setContent { MaterialTheme { StatusChip("Camera", PermStatus.PermanentlyDenied, Icons.Rounded.CameraAlt) } }
        compose.onNodeWithText("Camera: Blocked").assertHasNoClickAction().assertHeightIsAtLeast(32.dp)
    }

}
