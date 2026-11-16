package com.example.chat.platform

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.example.chat.data.services.LockCooldownClock
import com.example.chat.data.services.LockCooldownInstant

/** Missing boot metadata is an error, never an assumed shared boot. */
class AndroidLockCooldownClock(context: Context) : LockCooldownClock {
    private val resolver = context.applicationContext.contentResolver
    override fun read(): LockCooldownInstant {
        val boot = Settings.Global.getInt(resolver, Settings.Global.BOOT_COUNT).toLong()
        check(boot >= 0) { "Boot metadata unavailable." }
        return LockCooldownInstant(SystemClock.elapsedRealtime(), boot)
    }
}
