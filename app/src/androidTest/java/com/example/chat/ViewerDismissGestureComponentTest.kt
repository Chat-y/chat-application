package com.example.chat

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.example.chat.ui.chats.detail.viewerImageDismissGesture
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import androidx.compose.ui.test.junit4.createComposeRule

/** MV-R1-050/MV-R3-026 isolated fixture, NOT RUN. Not image-transform/native VideoView proof. */
class ViewerDismissGestureComponentTest {
    @get:Rule val compose = createComposeRule()
    private var density = 1f
    private var dismissed = 0
    private fun content(allowed: Boolean = true, reduceMotion: Boolean = false, transformChild: Boolean = false) {
        compose.setContent { MaterialTheme {
            density = LocalDensity.current.density
            Box(Modifier.size(240.dp, 480.dp).testTag("dismiss region")
                .viewerImageDismissGesture("fixture", reduceMotion, { allowed }, { dismissed++ })) {
                if (transformChild) {
                    // Mirrors the passive1x image's transform detector: consumes transform slop,
                    // but the image pan is clamped at1x. No image/native rendering in this fixture.
                    Box(Modifier.fillMaxSize().pointerInput(Unit) {
                        detectTransformGestures { _, _, _, _ -> }
                    })
                }
            }
        } }
    }
    private fun drag(dxDp: Float, dyDp: Float, duration: Long = 1200L) {
        compose.onNodeWithTag("dismiss region").performTouchInput {
            val start = Offset(width / 2f, height / 4f)
            swipe(start, start + Offset(dxDp * density, dyDp * density), durationMillis = duration)
        }
    }
    private fun count(expected: Int) = compose.runOnIdle { assertEquals(expected, dismissed) }
    @Test fun distanceDismissesOrdinaryRegion() { content(); drag(0f, 140f); count(1) }
    @Test fun slowShortDragSpringsBackWithoutDismiss() { content(); drag(0f, 60f); compose.waitForIdle(); count(0) }
    @Test fun upwardDragDoesNotDismiss() { content(); drag(0f, -60f); count(0) }
    @Test fun horizontalIntentDoesNotDismiss() { content(); drag(70f, 30f); count(0) }
    @Test fun gatedZoomedRegionDoesNotDismiss() { content(allowed = false); drag(0f, 140f); count(0) }
    @Test fun reducedMotionKeepsDistanceGesture() { content(reduceMotion = true); drag(0f, 140f); count(1) }
    @Test fun cancelledDragDoesNotDismiss() {
        content(); compose.onNodeWithTag("dismiss region").performTouchInput {
            down(Offset(width / 2f, height / 4f)); moveBy(Offset(0f, 50f * density)); cancel()
        }; count(0)
    }
    @Test fun completedDismissIsNotRepeated() { content(); drag(0f, 140f); drag(0f, 140f); count(1) }
    //150 regression fixtures for149 source correction. NOT RUN, not device arbitration proof.
    @Test fun oneXTransformChildStillAllowsLongVerticalDismiss() {
        content(transformChild = true); drag(0f, 140f); count(1)
    }
    @Test fun oneXTransformChildDoesNotTurnShortDragIntoDismiss() {
        content(transformChild = true); drag(0f, 60f); compose.waitForIdle(); count(0)
    }
    @Test fun oneXTransformChildKeepsHorizontalIntentOutOfDismiss() {
        content(transformChild = true); drag(90f, 30f); count(0)
    }
    @Test fun oneXTransformChildRespectsZoomEligibilityGate() {
        content(allowed = false, transformChild = true); drag(0f, 140f); count(0)
    }
    @Test fun oneXTransformChildKeepsReducedMotionDistanceDismiss() {
        content(reduceMotion = true, transformChild = true); drag(0f, 140f); count(1)
    }
}
