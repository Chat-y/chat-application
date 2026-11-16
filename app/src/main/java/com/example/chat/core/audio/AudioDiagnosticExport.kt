package com.example.chat.core.audio

/** Explicit share payload only. Never exports free-form messages, errors, device/contact identifiers. */
internal fun formatAudioDiagnosticExport(events: List<AudioPolicyDiagnosticEvent>): String {
    val snapshot = events.takeLast(200)
    return buildString {
        append("Audio diagnostics (debug)\nEvents: ").append(snapshot.size)
        snapshot.forEach { event ->
            append('\n').append(event.timestampMillis)
            append(" | ").append(event.useCase.name)
            append(" | ").append(event.state.name)
            append(" | ").append(event.route?.name ?: "unknown")
            if (event.error != null) append(" | Audio operation failed")
        }
    }
}
