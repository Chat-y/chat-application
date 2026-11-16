package com.example.chat.ui.chats.detail
import org.junit.Assert.*
import org.junit.Test
class ConversationHeaderCallRolloutTest {
    @Test fun legacyDefaultsKeepBothShortcuts() { val v=ConversationHeaderAppearance.fromMap(emptyMap()).effective();assertTrue(v.audioCallVisible);assertTrue(v.videoCallVisible) }
    @Test fun optInSplitsAudioAndVideo() { val v=ConversationHeaderAppearance.fromMap(mapOf(ConversationHeaderAppearance.OPTIONS_ENABLED to true,ConversationHeaderAppearance.AUDIO_VISIBLE to false)).effective();assertFalse(v.audioCallVisible);assertTrue(v.videoCallVisible) }
    @Test fun rollbackKeepsSavedChoicesButIgnoresThem() { val map=mapOf<String,Any?>(ConversationHeaderAppearance.VIDEO_VISIBLE to false);val saved=ConversationHeaderAppearance.fromMap(map);assertFalse(saved.videoCallVisible);assertTrue(saved.effective().videoCallVisible);assertEquals(false,map[ConversationHeaderAppearance.VIDEO_VISIBLE]) }
    @Test fun resetDefaultsIncludeSplitKeys() { val map=ConversationHeaderAppearance.defaultValues();assertEquals(true,map[ConversationHeaderAppearance.AUDIO_VISIBLE]);assertEquals(true,map[ConversationHeaderAppearance.VIDEO_VISIBLE]);assertEquals(false,map[ConversationHeaderAppearance.OPTIONS_ENABLED]) }
    @Test fun oldCombinedHideStillPreventsOverflow() = assertFalse(conversationHeaderCallsInOverflow(false,true))
}
