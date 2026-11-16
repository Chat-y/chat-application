package com.example.chat.ui.chats.home

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * R3-01..08, R3-45, R3-46: pure model and rules for personal follow-ups.
 *
 * Decision 2A: follow-ups are LOCAL-ONLY, per account, on-device only. Nothing
 * here syncs, sends a chat message, assigns work to another person, or touches
 * the server task API. Persistence is a separate REVIEW-ONLY proposal
 * (FollowUpStore); this file contains no storage, alarm, or network code.
 *
 * A follow-up is never a bookmark: saved state lives in the proposed
 * SavedItemStore and is only REFERENCED here by [HomeFollowUp.savedItemId], so
 * "remove reminder, keep saved" (R3-08) and "convert saved into follow-up"
 * (R3-07) stay distinct operations on separate records.
 */

/** Upper bounds mirror HomeScratchpadPolicy's defensive style. */
const val HOME_FOLLOW_UP_TITLE_MAX_CODE_POINTS = 200
const val HOME_FOLLOW_UP_NOTE_MAX_CODE_POINTS = 1000

enum class HomeFollowUpStatus { Active, Completed }

/**
 * One personal follow-up. [dueAt] is the intended INSTANT; [dueZoneId] records
 * the timezone the user picked it in (R3-45). Travel keeps the instant: the
 * resolved local label is re-derived from the display zone, never silently
 * shifted. A null [dueAt] means "no reminder" - the follow-up still exists.
 */
data class HomeFollowUp(
    val id: String,
    val conversationId: String,
    /** Set only when created from a saved item (R3-07). Pure reference. */
    val savedItemId: String? = null,
    val title: String,
    val note: String = "",
    val dueAt: Instant? = null,
    val dueZoneId: ZoneId? = null,
    val status: HomeFollowUpStatus = HomeFollowUpStatus.Active,
    val createdAt: Instant,
    val updatedAt: Instant,
    val completedAt: Instant? = null,
)

/** Creation input. No id/instant defaults are invented here; the caller
 *  supplies id and clock so this stays deterministic and testable. */
data class HomeFollowUpDraft(
    val conversationId: String,
    val savedItemId: String? = null,
    val title: String,
    val note: String = "",
    val dueAt: Instant? = null,
    val dueZoneId: ZoneId? = null,
)

/** R3-46: when an active follow-up already exists for the same target, the
 *  user chooses explicitly. Silent overwrite and duplicate alarms are never
 *  produced by this model. */
enum class HomeFollowUpDuplicateChoice { OpenExisting, CreateAnother, Cancel }

/** Undo snapshot for R3-06. In-memory only; never persisted or logged with
 *  content. Restoring it is the ONLY undo path. */
data class HomeFollowUpUndo(val followUpId: String, val previous: HomeFollowUp)

object HomeFollowUpPolicy {

    fun boundedTitle(raw: String): String = bound(raw, HOME_FOLLOW_UP_TITLE_MAX_CODE_POINTS)
    fun boundedNote(raw: String): String = bound(raw, HOME_FOLLOW_UP_NOTE_MAX_CODE_POINTS)

    private fun bound(text: String, maxCodePoints: Int): String {
        val points = text.codePointCount(0, text.length)
        return if (points <= maxCodePoints) text
        else text.substring(0, text.offsetByCodePoints(0, maxCodePoints))
    }

    /** R3-01: draft linked to a conversation. Blank titles are rejected so a
     *  follow-up is never created with an invisible label. */
    fun draftForConversation(conversationId: String, title: String): HomeFollowUpDraft? {
        val bounded = boundedTitle(title).trim()
        if (conversationId.isBlank() || bounded.isEmpty()) return null
        return HomeFollowUpDraft(conversationId = conversationId, title = bounded)
    }

    /** R3-07: draft linked to an existing saved item. The saved item keeps its
     *  own record; this only points at it. A bookmark does not become a
     *  notification unless the caller also sets a due time. */
    fun draftFromSavedItem(
        savedItemId: String,
        conversationId: String,
        title: String,
        dueAt: Instant? = null,
        dueZoneId: ZoneId? = null,
    ): HomeFollowUpDraft? {
        val bounded = boundedTitle(title).trim()
        if (savedItemId.isBlank() || conversationId.isBlank() || bounded.isEmpty()) return null
        return HomeFollowUpDraft(
            conversationId = conversationId,
            savedItemId = savedItemId,
            title = bounded,
            dueAt = dueAt,
            dueZoneId = dueZoneId,
        )
    }

    fun materialize(draft: HomeFollowUpDraft, id: String, now: Instant): HomeFollowUp =
        HomeFollowUp(
            id = id,
            conversationId = draft.conversationId,
            savedItemId = draft.savedItemId,
            title = draft.title,
            note = boundedNote(draft.note),
            dueAt = draft.dueAt,
            dueZoneId = draft.dueZoneId,
            createdAt = now,
            updatedAt = now,
        )

    /** R3-46: the active follow-up for the same conversation/saved target, if
     *  any. The UI offers Open existing / Create another from this; this
     *  function never overwrites the existing record's deadline. */
    fun findActiveDuplicate(
        all: List<HomeFollowUp>,
        conversationId: String,
        savedItemId: String?,
    ): HomeFollowUp? = all.firstOrNull {
        it.status == HomeFollowUpStatus.Active &&
            it.conversationId == conversationId &&
            it.savedItemId == savedItemId
    }

    /** R3-03: snooze moves the due instant on the SAME record. Updating in
     *  place means the reminder scheduler (proposed) re-arms one reminder;
     *  no duplicate alarm can be created from this model. */
    fun snooze(followUp: HomeFollowUp, newDueAt: Instant, newZoneId: ZoneId?, now: Instant): HomeFollowUp =
        followUp.copy(dueAt = newDueAt, dueZoneId = newZoneId, updatedAt = now)

    /** R3-08: clear the reminder while preserving the record and its saved
     *  link. The saved item itself lives in another store and is untouched. */
    fun clearReminder(followUp: HomeFollowUp, now: Instant): HomeFollowUp =
        followUp.copy(dueAt = null, dueZoneId = null, updatedAt = now)

    /** R3-06: complete updates ONLY the personal completion flag. Read
     *  receipts, archive state and the contact are out of scope by
     *  construction. Returns the completed record plus an undo snapshot. */
    fun complete(followUp: HomeFollowUp, now: Instant): Pair<HomeFollowUp, HomeFollowUpUndo> =
        followUp.copy(status = HomeFollowUpStatus.Completed, completedAt = now, updatedAt = now) to
            HomeFollowUpUndo(followUp.id, followUp)

    fun undoComplete(undo: HomeFollowUpUndo, current: HomeFollowUp, now: Instant): HomeFollowUp =
        if (current.id != undo.followUpId) current
        else undo.previous.copy(updatedAt = now)

    /** R3-04: follow-ups whose explicit due instant falls on the active
     *  account's local TODAY (including earlier-today times not yet done).
     *  Message timestamps and unread state are never used. */
    fun dueToday(all: List<HomeFollowUp>, now: Instant, zone: ZoneId): List<HomeFollowUp> {
        val today = now.atZone(zone).toLocalDate()
        return all.filter {
            it.status == HomeFollowUpStatus.Active &&
                it.dueAt != null && it.dueAt.atZone(zone).toLocalDate() == today
        }.sortedBy { it.dueAt }
    }

    /** R3-05: overdue = explicit deadline before the start of local today,
     *  still active. Shown separately from the due-today queue; no inference
     *  from message age. */
    fun overdue(all: List<HomeFollowUp>, now: Instant, zone: ZoneId): List<HomeFollowUp> {
        val today = now.atZone(zone).toLocalDate()
        return all.filter {
            it.status == HomeFollowUpStatus.Active && it.dueAt != null &&
                it.dueAt.atZone(zone).toLocalDate().isBefore(today)
        }.sortedBy { it.dueAt }
    }

    /** R3-45: resolved local due label. The stored instant is preserved; only
     *  the display is resolved into [displayZone]. The stored creation zone is
     *  shown when it differs, so a timezone change is visible, never silent. */
    fun resolvedDueLabel(
        followUp: HomeFollowUp,
        displayZone: ZoneId,
        locale: Locale = Locale.getDefault(),
    ): String? {
        val due = followUp.dueAt ?: return null
        val local: ZonedDateTime = due.atZone(displayZone)
        val base = DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm", locale).format(local)
        val storedZone = followUp.dueZoneId
        return if (storedZone != null && storedZone != displayZone) {
            "$base (set in ${storedZone.id})"
        } else base
    }
}
