package com.example.chat.ui.search

/** Bounds downstream display/deduplication work, not the network payload already materialized by the caller. */
internal data class SearchResultWindow<T>(val items: List<T>, val truncated: Boolean) {
    companion object {
        fun <T, K> first(source: List<T>, limit: Int = 100, key: (T) -> K): SearchResultWindow<T> {
            require(limit > 0)
            // Preserve backend rank, keep first duplicate, never scan beyond the requested raw window.
            return SearchResultWindow(source.take(limit).distinctBy(key), source.size > limit)
        }
    }
}
