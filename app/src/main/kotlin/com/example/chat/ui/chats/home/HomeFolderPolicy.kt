package com.example.chat.ui.chats.home

import com.example.chat.data.local.ChatFolder
import com.example.chat.data.local.ChatFolderStore
import com.example.chat.domain.models.Conversation
import java.time.Duration
import java.time.Instant

/** Read-only projections of the current account's manual folders.
 *  Nothing here changes membership, resolves another account's IDs or reads message content.
 *  Observe store.revision at the call site before caching these results in Compose. The store
 *  exposes individual getters, not an atomic snapshot; refresh after a membership change. */
object HomeFolderPolicy {
    /** R4-50: unresolved references, not evidence that a chat was deleted remotely.
     *  [all] must be the complete available conversation list for this store's active account,
     *  never a search, folder or privacy-filtered subset. Do not offer stale-reference removal
     *  until the account's conversation loading has completed successfully. */
    fun missingMemberCount(store: ChatFolderStore, folderId: String, all: List<Conversation>): Int {
        if (store.folders().none { it.id == folderId }) return 0
        val knownIds = all.mapTo(mutableSetOf()) { it.id }
        return store.memberIds(folderId).count { it !in knownIds }
    }

    /** R4-09: preserves the supplied scope and order; never moves or changes any chat.
     *  Pass only active-account conversations that may appear in the Unfiled view. */
    fun chatsOutsideFolders(store: ChatFolderStore, all: List<Conversation>): List<Conversation> {
        val filedIds = mutableSetOf<String>()
        store.folders().forEach { folder -> filedIds.addAll(store.memberIds(folder.id)) }
        return all.distinctBy { it.id }.filterNot { it.id in filedIds }
    }

    /** R4-08/R4-46: local folder names, in the store's order, for an inspector or opt-in badges.
     *  Default is no disclosure. Set [revealLabels] only for a visible, unlocked conversation
     *  after the user opts in; never expose private folder labels through a locked row. */
    fun folderBadges(
        store: ChatFolderStore,
        conversationId: String,
        revealLabels: Boolean = false,
    ): List<String> {
        if (!revealLabels || conversationId.isBlank()) return emptyList()
        return store.folders().distinctBy { it.id }
            .filter { conversationId in store.memberIds(it.id) }
            .map { it.name }
    }

    /** R2-19: content-free counts, never an unread-message count.
     *  [visible] must already exclude hidden/protected chats for this account and surface.
     *  Recent means last activity in the inclusive [recentWindow] ending at [now], seven days
     *  by default. Future timestamps are excluded. The caller may label a custom window.
     *  Unknown/hidden member IDs contribute to neither count and no names are returned. */
    fun folderActivitySummary(
        store: ChatFolderStore,
        folderId: String,
        visible: List<Conversation>,
        now: Instant = Instant.now(),
        recentWindow: Duration = Duration.ofDays(7),
    ): String {
        val memberIds = if (store.folders().any { it.id == folderId }) {
            store.memberIds(folderId)
        } else {
            emptySet()
        }
        val members = visible.distinctBy { it.id }.filter { it.id in memberIds }
        val window = if (recentWindow.isNegative) Duration.ZERO else recentWindow
        val recent = members.count { conversation ->
            !conversation.lastMessageTime.isAfter(now) &&
                Duration.between(conversation.lastMessageTime, now) <= window
        }
        val chatLabel = if (members.size == 1) "chat" else "chats"
        return "${members.size} visible $chatLabel; $recent with recent activity"
    }

    /** R4-10: name-only search, independent of message search. Keeps input order and selection
     *  ownership at the call site; filtering never changes the selected folder ID. */
    fun folderPickerFilter(query: String, folders: List<ChatFolder>): List<ChatFolder> {
        val trimmed = query.trim()
        return if (trimmed.isEmpty()) folders
        else folders.filter { it.name.contains(trimmed, ignoreCase = true) }
    }
}
