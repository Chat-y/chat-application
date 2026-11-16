package com.example.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.chat.ui.chats.detail.EditorExportButtonContent
import org.junit.Rule
import org.junit.Test

/** MR-R3-027 source-only cases. No native export or screen-lifecycle claim. */
class EditorExportButtonContentTest {
    @get:Rule val compose = createComposeRule()

    @Test fun idleHasApplyAndNoSpinner() {
        compose.setContent { MaterialTheme { EditorExportButtonContent(false) } }
        compose.onNodeWithText("Apply").assertIsDisplayed()
        compose.onNodeWithText("Exporting").assertDoesNotExist()
        compose.onAllNodes(hasProgressBarRangeInfo()).assertCountEquals(0)
    }

    @Test fun busyHasExportingAndOneSpinner() {
        compose.setContent { MaterialTheme { EditorExportButtonContent(true) } }
        compose.onNodeWithText("Exporting").assertIsDisplayed()
        compose.onNodeWithText("Apply").assertDoesNotExist()
        compose.onAllNodes(hasProgressBarRangeInfo()).assertCountEquals(1)
    }

    @Test fun clearingBusyRestoresIdleContent() {
        val saving = mutableStateOf(true)
        compose.setContent { MaterialTheme { EditorExportButtonContent(saving.value) } }
        compose.runOnIdle { saving.value = false }
        compose.onNodeWithText("Apply").assertIsDisplayed()
        compose.onAllNodes(hasProgressBarRangeInfo()).assertCountEquals(0)
    }
}
