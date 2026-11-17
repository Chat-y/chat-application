package com.example.chat.ui.settings.conversation

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** Fixed event-category counts only. No color values, names, messages, storage or upload. */
internal fun recordHeaderDebugUsage(previous: Map<String, Int>, event: ActionBarEvent): Map<String, Int> {
    val key = when (event) {
        is ActionBarEvent.ToggleAvatar -> "avatar"
        is ActionBarEvent.ToggleName -> "name"
        is ActionBarEvent.ToggleCall -> "calls"
        is ActionBarEvent.ToggleStatus -> "status"
        is ActionBarEvent.PickColor -> "colour_${event.target.name}"
        ActionBarEvent.Reset -> "reset"
    }
    val count = previous[key] ?: 0
    return previous + (key to if (count < Int.MAX_VALUE) count + 1 else count)
}

@Composable
internal fun ConversationHeaderDebugUsage(usage: Map<String, Int>) {
    if (!com.example.chat.BuildConfig.DEBUG) return
    var expanded by remember { mutableStateOf(false) }
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    Column {
        // Previous stock text action:
        // TextButton(onClick = { expanded = !expanded }) {
        //             Text(resources.getString(com.example.chat.R.string.conv_proposal_debug_usage))
        //         }
        com.example.chat.ui.kit.ChatyTextButton(
            label = resources.getString(com.example.chat.R.string.conv_proposal_debug_usage),
            onClick = { expanded = !expanded },
        )
        if (expanded) {
            Text(resources.getString(com.example.chat.R.string.conv_proposal_debug_usage_scope))
            usage.toSortedMap().forEach { (key, count) ->
                Text(resources.getString(com.example.chat.R.string.conv_proposal_debug_usage_count, key, count))
            }
        }
    }
}
