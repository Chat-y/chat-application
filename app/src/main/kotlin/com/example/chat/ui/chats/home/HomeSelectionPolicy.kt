package com.example.chat.ui.chats.home

import com.example.chat.domain.models.Conversation

/** Explicit directions, not permission to execute an operation or bypass confirmation. */
enum class HomeBulkAction {
    PIN,
    UNPIN,
    MUTE,
    UNMUTE,
    ARCHIVE,
    UNARCHIVE,
    MARK_READ,
    MARK_UNREAD,
    DELETE,
}

/**
 * Pure selection rules for the current rendered scope.
 *
 * Callers must pass only rendered, eligible chat entries in display order, after account,
 * hidden/protected-content and collapsed-section filtering. Headers and archive affordances
 * are not conversations. This policy cannot recover those rules from a Conversation snapshot.
 * Resolve fresh snapshots again before inspecting or acting; never use this object as an
 * authorization check. Scope/account changes must clear the caller's selection and anchor.
 */
object HomeSelectionPolicy {
    fun selectAll(visible: List<Conversation>): Set<String> =
        orderedScope(visible).mapTo(linkedSetOf()) { it.id }

    /** Off-scope IDs are discarded rather than carried into the next bulk operation. */
    fun invert(visible: List<Conversation>, selected: Set<String>): Set<String> =
        orderedScope(visible).filterNot { it.id in selected }.mapTo(linkedSetOf()) { it.id }

    /** Inclusive range in render order, including when the target precedes the anchor. */
    fun range(visible: List<Conversation>, anchorId: String, targetId: String): Set<String> {
        val scope = orderedScope(visible)
        val anchor = scope.indexOfFirst { it.id == anchorId }
        val target = scope.indexOfFirst { it.id == targetId }
        // A vanished anchor must not select from an unrelated index after a refresh.
        if (anchor < 0 || target < 0) return emptySet()
        return scope.subList(minOf(anchor, target), maxOf(anchor, target) + 1)
            .mapTo(linkedSetOf()) { it.id }
    }

    /**
     * Snapshot-safe directions for a complete selected set, not server capabilities.
     *
     * An empty set, blank ID or duplicate target fails closed. A toggle family is offered
     * only for a uniform current state: applying the existing per-chat toggle callback to
     * mixed values would otherwise invert some targets in the opposite direction. Do not
     * execute a missing action on a subset. Explain mixed state in the caller's UI instead.
     *
     * Lock/unlock and role-specific operations are deliberately absent: their capability
     * data is not in Conversation. Hosts must also check availability, current permissions,
     * protected-content rules and failures for every target. DELETE still needs confirmation.
     */
    fun validBulkActions(selected: List<Conversation>): Set<HomeBulkAction> {
        if (selected.isEmpty() || selected.any { it.id.isBlank() }) return emptySet()
        if (selected.map { it.id }.toSet().size != selected.size) return emptySet()

        val actions = linkedSetOf<HomeBulkAction>()
        if (selected.all { !it.isPinned }) actions.add(HomeBulkAction.PIN)
        if (selected.all { it.isPinned }) actions.add(HomeBulkAction.UNPIN)
        if (selected.all { !it.isMuted }) actions.add(HomeBulkAction.MUTE)
        if (selected.all { it.isMuted }) actions.add(HomeBulkAction.UNMUTE)
        if (selected.all { !it.isArchived }) actions.add(HomeBulkAction.ARCHIVE)
        if (selected.all { it.isArchived }) actions.add(HomeBulkAction.UNARCHIVE)
        if (selected.all { it.unreadCount > 0 }) actions.add(HomeBulkAction.MARK_READ)
        if (selected.all { it.unreadCount == 0 }) actions.add(HomeBulkAction.MARK_UNREAD)
        actions.add(HomeBulkAction.DELETE)
        return actions
    }

    /** Current eligible snapshots only, in Home order, with each ID included once. */
    fun inspectorOrder(visible: List<Conversation>, selected: Set<String>): List<Conversation> =
        orderedScope(visible).filter { it.id in selected }

    private fun orderedScope(visible: List<Conversation>): List<Conversation> =
        visible.filter { it.id.isNotBlank() }.distinctBy { it.id }
}
