package com.example.chat

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Resource presence fixture, NOT RUN; no device-status verification. */
class AudioStatusResourcesGapTest {
    @Test fun everyDualStatusLabelResolvesWithoutFormattingTokens() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val labels = listOf(R.string.audio_dual_active, R.string.audio_dual_not_verified,
            R.string.audio_dual_unavailable, R.string.audio_dual_unsupported, R.string.audio_dual_failed)
            .map { context.getString(it) }
        assertEquals(5, labels.distinct().size)
        assertTrue(labels.all { it.isNotBlank() && '%' !in it })
    }
}
