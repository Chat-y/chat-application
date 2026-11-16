package com.example.chat.ui.chats.home

import com.example.chat.domain.models.Conversation

/** Pure, JVM-testable helpers for the LazyColumn identity half of the home
 *  list: type-namespaced item keys (R5-01) and duplicate conversation-ID
 *  reconciliation before rendering (R5-02). HomeConversationList consumes
 *  these; the rules stay identical for every home style. */

/** R5-01: type-namespaced, duplicate-safe key for one home entry.
 *
 *  Conversation IDs are opaque server values, so the old raw-ID key for an
 *  [HomeEntry.Item] could equal a synthetic section/archive key ("section_..."
 *  / "archived_entry"). The "chat:" / "section:" / "archive:" namespaces make
 *  that collision impossible: an Item key always starts with "chat:", which
 *  no synthetic key ever does.
 *
 *  [seen] is the set of keys already emitted for the current list pass; the
 *  caller owns one set per pass. Normally every base key is unique and is
 *  returned as-is. If a duplicate slips through (two sections with the same
 *  label, or a duplicated conversation ID that escaped [dedupeConversations]),
 *  the key still stays unique via a deterministic "#2", "#3", ... suffix, so
 *  Compose never sees two identical keys. Suffixing is last-resort: the real
 *  fix for duplicate conversation IDs is [dedupeConversations] upstream. */
fun homeEntryKey(entry: HomeEntry, seen: MutableSet<String>): Any {
    val base = when (entry) {
        // One archived row per list pass; a constant namespace key keeps its
        // state stable when the archived count changes.
        is HomeEntry.Archived -> "archive:entry"
        is HomeEntry.Section -> "section:${entry.label}"
        is HomeEntry.Item -> "chat:${entry.conversation.id}"
    }
    if (seen.add(base)) return base
    var suffix = 2
    while (true) {
        val candidate = "$base#$suffix"
        if (seen.add(candidate)) return candidate
        suffix += 1
    }
}

/** R5-02: reconcile duplicate conversation IDs before entries reach
 *  buildHomeEntries / LazyColumn, instead of relying on Compose to survive
 *  duplicate keys.
 *
 *  The first occurrence of each ID wins: input order is the current account's
 *  conversation ordering, so the surviving record is deterministic. Later
 *  duplicates of the same ID are dropped. The input list is returned
 *  unchanged when there is nothing to drop, so the no-duplicate path keeps
 *  the original instance (and its equality semantics for callers that
 *  remember() on it). */
fun dedupeConversations(list: List<Conversation>): List<Conversation> {
    if (list.size < 2) return list
    val seenIds = HashSet<String>(list.size)
    val kept = ArrayList<Conversation>(list.size)
    var dropped = false
    for (conversation in list) {
        if (seenIds.add(conversation.id)) {
            kept.add(conversation)
        } else {
            dropped = true
        }
    }
    return if (dropped) kept else list
}
