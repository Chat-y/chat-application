package com.example.chat.ui.settings.conversation

import androidx.compose.ui.graphics.Color
import com.example.chat.core.theme.BubbleStyleId
import com.example.chat.domain.models.ConversationPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Preview playback metadata cases, not media playback tests. Source only, unrun. */
class ConversationPreviewPlaybackTest {
    private fun state() = MoreOptionsPreviewState(BubbleStyleId.stock,
        Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified,
        Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified,
        Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified)
    @Test fun round1PreviewSpeedDefaultsToOne() = assertEquals(1.0, state().playbackSpeed, 0.0)
    @Test fun round2SpeedChangeInvalidatesPreviewState() = assertNotEquals(state(), state().copy(playbackSpeed = 1.5))
    @Test fun round3SpeedDoesNotChangeCustomTrack() = assertEquals(state().voiceProgress, state().copy(playbackSpeed = 2.0).voiceProgress)
    @Test fun round4ExistingStoredSpeedReachesSnapshot() = assertEquals(1.5,
        selectMoreOptionsPreferences(ConversationPreferences(voicePlaybackSpeed = 1.5)).voicePlaybackSpeed, 0.0)
    @Test fun round5SpeedDoesNotSelectAnotherBubble() = assertEquals(state().bubbleStyle,
        state().copy(playbackSpeed = 0.5).bubbleStyle)
}
