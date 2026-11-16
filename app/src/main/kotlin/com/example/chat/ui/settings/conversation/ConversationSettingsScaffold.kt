package com.example.chat.ui.settings.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.example.chat.ui.kit.ChatyAppBar
import com.example.chat.ui.theme.LocalThemeConfig

/** Common back/header/surface shell. Screens retain their own scroll, preview and snackbar scopes. */
@Composable
internal fun ConversationSettingsScaffold(
    title: String,
    onBack: () -> Unit,
    titleTextStyle: androidx.compose.ui.text.TextStyle? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Color(LocalThemeConfig.current.backgroundColor))) {
        ChatyAppBar(title = title, onBack = onBack, titleModifier = Modifier.semantics { heading() }, titleTextStyle = titleTextStyle)
        content()
    }
}
