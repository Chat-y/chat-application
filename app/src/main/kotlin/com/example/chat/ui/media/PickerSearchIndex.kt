package com.example.chat.ui.media

/** FIL-R3-132 presentation cache. Same invariant lowercase and literal substring as library filtering.
 * Immutable descriptor snapshot; no filesystem, service state, query trimming or fuzzy matching. */
internal class PickerSearchIndex(files: List<MediaFileDescriptor>) {
    private val entries = files.map { it to it.name.lowercase() }
    fun filter(query: String, category: MediaCategory?): List<MediaFileDescriptor> {
        val normalizedQuery = query.lowercase()
        return entries.mapNotNull { (file, name) ->
            file.takeIf {
                (category == null || file.category == category) &&
                    (query.isEmpty() || name.contains(normalizedQuery))
            }
        }
    }
}
