package com.example.chat.core.audio

// Previous implementation retained: /** Process-local debug data only. No disk, network, device names/addresses or external export. */
/** Process-local debug data. No automatic disk/network/export; explicit share uses an allowlisted payload. */
internal class AudioDiagnosticBuffer(private val enabled: Boolean, private val capacity: Int = 200) {
    init { require(capacity > 0) }
    private val events = java.util.ArrayDeque<AudioPolicyDiagnosticEvent>()
    private var failures = 0L
    @Synchronized fun snapshot(): List<AudioPolicyDiagnosticEvent> = events.toList()
    @Synchronized fun failureCount(): Long = failures
    @Synchronized fun record(event: AudioPolicyDiagnosticEvent, routeFailure: Boolean = false) {
        if (!enabled) return
        if (events.size == capacity) events.removeFirst()
        events.addLast(event.copy(error = event.error?.let { "Audio operation failed" }))
        if (routeFailure && failures < Long.MAX_VALUE) failures++
    }
}
