package com.example.chat.ui.chats.starred

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** UI source guards only, authored but not executed in source-only batch. */
class StatusProposalSourceTest {
    private fun parts() = File("src/main/kotlin/com/example/chat/ui/updates/UpdatesScreenParts.kt").readText()
    @Test fun audioSeekHasNameValueAndDisabledState() {
        val s = parts()
        assertTrue(s.contains("contentDescription = sliderLabel; stateDescription = sliderDescription"))
        assertTrue(s.contains("enabled = !loading && !failed && durationMs > 0"))
    }
    @Test fun composerUsesPersistentLabelAndNamedProgress() {
        val s = parts()
        assertTrue(s.contains("label = { Text(stringResource(R.string.social_status_composer_input)) }"))
        assertTrue(s.contains("contentDescription = publishingLabel"))
    }
    @Test fun zoomAlternativesAndStableViewportRemain() {
        val s = parts()
        assertTrue(s.contains("R.string.social_status_zoom_in"))
        assertTrue(s.contains("R.string.social_status_zoom_out"))
        assertTrue(s.contains("R.string.social_status_zoom_reset"))
        assertTrue(s.contains("clip(RoundedCornerShape(18.dp))"))
    }
}
