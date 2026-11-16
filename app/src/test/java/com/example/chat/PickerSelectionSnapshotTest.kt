package com.example.chat

import com.example.chat.ui.media.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

/** Source-only fixtures, NOT RUN under the overnight instruction. */
class PickerSelectionSnapshotTest {
    private fun item(path: String) = MediaFileDescriptor(path, path, path, 1, "text/plain", MediaCategory.document, Instant.EPOCH)
    @Test fun snapshotPreservesExistingLibraryOrderNotTapOrder() {
        val result = snapshotPickerSelection(listOf(item("a"), item("b"), item("c")), linkedSetOf("c", "a"))
        assertEquals(listOf("a", "c"), result.files.map { it.path })
        assertTrue(result.missingPaths.isEmpty()); assertTrue(result.duplicatePaths.isEmpty())
    }
    @Test fun missingPathsAreExplicitAndDuplicateSelectionIsNotSilentlyAccepted() {
        val result = snapshotPickerSelection(listOf(item("a"), item("a")), setOf("a", "missing"))
        assertEquals(setOf("missing"), result.missingPaths)
        assertEquals(setOf("a"), result.duplicatePaths)
        assertEquals(1, result.files.size)
    }
    @Test fun laterSourceCollectionChangesDoNotMutateSnapshot() {
        val library = mutableListOf(item("a"))
        val selected = mutableSetOf("a")
        val result = snapshotPickerSelection(library, selected)
        library.clear(); selected.clear()
        assertEquals(listOf("a"), result.files.map { it.path })
    }
    @Test fun grownOrDeletedFileCannotPassSummaryPreflight() {
        val file = java.io.File.createTempFile("picker-preflight-", ".txt")
        try {
            file.writeText("a")
            val descriptor = item(file.path).copy(sizeBytes = file.length())
            assertEquals(PickerSummaryFileState.Ready, pickerSummaryFileState(descriptor))
            file.appendText("more")
            assertEquals(PickerSummaryFileState.ChangedSize, pickerSummaryFileState(descriptor))
            file.delete()
            assertEquals(PickerSummaryFileState.Unavailable, pickerSummaryFileState(descriptor))
        } finally { file.delete() }
    }
}
