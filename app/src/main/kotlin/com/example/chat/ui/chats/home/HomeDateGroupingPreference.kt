package com.example.chat.ui.chats.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.chat.core.preferences.ChatyPreferencesController

internal const val HOME_GROUP_CHATS_BY_DATE = "home_group_chats_by_date"
internal fun homeDateGroupingEnabled(values: Map<String,Any?>): Boolean = values[HOME_GROUP_CHATS_BY_DATE] as? Boolean ?: false

@Composable
internal fun rememberHomeDateGrouping(preferences: ChatyPreferencesController?): Boolean {
    var enabled by remember(preferences) { mutableStateOf(preferences?.let { homeDateGroupingEnabled(it.gbFeatures) } ?: false) }
    DisposableEffect(preferences) {
        // Notify callbacks may arrive off-main; never publish after disposal.
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        var attached = true
        val listener: () -> Unit = {
            val refresh = { if (attached) enabled = preferences?.let { homeDateGroupingEnabled(it.gbFeatures) } ?: false }
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) refresh() else main.post { refresh() }
        }
        preferences?.addListener(listener); listener()
        onDispose { attached = false; preferences?.removeListener(listener) }
    }
    return enabled
}
