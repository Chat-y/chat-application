package com.example.chat.ui.kit

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

internal fun systemAnimationsAllowed(scale: Float): Boolean = scale.isFinite() && scale > 0f

/** Read-only system preference. Observer is removed with the composition; never changes system settings. */
@Composable
internal fun rememberSystemAnimationsAllowed(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun read(): Boolean = try {
        systemAnimationsAllowed(Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f))
    } catch (_: SecurityException) { false }
    var allowed by remember(resolver) { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { allowed = read() }
        }
        var registered = false
        try {
            resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
            registered = true
        } catch (_: SecurityException) { allowed = false }
        allowed = if (registered) read() else false
        onDispose { if (registered) resolver.unregisterContentObserver(observer) }
    }
    return allowed
}
