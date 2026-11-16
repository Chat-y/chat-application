package com.example.chat.ui.calls

import com.example.chat.R
import com.example.chat.domain.models.CallOutcome
import com.example.chat.domain.models.CallRecord

/** Pure presentation seam: don't turn a sub-second connection into "No connection". */
internal fun callLogOutcomeText(call: CallRecord, text: (Int) -> String): String {
    val connection = when {
        call.durationSeconds > 0 -> "%d:%02d".format(call.durationSeconds / 60, call.durationSeconds % 60)
        call.wasConnected == true -> text(R.string.call_log_short_connected)
        call.wasConnected == false -> text(R.string.calls_ui_16)
        else -> text(R.string.call_log_connection_unknown)
    }
    val outcome = when (call.outcome) {
        CallOutcome.declined -> R.string.call_log_declined
        CallOutcome.failed -> R.string.call_log_failed
        CallOutcome.cancelled -> R.string.call_log_cancelled
        CallOutcome.busy -> R.string.call_log_busy
        CallOutcome.missed -> R.string.call_log_missed_outcome
        else -> null
    } ?: return connection
    return if (call.wasConnected == true || call.durationSeconds > 0) text(outcome) + " • " + connection
        else text(outcome)
}
