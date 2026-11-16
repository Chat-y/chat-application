package com.example.chat.ui.media

import java.util.Locale

/** Presentation-only ordering. Selection identity stays path-based and the library is not mutated. */
internal enum class MediaBrowseOrder(val label: String) { Newest("Newest"), Name("Name"), Size("Largest") }
internal fun orderMediaFiles(files: List<MediaFileDescriptor>, order: MediaBrowseOrder): List<MediaFileDescriptor> {
    val nameOrder = compareBy<MediaFileDescriptor> { it.name.lowercase(Locale.ROOT) }.thenBy { it.path }
    val comparator = when (order) {
        MediaBrowseOrder.Newest -> compareByDescending<MediaFileDescriptor> { it.modifiedAt }.then(nameOrder)
        MediaBrowseOrder.Name -> nameOrder
        MediaBrowseOrder.Size -> compareByDescending<MediaFileDescriptor> { it.sizeBytes }.then(nameOrder)
    }
    return files.sortedWith(comparator)
}
