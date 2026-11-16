package com.example.chat

import com.example.chat.core.audio.DualOutputStatus
import com.example.chat.ui.settings.dualStatusLabelResource
import org.junit.Assert.assertEquals
import org.junit.Test

/** NOT RUN. Verifies the exact status resource IDs used by the rendered badge. */
class AudioStatusLabelMappingGapTest {
    @Test fun allStatusBranchesMapToTheExpectedString() {
        assertEquals(R.string.audio_dual_active, dualStatusLabelResource(DualOutputStatus.active))
        assertEquals(R.string.audio_dual_not_verified, dualStatusLabelResource(DualOutputStatus.notVerified))
        assertEquals(R.string.audio_dual_unavailable, dualStatusLabelResource(DualOutputStatus.unavailable))
        assertEquals(R.string.audio_dual_unsupported, dualStatusLabelResource(DualOutputStatus.unsupported))
        assertEquals(R.string.audio_dual_failed, dualStatusLabelResource(DualOutputStatus.failed))
    }
}
