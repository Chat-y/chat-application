package com.example.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.chat.ui.camera.PaperQuad
import com.example.chat.ui.camera.paperCornerAccessibility
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** CAM-R4-034 UI fixture only, NOT RUN. No bitmap/crop/native camera or IO. */
class PaperCornerAccessibilitySemanticsTest {
    @get:Rule val compose = createComposeRule()
    private var result = PaperQuad.inset
    private var callbacks = 0
    private fun content(enabled: Boolean = true) {
        compose.setContent { MaterialTheme {
            var quad by remember { mutableStateOf(PaperQuad.inset) }
            Box(Modifier.size(200.dp).paperCornerAccessibility(quad, enabled) {
                result = it; quad = it; callbacks++
            })
        } }
    }
    private fun corner() = compose.onNodeWithContentDescription("Adjust paper corners")
    private fun focus() { corner().performSemanticsAction(SemanticsActions.RequestFocus) { it() }; corner().assertIsFocused() }
    private fun action(label: String) {
        corner().performSemanticsAction(SemanticsActions.CustomActions) { actions ->
            assertTrue(actions.single { it.label == label }.action())
        }
    }
    private fun position(prefix: String) = corner().assert(SemanticsMatcher("active corner starts with $prefix") {
        (if (it.config.contains(SemanticsProperties.StateDescription)) it.config[SemanticsProperties.StateDescription] else "").startsWith(prefix)
    })
    @Test fun exposesEightCustomActions() {
        content(); corner().performSemanticsAction(SemanticsActions.CustomActions) { assertEquals(8, it.size) }
    }
    @Test fun selectingEachCornerChangesStateWithoutEditing() {
        content()
        listOf("Top left", "Top right", "Bottom right", "Bottom left").forEach { name ->
            action("Select $name corner"); position("$name corner")
        }
        compose.runOnIdle { assertEquals(0, callbacks); assertEquals(PaperQuad.inset, result) }
    }
    @Test fun customRightMovesOnlySelectedCorner() {
        content(); action("Select Bottom right corner"); action("Move selected corner right")
        compose.runOnIdle { assertEquals(.93f, result.bottomRight.x, .00001f); assertEquals(PaperQuad.inset.topLeft, result.topLeft); assertEquals(1, callbacks) }
    }
    @Test fun customLeftMovesSelectedCorner() { content(); action("Move selected corner left"); compose.runOnIdle { assertEquals(.07f, result.topLeft.x, .00001f) } }
    @Test fun customUpMovesSelectedCorner() { content(); action("Move selected corner up"); compose.runOnIdle { assertEquals(.07f, result.topLeft.y, .00001f) } }
    @Test fun customDownMovesSelectedCorner() { content(); action("Move selected corner down"); compose.runOnIdle { assertEquals(.09f, result.topLeft.y, .00001f) } }
    @Test fun arrowKeyMovesOnePercentWithOneCallback() {
        content(); focus(); corner().performKeyInput { pressKey(Key.DirectionRight) }
        compose.runOnIdle { assertEquals(.09f, result.topLeft.x, .00001f); assertEquals(1, callbacks) }
    }
    @Test fun enterCyclesAllFourCornersWithoutEditing() {
        content(); focus()
        listOf("Top right", "Bottom right", "Bottom left", "Top left").forEach { name ->
            corner().performKeyInput { pressKey(Key.Enter) }; position("$name corner")
        }
        compose.runOnIdle { assertEquals(0, callbacks) }
    }
    @Test fun shiftEnterReversesCornerSelection() {
        content(); focus(); corner().performKeyInput { keyDown(Key.ShiftLeft); pressKey(Key.Enter); keyUp(Key.ShiftLeft) }
        position("Bottom left corner")
    }
    @Test fun busyTargetHasNoAdjustmentActions() {
        content(enabled = false); corner().assertIsNotEnabled()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.CustomActions))
        compose.runOnIdle { assertEquals(0, callbacks) }
    }
}
