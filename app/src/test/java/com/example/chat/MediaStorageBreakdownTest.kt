package com.example.chat

import com.example.chat.ui.media.*
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Source-only fixtures, NOT RUN under the overnight instruction. */
class MediaStorageBreakdownTest {
    @Test fun beforeFirstScanTotalsAreUnknownNotMeasuredZero() {
        val result = MediaLibraryService({ emptyList() }).getStorageBreakdown()
        assertFalse(result.isComplete); assertFalse(result.scanCompleted)
    }
    @Test fun missingConfiguredDirectoryMakesTotalsIncomplete() = runBlocking {
        val missing = File(System.getProperty("java.io.tmpdir"), "missing-storage-${java.util.UUID.randomUUID()}")
        val service = MediaLibraryService({ listOf(missing) })
        service.refreshLibrary()
        val result = service.getStorageBreakdown()
        assertTrue(result.scanCompleted); assertFalse(result.isComplete)
        assertEquals(1, result.unavailableDirectories)
    }
    @Test fun resultPreservesMapReaderCompatibility() {
        val result = MediaStorageBreakdown(mapOf(MediaCategory.pdf to 12L), 0, true)
        assertTrue(result.isComplete)
        assertEquals(12L, result[MediaCategory.pdf])
        assertEquals(setOf(MediaCategory.pdf), result.keys)
    }
    @Test fun canceledRefreshCannotRelabelPreviousTotalsAsLatestCompleteScan() = runBlocking {
        var cancel = false
        val service = MediaLibraryService({
            if (cancel) throw kotlinx.coroutines.CancellationException("fixture")
            emptyList()
        })
        service.refreshLibrary()
        assertTrue(service.getStorageBreakdown().isComplete)
        cancel = true
        try { service.refreshLibrary(); fail("Expected cancellation") }
        catch (_: kotlinx.coroutines.CancellationException) { }
        val result = service.getStorageBreakdown()
        assertFalse(result.isComplete)
        assertFalse(result.scanCompleted)
    }
}
