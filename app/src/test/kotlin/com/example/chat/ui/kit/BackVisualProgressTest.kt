package com.example.chat.ui.kit

import org.junit.Assert.assertEquals
import org.junit.Test

/** Source-only pure math coverage. Not run; does not prove gesture or guard integration. */
class BackVisualProgressTest {
    @Test fun preservesGestureProgress() {
        assertEquals(0.4f, backVisualProgress(0.4f), 0f)
    }
    @Test fun clampsOutOfRangeValues() {
        assertEquals(0f, backVisualProgress(-1f), 0f)
        assertEquals(1f, backVisualProgress(2f), 0f)
    }
    @Test fun rejectsNonFiniteValues() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach {
            assertEquals(0f, backVisualProgress(it), 0f)
        }
    }
}
