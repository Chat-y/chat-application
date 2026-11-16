package com.example.chat.ui.camera

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Entry-owned system observation only. Never requests an exemption, wake lock or permission. */
@Composable
internal fun rememberCameraPowerSaveMode(): State<Boolean> {
    val context = LocalContext.current.applicationContext
    val power = remember(context) { context.getSystemService(PowerManager::class.java) }
    val state = remember(power) { mutableStateOf(runCatching { power?.isPowerSaveMode ?: false }.getOrDefault(false)) }
    DisposableEffect(context, power) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) {
                    state.value = runCatching { power?.isPowerSaveMode ?: false }.getOrDefault(state.value)
                }
            }
        }
        val registered = runCatching {
            val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            else {
                @Suppress("DEPRECATION")
                context.registerReceiver(receiver, filter)
            }
            true
        }.getOrDefault(false)
        onDispose { if (registered) runCatching { context.unregisterReceiver(receiver) } }
    }
    return state
}

internal fun shouldPauseCameraSegmentationForThermal(status: Int): Boolean =
    status >= PowerManager.THERMAL_STATUS_SEVERE

/** API29 thermal budget. Drops segmentation first, without silently changing capture settings. */
@Composable
internal fun rememberCameraThermalLimit(): State<Boolean> {
    val context = LocalContext.current.applicationContext
    val power = remember(context) { context.getSystemService(PowerManager::class.java) }
    val state = remember(power) { mutableStateOf(
        if (Build.VERSION.SDK_INT >= 29) runCatching {
            power?.currentThermalStatus?.let(::shouldPauseCameraSegmentationForThermal) ?: false
        }.getOrDefault(false) else false,
    ) }
    DisposableEffect(context, power) {
        if (Build.VERSION.SDK_INT >= 29 && power != null) {
            val listener = PowerManager.OnThermalStatusChangedListener { status ->
                state.value = shouldPauseCameraSegmentationForThermal(status)
            }
            val registered = runCatching { power.addThermalStatusListener(context.mainExecutor, listener); true }.getOrDefault(false)
            onDispose { if (registered) runCatching { power.removeThermalStatusListener(listener) } }
        } else onDispose { }
    }
    return state
}
