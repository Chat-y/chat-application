package com.example.chat.ui.chats.starred

/** Approximately two laid-out list windows; no dependence on fixed row heights. */
internal object StarredScrollRules {
    fun showTop(firstVisible: Int, visibleCount: Int): Boolean =
        visibleCount > 0 && firstVisible > 0 && firstVisible.toLong() >= visibleCount.toLong() * 2L
}
