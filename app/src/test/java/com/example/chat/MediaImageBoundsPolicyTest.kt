package com.example.chat

import com.example.chat.ui.media.isAllowedMediaImageDimensions
import org.junit.Assert.*
import org.junit.Test

/** Source fixtures NOT RUN; BitmapFactory and stream budgets need Android tests. */
class MediaImageBoundsPolicyTest {
    @Test fun positiveDimensionsUpToHundredMegapixelsAllowed() {
        assertTrue(isAllowedMediaImageDimensions(1, 1)); assertTrue(isAllowedMediaImageDimensions(10_000, 10_000))
    }
    @Test fun unknownOversizedAndOverflowInputsRejected() {
        assertFalse(isAllowedMediaImageDimensions(0, 100)); assertFalse(isAllowedMediaImageDimensions(-1, 100))
        assertFalse(isAllowedMediaImageDimensions(10_001, 10_000)); assertFalse(isAllowedMediaImageDimensions(Int.MAX_VALUE, Int.MAX_VALUE))
    }
}
