package com.example.chat

import com.example.chat.core.audio.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** NOT RUN. Typed failures and retry/ownership seams only; no permission or HAL simulation. */
@OptIn(ExperimentalCoroutinesApi::class)
class AudioTypedFailureGapTest {
    private class Platform : ChatyAudioPlatform {
        var capabilities = failedAudioCapabilities(AudioUseCase.communication, SecurityException("private native detail"))
        var queries = 0
        var releases = 0
        override val capabilityChanges = emptyFlow<AudioCapabilities>()
        override val routeChanges = emptyFlow<AudioOutputRoute>()
        override suspend fun getCapabilities(useCase: AudioUseCase): AudioCapabilities { queries++; return capabilities }
        override suspend fun getCurrentRoute(useCase: AudioUseCase) = capabilities.activeRoute
        override suspend fun setPreferredRoute(useCase: AudioUseCase, route: AudioOutputRoute) = capabilities
        override suspend fun getDualOutputStatus(useCase: AudioUseCase) = DualOutputStatus.notVerified
        override suspend fun releaseSession(useCase: AudioUseCase) { releases++ }
    }
    @Test fun errorsAreTypedAndPrivateExceptionTextIsNotCopied() {
        assertEquals(AudioFailureKind.permissionDenied, classifyAudioFailure(SecurityException("secret")))
        assertEquals(AudioFailureKind.invalidState, classifyAudioFailure(IllegalStateException("secret")))
        assertEquals(AudioFailureKind.platformUnavailable, classifyAudioFailure(Exception("secret")))
        val caps = failedAudioCapabilities(AudioUseCase.communication, SecurityException("private device address"))
        assertFalse(caps.failureReason!!.contains("private device address"))
        assertEquals("android", caps.platform)
    }
    @Test fun failedSnapshotIsRequeriedAndSuccessfulSnapshotCanBeCached() = runTest {
        val platform = Platform()
        val service = AudioCapabilityService(platform, backgroundScope)
        assertNotNull(service.getCapabilities(AudioUseCase.communication).failureKind)
        platform.capabilities = defaultAudioCapabilities(AudioUseCase.communication)
        assertNull(service.getCapabilities(AudioUseCase.communication).failureKind)
        service.getCapabilities(AudioUseCase.communication)
        assertEquals(2, platform.queries)
    }
    @Test fun typedSetupFailureCannotBecomeSuccessfulCallAndProvisionalOwnerIsReleased() = runTest {
        val platform = Platform()
        val service = AudioCapabilityService(platform, backgroundScope,
            routeDispatcher = coroutineContext[kotlin.coroutines.ContinuationInterceptor] as kotlinx.coroutines.CoroutineDispatcher)
        val coordinator = AudioPolicyCoordinator(service)
        val sessions = OwnedCallAudioSessions(CallAudioController(coordinator, service))
        assertFalse(sessions.start(sessions.newOwner(), false))
        assertEquals(1, platform.releases)
        assertNull(coordinator.activeOwner)
        assertEquals(AudioSessionState.idle, coordinator.state)
    }
    @Test fun typedRouteFailureNeverReportsSuccessOrOverwritesKnownRoute() = runTest {
        val platform = Platform()
        platform.capabilities = defaultAudioCapabilities(AudioUseCase.communication).copy(activeRoute = AudioOutputRoute.receiver)
        val service = AudioCapabilityService(platform, backgroundScope,
            routeDispatcher = coroutineContext[kotlin.coroutines.ContinuationInterceptor] as kotlinx.coroutines.CoroutineDispatcher)
        val coordinator = AudioPolicyCoordinator(service)
        assertTrue(coordinator.startCallSession(AudioOutputRoute.receiver))
        platform.capabilities = failedAudioCapabilities(AudioUseCase.communication, IllegalStateException("native detail"))
        assertFalse(coordinator.setCallRoute(AudioOutputRoute.bluetooth))
        assertEquals(AudioOutputRoute.receiver, coordinator.activeRoute)
        assertFalse(coordinator.setCallRouteWithoutFallback(AudioOutputRoute.systemDefault))
        assertEquals(AudioOutputRoute.receiver, coordinator.activeRoute)
    }
    @Test fun cleanupAttemptsEveryRestorationAndPreservesFirstFailure() {
        val seen = mutableListOf<Int>()
        val first = SecurityException("first")
        val second = IllegalStateException("second")
        val error = runCatching { attemptAudioCleanup(listOf(
            { seen.add(1); throw first },
            { seen.add(2); throw second },
            { seen.add(3); Unit },
        )) }.exceptionOrNull()
        assertSame(first, error)
        assertEquals(listOf(1, 2, 3), seen)
        assertEquals(listOf(second), error!!.suppressed.toList())
    }
}
