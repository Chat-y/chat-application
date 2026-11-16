package com.example.chat.ui.chats.home

/**
 * R3-48 (decision 4A): capability model for the personal workspace. Pure
 * logic only: the UI hides any operation whose backing source is not
 * present, instead of showing a plausible button backed by a no-op.
 *
 * Every capability defaults to false and is turned on only when the real
 * store/adapter exists and is wired. Today ALL stores are proposals, so the
 * default [current] snapshot is empty on purpose; it is the honest state,
 * not a stub pretending support.
 */

enum class HomeWorkspaceAction {
    CreateFollowUp, SnoozeFollowUp, CompleteFollowUp,
    ConvertSavedToFollowUp, ClearReminderKeepSaved,
    SaveItem, ViewSavedItems,
    EditPrivateNote, DeletePrivateNote,
    SearchByFilter, SearchExactPhrase,
    ScheduleReminderNotification,
}

data class HomeWorkspaceCapabilities(
    /** Local follow-up store exists (proposal: FollowUpStore). */
    val followUpStore: Boolean = false,
    /** Local saved-item store exists (proposal: SavedItemStore). */
    val savedItemStore: Boolean = false,
    /** Local per-conversation note store exists (proposal: ConversationNoteStore). */
    val noteStore: Boolean = false,
    /** Local search index exists (R3-15: held, needs its own decision). */
    val localIndex: Boolean = false,
    /** Reminder channel + in-process scheduler wired (proposal). */
    val reminderScheduling: Boolean = false,
    /** Exact-alarm permission. Decision 3A: never requested this wave. */
    val exactAlarms: Boolean = false,
)

object HomeWorkspacePolicy {

    /** The honest capability snapshot for this wave: pure logic exists, no
     *  stores or schedulers are wired yet. */
    fun current(): HomeWorkspaceCapabilities = HomeWorkspaceCapabilities()

    fun isSupported(caps: HomeWorkspaceCapabilities, action: HomeWorkspaceAction): Boolean =
        when (action) {
            HomeWorkspaceAction.CreateFollowUp,
            HomeWorkspaceAction.SnoozeFollowUp,
            HomeWorkspaceAction.CompleteFollowUp,
            -> caps.followUpStore

            HomeWorkspaceAction.ConvertSavedToFollowUp,
            HomeWorkspaceAction.ClearReminderKeepSaved,
            -> caps.followUpStore && caps.savedItemStore

            HomeWorkspaceAction.SaveItem,
            HomeWorkspaceAction.ViewSavedItems,
            -> caps.savedItemStore

            HomeWorkspaceAction.EditPrivateNote,
            HomeWorkspaceAction.DeletePrivateNote,
            -> caps.noteStore

            HomeWorkspaceAction.SearchByFilter,
            HomeWorkspaceAction.SearchExactPhrase,
            -> caps.localIndex

            HomeWorkspaceAction.ScheduleReminderNotification ->
                caps.followUpStore && caps.reminderScheduling
        }

    /** Actions the UI may show for a snapshot. Everything else stays hidden. */
    fun availableActions(caps: HomeWorkspaceCapabilities): Set<HomeWorkspaceAction> =
        HomeWorkspaceAction.entries.filter { isSupported(caps, it) }.toSet()
}

/** Bounds for a per-conversation private note (R3-10/R3-42 pure rules; the
 *  note store itself is proposed separately). A note is never sent to the
 *  contact and stays out of Home previews unless deliberately opened. */
/** The exact scope a delete-personal-note action may touch. */
data class HomeNoteDeleteScope(val accountKey: String, val conversationId: String)

object HomeNotePolicy {
    const val MAX_CODE_POINTS = 4000

    fun bounded(text: String): String {
        val points = text.codePointCount(0, text.length)
        return if (points <= MAX_CODE_POINTS) text
        else text.substring(0, text.offsetByCodePoints(0, MAX_CODE_POINTS))
    }

    /** R3-42: deleting a note affects ONLY the note record for this account
     *  and conversation. The conversation, its messages and any saved items
     *  are out of scope by construction. */
    fun deleteScope(conversationId: String, accountKey: String): HomeNoteDeleteScope =
        HomeNoteDeleteScope(accountKey = accountKey, conversationId = conversationId)
}

/** Minimal saved-item reference used by follow-ups (R3-07/08) and the
 *  proposed SavedItemStore. References only - no bytes are copied. */
enum class HomeSavedItemKind { Message, Attachment, Link }

data class HomeSavedItemRef(
    val id: String,
    val conversationId: String,
    val messageId: String,
    val kind: HomeSavedItemKind,
    /** Set only for Attachment kind; the attachment is referenced by id,
     *  never downloaded or duplicated here. */
    val attachmentId: String? = null,
)
