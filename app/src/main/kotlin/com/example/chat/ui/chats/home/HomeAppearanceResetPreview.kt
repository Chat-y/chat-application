package com.example.chat.ui.chats.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.chat.domain.models.HomePreferences
import com.example.chat.ui.kit.ChatyConfirmationDialog
import com.example.chat.ui.kit.ChatyTextButton

data class HomeAppearancePreviewField(val label: String, val differsFromDefault: Boolean)

/** R2-40: a proposed, deliberately narrow field set, not a reset transaction.
 * No toMap/updateHome/store API is used. Defaults come from the model, not copied constants.
 * Excludes identity, visibility, privacy, security, stories opt-in, folders and messages. */
fun homeAppearancePreviewFields(current: HomePreferences): List<HomeAppearancePreviewField> {
    val defaults = HomePreferences()
    return listOf(
        HomeAppearancePreviewField("Home style", current.homeStyle != defaults.homeStyle),
        HomeAppearancePreviewField("Avatar shape", current.avatarShape != defaults.avatarShape),
        HomeAppearancePreviewField("Text size", current.screenTextSize != defaults.screenTextSize),
        HomeAppearancePreviewField("Row text color", current.rowTextColor != defaults.rowTextColor),
        HomeAppearancePreviewField("Contact name color", current.rowContactNameColor != defaults.rowContactNameColor),
        HomeAppearancePreviewField("Unread counter color", current.unreadCounterColor != defaults.unreadCounterColor),
        HomeAppearancePreviewField("Unread counter text color", current.unreadCounterTextColor != defaults.unreadCounterTextColor),
    )
}

@Composable
fun HomeAppearanceResetPreview(current: HomePreferences, onDismiss: () -> Unit) {
    ChatyConfirmationDialog(
        onDismissRequest = onDismiss,
        title = { Text("Appearance reset preview") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Preview only. Nothing will be reset, and there is no Apply action.")
                Text("Proposed appearance fields compared with model defaults:")
                homeAppearancePreviewFields(current).forEach { field ->
                    Text(field.label + if (field.differsFromDefault) ": differs from default" else ": already default")
                }
                Text("All other settings are outside this proposal. Folders, messages, identity, privacy and security are untouched.")
            }
        },
        confirmButton = { ChatyTextButton(onClick = onDismiss, label = "Close preview") },
    )
}
