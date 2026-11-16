package com.example.chat.data.services

/** Values must come from one device boot and a monotonic clock, not civil time. */
fun interface LockCooldownClock {
    fun read(): LockCooldownInstant
}

data class LockCooldownInstant(val elapsedMillis: Long, val bootCount: Long)
