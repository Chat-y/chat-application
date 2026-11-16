package com.example.chat.domain.models

import java.time.Instant

/** Exact port of the CallRecord model in lib/domain/models/other_models.dart. */

enum class CallType { voice, video }

enum class CallDirection { incoming, outgoing, missed }

/** Terminal outcome is separate from direction. Unknown older records remain unknown. */
enum class CallOutcome { ended, declined, failed, cancelled, busy, missed }

internal fun callOutcomeFromStatus(status: String): CallOutcome? = when (status) {
    "answered", "ended" -> CallOutcome.ended
    "declined" -> CallOutcome.declined
    "failed" -> CallOutcome.failed
    "cancelled", "canceled" -> CallOutcome.cancelled
    "busy" -> CallOutcome.busy
    "missed" -> CallOutcome.missed
    else -> null
}

/** A failed zero-duration server entry does not prove the call never connected. */
internal fun callConnectionEvidence(status: String, durationSeconds: Long): Boolean? = when {
    durationSeconds > 0 || status == "answered" -> true
    status in setOf("missed", "declined", "cancelled", "canceled", "busy") -> false
    else -> null
}

data class CallRecord(
    val id: String,
    val callerId: String,
    val participantIds: List<String>,
    val type: CallType,
    val direction: CallDirection,
    val timestamp: Instant,
    val durationSeconds: Long,
    val isEncrypted: Boolean = true,
    val remoteDisplayName: String? = null,
    val remoteUsername: String? = null,
    val outcome: CallOutcome? = null,
    /** null means not recorded, not evidence of no connection. */
    val wasConnected: Boolean? = null,
)
