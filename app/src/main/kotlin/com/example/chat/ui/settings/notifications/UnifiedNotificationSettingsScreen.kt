package com.example.chat.ui.settings.notifications

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.chat.core.preferences.ChatyPreferencesController

/** Both legacy destinations resolve here; exactly one editor for every activity-toast setting. */
@Composable
fun UnifiedNotificationSettingsScreen(
    preferences: ChatyPreferencesController,
    onBack: () -> Unit,
    initialActivityTab: Boolean = false,
    bridge: DynamicIslandBridge? = null,
    triggerEventNotification: TriggerEventNotification = { _, _, _ -> },
) {
    var activity by rememberSaveable(preferences.scopeGeneration, initialActivityTab) { mutableStateOf(initialActivityTab) }
    val colors = com.example.chat.ui.theme.LocalAppColors.current
    // Consume the top inset here so the tab row is safe and child headers do not apply it twice.
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))) {
        TabRow(selectedTabIndex = if (activity) 1 else 0, containerColor = colors.surface, contentColor = colors.primary) {
            Tab(selected = !activity, onClick = { activity = false }, text = { Text("General") })
            Tab(selected = activity, onClick = { activity = true }, text = { Text("Activity toasts") })
        }
        Box(Modifier.weight(1f)) {
            if (activity) NotificationToastSettingsScreen(preferences = preferences, onBack = onBack)
            else NotificationSettingsPage(preferences = preferences, onBack = onBack, bridge = bridge,
                triggerEventNotification = triggerEventNotification)
        }
    }
}
