package com.example.chat.ui.settings.conversation

import androidx.test.platform.app.InstrumentationRegistry
import com.example.chat.R
import org.junit.Assert.*
import org.junit.Test

/** Source-only copy contracts. Not executed. */
class ConversationExtrasCopyTest {
    private val resources get() = InstrumentationRegistry.getInstrumentation().targetContext.resources
    @Test fun emojiTitlesUseOneSpelling() {
        listOf(R.string.conv_ui_emoji_header_color_1c7dd8, R.string.conv_ui_emoji_header_icons_color_85cd20,
            R.string.conv_ui_emoji_picker_background_colo_56e57d).forEach {
            val copy = resources.getString(it); assertTrue(copy.contains("colour")); assertFalse(copy.contains("color"))
        }
    }
    @Test fun emojiSubtitlesAreShortSentences() {
        listOf(R.string.conv_ui_change_emoji_popup_header_ba_3f92a1, R.string.conv_ui_change_emoji_popup_icons_col_5606e8,
            R.string.conv_ui_change_emoji_popup_backgroun_23fe59).forEach { assertTrue(resources.getString(it).length <= 40) }
    }
    @Test fun voiceSubtitleNamesTheControl() {
        assertEquals("Play button on voice notes", resources.getString(R.string.conv_ui_change_color_of_voice_messag_4cd41b))
    }
    @Test fun linkSubtitleKeepsBothDirections() {
        val copy = resources.getString(R.string.conv_ui_links_in_incoming_and_outgoi_7b17b4)
        assertTrue(copy.contains("sent")); assertTrue(copy.contains("received")); assertTrue(copy.length <= 40)
    }
    @Test fun systemSubtitleKeepsDateAndCallMeaning() {
        val copy = resources.getString(R.string.conv_ui_background_of_date_and_misse_0116fd)
        assertTrue(copy.contains("date")); assertTrue(copy.contains("call")); assertTrue(copy.length <= 40)
    }
}
