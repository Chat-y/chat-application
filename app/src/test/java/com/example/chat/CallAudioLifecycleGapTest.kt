package com.example.chat

import com.example.chat.core.audio.AudioOutputRoute
import com.example.chat.core.audio.AudioUseCase
import com.example.chat.core.audio.CallAudioSessionDriver
import com.example.chat.core.audio.OwnedCallAudioSessions
import com.example.chat.ui.settings.settingsRouteBlocked
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CallAudioLifecycleGapTest {
    private class Driver : CallAudioSessionDriver {
        override var activeRoute = AudioOutputRoute.receiver
        val events = mutableListOf<String>()
        var startGate: CompletableDeferred<Unit>? = null
        var startResult = true
        var startFailure: Throwable? = null
        var routeMatches = true
        var routeGate: CompletableDeferred<Unit>? = null
        override suspend fun initializeCallAudio(isVideo: Boolean): Boolean {
            events += "start:$isVideo"
            startGate?.await()
            startFailure?.let { throw it }
            return startResult
        }
        override suspend fun switchMediaRoute(targetRoute: AudioOutputRoute): Boolean {
            events += "switch:$targetRoute"
            routeGate?.await()
            if (routeMatches) activeRoute = targetRoute
            return true
        }
        override suspend fun terminateCallAudio() { events += "end" }
    }

    @Test fun `replacement releases old audio and stale dispose leaves new owner intact`() = runTest {
        val driver = Driver(); val sessions = OwnedCallAudioSessions(driver)
        val old = sessions.newOwner(); val next = sessions.newOwner()
        assertTrue(sessions.start(old, false))
        assertTrue(sessions.start(next, true))
        sessions.end(old)
        assertEquals(listOf("start:false", "end", "start:true"), driver.events)
        assertFalse(sessions.switchRoute(old, AudioOutputRoute.speaker))
        assertTrue(sessions.switchRoute(next, AudioOutputRoute.speaker))
        sessions.end(next); sessions.end(next)
        assertEquals(2, driver.events.count { it == "end" })
    }

    @Test fun `older preparation cannot acquire audio after newer transport started`() = runTest {
        val driver = Driver(); val sessions = OwnedCallAudioSessions(driver)
        val old = sessions.newOwner(); val next = sessions.newOwner()
        assertTrue(sessions.start(next, true))
        assertFalse(sessions.start(old, false))
        sessions.end(old)
        assertEquals(listOf("start:true"), driver.events)
        sessions.end(next)
    }

    @Test fun `failed and cancelled initialization release provisional audio`() = runTest {
        val driver = Driver(); val sessions = OwnedCallAudioSessions(driver)
        driver.startResult = false
        assertFalse(sessions.start(sessions.newOwner(), false))
        driver.startFailure = CancellationException("cancel initialization")
        try { sessions.start(sessions.newOwner(), true); fail("cancellation must propagate") }
        catch (_: CancellationException) { }
        assertEquals(listOf("start:false", "end", "start:true", "end"), driver.events)
    }

    @Test fun `native nonthrowing mismatch is not a successful route switch`() = runTest {
        val driver = Driver(); val sessions = OwnedCallAudioSessions(driver); val owner = sessions.newOwner()
        sessions.start(owner, false); driver.routeMatches = false
        assertFalse(sessions.switchRoute(owner, AudioOutputRoute.speaker))
        sessions.end(owner)
    }

    @Test fun `teardown waits for route transition and ends once`() = runTest {
        val driver = Driver(); val sessions = OwnedCallAudioSessions(driver); val owner = sessions.newOwner()
        sessions.start(owner, false)
        driver.routeGate = CompletableDeferred()
        val route = async { sessions.switchRoute(owner, AudioOutputRoute.speaker) }
        runCurrent()
        val end = async { sessions.end(owner) }
        runCurrent()
        assertFalse(end.isCompleted)
        driver.routeGate!!.complete(Unit)
        assertTrue(route.await()); end.await()
        assertEquals("end", driver.events.last())
    }

    @Test fun `settings block both real call state and coordinator ownership`() {
        assertTrue(settingsRouteBlocked(true, null))
        assertTrue(settingsRouteBlocked(false, AudioUseCase.communication))
        assertFalse(settingsRouteBlocked(false, AudioUseCase.media))
        assertFalse(settingsRouteBlocked(false, null))
    }

    @Test fun `cancelling suspended initialization still releases its audio owner`() = runTest {
        val driver = Driver(); val sessions = OwnedCallAudioSessions(driver)
        driver.startGate = CompletableDeferred()
        val start = async { sessions.start(sessions.newOwner(), false) }
        runCurrent()
        start.cancelAndJoin()
        assertEquals(listOf("start:false", "end"), driver.events)
        driver.startGate = null
        val replacement = sessions.newOwner()
        assertTrue(sessions.start(replacement, true))
        sessions.end(replacement)
    }

}
