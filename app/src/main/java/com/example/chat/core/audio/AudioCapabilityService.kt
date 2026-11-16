package com.example.chat.core.audio

import kotlinx.coroutines.flow.collect
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Service responsible for querying, caching, and streaming hardware and OS
 * audio capabilities. Exact port of lib/core/audio/audio_capability_service.dart.
 *
 * Dart used ChangeNotifier + broadcast stream subscriptions created in the
 * constructor; the Kotlin port collects the platform flows on an injected
 * scope (constructor-injected for pure-JVM testability) and fans changes out
 * to synchronous listeners.
 */
class AudioCapabilityService(
    private val platform: ChatyAudioPlatform,
    scope: CoroutineScope,
    private val routeTimeoutMillis: Long = 4000L,
    private val routeDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO,
// Previous implementation retained: ) {
) : AudioCapabilitySource {
// Previous implementation retained:     private val cachedCapabilities = mutableMapOf<AudioUseCase, AudioCapabilities>()
    private val cachedCapabilities = java.util.concurrent.ConcurrentHashMap<AudioUseCase, AudioCapabilities>()
    // One serialization point for UI requests, release and platform callback cache updates.
    private val routeMutex = Mutex()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val _communicationCapabilities = kotlinx.coroutines.flow.MutableStateFlow<AudioCapabilities?>(null)
// Previous implementation retained:     val communicationCapabilities: kotlinx.coroutines.flow.StateFlow<AudioCapabilities?> =
    override val communicationCapabilities: kotlinx.coroutines.flow.StateFlow<AudioCapabilities?> =
        _communicationCapabilities.asStateFlow()

    init {
        // Mirrors _initStreams(): capability stream replaces the cached
        // communication capabilities; route stream patches activeRoute only
        // when a communication capability is already cached. Errors are
        // swallowed exactly like the Dart onError: (_) {} handlers.
        scope.launch {
            platform.capabilityChanges
                .stableAudioCapabilities()
                .catch { }
                .collect { caps ->
// Previous implementation retained:                     cachedCapabilities[AudioUseCase.communication] = caps
// Previous implementation retained:                     notifyListeners()
                    routeMutex.withLock {
// Previous implementation retained:                         cachedCapabilities[AudioUseCase.communication] = caps
                        // A debounced device snapshot can predate a manual route change.
                        val currentRoute = platform.getCurrentRoute(AudioUseCase.communication)
                        cachedCapabilities[AudioUseCase.communication] = caps.copy(activeRoute = currentRoute)
                        notifyListeners()
                    }
                }
        }
        scope.launch {
            platform.routeChanges
                .catch { }
                .collect { route ->
// Previous implementation retained:                     val current = cachedCapabilities[AudioUseCase.communication]
// Previous implementation retained:                     if (current != null) {
// Previous implementation retained:                         cachedCapabilities[AudioUseCase.communication] =
// Previous implementation retained:                             current.copy(activeRoute = route)
// Previous implementation retained:                         notifyListeners()
                    routeMutex.withLock {
                        val current = cachedCapabilities[AudioUseCase.communication]
                        if (current != null) {
                            cachedCapabilities[AudioUseCase.communication] = current.copy(activeRoute = route)
                            notifyListeners()
                        }
                    }
                }
        }
    }

    fun addListener(listener: () -> Unit) { listeners.addIfAbsent(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
// Previous implementation retained:     private fun notifyListeners() { listeners.forEach { it() } }
    private fun notifyListeners() {
        _communicationCapabilities.value = cachedCapabilities[AudioUseCase.communication]?.let {
            it.copy(availableRoutes = it.availableRoutes.toList())
        }
        listeners.forEach { it() }
    }

    /** Get capabilities for a use case, returning cache if available or querying platform. */
// Previous implementation retained:     suspend fun getCapabilities(useCase: AudioUseCase): AudioCapabilities {
// Previous implementation retained:     suspend fun getCapabilities(useCase: AudioUseCase): AudioCapabilities = routeMutex.withLock {
    override suspend fun getCapabilities(useCase: AudioUseCase): AudioCapabilities = routeMutex.withLock {
        val cached = cachedCapabilities[useCase]
// Previous implementation retained:         if (cached != null) return cached
// Previous implementation retained:         if (cached != null) return@withLock cached
        if (cached != null && cached.failureKind == null) return@withLock cached
// Previous implementation retained:         val queried = platform.getCapabilities(useCase)
        val queried = kotlinx.coroutines.withContext(routeDispatcher) { platform.getCapabilities(useCase) }
        cachedCapabilities[useCase] = queried
        notifyListeners()
// Previous implementation retained:         return queried
        queried
    }

    /** Active route for communication use case. */
    val activeCommunicationRoute: AudioOutputRoute
        get() = cachedCapabilities[AudioUseCase.communication]?.activeRoute
            ?: AudioOutputRoute.systemDefault

    /** Current dual output status for communication. */
    val communicationDualStatus: DualOutputStatus
        get() = cachedCapabilities[AudioUseCase.communication]?.dualOutputStatus
            ?: DualOutputStatus.notVerified

    /** Check whether independent dual-speaker output is verified active. */
    val isDualOutputActive: Boolean
        get() = communicationDualStatus == DualOutputStatus.active

    /** Available routes for communication. */
    val availableCommunicationRoutes: List<AudioOutputRoute>
        get() = cachedCapabilities[AudioUseCase.communication]?.availableRoutes
            ?: listOf(
                AudioOutputRoute.systemDefault,
                AudioOutputRoute.speaker,
                AudioOutputRoute.receiver,
            )

    /** Only real call acquisition enters the native communication mode. */
    suspend fun beginCommunicationSession() = routeMutex.withLock {
        kotlinx.coroutines.withContext(routeDispatcher) { platform.beginCommunicationSession() }
    }

    /** Request route selection through the platform. */
// Previous implementation retained:     suspend fun setPreferredRoute(useCase: AudioUseCase, route: AudioOutputRoute): AudioCapabilities {
// Previous implementation retained:     suspend fun setPreferredRoute(useCase: AudioUseCase, route: AudioOutputRoute): AudioCapabilities = routeMutex.withLock {
    override suspend fun setPreferredRoute(useCase: AudioUseCase, route: AudioOutputRoute): AudioCapabilities = routeMutex.withLock {
// Previous implementation retained:         val updated = platform.setPreferredRoute(useCase, route)
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        val updated = kotlinx.coroutines.withTimeout(routeTimeoutMillis) {
            // Binder/HAL work must not freeze Compose. Timeout is cooperative, not a HAL abort promise.
            // Previous implementation retained: kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO)
            kotlinx.coroutines.withContext(routeDispatcher) {
                platform.setPreferredRoute(useCase, route)
            }
        }
        cachedCapabilities[useCase] = updated
        notifyListeners()
// Previous implementation retained:         return updated
        updated
    }

    /** Release native session. */
// Previous implementation retained:     suspend fun releaseSession(useCase: AudioUseCase) {
    suspend fun releaseSession(useCase: AudioUseCase) = routeMutex.withLock {
// Previous implementation retained:         platform.releaseSession(useCase)
        kotlinx.coroutines.withContext(routeDispatcher) { platform.releaseSession(useCase) }
        cachedCapabilities.remove(useCase)
        notifyListeners()
    }
}


/** Only availability flaps are coalesced; active-route callbacks stay immediate. */
@OptIn(kotlinx.coroutines.FlowPreview::class)
internal fun kotlinx.coroutines.flow.Flow<AudioCapabilities>.stableAudioCapabilities(): kotlinx.coroutines.flow.Flow<AudioCapabilities> =
    debounce(500L).distinctUntilChanged()
