package com.example.chat.ui.updates

import com.example.chat.data.services.StatusRecord
import java.util.Locale

/** Filters only the caller's already-authorized feed. No remote query or media read. */
internal object StatusLocalSearchRules {
    fun filter(statuses: List<StatusRecord>, query: String, authorLabels: (String) -> List<String>): List<StatusRecord> {
        val needle = query.trim().lowercase(Locale.ROOT)
        if (needle.isEmpty()) return statuses
        return statuses.filter { status ->
            val fields = listOf(status.text, status.mediaName.orEmpty(), status.mediaType) + authorLabels(status.userId)
            fields.any { it.lowercase(Locale.ROOT).contains(needle) }
        }
    }
}
