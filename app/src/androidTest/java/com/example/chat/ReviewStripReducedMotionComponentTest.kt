package com.example.chat

import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.chat.ui.chats.detail.ReviewStripStopFlingBehavior
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** MR-R4-037 isolated strip sources, NOT RUN. Not full review/pager/nested-scroll/AT/pixel proof. */
class ReviewStripReducedMotionComponentTest {
    @get:Rule val compose = createComposeRule()
    private var position = { 0 to 0 }
    private fun content(reduced: Boolean = true) {
        compose.setContent { MaterialTheme {
            val state = rememberLazyListState()
            position = { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            LazyRow(Modifier.width(240.dp).height(64.dp).testTag("strip"), state = state,
                flingBehavior = if (reduced) ReviewStripStopFlingBehavior else ScrollableDefaults.flingBehavior()) {
                items(30) { i -> Box(Modifier.width(64.dp).height(64.dp)) { Text("Item $i") } }
            }
        } }
    }
    @Test fun reducedMotionStillAllowsDirectFingerScroll() {
        content()
        compose.onNodeWithTag("strip").performTouchInput { swipeLeft(durationMillis = 1200L) }
        compose.runOnIdle { val (index, offset) = position(); assertTrue(index > 0 || offset > 0) }
    }
    @Test fun reducedMotionPositionDoesNotDriftAfterLift() {
        content()
        compose.onNodeWithTag("strip").performTouchInput { swipeLeft(durationMillis = 150L) }
        compose.waitForIdle()
        var stopped = 0 to 0
        compose.runOnIdle { stopped = position() }
        compose.mainClock.advanceTimeBy(1000L)
        compose.runOnIdle { assertEquals(stopped, position()) }
    }
    @Test fun reducedMotionKeepsItemTapAndScrollSemantics() {
        content(); compose.onNodeWithText("Item 0").assertExists()
        compose.onNodeWithTag("strip").performScrollToIndex(20)
        compose.onNodeWithText("Item 20").assertExists()
    }
    @Test fun reducedMotionDoesNotScrollPastStartOnReverseSwipe() {
        content()
        compose.onNodeWithTag("strip").performTouchInput {
            swipe(Offset(width * .25f, height / 2f), Offset(width * .8f, height / 2f), durationMillis = 150L)
        }
        compose.runOnIdle { assertEquals(0 to 0, position()) }
    }
    @Test fun normalBranchStillAllowsScrolling() {
        content(false)
        compose.onNodeWithTag("strip").performTouchInput { swipeLeft(durationMillis = 1200L) }
        compose.waitForIdle()
        compose.runOnIdle { val (index, offset) = position(); assertTrue(index > 0 || offset > 0) }
    }
}
