package com.example.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.Locale

/** MR-R4-043/CAM-R4-004 format fixtures, NOT RUN. Not locale/RTL/font/pixel proof. */
class ReleasedMediaStringResourceTest {
    @get:Rule val compose = createComposeRule()
    private fun englishString(id: Int, vararg args: Any): String {
        var result = ""
        compose.setContent {
            MaterialTheme {
                val base = LocalContext.current
                val configuration = android.content.res.Configuration(base.resources.configuration)
                configuration.setLocale(Locale.ENGLISH)
                result = base.createConfigurationContext(configuration).getString(id, *args)
            }
        }
        compose.waitForIdle()
        return result
    }
    @Test fun effectIntensityKeepsExactlyOnePercentSign() {
        assertEquals("100%", englishString(R.string.camera_effect_intensity_percent, 100))
    }
    @Test fun editorPercentageKeepsValueAndUnit() {
        assertEquals("Text size: 7% of image width", englishString(R.string.media_released_editor_size_percent, 7))
    }
    @Test fun startEndKeepsThreeSpaceDelimiter() {
        assertEquals("Start: 0:01.00   End: 0:03.00", englishString(R.string.media_released_trim_boundaries, "0:01.00", "0:03.00"))
    }
    @Test fun editSummaryBindsAllTenArgumentsInOrder() {
        assertEquals("2 strokes, saturation 100 percent, warmth -5 percent, tint 8 percent, fade 10 percent, brightness +4 percent, contrast 120 percent, text alignment Left, text strip off, text: hello",
            englishString(R.string.media_released_editor_summary, 2, 100, -5, 8, 10, "+4", "120", "Left", "off", "hello"))
    }
    @Test fun thumbnailKeepsPositionTypeAndFilename() {
        assertEquals("Attachment 2 of 4, video, clip.mp4", englishString(R.string.media_released_review_thumbnail_description, 2, 4, "video", "clip.mp4"))
    }
    @Test fun captionCountAcceptsZeroRemaining() {
        assertEquals("0 characters remaining", englishString(R.string.media_released_review_caption_remaining, 0))
    }
    @Test fun lensFavoriteFormatKeepsApostropheInName() {
        assertEquals("Add Maya's lens to favorites", englishString(R.string.camera_lens_add_favorite, "Maya's lens"))
    }
    @Test fun sectionFormatUsesDisplayLabelWithoutChangingKey() {
        assertEquals("Text controls: Entry label", englishString(R.string.media_released_editor_section_format, "Text controls", "Entry label"))
    }
    @Test fun unknownPickerTypeKeepsExactDisplayString() {
        var result = ""
        compose.setContent { result = com.example.chat.ui.chats.detail.reviewTypeDisplayLabel("custom/PHOTO") }
        compose.runOnIdle { assertEquals("custom/PHOTO", result) }
    }
    @Test fun emptyPickerTypeIsNotRenamedFromMimeGuess() {
        var result = "unset"
        compose.setContent { result = com.example.chat.ui.chats.detail.reviewTypeDisplayLabel("") }
        compose.runOnIdle { assertEquals("", result) }
    }
}
