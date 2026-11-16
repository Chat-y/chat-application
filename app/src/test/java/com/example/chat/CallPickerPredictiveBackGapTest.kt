package com.example.chat

import com.example.chat.ui.calls.completeCallPickerBack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** NOT RUN. Flow commit/cancel contract only, not real Android back-to-home animation proof. */
class CallPickerPredictiveBackGapTest {
    @Test fun committedGestureCollapsesOnceAndResetsPreview() = runBlocking {
        val frames = mutableListOf<Float>()
        var commits = 0
        completeCallPickerBack(flowOf(0.2f, 0.8f, 1f), { frames += it }) { commits++ }
        assertEquals(1, commits)
        assertEquals(listOf(0.2f, 0.8f, 1f, 0f), frames)
    }
    @Test fun cancelledGestureKeepsKeypadOpenAndResetsPreview() = runBlocking {
        val frames = mutableListOf<Float>()
        var commits = 0
        val cancelled = flow { emit(0.4f); throw CancellationException("Fixture gesture cancelled") }
        val failure = runCatching { completeCallPickerBack(cancelled, { frames += it }) { commits++ } }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertEquals(0, commits)
        assertEquals(listOf(0.4f, 0f), frames)
    }
    @Test fun malformedProgressCannotTranslateOutsideTheSheet() = runBlocking {
        val frames = mutableListOf<Float>()
        completeCallPickerBack(flowOf(Float.NaN, -1f, 4f), { frames += it }) {}
        assertEquals(listOf(0f, 0f, 1f, 0f), frames)
    }
}
