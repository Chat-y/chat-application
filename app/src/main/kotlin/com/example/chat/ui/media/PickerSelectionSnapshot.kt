package com.example.chat.ui.media

/** One immutable point-in-time library-order selection. Never silently drops requested paths. */
internal data class PickerSelectionSnapshot(
    val files: List<MediaFileDescriptor>,
    val missingPaths: Set<String>,
    val duplicatePaths: Set<String>,
)
internal fun snapshotPickerSelection(library: List<MediaFileDescriptor>, requestedPaths: Set<String>): PickerSelectionSnapshot {
    val selected = library.filter { it.path in requestedPaths }
    val byPath = selected.groupBy { it.path }
    val missing = requestedPaths - byPath.keys
    val duplicates = byPath.filterValues { it.size > 1 }.keys.toSet()
    return PickerSelectionSnapshot(selected.distinctBy { it.path }.toList(), missing, duplicates)
}

/** Preflight only, not a content hash or a lock against changes after this check. */
internal enum class PickerSummaryFileState { Unavailable, ChangedSize, Ready }
internal fun pickerSummaryFileState(descriptor: MediaFileDescriptor): PickerSummaryFileState = try {
    val file = java.io.File(descriptor.path)
    when {
        !file.isFile || !file.canRead() -> PickerSummaryFileState.Unavailable
        file.length() != descriptor.sizeBytes -> PickerSummaryFileState.ChangedSize
        else -> PickerSummaryFileState.Ready
    }
} catch (_: SecurityException) { PickerSummaryFileState.Unavailable }
