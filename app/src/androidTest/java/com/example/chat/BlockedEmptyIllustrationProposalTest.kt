package com.example.chat

import androidx.compose.material3.MaterialTheme
// b35: invalid top-level import retained as comment: import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsDisplayed
// b35: invalid top-level import retained as comment: import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.chat.ui.settings.privacy.BlockedContactsDialog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** PV1-58 source-only tests. Uncompiled/unrun. */
class BlockedEmptyIllustrationProposalTest {
    @get:Rule val compose = createComposeRule()

    @Test fun emptyListKeepsTextAndAddsDecorativeIllustration() {
        compose.setContent { MaterialTheme {
            BlockedContactsDialog(emptyList(), false, {}, {})
        } }
        compose.onNodeWithTag("blocked-contacts-empty-illustration", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("No blocked contacts.").assertIsDisplayed()
        compose.onNodeWithText("Unblock").assertDoesNotExist()
    }

    @Test fun emptyIllustrationDoesNotChangeDoneDismissal() {
        var dismissals = 0
        compose.setContent { MaterialTheme {
            BlockedContactsDialog(emptyList(), false, {}, { dismissals++ })
        } }
        compose.onNodeWithText("Done").performClick()
        compose.runOnIdle { assertEquals(1, dismissals) }
    }
}
