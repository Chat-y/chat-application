package com.example.chat

import com.example.chat.ui.chats.detail.viewerDismissClaimsVertical
import com.example.chat.ui.chats.detail.viewerDismissThreshold
import org.junit.Assert.*
import org.junit.Test

/** MV-R1-050/MV-R3-026 pure boundary source cases, NOT RUN, not touch/device proof. */
class ViewerDismissGestureThresholdTest {
    @Test fun noClaimBeforeTwentyFourDp() { assertFalse(viewerDismissClaimsVertical(0f, 23.99f)) }
    @Test fun claimsAtTwentyFourDp() { assertTrue(viewerDismissClaimsVertical(0f, 24f)) }
    @Test fun horizontalOrDiagonalIntentDoesNotClaim() { assertFalse(viewerDismissClaimsVertical(30f, 24f)); assertFalse(viewerDismissClaimsVertical(-24f, 24f)) }
    @Test fun upwardTravelDoesNotClaim() { assertFalse(viewerDismissClaimsVertical(0f, -100f)) }
    @Test fun distanceBoundaryDismisses() { assertTrue(viewerDismissThreshold(120f, 0f)); assertFalse(viewerDismissThreshold(119.99f, 0f)) }
    @Test fun velocityBoundaryDismissesOnlyAfterPositiveDrag() { assertTrue(viewerDismissThreshold(24f, 300f)); assertFalse(viewerDismissThreshold(24f, 299.99f)); assertFalse(viewerDismissThreshold(0f, 500f)) }
    @Test fun upwardVelocityDoesNotDismissShortDrag() { assertFalse(viewerDismissThreshold(24f, -500f)) }
    @Test fun distanceStillWinsWithUpwardRelease() { assertTrue(viewerDismissThreshold(121f, -500f)) }
    @Test fun rejectsNonFiniteInput() { assertFalse(viewerDismissClaimsVertical(Float.NaN, 40f)); assertFalse(viewerDismissThreshold(140f, Float.NaN)); assertFalse(viewerDismissThreshold(Float.POSITIVE_INFINITY, 400f)) }
}
