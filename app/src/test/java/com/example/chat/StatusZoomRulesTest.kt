package com.example.chat

import com.example.chat.ui.updates.StatusZoomRules
import org.junit.Assert.assertEquals
import org.junit.Test

/** Geometry fixtures only. Rendered gestures, orientation changes and device pixels remain unexecuted. */
class StatusZoomRulesTest {
    @Test fun noPanAtFitScale() {
        assertEquals(0f to 0f, StatusZoomRules.bounds(1000f, 1000f, 100f, 1000f, 1f))
    }
    @Test fun portraitLetterboxHasNoHorizontalPanUntilImageFillsWidth() {
        assertEquals(0f to 500f, StatusZoomRules.bounds(1000f, 1000f, 100f, 1000f, 2f))
        assertEquals(0f to 2000f, StatusZoomRules.bounds(1000f, 1000f, 100f, 1000f, 5f))
    }
    @Test fun landscapeLetterboxHasNoVerticalPanUntilImageFillsHeight() {
        assertEquals(500f to 0f, StatusZoomRules.bounds(1000f, 1000f, 1000f, 100f, 2f))
    }
    @Test fun squareAndWideViewportUseFittedDimensions() {
        assertEquals(500f to 250f, StatusZoomRules.bounds(1000f, 500f, 1000f, 500f, 2f))
        assertEquals(0f to 250f, StatusZoomRules.bounds(1000f, 500f, 500f, 500f, 2f))
    }
    @Test fun unknownOrNonFiniteDimensionsKeepImageCentered() {
        for (bad in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertEquals(0f to 0f, StatusZoomRules.bounds(1000f, 1000f, bad, 1000f, 2f))
        }
    }
    @Test fun zoomIsClampedToSupportedRange() {
        assertEquals(0f to 0f, StatusZoomRules.bounds(100f, 100f, 100f, 100f, 0.5f))
        assertEquals(200f to 200f, StatusZoomRules.bounds(100f, 100f, 100f, 100f, 20f))
    }
}
