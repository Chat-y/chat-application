package com.example.chat.ui.calls

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.chat.ui.theme.LocalAppColors

/** Passive warning only. Never changes camera, audio, routing or system battery settings. */
@Composable
internal fun CallLowBatteryWarning(callId: String, isVideo: Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var lowBattery by remember(callId) { mutableStateOf(false) }
    var dismissed by rememberSaveable(callId) { mutableStateOf(false) }
    DisposableEffect(context, callId, isVideo) {
        if (!isVideo) return@DisposableEffect onDispose { }
        var attached = true
        fun update(intent: Intent?) {
            if (!attached) return
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
            // Unknown/malformed readings never become a low-battery claim.
            lowBattery = level >= 0 && scale > 0 && level <= scale &&
                level.toLong() * 100L <= scale.toLong() * 15L && plugged == 0
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { update(intent) }
        }
        var registered = false
        try {
            val initial = androidx.core.content.ContextCompat.registerReceiver(context, receiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
            registered = true
            update(initial)
        } catch (_: Exception) { lowBattery = false }
        onDispose {
            attached = false
            if (registered) runCatching { context.unregisterReceiver(receiver) }
        }
    }
    if (isVideo && lowBattery && !dismissed) {
        val colors = LocalAppColors.current
        Surface(color = colors.surface, contentColor = colors.foreground) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }) {
                Text("Battery is low. Video calls use more power. Consider charging or switching to audio.")
                TextButton(onClick = { dismissed = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Dismiss") }
            }
        }
    }
}
