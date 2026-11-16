package com.example.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.example.chat.ui.settings.permissions.PermissionContentFrame
import org.junit.Rule
import org.junit.Test

/** PM5-17 constraint cases, uncompiled/unrun. Centering pixels/resize require device review. */
class PermissionContentWidthProposalTest {
    @get:Rule val compose = createComposeRule()

    @Test fun expandedParentCapsContentAt720dp() {
        compose.setContent { MaterialTheme {
            Box(Modifier.requiredSize(1000.dp, 600.dp)) { PermissionContentFrame {
                Box(Modifier.fillMaxSize().testTag("content"))
            } }
        } }
        compose.onNodeWithTag("content").assertWidthIsEqualTo(720.dp)
    }

    @Test fun narrowParentKeepsAvailableWidth() {
        compose.setContent { MaterialTheme {
            Box(Modifier.requiredSize(320.dp, 600.dp)) { PermissionContentFrame {
                Box(Modifier.fillMaxSize().testTag("content"))
            } }
        } }
        compose.onNodeWithTag("content").assertWidthIsEqualTo(320.dp)
    }
}
