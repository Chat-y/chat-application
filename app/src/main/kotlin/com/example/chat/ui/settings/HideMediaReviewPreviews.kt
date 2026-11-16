package com.example.chat.ui.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GifBox
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.chat.R
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.chat.core.theme.ThemePresets
import com.example.chat.ui.theme.ChatyTheme

/** Offline row fixtures only: no preferences, marker writes, accounts or navigation. Not rendered evidence. */
@Composable
private fun HideMediaRowsFixture(dark: Boolean = false) {
    ChatyTheme(config = if (dark) ThemePresets.monochromeDark else ThemePresets.monochromeLight) {
        Surface {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Hide media row review")
                MediaVisibilitySwitch(Icons.Rounded.PhotoLibrary, "Photos", stringResource(R.string.privacy_media_photos_effect), false) {}
                MediaVisibilitySwitch(Icons.Rounded.VideoLibrary, "Videos", stringResource(R.string.privacy_media_videos_effect), true) {}
                MediaVisibilitySwitch(Icons.Rounded.GifBox, "GIFs", stringResource(R.string.privacy_media_gifs_effect), false) {}
            }
        }
    }
}

@Preview(name = "Hide media rows light", widthDp = 360, showBackground = true)
@Composable private fun HideMediaLightReview() = HideMediaRowsFixture()

@Preview(name = "Hide media rows dark", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable private fun HideMediaDarkReview() = HideMediaRowsFixture(dark = true)

@Preview(name = "Hide media rows 200 percent", widthDp = 320, fontScale = 2f, showBackground = true)
@Composable private fun HideMediaLargeTextReview() = HideMediaRowsFixture()
