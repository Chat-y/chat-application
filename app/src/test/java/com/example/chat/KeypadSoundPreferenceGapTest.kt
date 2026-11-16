package com.example.chat

import com.example.chat.ui.calls.KeypadSoundPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** NOT RUN. Local sound-choice persistence only; no audible sound or global system setting mutation. */
@OptIn(ExperimentalCoroutinesApi::class)
class KeypadSoundPreferenceGapTest {
    @Test fun defaultsOffUntilReadAndFailedWriteNeverEnablesSound() = runTest {
        val readGate = CompletableDeferred<Unit>()
        val prefs = KeypadSoundPreferences(backgroundScope,
            read = { readGate.await(); false }, write = { error("fixture disk failure") })
        assertFalse(prefs.enabled.value)
        prefs.choose(true) // Not ready yet.
        readGate.complete(Unit); runCurrent()
        assertTrue(prefs.ready.value)
        assertFalse(prefs.enabled.value)
        prefs.choose(true); runCurrent()
        assertFalse(prefs.enabled.value)
        assertTrue(prefs.saveFailed.value)
    }
    @Test fun staleEnableCompletionCannotOverrideNewerDisable() = runTest {
        val writeGate = CompletableDeferred<Unit>()
        val writes = mutableListOf<Boolean>()
        val prefs = KeypadSoundPreferences(backgroundScope, read = { false }, write = {
            writes += it
            if (it) writeGate.await()
        })
        runCurrent()
        prefs.choose(true); runCurrent()
        assertFalse(prefs.enabled.value)
        prefs.choose(false)
        writeGate.complete(Unit); runCurrent()
        assertFalse(prefs.enabled.value)
        assertEquals(listOf(true, false), writes)
        assertFalse(prefs.saveFailed.value)
    }
    @Test fun explicitSavedOptInEnablesAndDisableSilencesImmediately() = runTest {
        val prefs = KeypadSoundPreferences(backgroundScope, read = { false }, write = {})
        runCurrent(); prefs.choose(true); runCurrent()
        assertTrue(prefs.enabled.value)
        prefs.choose(false)
        assertFalse(prefs.enabled.value)
    }
}
