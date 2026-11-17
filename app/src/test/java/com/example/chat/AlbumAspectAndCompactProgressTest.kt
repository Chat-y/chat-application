package com.example.chat

import com.example.chat.ui.chats.detail.*
import com.example.chat.data.media.MediaDownloadState as S
import org.junit.Assert.*
import org.junit.Test

class AlbumAspectAndCompactProgressTest {
    @Test fun portraitAndLandscapeKeepDifferentBoundedRatios() {
        assertEquals(0.75f, AlbumAspectPolicy.tileRatio(0.5f, 2), 0f)
        assertEquals(1.8f, AlbumAspectPolicy.tileRatio(3f, 2), 0f)
        assertEquals(1f, AlbumAspectPolicy.tileRatio(0.5f, 1), 0f)
        assertEquals(2f, AlbumAspectPolicy.tileRatio(3f, 1), 0f)
    }
    @Test fun missingAndMalformedDimensionsKeepStableFallback() {
        for (value in listOf(null, Float.NaN, Float.POSITIVE_INFINITY, -1f, 0f)) {
            assertEquals(1f, AlbumAspectPolicy.tileRatio(value, 2), 0f)
            assertEquals(2f, AlbumAspectPolicy.tileRatio(value, 1), 0f)
        }
    }
    @Test fun proportionalWidthsProduceEqualHeightRowsWithinWidth() {
        val ratios = listOf(AlbumAspectPolicy.tileRatio(.75f, 2), AlbumAspectPolicy.tileRatio(1.8f, 2))
        val available = 298f
        val widths = ratios.map { available * it / ratios.sum() }
        assertEquals(available, widths.sum(), .001f)
        val reservedRowHeight = AlbumAspectPolicy.reservedRowHeight(300f, 2f)
        assertEquals(149f, reservedRowHeight, .001f)
        // Children fill this one allocated height; they never apply a second aspectRatio.
        assertTrue(widths.all { it > 0f && it <= available })
    }
    @Test fun knownProgressIsShownAndUnknownTotalNeverGetsFakePercent() {
        assertEquals("62%", compactDownloadLabel(S.Downloading(62, 100, 5)))
        assertEquals("Paused 38%", compactDownloadLabel(S.Paused(38, 100, S.PauseReason.USER)))
        assertEquals("Downloading", compactDownloadLabel(S.Downloading(62, null, 5)))
        assertEquals("Waiting", compactDownloadLabel(S.Queued(null)))
        assertNull(compactDownloadLabel(S.Done))
        assertNull(compactDownloadLabel(S.Failed(62, 100, S.FailReason.NOT_ALLOWED)))
    }
}
