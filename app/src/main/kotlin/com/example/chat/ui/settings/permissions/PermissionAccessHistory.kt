package com.example.chat.ui.settings.permissions

/** Detect lost access, not its cause. User revocation and Android auto-reset look the same here. */
object PermissionAccessHistory {
    fun offSinceLastCheck(previouslyAllowed: Set<String>, deniedNow: Set<String>, alreadyFlagged: Set<String>): Set<String> =
        (alreadyFlagged + previouslyAllowed.intersect(deniedNow)).intersect(deniedNow)
}
