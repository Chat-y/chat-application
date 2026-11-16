package com.example.chat

import com.example.chat.ui.chats.detail.reviewPreviewDecodeSize
import org.junit.Assert.assertEquals
import org.junit.Test

/** Source-only cases, NOT RUN. */
class ReviewPreviewDecodePolicyTest {
    @Test fun currentUsesViewportBound() { assertEquals(1080 to 2048, reviewPreviewDecodeSize(true, 1080, 2400)) }
    @Test fun neighborRequestsThumbnailOnly() { assertEquals(192 to 192, reviewPreviewDecodeSize(false, 1080, 2400)) }
    @Test fun smallViewportDoesNotUpscaleRequest() { assertEquals(100 to 80, reviewPreviewDecodeSize(false, 100, 80)) }
    @Test fun invalidExtentKeepsPositiveRequest() { assertEquals(1 to 1, reviewPreviewDecodeSize(true, -1, 0)) }
    @Test fun largeIntegerExtentCannotExceedCap() { assertEquals(192 to 192, reviewPreviewDecodeSize(false, Int.MAX_VALUE, Int.MAX_VALUE)) }
}
