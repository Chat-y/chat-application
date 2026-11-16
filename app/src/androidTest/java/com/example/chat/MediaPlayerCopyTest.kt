package com.example.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

/** MV-R4-042 default-English resource cases authored only, NOT RUN. */
class MediaPlayerCopyTest {
    @get:Rule val compose = createComposeRule()
    @Test fun nowPlayingUsesResource() {
        compose.setContent { MaterialTheme { Text(stringResource(R.string.media_player_now_playing)) } }
        compose.onNodeWithText("Now Playing").assertIsDisplayed()
    }
    @Test fun oneTrackUsesSingular() {
        compose.setContent { MaterialTheme { Text(pluralStringResource(R.plurals.media_player_queue_tracks, 1, 1)) } }
        compose.onNodeWithText("1 track").assertIsDisplayed()
    }
    @Test fun threeTracksUsePlural() {
        compose.setContent { MaterialTheme { Text(pluralStringResource(R.plurals.media_player_queue_tracks, 3, 3)) } }
        compose.onNodeWithText("3 tracks").assertIsDisplayed()
    }
}
