package com.example.chat

import com.example.chat.ui.settings.completeAudioBackProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** NOT RUN. Progress/commit/cancel only, no actual Android gesture or pixels. */
class AudioSettingsPredictiveBackGapTest {
    @Test fun completeGestureExitsOnceAndResetsTranslation() = runBlocking {
        val frames = mutableListOf<Float>(); var exits = 0
        completeAudioBackProgress(flowOf(0.1f, 0.9f), { it }, { frames += it }) { exits++ }
        assertEquals(1, exits)
        assertEquals(listOf(0.1f, 0.9f, 0f), frames)
    }
    @Test fun cancellationResetsWithoutExiting() = runBlocking {
        val frames = mutableListOf<Float>(); var exits = 0
        val events = flow { emit(0.5f); throw CancellationException("fixture cancel") }
        val failure = runCatching {
            completeAudioBackProgress(events, { it }, { frames += it }) { exits++ }
        }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertEquals(0, exits)
        assertEquals(listOf(0.5f, 0f), frames)
    }
    @Test fun invalidProgressStaysInsidePageBounds() = runBlocking {
        val frames = mutableListOf<Float>()
        completeAudioBackProgress(flowOf(-1f, 3f, Float.NaN), { it }, { frames += it }) {}
        assertEquals(listOf(0f, 1f, 0f, 0f), frames)
    }
}
