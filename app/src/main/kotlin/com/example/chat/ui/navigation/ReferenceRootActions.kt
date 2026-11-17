package com.example.chat.ui.navigation

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import com.example.chat.domain.models.UserProfile
import com.example.chat.ui.chats.home.HomeAvatar
import com.example.chat.ui.chats.home.parseAvatarColorHex
import com.example.chat.ui.core.theme.ChatyColors

/** Account/global-search actions shared with the existing Home header.
 * Screen-specific menus remain with their existing screens; a blank slot is not a fake menu.
 */
@Composable
fun ReferenceRootActions(
    colors: ChatyColors,
    user: UserProfile?,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onAccountSwitcher: () -> Unit = onSettings,
    // TAS-SEARCH-1: the label follows the visible destination (e.g. "Search tasks").
    searchLabel: String = "Search chats and messages",
    showOverflow: Boolean = false,
) {
    val overflow = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Row {
        IconButton(onClick = onSearch) { com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.search, semanticLabel = searchLabel, color = colors.foreground) }
        IconButton(onClick = onAccountSwitcher) {
            HomeAvatar(initials = user?.avatarInitials.orEmpty(),
                color = parseAvatarColorHex(user?.avatarColorHex, colors.primary),
                size = 28f, shape = "circle", url = user?.avatarUrl)
        }
        if (showOverflow) androidx.compose.foundation.layout.Box {
            IconButton(onClick = { overflow.value = true }) {
                com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.moreOptions,
                    semanticLabel = "More options", color = colors.foreground)
            }
            androidx.compose.material3.DropdownMenu(expanded = overflow.value, onDismissRequest = { overflow.value = false }) {
                androidx.compose.material3.DropdownMenuItem(text = { androidx.compose.material3.Text("Settings") },
                    onClick = { overflow.value = false; onSettings() })
            }
        }
    }
}
