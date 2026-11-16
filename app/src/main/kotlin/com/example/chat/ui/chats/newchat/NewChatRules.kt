package com.example.chat.ui.chats.newchat

import com.example.chat.domain.models.UserProfile

/** Pure rules ported from new_chat_screen.dart (api branch anchor
 *  3bee780) and ApiBackendService.searchUsers
 *  (lib/data/services/api_backend_service.dart). UI-free so the JVM parity
 *  tests exercise the exact decision logic. */
object NewChatRules {

    /** Debounce applied by _onSearchChanged before firing _searchUsers. */
    const val SEARCH_DEBOUNCE_MS = 250L

    /** _finishGroupCreation title rules:
     *  - empty title + broadcast -> "Broadcast (<memberCount>)"
     *  - any other empty title -> "" (creation blocked)
     *  - broadcast title not starting with "broadcast" (case-insensitive)
     *    -> "Broadcast: <title>" */
    fun effectiveGroupTitle(rawTitle: String, isBroadcast: Boolean, memberCount: Int): String {
        var title = rawTitle.trim()
        if (title.isEmpty() && isBroadcast) title = "Broadcast ($memberCount)"
        if (title.isEmpty()) return ""
        return if (isBroadcast && !Regex("^broadcast(?:$|[\\s:(])", RegexOption.IGNORE_CASE).containsMatchIn(title)) {
            "Broadcast: $title"
        } else {
            title
        }
    }

    /** Dart guard: `if (title.isEmpty || _selectedGroupMembers.isEmpty || _isLoading) return;` */
    fun canCreateGroup(rawTitle: String, isBroadcast: Boolean, memberCount: Int, isLoading: Boolean): Boolean =
        effectiveGroupTitle(rawTitle, isBroadcast, memberCount).isNotEmpty() && memberCount > 0 && !isLoading

    /** ApiBackendService.searchUsers local filter: after the remote
     *  loadUsers(query) the in-memory directory is matched by lowercase
     *  contains on displayName/username/email; self excluded unless
     *  includeSelf. */
    fun filterUsers(
        users: List<UserProfile>,
        query: String,
        selfId: String?,
        includeSelf: Boolean = false,
    ): List<UserProfile> {
        val q = query.lowercase().trim()
        return users.filter { u ->
            if (!includeSelf && selfId != null && u.id == selfId) {
                false
            } else if (q.isEmpty()) {
                true
            } else {
                u.displayName.lowercase().contains(q) ||
                    u.username.lowercase().contains(q) ||
                    u.email.lowercase().contains(q)
            }
        }
    }

    /** New-message lookup is an exact username, never a name/email search. */
    fun usernameKey(value: String): String = value.lowercase(java.util.Locale.ROOT)

    fun normalizedUsername(value: String): String = usernameKey(value.trim().removePrefix("@"))

    fun exactUsernameMatch(users: List<UserProfile>, query: String, selfId: String?): List<UserProfile> {
        val target = normalizedUsername(query)
        if (target.isEmpty() || selfId.isNullOrBlank()) return emptyList()
        val matches = users.filter { it.id.isNotBlank() && it.id != selfId && usernameKey(it.username) == target }
            .distinctBy { it.id }
        check(matches.size <= 1) { "This username matched more than one account. Try again later." }
        return matches
    }

    /** Missing broadcast capability must never fall back to an ordinary group creation. */
    fun <T> creatorFor(broadcast: Boolean, groupCreator: T, broadcastCreator: T?): T? =
        if (broadcast) broadcastCreator else groupCreator

    /** Synchronous immutable-ID reservation, independent of mutable username aliases. */
    fun canReserveRequest(id: String, ownerId: String?, pending: Set<String>, sending: Set<String>, uncertain: Set<String>): Boolean =
        !ownerId.isNullOrBlank() && id.isNotBlank() && id != ownerId && id !in pending && id !in sending && id !in uncertain

    /** Local review eligibility only. Server permissions are rechecked through the host callback. */
    fun validSelection(ids: Set<String>, ownerId: String?, knownIds: Set<String>): Boolean =
        !ownerId.isNullOrBlank() && ids.isNotEmpty() && ids.all { it.isNotBlank() && it != ownerId && it in knownIds }

    /** Validate the title actually sent, including any generated broadcast prefix. */
    fun validFinalTitle(rawTitle: String, broadcast: Boolean, count: Int, limit: Int): Boolean =
        effectiveGroupTitle(rawTitle, broadcast, count).let { it.isNotBlank() && it.length <= limit.coerceAtLeast(1) }

    /** A callback completion is not enough to authorize navigating to an unrelated room. */
    fun validConversation(
        conversation: com.example.chat.domain.models.Conversation,
        ownerId: String?, requiredMembers: Set<String>, group: Boolean,
    ): Boolean {
        if (ownerId.isNullOrBlank() || conversation.id.isBlank() || requiredMembers.isEmpty()) return false
        if (conversation.isGroup != group || requiredMembers.any { it.isBlank() || it == ownerId }) return false
        val participants = conversation.participantIds.toSet()
        return ownerId in participants && participants.containsAll(requiredMembers) &&
            (group || participants == requiredMembers + ownerId)
    }

    /** Dart epoch gate: async results apply only when `epoch == _epoch`. */
    fun isCurrentEpoch(resultEpoch: Int, currentEpoch: Int): Boolean = resultEpoch == currentEpoch

    /** _toggleMember: add when absent, remove when present. */
    fun toggleMember(selected: Set<String>, userId: String): Set<String> =
        if (selected.contains(userId)) selected - userId else selected + userId
}

// Superseded source lines retained inactive for review:
//         return if (isBroadcast && !title.lowercase().startsWith("broadcast")) {
//         if (target.isEmpty()) return emptyList()
//         val matches = users.filter { it.id != selfId && usernameKey(it.username) == target }
