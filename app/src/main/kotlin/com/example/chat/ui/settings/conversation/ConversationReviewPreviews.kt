package com.example.chat.ui.settings.conversation

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.chat.core.templates.ComposerActionPlacement
import com.example.chat.core.templates.ComposerTemplate
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.chats.detail.ChatEntryBar
import com.example.chat.ui.chats.detail.ComposerColors
import com.example.chat.ui.kit.ChatyBubbleContainer
import com.example.chat.ui.theme.ChatyTheme

/** Source fixtures only. These are real shared renderers, not rendered/screenshot-tested evidence. */
@Composable
private fun ConversationRendererFixture(dark: Boolean = false, rtl: Boolean = false,
    style: com.example.chat.core.theme.BubbleStyleId = com.example.chat.core.theme.BubbleStyleId.rounded) {
    val theme = if (dark) ThemePresets.monochromeDark else ThemePresets.monochromeLight
    ChatyTheme(config = theme) {
        CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            Column(Modifier.padding(16.dp)) {
                ChatyBubbleContainer(styleId = style,
                    isMe = false, fillColor = Color(theme.incomingBubbleColor), accentColor = Color(theme.accentColor)) {
                    androidx.compose.material3.Text("Sample conversation", color = Color(theme.incomingTextColor))
                }
                ChatEntryBar(entryStyle = "stock", colors = ComposerColors.resolve(theme),
                    template = ComposerTemplate(ComposerActionPlacement.integrated), theme = theme,
                    text = "Sample message", onTextChange = {}, onSend = {})
            }
        }
    }
}

@Preview(name = "Conversation light", widthDp = 360, showBackground = true)
@Composable private fun ConversationLightFixture() = ConversationRendererFixture()

@Preview(name = "Conversation dark", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable private fun ConversationDarkFixture() = ConversationRendererFixture(dark = true)

@Preview(name = "Conversation RTL", widthDp = 360, locale = "ar", showBackground = true)
@Composable private fun ConversationRtlFixture() = ConversationRendererFixture(rtl = true)

@Preview(name = "Conversation 200 percent", widthDp = 320, fontScale = 2f, showBackground = true)
@Composable private fun ConversationLargeTextFixture() = ConversationRendererFixture()

// Four additional source style fixtures. Not rendered or accepted as screenshot evidence.
@Preview(name = "Bubble stock", widthDp = 360, showBackground = true)
@Composable private fun BubbleStockFixture() = ConversationRendererFixture(style = com.example.chat.core.theme.BubbleStyleId.stock)
@Preview(name = "Bubble rounded", widthDp = 360, showBackground = true)
@Composable private fun BubbleRoundedFixture() = ConversationRendererFixture(style = com.example.chat.core.theme.BubbleStyleId.rounded)
@Preview(name = "Bubble transparent", widthDp = 360, showBackground = true)
@Composable private fun BubbleTransparentFixture() = ConversationRendererFixture(style = com.example.chat.core.theme.BubbleStyleId.transparent)
@Preview(name = "Bubble crayon 200 percent", widthDp = 320, fontScale = 2f, showBackground = true)
@Composable private fun BubbleCrayonFixture() = ConversationRendererFixture(style = com.example.chat.core.theme.BubbleStyleId.samyadeepCrayonAlt)
