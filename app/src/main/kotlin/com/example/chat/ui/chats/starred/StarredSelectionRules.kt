package com.example.chat.ui.chats.starred

/** Pure selection over a captured UI snapshot. Caller owns dispatcher and cancellation. */
internal object StarredSelectionRules {
    fun <T, R> select(rows: List<T>, accepts: (T) -> Boolean, project: (T) -> R,
                      oldestFirst: Boolean, comparator: Comparator<R>?,
                      checkActive: () -> Unit): List<R> {
        val matches = ArrayList<R>()
        for (row in rows) {
            checkActive()
            if (accepts(row)) matches.add(project(row))
        }
        checkActive()
        if (comparator != null) matches.sortWith(Comparator { a, b ->
            checkActive()
            comparator.compare(a, b)
        }) else if (oldestFirst) matches.reverse()
        checkActive()
        return matches
    }
}
