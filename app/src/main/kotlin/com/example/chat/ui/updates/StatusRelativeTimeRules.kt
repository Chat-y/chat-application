package com.example.chat.ui.updates

import java.time.Duration
import java.time.Instant

/** Presentation only; never use relative labels to decide status expiry or access. */
internal object StatusRelativeTimeRules {
    enum class Unit { JUST_NOW, MINUTES, HOURS, DAYS }
    data class Bucket(val unit: Unit, val amount: Long = 0)

    /** Bounded wait only, not access authorization. Recheck wall time after every wait. */
    fun expiryWaitMillis(expiresAt: Instant, now: Instant): Long {
        if (!expiresAt.isAfter(now)) return 0L
        val remaining = Duration.between(now, expiresAt)
        // Compare before converting to milliseconds, which can overflow for malformed extreme dates.
        return if (remaining.seconds >= 60L) 60_000L else remaining.toMillis().coerceAtLeast(1L)
    }

    fun bucket(value: Instant, now: Instant): Bucket {
        if (value.isAfter(now)) return Bucket(Unit.JUST_NOW)
        val difference = Duration.between(value, now)
        return when {
            difference.seconds < 60 -> Bucket(Unit.JUST_NOW)
            difference.seconds < 3600 -> Bucket(Unit.MINUTES, difference.toMinutes())
            difference.seconds < 86400 -> Bucket(Unit.HOURS, difference.toHours())
            else -> Bucket(Unit.DAYS, difference.toDays())
        }
    }
}
