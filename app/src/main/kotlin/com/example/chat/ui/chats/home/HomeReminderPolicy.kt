package com.example.chat.ui.chats.home

import java.time.Instant

/**
 * R3-02, R3-39, R3-40, R3-47: pure reminder/notification rules for personal
 * follow-ups.
 *
 * Decision 3A: reminders get their OWN notification category, an in-app
 * explanation before the system permission prompt, and a seen-key so each
 * reminder tap acts once. In-app scheduled reminders only; NO exact alarms.
 * Reminders are best-effort while the app process lives.
 *
 * This file contains no platform code. Registering the channel (shared
 * NotificationChannelManager), scheduling, posting, and persisting seen-keys
 * are all part of the separate REVIEW-ONLY proposal; only the constants and
 * pure decisions live here so the wiring cannot invent its own semantics.
 */
object HomeReminderPolicy {

    /** R3-39: dedicated channel. Snoozing/muting reminders can never mute
     *  chat alerts because the categories are separate by construction. */
    const val REMINDER_CHANNEL_ID = "home_personal_reminders"
    const val REMINDER_CHANNEL_NAME = "Personal reminders"
    const val REMINDER_CHANNEL_DESCRIPTION =
        "Reminders you set for yourself about chats. These are separate from message notifications."

    /** R3-40: shown once before the platform permission request. Personal
     *  follow-ups keep working without the permission; the reminder is then
     *  in-app only. No repeated nagging: [shouldShowExplanation] is false
     *  once the explanation was seen or the permission already exists. */
    const val REMINDER_PERMISSION_EXPLANATION =
        "Reminders can notify you when a follow-up is due. " +
        "Without notification permission your follow-ups still work, " +
        "but you will only see them inside the app."

    fun shouldShowExplanation(explanationSeen: Boolean, permissionGranted: Boolean): Boolean =
        !explanationSeen && !permissionGranted

    /** R3-02: a reminder time must be a real future instant. Marking unread
     *  is a different feature and is never used as a reminder here. */
    fun isValidReminderTime(dueAt: Instant, now: Instant): Boolean = dueAt.isAfter(now)

    /** Best-effort in-process scheduling: the next active due instant after
     *  [now]. The proposed scheduler re-computes this after every change; a
     *  process death simply loses the wake, per decision 3A. */
    fun nextDueInstant(followUps: List<HomeFollowUp>, now: Instant): Instant? =
        followUps.filter {
            it.status == HomeFollowUpStatus.Active && it.dueAt != null && it.dueAt.isAfter(now)
        }.minByOrNull { it.dueAt!! }?.dueAt

    /** Active follow-ups whose due instant has passed and which an in-process
     *  checker should surface now. */
    fun dueNow(followUps: List<HomeFollowUp>, now: Instant): List<HomeFollowUp> =
        followUps.filter {
            it.status == HomeFollowUpStatus.Active && it.dueAt != null && !it.dueAt.isAfter(now)
        }.sortedBy { it.dueAt }
}

/**
 * R3-47: one reminder tap = one action. [eventId] is a stable id assigned
 * when the reminder is posted; the proposed seen-key store remembers acted
 * ids. Pure decision logic only: [isDuplicate] against the seen set, and
 * [isBindingValid] so a stale notification from another account session can
 * never open the wrong target.
 */
data class HomeReminderTapEvent(
    val eventId: String,
    val followUpId: String,
    val accountKey: String,
)

object HomeReminderTapPolicy {
    fun isDuplicate(eventId: String, seenEventIds: Set<String>): Boolean =
        eventId in seenEventIds

    fun isBindingValid(event: HomeReminderTapEvent, activeAccountKey: String?): Boolean =
        activeAccountKey != null && event.accountKey == activeAccountKey

    /** The single action a non-duplicate, correctly-bound tap may perform:
     *  open the follow-up's conversation target. Repeated taps after the
     *  seen-key is recorded are no-ops, never repeated read actions. A blank
     *  event id can never act: it has no stable identity to deduplicate on. */
    fun mayAct(event: HomeReminderTapEvent, activeAccountKey: String?, seenEventIds: Set<String>): Boolean =
        event.eventId.isNotBlank() &&
            !isDuplicate(event.eventId, seenEventIds) &&
            isBindingValid(event, activeAccountKey)
}
