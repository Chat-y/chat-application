package com.example.chat.ui.settings.navigation

import com.example.chat.core.templates.ChatyNavigationDestinationIds

/** Pure rules ported from navigation_destinations_settings_screen.dart
 *  (api branch anchor 3bee780): configuration reconciliation, move guards
 *  and reorder. JVM-testable; the screen holds the resulting lists. */
object NavigationDestinationsLogic {

    const val MAX_PRIMARY = 4
    const val MIN_PRIMARY = 1

    /** Row labels from the Dart _labels map, keyed by destination id. */
    val labels: Map<String, String> = linkedMapOf(
        ChatyNavigationDestinationIds.chats to "Chats",
        ChatyNavigationDestinationIds.groups to "Groups",
        ChatyNavigationDestinationIds.updates to "Updates",
        ChatyNavigationDestinationIds.tasks to "Tasks",
        ChatyNavigationDestinationIds.calls to "Calls",
        ChatyNavigationDestinationIds.settings to "Settings",
        ChatyNavigationDestinationIds.desktop to "Linked devices",
    )

    /** _loadCurrentConfiguration: start from the active configuration, append
     *  every unassigned known destination to overflow, drop ids without a
     *  label, then append any still-missing known destination to overflow. */
    fun reconcile(
        primary: List<String>,
        overflow: List<String>,
        onCorrection: ((String) -> Unit)? = null, // SET-R2-002: report silent drops (default: no-op)
    ): Pair<MutableList<String>, MutableList<String>> {
        // Repair to the same invariants enforced by TemplateController at Save:
        // known, unique, disjoint, 1..4 primary, and every destination present.
        val nextPrimary = mutableListOf<String>()
        val nextOverflow = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        fun accept(id: String, preferPrimary: Boolean) {
            if (id !in ChatyNavigationDestinationIds.known) {
                onCorrection?.invoke("dropped unknown destination id: $id")
                return
            }
            if (!seen.add(id)) {
                onCorrection?.invoke("dropped duplicate navigation destination")
                return
            }
            if (preferPrimary && nextPrimary.size < MAX_PRIMARY) nextPrimary.add(id)
            else nextOverflow.add(id)
        }
        primary.forEach { accept(it, true) }
        overflow.forEach { accept(it, false) }
        ChatyNavigationDestinationIds.all.forEach { if (it !in seen) accept(it, false) }
        if (nextPrimary.isEmpty()) {
            // Promote the first surviving entry, preserving the user's relative order.
            nextPrimary.add(nextOverflow.removeAt(0))
            onCorrection?.invoke("restored required primary destination")
        }
        return nextPrimary to nextOverflow
        // Previous reconciliation retained inactive (did not repair duplicates/cardinality):
        // val nextPrimary = primary.toMutableList()
        // val nextOverflow = overflow.toMutableList()
        // val assigned = nextPrimary.toSet() + nextOverflow.toSet()
        // for (destination in ChatyNavigationDestinationIds.all) {
        // if (!assigned.contains(destination)) nextOverflow.add(destination)
        // }
        // (nextPrimary + nextOverflow).filter { !labels.containsKey(it) }.forEach { onCorrection?.invoke("dropped unknown destination id: $it") }
        // nextPrimary.removeAll { !labels.containsKey(it) }
        // nextOverflow.removeAll { !labels.containsKey(it) }
        // for (destination in ChatyNavigationDestinationIds.all) {
        // if (!nextPrimary.contains(destination) && !nextOverflow.contains(destination)) {
        // nextOverflow.add(destination)
        // }
        // }
        // return nextPrimary to nextOverflow

    }

    /** _moveToOverflow guard: blocked at one remaining primary or when the id
     *  is not primary. */
    fun canMoveToOverflow(primary: List<String>, id: String): Boolean =
        primary.size > MIN_PRIMARY && primary.contains(id)

    /** _moveToPrimary guard: blocked at four primaries or when the id is not
     *  in overflow. */
    fun canMoveToPrimary(primary: List<String>, overflow: List<String>, id: String): Boolean =
        primary.size < MAX_PRIMARY && overflow.contains(id)

    fun moveToOverflow(primary: MutableList<String>, overflow: MutableList<String>, id: String) {
        if (!canMoveToOverflow(primary, id)) return
        primary.remove(id)
        overflow.add(id)
    }

    fun moveToPrimary(primary: MutableList<String>, overflow: MutableList<String>, id: String) {
        if (!canMoveToPrimary(primary, overflow, id)) return
        overflow.remove(id)
        primary.add(id)
    }

    /** _reorder: moves the item at [index] by [delta] positions (remove + insert, not a swap), bounds-checked. */
    fun reorder(list: MutableList<String>, index: Int, delta: Int) {
        // Prior: val next = index + delta; if (next < 0 || next >= list.size) return
        if (index !in list.indices) return
        val target = index.toLong() + delta.toLong()
        if (target < 0L || target >= list.size.toLong()) return
        val next = target.toInt()
        val item = list.removeAt(index)
        list.add(next, item)
    }

    /** The Devices tab was removed from the bottom bar; Linked devices stays
     *  reachable from Settings, so the Dart body skips its row. */
    fun isRowVisible(id: String): Boolean = id != ChatyNavigationDestinationIds.desktop
}
