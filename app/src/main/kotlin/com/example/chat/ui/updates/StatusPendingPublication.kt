package com.example.chat.ui.updates

/** In-memory approval payload only, never saved state, disk, diagnostics or logs. UI-thread confined. */
internal open class StatusOneShotApproval {
    private var consumed = false
    /** Consume synchronously before any publication coroutine starts. A dismissed/stale dialog cannot submit twice. */
    fun consumeOnce(): Boolean {
        if (consumed) return false
        consumed = true
        return true
    }

}

internal sealed class StatusPendingPublication(val type: String) : StatusOneShotApproval() {
    class Text(val value: String) : StatusPendingPublication("text")
    // The selection's owned byte array is retained without another full-size copy.
    class Media(type: String, val picked: PickedStatusMedia, val caption: String) : StatusPendingPublication(type)
}

internal sealed interface StatusPublicationWork {
    val type: String
    data class Picking(override val type: String) : StatusPublicationWork
    data class Publishing(val action: StatusPendingPublication) : StatusPublicationWork {
        override val type: String get() = action.type
    }
}

/** Camera review is scoped to the account that received this capture; bytes stay in memory. */
internal class StatusPendingStory(val ownerId: String, val captured: CapturedStoryMedia) : StatusOneShotApproval()
