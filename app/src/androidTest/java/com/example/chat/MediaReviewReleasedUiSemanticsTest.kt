package com.example.chat

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.chat.ui.chats.detail.ReviewSelectedButton
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** MR-R5-026 partial component suite, authored only, NOT RUN.
 * Does not cover full review/editor/trim routes, actual expanded hit bounds, or live error speech.
 * Espresso accessibility integration is held pending permission for its dependency/build-file change.
 */
class MediaReviewReleasedUiSemanticsTest {
    @get:Rule val compose = createComposeRule()

    private fun auditFocusableComponentTargets() {
        val targets = compose.onAllNodes(isFocusable(), useUnmergedTree = false).fetchSemanticsNodes()
        assertTrue("Fixture must expose focusable targets, not a vacuous pass", targets.isNotEmpty())
        val floor = with(compose.density) { 48.dp.toPx() }
        targets.forEach { node ->
            val spoken = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it.isNotBlank() } ||
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text.isNotBlank() } ||
                node.config.getOrNull(SemanticsProperties.EditableText)?.text?.isNotBlank() == true
            assertTrue("Focusable target ${node.id} needs a label", spoken)
            assertTrue("Target ${node.id} width ${node.boundsInRoot.width} must be>=48dp", node.boundsInRoot.width + 0.5f >= floor)
            assertTrue("Target ${node.id} height ${node.boundsInRoot.height} must be>=48dp", node.boundsInRoot.height + 0.5f >= floor)
        }
    }

    @Test fun toolChoicesHaveLabelsSelectedStateAnd48DpBounds() {
        compose.setContent { MaterialTheme { Row {
            ReviewSelectedButton("Text", false, true, {})
            ReviewSelectedButton("Draw", true, true, {})
            ReviewSelectedButton("Adjust", false, true, {})
        } } }
        compose.onNodeWithText("Draw").assertIsSelected().assertHasClickAction()
        compose.onNodeWithText("Text").assertIsNotSelected()
        auditFocusableComponentTargets()
    }

    @Test fun keyboardFocusCanReachAChoice() {
        compose.setContent { MaterialTheme { ReviewSelectedButton("Draw", false, true, {}) } }
        compose.onNodeWithText("Draw").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithText("Draw").assertIsFocused()
        auditFocusableComponentTargets()
    }

    @Test fun selectionCallbackUpdatesTheSemanticState() {
        compose.setContent { MaterialTheme {
            var selected by remember { mutableStateOf(false) }
            ReviewSelectedButton("Text strip", selected, true, { selected = !selected })
        } }
        compose.onNodeWithText("Text strip").assertIsNotSelected().performClick().assertIsSelected()
        auditFocusableComponentTargets()
    }

    @Test fun disabledChoiceRetainsItsLabelAndState() {
        compose.setContent { MaterialTheme { ReviewSelectedButton("Text", true, false, {}) } }
        compose.onNodeWithText("Text").assertIsDisplayed().assertIsSelected().assertIsNotEnabled()
    }
}
