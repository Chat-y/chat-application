package com.example.chat

import androidx.compose.ui.geometry.Rect
import com.example.chat.ui.chats.detail.viewerBoundsChanged
import org.junit.Assert.assertEquals
import org.junit.Test

/** Source-only MV-R5-044 cases, NOT RUN. */
class ViewerBoundsPolicyTest {
    private val base = Rect(10f, 20f, 310f, 420f)
    @Test fun firstMeasurementIsAccepted() { assertEquals(true, viewerBoundsChanged(null, base)) }
    @Test fun equalMeasurementIsIgnored() { assertEquals(false, viewerBoundsChanged(base, base)) }
    @Test fun subpixelDeltaIsIgnored() { assertEquals(false, viewerBoundsChanged(base, Rect(10.4f, 20.4f, 310.4f, 420.4f))) }
    @Test fun exactHalfPixelIsIgnored() { assertEquals(false, viewerBoundsChanged(base, Rect(10.5f, 20.5f, 310.5f, 420.5f))) }
    @Test fun leftBeyondThresholdIsAccepted() { assertEquals(true, viewerBoundsChanged(base, Rect(9.4f, 20f, 310f, 420f))) }
    @Test fun topBeyondThresholdIsAccepted() { assertEquals(true, viewerBoundsChanged(base, Rect(10f, 20.6f, 310f, 420f))) }
    @Test fun rightBeyondThresholdIsAccepted() { assertEquals(true, viewerBoundsChanged(base, Rect(10f, 20f, 310.6f, 420f))) }
    @Test fun bottomBeyondThresholdIsAccepted() { assertEquals(true, viewerBoundsChanged(base, Rect(10f, 20f, 310f, 420.6f))) }
    @Test fun accumulatedDriftUsesLastAcceptedBounds() { assertEquals(true, viewerBoundsChanged(base, Rect(10.6f, 20.6f, 310.6f, 420.6f))) }
}
