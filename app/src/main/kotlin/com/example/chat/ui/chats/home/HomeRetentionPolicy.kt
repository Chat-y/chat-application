package com.example.chat.ui.chats.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.chat.ui.kit.ChatyConfirmationDialog
import com.example.chat.ui.kit.ChatyTextButton

/**
 * R3-49 (decision 5A): a DISPLAY-ONLY workspace data-retention policy
 * surface. It states what is stored and how long, and performs NO deletion,
 * purge, or sign-out erase. Actual retention rules are a separate decision.
 *
 * Every statement below must describe CURRENT truth. When a proposed store
 * lands, its statement is updated in the same change that wires the store -
 * never before, so the screen cannot promise behavior that does not exist.
 */

data class HomeRetentionStatement(
    /** Short area name, e.g. "Chat folders". */
    val area: String,
    /** What is stored, in plain words. */
    val stored: String,
    /** What happens on sign-out / account removal, in plain words. */
    val onSignOut: String,
)

object HomeRetentionPolicy {

    /** Current truth at the pinned baseline + Home patch stack. */
    fun statements(): List<HomeRetentionStatement> = listOf(
        HomeRetentionStatement(
            area = "Chat folders",
            stored = "Folder names and memberships are stored on this device only, one file per account, not synced.",
            onSignOut = "Signing out or removing an account currently leaves this file on the device. No automatic cleanup runs yet.",
        ),
        HomeRetentionStatement(
            area = "Home scratchpad",
            stored = "Scratchpad text is kept in memory only. It is not saved, synced or sent.",
            onSignOut = "It is lost when Home closes, the app restarts or the account changes.",
        ),
        HomeRetentionStatement(
            area = "Personal follow-ups, saved items and private notes",
            stored = "Not stored yet. When introduced, they are planned as device-only, per-account, encrypted records.",
            onSignOut = "No cleanup runs yet; retention rules will be decided separately before any deletion exists.",
        ),
        HomeRetentionStatement(
            area = "Search",
            stored = "Search matches chat titles and, for unlocked chats, the last loaded message. Locked chats match titles only. No message or note index is built or kept.",
            onSignOut = "Nothing search-related persists between sessions.",
        ),
    )
}

/** Display-only rendering of [HomeRetentionPolicy.statements]. No actions,
 *  no toggles, no erase paths. */
@Composable
fun HomeRetentionPolicyDialog(onDismiss: () -> Unit) {
    ChatyConfirmationDialog(
        onDismissRequest = onDismiss,
        title = { Text("Workspace data on this device") },
        text = {
            Column {
                Text("What the personal workspace stores on this device today. Nothing here deletes data.")
                HomeRetentionPolicy.statements().forEach { s ->
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        Text(s.area)
                        Text(s.stored)
                        Text(s.onSignOut)
                    }
                }
            }
        },
        confirmButton = { ChatyTextButton(onClick = onDismiss, label = "Close") },
    )
}
