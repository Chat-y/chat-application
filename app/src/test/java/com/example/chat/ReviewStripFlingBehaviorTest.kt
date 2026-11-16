package com.example.chat

import androidx.compose.foundation.gestures.ScrollScope
import com.example.chat.ui.chats.detail.ReviewStripStopFlingBehavior
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** MR-R4-037 isolated policy sources, NOT RUN. Not drag/nested-scroll/pixel proof. */
class ReviewStripFlingBehaviorTest {
    private fun stops(velocity: Float) = runBlocking {
        var scrollCalls = 0
        val scope = object : ScrollScope {
            override fun scrollBy(pixels: Float): Float { scrollCalls++; return pixels }
        }
        val remaining = with(ReviewStripStopFlingBehavior) { scope.performFling(velocity) }
        assertEquals(0f, remaining, 0f)
        assertEquals(0, scrollCalls)
    }
    @Test fun positiveVelocityDoesNotScrollAfterLift() { stops(6000f) }
    @Test fun negativeVelocityDoesNotScrollAfterLift() { stops(-6000f) }
    @Test fun zeroVelocityDoesNotScroll() { stops(0f) }
    @Test fun smallVelocityDoesNotStartDecay() { stops(1f) }
}
