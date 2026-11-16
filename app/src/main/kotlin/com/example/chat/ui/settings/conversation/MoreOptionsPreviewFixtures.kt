package com.example.chat.ui.settings.conversation

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.example.chat.core.theme.BubbleStyleId
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.theme.ChatyTheme

@Composable private fun ExtraFixture(dark: Boolean = false) {
    val theme = if (dark) ThemePresets.monochromeDark else ThemePresets.monochromeLight
    val foreground = Color(theme.primaryTextColor)
    val surface = Color(theme.surfaceColor)
    val accent = Color(theme.accentColor)
    ChatyTheme(theme) {
        MoreOptionsPreview(MoreOptionsPreviewState(
            bubbleStyle = BubbleStyleId.stock, incomingFill = Color(theme.incomingBubbleColor),
            emojiHeader = surface, emojiIcons = foreground, emojiBackground = surface,
            primaryText = foreground, secondaryText = Color(theme.secondaryTextColor),
            participantColor = accent, linkColor = Color(theme.linkColor), voiceButton = accent,
            voiceProgress = accent, infoBackground = surface, infoText = foreground,
        ))
    }
}
@Preview(name = "Extras light", widthDp = 360, showBackground = true)
@Composable private fun ExtrasLight() = ExtraFixture()
@Preview(name = "Extras dark", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable private fun ExtrasDark() = ExtraFixture(dark = true)
@Preview(name = "Extras large font", widthDp = 320, fontScale = 2f, showBackground = true)
@Composable private fun ExtrasLargeText() = ExtraFixture()
@Preview(name = "Extras RTL", widthDp = 360, locale = "ar", showBackground = true)
@Composable private fun ExtrasRtl() = ExtraFixture()
@Preview(name = "Extras tablet", widthDp = 600, showBackground = true)
@Composable private fun ExtrasTablet() = ExtraFixture()
