package com.example.chat.ui.settings.conversation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.example.chat.ui.theme.LocalThemeConfig

/** Kept in the actual screen and directly hosted by the visibility regression tests. */
@Composable
internal fun ConversationHeaderIdentityWarning(hideAvatar: Boolean, hideName: Boolean) {
    if (hideAvatar && hideName) Text(
        androidx.compose.ui.res.stringResource(com.example.chat.R.string.conv_ui_you_will_not_see_who_you_are_3ef955),
        modifier = Modifier.testTag("conversation-header-identity-warning"),
        color = Color(LocalThemeConfig.current.primaryTextColor), fontSize = 14.sp,
    )
}
