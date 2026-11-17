package com.example.chat.data.services

/** One native generation. Admission and Stop have one linearization lock. A read admitted
 * before Stop is in flight even if the OS schedules the native read call just after the tap.
 * Never hold this lock through a blocking AudioRecord.read: Send must stay non-blocking. */
internal class VoiceCaptureBoundary {
    private var accepting = false
    private var reading = false
    @Synchronized fun start() { accepting = true }
    @Synchronized fun requestStop() { accepting = false }
    @Synchronized fun canStartRead(): Boolean = accepting
    @Synchronized fun tryBeginRead(): Boolean {
        if (!accepting || reading) return false
        reading = true
        return true
    }
    @Synchronized fun endRead() { reading = false }
    fun keepCompletedRead(readGeneration: Long, currentGeneration: Long, paused: Boolean): Boolean =
        !paused && readGeneration == currentGeneration
}
