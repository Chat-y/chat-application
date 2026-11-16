package com.example.chat.ui.settings.conversation

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.theme.ChatyTheme

/** Five new source fixtures, not rendered or device-verified. */
@Preview(name = "Header light", widthDp = 360, showBackground = true)
@Composable private fun HeaderLight() {
    ChatyTheme(ThemePresets.monochromeLight) { ConversationHeaderPreview() }
}
@Preview(name = "Header dark", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable private fun HeaderDark() {
    ChatyTheme(ThemePresets.monochromeDark) { ConversationHeaderPreview() }
}
@Preview(name = "Header 200 percent", widthDp = 320, fontScale = 2f, showBackground = true)
@Composable private fun HeaderLargeText() {
    ChatyTheme(ThemePresets.monochromeLight) { ConversationHeaderPreview(longSampleName = true) }
}
@Preview(name = "Header RTL", widthDp = 360, locale = "ar", showBackground = true)
@Composable private fun HeaderRtl() {
    ChatyTheme(ThemePresets.monochromeLight) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { ConversationHeaderPreview() }
    }
}
@Preview(name = "Header hidden controls", widthDp = 360, showBackground = true)
@Composable private fun HeaderHiddenControls() {
    ChatyTheme(ThemePresets.monochromeLight) {
        ConversationHeaderPreview(hideProfilePicture = true, hideContactName = true,
            hideCallButton = true, disableContactStatus = true)
    }
}
