package com.example.chat.ui.chats.detail

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope

/** MR-R4-037: direct finger scrolling remains; reduced motion stops kinetic travel at lift.
 * Consumes the fling velocity deliberately, so a parent does not start a new kinetic animation.
 * This is not the pager's target-page policy and must not be used as its fling behavior.
 */
internal object ReviewStripStopFlingBehavior : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float = 0f
}
