package com.example.chat.ui.chats.home

import com.example.chat.domain.models.Conversation
import com.example.chat.domain.models.ConversationType
import java.text.Collator
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** Session-only presentation choices. These do not change membership or pin state. */
sealed interface HomeGroupMode {
    object None : HomeGroupMode

    /** Use the same privacy-safe title resolver and locale as Home's name order. */
    data class Alphabetical(
        val displayTitleFor: (Conversation) -> String,
        val locale: Locale = Locale.getDefault(),
    ) : HomeGroupMode

    /**
     * Recreate on Home's clock/timezone ticks, rather than saving this snapshot.
     * Locked rows get no activity-date heading, matching their hidden row timestamp.
     */
    data class Date(
        val isConversationLocked: (String) -> Boolean,
        val now: Instant = Instant.now(),
        val zone: ZoneId = ZoneId.systemDefault(),
    ) : HomeGroupMode

    /** Enable only within a chosen local folder; this is not the stock type filter. */
    object DirectGroup : HomeGroupMode
}

/**
 * Post-process already filtered Home entries. Existing sections, archive placement and
 * pinned order remain intact. Grouping stays inside each existing section, so the
 * Productivity unread section is not silently merged into the messages section.
 *
 * The caller owns session-only controls and must pass collapsedPinned=false during
 * search. Counts in the collapsed heading describe the supplied entries, not remote
 * totals. No conversation is changed, and expanding restores the supplied pin order.
 */
fun groupHomeEntries(
    entries: List<HomeEntry>,
    mode: HomeGroupMode,
    collapsedPinned: Boolean,
): List<HomeEntry> {
    if (entries.isEmpty() || (mode == HomeGroupMode.None && !collapsedPinned)) return entries
    val result = mutableListOf<HomeEntry>()
    val rows = mutableListOf<HomeEntry.Item>()
    var section: HomeEntry.Section? = null

    fun flush() {
        val header = section
        // A collapsed pinned run supplies its own count-bearing section heading.
        if (header != null && !(collapsedPinned && rows.isNotEmpty() && rows.all { it.conversation.isPinned })) {
            result.add(header)
        }
        var offset = 0
        while (offset < rows.size) {
            val pinned = rows[offset].conversation.isPinned
            var end = offset + 1
            while (end < rows.size && rows[end].conversation.isPinned == pinned) end++
            val run = rows.subList(offset, end)
            when {
                pinned && collapsedPinned -> {
                    val unread = run.sumOf { it.conversation.unreadCount.coerceAtLeast(0).toLong() }
                    result.add(HomeEntry.Section(pinnedCollapsedSectionLabel(run.size, unread)))
                }
                pinned -> result.addAll(run)
                else -> result.addAll(groupUnpinnedHomeRows(run, mode))
            }
            offset = end
        }
        rows.clear()
        section = null
    }

    entries.forEach { entry ->
        when (entry) {
            is HomeEntry.Item -> rows.add(entry)
            is HomeEntry.Section -> {
                flush()
                section = entry
            }
            is HomeEntry.Archived -> {
                flush()
                result.add(entry)
            }
        }
    }
    flush()
    return result
}

private fun groupUnpinnedHomeRows(
    rows: List<HomeEntry.Item>,
    mode: HomeGroupMode,
): List<HomeEntry> {
    if (rows.isEmpty()) return emptyList()
    val result = mutableListOf<HomeEntry>()
    fun append(label: String, members: List<HomeEntry.Item>) {
        if (members.isEmpty()) return
        result.add(HomeEntry.Section(label))
        result.addAll(members)
    }
    when (mode) {
        HomeGroupMode.None -> result.addAll(rows)
        is HomeGroupMode.Alphabetical -> {
            val collator = Collator.getInstance(mode.locale)
            // Capture each display name once, using the same comparator/tie-break
            // as HomeQuickViewPolicy.Name. Never fall back to a raw private title.
            val named = rows.map { it to mode.displayTitleFor(it.conversation) }
            val ordered = named.sortedWith(Comparator { a, b ->
                val byName = collator.compare(a.second, b.second)
                if (byName != 0) byName else a.first.conversation.id.compareTo(b.first.conversation.id)
            })
            var previousLabel: String? = null
            ordered.forEach { (entry, title) ->
                val label = homeAlphabeticalSectionLabel(title, mode.locale)
                if (label != previousLabel) result.add(HomeEntry.Section(label))
                result.add(entry)
                previousLabel = label
            }
        }
        is HomeGroupMode.Date -> {
            val today = mode.now.atZone(mode.zone).toLocalDate()
            val yesterday = today.minusDays(1)
            val groups = rows.groupBy { row ->
                if (mode.isConversationLocked(row.conversation.id)) {
                    return@groupBy "Locked conversations"
                }
                val date = row.conversation.lastMessageTime.atZone(mode.zone).toLocalDate()
                when {
                    date == today -> "Today"
                    date == yesterday -> "Yesterday"
                    date.isBefore(yesterday) -> "Earlier"
                    // Do not mislabel future timestamps as earlier activity.
                    else -> "Future activity"
                }
            }
            listOf("Today", "Yesterday", "Earlier", "Future activity", "Locked conversations").forEach { label ->
                append(label, groups[label].orEmpty())
            }
        }
        HomeGroupMode.DirectGroup -> {
            append("Direct", rows.filter { it.conversation.type == ConversationType.direct })
            append("Groups", rows.filter { it.conversation.type == ConversationType.group })
        }
    }
    return result
}

/** Label of the stock pinned section heading produced by the Home entry builder. */
const val HOME_PINNED_SECTION_LABEL = "PINNED"

private const val PINNED_COLLAPSED_PREFIX = "PINNED ("

/** R4-43: count-bearing heading for a collapsed pinned run. Counts describe the supplied
 *  (current-account, visible) chats only - never remote totals. */
fun pinnedCollapsedSectionLabel(chatCount: Int, unreadCount: Long): String =
    "$PINNED_COLLAPSED_PREFIX${chatCount.coerceAtLeast(0)} chats, ${unreadCount.coerceAtLeast(0)} unread)"

/** Robust pinned-header matching: true for the stock "PINNED" heading and for the
 *  count-bearing collapsed form from [pinnedCollapsedSectionLabel], with any counts.
 *  Section tap handlers must use this instead of label equality: with `label == "PINNED"`
 *  the collapse toggle stops working exactly when the collapsed heading carries the count. */
fun isPinnedHomeSectionLabel(label: String): Boolean {
    val trimmed = label.trim()
    return trimmed == HOME_PINNED_SECTION_LABEL ||
        (trimmed.startsWith(PINNED_COLLAPSED_PREFIX) && trimmed.endsWith(")"))
}

/** Counts parsed from a collapsed pinned heading, so a collapsed section can keep the
 *  pinned count and unread total visible. Null for any other label. */
data class HomePinnedSectionCounts(val chats: Int, val unread: Long)

private val PINNED_COUNTS_REGEX = Regex("^PINNED \\((\\d+) chats, (\\d+) unread\\)$")

fun pinnedHomeSectionCounts(label: String): HomePinnedSectionCounts? {
    val match = PINNED_COUNTS_REGEX.matchEntire(label.trim()) ?: return null
    return HomePinnedSectionCounts(match.groupValues[1].toInt(), match.groupValues[2].toLong())
}

/** One jump target: [entryIndex] is the position of the section's first row in the grouped
 *  list - the index a LazyList scrolls to - and [label] is the section heading shown. */
data class HomeSectionJumpTarget(val label: String, val entryIndex: Int)

/** R4-41: jump targets over an already-grouped entry list. Section labels come from the
 *  same displayed-name comparator that produced the grouping, so collation stays consistent
 *  and no raw private title is used. Sections without a following row are skipped, and the
 *  archive affordance clears any pending section. Callers show the jump control only when
 *  name order is deliberately selected and hide it in activity order. */
fun homeSectionJumpTargets(entries: List<HomeEntry>): List<HomeSectionJumpTarget> {
    val targets = mutableListOf<HomeSectionJumpTarget>()
    var pendingLabel: String? = null
    entries.forEachIndexed { index, entry ->
        when (entry) {
            is HomeEntry.Section -> pendingLabel = entry.label
            is HomeEntry.Item -> {
                val label = pendingLabel
                if (label != null) {
                    targets.add(HomeSectionJumpTarget(label, index))
                    pendingLabel = null
                }
            }
            is HomeEntry.Archived -> pendingLabel = null
        }
    }
    return targets
}

/** Stable session key for a grouping mode, as carried by HomeSavedView/HomeViewState
 *  groupingKey. "" is [HomeGroupMode.None]. */
fun homeGroupModeKey(mode: HomeGroupMode): String = when (mode) {
    HomeGroupMode.None -> ""
    is HomeGroupMode.Alphabetical -> "name"
    is HomeGroupMode.Date -> "date"
    HomeGroupMode.DirectGroup -> "direct-group"
}

/** Resolve a saved grouping key back to a fresh mode. Alphabetical and Date need the
 *  caller's current resolvers (display titles, lock state); an unknown or empty key falls
 *  back to [HomeGroupMode.None] rather than guessing a stale snapshot. */
fun homeGroupModeForKey(
    key: String,
    displayTitleFor: (Conversation) -> String,
    isConversationLocked: (String) -> Boolean,
    locale: Locale = Locale.getDefault(),
): HomeGroupMode = when (key) {
    "name" -> HomeGroupMode.Alphabetical(displayTitleFor, locale)
    "date" -> HomeGroupMode.Date(isConversationLocked)
    "direct-group" -> HomeGroupMode.DirectGroup
    else -> HomeGroupMode.None
}

/** Display-only index label. Name ordering itself always uses the locale collator. */
fun homeAlphabeticalSectionLabel(displayTitle: String, locale: Locale = Locale.getDefault()): String {
    val trimmed = displayTitle.trimStart()
    if (trimmed.isEmpty()) return "#"
    val first = trimmed.codePointAt(0)
    if (!Character.isLetter(first)) return "#"
    return String(Character.toChars(first)).uppercase(locale)
}
