package com.example.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import com.example.chat.ui.settings.permissions.SystemPermissionsScreen
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test

/** PM3-27 target/busy checks, uncompiled/unrun. No permission request interaction. */
class PermissionRefreshTargetProposalTest {
    @get:Rule val compose = createComposeRule()

    @Test fun refreshTargetHasExplicit48dpMinimumBounds() {
        compose.setContent { MaterialTheme {
            SystemPermissionsScreen({}, {}, { false }, { false })
        } }
        compose.onNodeWithContentDescription("Re-check permissions")
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    }

    @Test fun pendingCapabilityReadKeepsRefreshDisabled() {
        val capability = CompletableDeferred<Boolean>()
        compose.setContent { MaterialTheme {
            SystemPermissionsScreen({}, {}, { capability.await() }, { false })
        } }
        compose.onNodeWithContentDescription("Re-check permissions").assertIsNotEnabled()
        compose.runOnIdle { capability.complete(false) }
    }
}
