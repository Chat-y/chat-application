package com.example.chat.ui.settings.conversation

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

internal fun conversationUsesTwoPanes(widthDp: Float): Boolean = widthDp.isFinite() && widthDp >= 840f

/** Compact: one ordered scroll. Wide: independent preview/control scrolls, never a stretched preview. */
@Composable
internal fun ConversationAdaptiveSettingsBody(
    modifier: Modifier = Modifier,
    compactPinnedPreview: Boolean = false,
    preview: @Composable ColumnScope.() -> Unit,
    controls: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier) {
        if (conversationUsesTwoPanes(maxWidth.value)) {
            Row(Modifier.fillMaxWidth().testTag("conversation-settings-two-pane")) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)
                    .testTag("conversation-settings-preview-pane"), content = preview)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)
                    .testTag("conversation-settings-controls-pane"), content = controls)
            }
        } else if (compactPinnedPreview) {
            Column(Modifier.align(Alignment.TopCenter).widthIn(max = 600.dp).fillMaxWidth()
                .testTag("conversation-settings-compact-pinned")) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), content = preview)
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), content = controls)
            }
        } else {
            Column(Modifier.align(Alignment.TopCenter).widthIn(max = 600.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)
                .testTag("conversation-settings-one-pane")) { preview(); controls() }
        }
    }
}
